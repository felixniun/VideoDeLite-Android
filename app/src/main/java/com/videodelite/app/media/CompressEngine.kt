package com.videodelite.app.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.videodelite.app.core.Bitrate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Hardware-first compression on top of Media3 Transformer. The default
 * encoder factory prefers hardware codecs and falls back to software for
 * H.264; H.265 requires a device hardware encoder (checked by callers).
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CompressEngine(private val context: Context) {

    fun hasHevcEncoder(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any {
            it.isEncoder && it.supportedTypes.contains("video/hevc")
        }
    }.getOrDefault(false)

    /**
     * Compresses [info] into [outputFile] (MP4) using the frozen bitrate
     * table, reporting progress 0–100 through [onProgress].
     */
    suspend fun compress(
        info: VideoInfo,
        codec: VideoCodec,
        quality: Quality,
        outputFile: File,
        onProgress: (Int) -> Unit,
    ): Unit = withContext(Dispatchers.Default) {
        val mbps = Bitrate.simpleBitrateMbps(
            info.width, info.height, info.fps, codec.id, quality.id,
        )
        val bitrateBps = (mbps * 1_000_000).toInt()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(bitrateBps).build()
            )
            .build()

        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .build()

        val done = CompletableDeferred<Unit>()
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                done.complete(Unit)
            }

            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                done.completeExceptionally(ExportFailure(exportException))
            }
        })

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(info.uri)).build()
        transformer.start(edited, outputFile.absolutePath)

        try {
            // Poll progress while the export runs (0–100 percent).
            val holder = ProgressHolder()
            var last = 0
            while (!done.isCompleted) {
                transformer.getProgress(holder)
                if (holder.progress in 1..100 && holder.progress > last) {
                    last = holder.progress
                    onProgress(last)
                }
                delay(200)
            }
            done.await()
        } catch (e: CancellationException) {
            runCatching { transformer.cancel() }
            throw e
        }
    }

    /**
     * Output validation, mirroring the desktop's ffprobe re-check: the file
     * must contain a video track and keep a plausible duration.
     */
    suspend fun validateOutput(outputFile: File, sourceDurationMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!outputFile.exists() || outputFile.length() == 0L) return@withContext false
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(outputFile.absolutePath)
                var videoMs = 0L
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        videoMs = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
                        break
                    }
                }
                extractor.release()
                if (videoMs <= 0) return@withContext false
                if (sourceDurationMs <= 0) return@withContext true
                val tolerance = max(2_000L, sourceDurationMs / 10)
                abs(videoMs - sourceDurationMs) <= tolerance
            } catch (e: Exception) {
                false
            }
        }

    /** Wraps ExportException with a readable message. */
    class ExportFailure(val exportError: ExportException) : Exception(exportError.message, exportError)
}
