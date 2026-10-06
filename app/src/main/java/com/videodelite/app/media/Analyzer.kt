package com.videodelite.app.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Probes a video via MediaExtractor (the framework equivalent of ffprobe):
 * resolution (rotation-aware), duration, fps, codec family and bitrate.
 */
class Analyzer(private val context: Context) {

    suspend fun analyze(uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "video"
        var sizeBytes = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) c.getString(nameIdx)?.let { displayName = it }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) sizeBytes = c.getLong(sizeIdx)
            }
        }

        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw AnalyzerException("cannot open $uri")
        pfd.use { fd ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(fd.fileDescriptor)
                val trackIndex = findVideoTrack(extractor)
                    ?: throw AnalyzerException("no video track")
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)

                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else 0L
                val durationMs = if (durationUs > 0) durationUs / 1000 else 0L

                var width = format.getInteger(MediaFormat.KEY_WIDTH)
                var height = format.getInteger(MediaFormat.KEY_HEIGHT)

                val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/unknown"
                val rotation = if (format.containsKey("rotation-degrees")) {
                    format.getInteger("rotation-degrees")
                } else 0
                if (rotation == 90 || rotation == 270) {
                    val t = width; width = height; height = t
                }

                val fps = probeFps(extractor, format, durationMs)
                val bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                } else if (durationMs > 0 && sizeBytes > 0) {
                    sizeBytes * 8 / (durationMs / 1000.0).toLong().coerceAtLeast(1)
                } else 0L

                val codec = when (mime) {
                    "video/avc" -> "h264"
                    "video/hevc" -> "h265"
                    else -> "other"
                }

                VideoInfo(
                    uri = uri,
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    fps = fps,
                    codec = codec,
                    bitrateBps = bitrate,
                )
            } catch (e: AnalyzerException) {
                android.util.Log.e("VdAnalyzer", "probe failed for $uri", e)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("VdAnalyzer", "probe failed for $uri", e)
                throw AnalyzerException("probe failed: ${e.message}", e)
            } finally {
                extractor.release()
            }
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return null
    }

    /** KEY_FRAME_RATE when present, else estimated from the first sample timestamps. */
    private fun probeFps(extractor: MediaExtractor, format: MediaFormat, durationMs: Long): Double {
        // Some muxers store KEY_FRAME_RATE as int, others as float.
        val declared: Float? = try {
            format.getFloat(MediaFormat.KEY_FRAME_RATE)
        } catch (e: Exception) {
            try {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } catch (e: Exception) {
                null
            }
        }
        if (declared != null && declared > 1f) return declared.toDouble()
        var first = -1L
        var last = -1L
        var count = 0
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (count < 60) {
            val pts = extractor.sampleTime
            if (pts < 0) break
            if (first < 0) first = pts
            last = pts
            count++
            if (!extractor.advance()) break
        }
        if (first >= 0 && last > first && count > 1) {
            val seconds = (last - first) / 1_000_000.0
            val fps = (count - 1) / seconds
            if (fps in 5.0..240.0) return fps
        }
        return 30.0
    }

    class AnalyzerException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
