package com.videodelite.app.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import com.videodelite.app.core.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class TaskState { QUEUED, ANALYZING, COMPRESSING, DONE, FAILED, CANCELED }

data class CompressionTask(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val codec: VideoCodec,
    val quality: Quality,
    val state: TaskState = TaskState.QUEUED,
    val progress: Int = 0,
    val sourceSizeBytes: Long = 0,
    val outputSizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

/**
 * Owns the compression queue: parallelism capped by the user setting (1–3),
 * history writes on completion, and the foreground-service lifecycle hook.
 * Lives as an app-scoped singleton so tasks survive activity recreation.
 */
class TaskManager(
    private val context: Context,
    private val analyzer: Analyzer,
    private val engine: CompressEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nextId = AtomicLong(1)
    private val activeCount = AtomicInteger(0)
    private val jobs = mutableMapOf<Long, Job>()

    private val _tasks = MutableStateFlow<List<CompressionTask>>(emptyList())
    val tasks: StateFlow<List<CompressionTask>> = _tasks.asStateFlow()

    private val _parallelLimit = MutableStateFlow(1)
    val parallelLimit: StateFlow<Int> = _parallelLimit.asStateFlow()

    fun setParallelism(n: Int) {
        _parallelLimit.value = n.coerceIn(1, 3)
    }

    fun enqueue(uris: List<Uri>, codec: VideoCodec, quality: Quality) {
        if (uris.isEmpty()) return
        val created = uris.map { uri ->
            CompressionTask(
                id = nextId.getAndIncrement(),
                uri = uri,
                displayName = uri.lastPathSegment ?: "video",
                codec = codec,
                quality = quality,
            )
        }
        _tasks.value = _tasks.value + created
        created.forEach { task -> jobs[task.id] = scope.launch { runTask(task.id) } }
        // Keep the foreground service alive while work exists.
        val intent = Intent(context, ExportService::class.java)
        runCatching { context.startForegroundService(intent) }
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter {
            it.state == TaskState.QUEUED || it.state == TaskState.ANALYZING || it.state == TaskState.COMPRESSING
        }
    }

    fun activeCount(): Int = _tasks.value.count {
        it.state == TaskState.QUEUED || it.state == TaskState.ANALYZING || it.state == TaskState.COMPRESSING
    }

    private fun update(id: Long, transform: (CompressionTask) -> CompressionTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun waitForSlot() {
        while (activeCount.get() >= _parallelLimit.value) delay(200)
    }

    private suspend fun runTask(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        waitForSlot()
        activeCount.incrementAndGet()
        val startedAt = System.currentTimeMillis()
        var tempFile: File? = null
        try {
            update(id) { it.copy(state = TaskState.ANALYZING) }
            val info = analyzer.analyze(task.uri)
            update(id) {
                it.copy(
                    state = TaskState.COMPRESSING,
                    sourceSizeBytes = info.sizeBytes,
                    width = info.width,
                    height = info.height,
                    durationMs = info.durationMs,
                )
            }

            if (task.codec == VideoCodec.H265 && !engine.hasHevcEncoder()) {
                throw IllegalStateException("no HEVC encoder")
            }
            if (!OutputSaver.hasLegacyWritePermission(context)) {
                throw SecurityException("storage permission")
            }

            val out = File(context.cacheDir, "vd_out_$id.mp4")
            tempFile = out
            engine.compress(info, task.codec, task.quality, out) { p ->
                update(id) { if (it.state == TaskState.COMPRESSING) it.copy(progress = p) else it }
            }
            if (!engine.validateOutput(out, info.durationMs)) {
                throw IllegalStateException("output validation failed")
            }

            val saved = OutputSaver.save(context, Format.outputBaseName(info.displayName, startedAt), out)
            val outputSize = out.length()
            update(id) {
                it.copy(
                    state = TaskState.DONE,
                    progress = 100,
                    outputSizeBytes = outputSize,
                    displayName = info.displayName,
                )
            }
            AppGraph.db.historyDao().insert(
                com.videodelite.app.data.HistoryEntry(
                    displayName = info.displayName,
                    codec = task.codec.id,
                    quality = task.quality.id,
                    sourceSizeBytes = info.sizeBytes,
                    outputSizeBytes = outputSize,
                    width = info.width,
                    height = info.height,
                    videoDurationMs = info.durationMs,
                    compressDurationMs = System.currentTimeMillis() - startedAt,
                    status = "success",
                    error = null,
                    outputName = saved.second,
                )
            )
        } catch (e: CancellationException) {
            update(id) { it.copy(state = TaskState.CANCELED) }
            throw e
        } catch (e: Exception) {
            val msg = when (e) {
                is Analyzer.AnalyzerException -> context.getString(R.string.error_probe_failed)
                is SecurityException -> context.getString(R.string.permission_denied)
                is CompressEngine.ExportFailure -> e.exportError.message ?: e.message ?: "export failed"
                else -> e.message ?: "error"
            }
            val t = _tasks.value.firstOrNull { it.id == id }
            update(id) { it.copy(state = TaskState.FAILED, error = msg) }
            AppGraph.db.historyDao().insert(
                com.videodelite.app.data.HistoryEntry(
                    displayName = t?.displayName ?: task.displayName,
                    codec = task.codec.id,
                    quality = task.quality.id,
                    sourceSizeBytes = t?.sourceSizeBytes ?: 0,
                    outputSizeBytes = 0,
                    width = t?.width ?: 0,
                    height = t?.height ?: 0,
                    videoDurationMs = t?.durationMs ?: 0,
                    compressDurationMs = System.currentTimeMillis() - startedAt,
                    status = "failed",
                    error = msg,
                    outputName = null,
                )
            )
        } finally {
            activeCount.decrementAndGet()
            tempFile?.delete()
            jobs.remove(id)
        }
    }
}
