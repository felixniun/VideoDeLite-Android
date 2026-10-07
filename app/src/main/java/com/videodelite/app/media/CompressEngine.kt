package com.videodelite.app.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
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
     *
     * Every Transformer call (build/start/getProgress/cancel) must happen on
     * a Looper thread — this whole block runs on the main looper; media3 does
     * the actual encoding on its own worker threads.
     */
    suspend fun compress(
        info: VideoInfo,
        codec: VideoCodec,
        quality: Quality,
        pro: ProConfig? = null,
        outputFile: File,
        onProgress: (Int) -> Unit,
    ): Unit = withContext(Dispatchers.Main) {
        val videoSettings = if (pro == null) {
            val mbps = Bitrate.simpleBitrateMbps(
                info.width, info.height, info.fps, codec.id, quality.id,
            )
            VideoEncoderSettings.Builder()
                .setBitrate((mbps * 1_000_000).toInt())
                .build()
        } else {
            // media3 1.5.1 exposes only setBitrate/setBitrateMode. Setting a
            // platform bitrate mode that the device's encoder does not accept
            // makes MediaCodec.configure() fail outright (Media3 never falls
            // back) — that is exactly what broke Professional mode on real
            // hardware while Simple mode kept working. So encode with an
            // average bitrate only; the rate-control choice decides how that
            // bitrate is derived.
            val bps = proBitrateBps(info, pro)
            android.util.Log.d(
                "VdExport",
                "pro encode: rateControl=${pro.rateControl.id} quality=${pro.quality} bitrate=$bps",
            )
            VideoEncoderSettings.Builder().setBitrate(bps).build()
        }

        val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoSettings)
        pro?.let { encoderFactoryBuilder.setRequestedAudioEncoderSettings(audioSettings(it)) }
        val encoderFactory = encoderFactoryBuilder.build()

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
     * Audio settings for Professional mode. media3's AudioEncoderSettings
     * only exposes `setBitrate` (no quality/VBR knob), so the desktop's
     * audio-VBR mode is approximated by its equivalent average bitrate; the
     * clamp matches the desktop's 64–512 kbps range.
     */
    private fun audioSettings(pro: ProConfig): AudioEncoderSettings =
        AudioEncoderSettings.Builder()
            .setBitrate(pro.audioBitrateKbps.coerceIn(64, 512) * 1000)
            .build()

    /**
     * Resolves the Professional-mode target bitrate (bits/s).
     *
     * CBR and VBR both target an average bitrate — without a platform rate
     * mode they behave the same at the encoder, but the mode still decides how
     * the number is interpreted by the user. CQ has no CRF/quality API in
     * media3 1.5.1, so constant quality is approximated by deriving a bitrate
     * from bits-per-pixel at the source resolution and frame rate.
     */
    private fun proBitrateBps(info: VideoInfo, pro: ProConfig): Int {
        val mbps = when (pro.rateControl) {
            RateControl.CBR, RateControl.VBR -> pro.bitrateMbps
            RateControl.CQ -> qualityDerivedMbps(info, pro.quality)
        }
        val clamped = mbps.coerceIn(ProConfig.MIN_BITRATE_MBPS, ProConfig.MAX_BITRATE_MBPS)
        return (clamped * 1_000_000).toInt()
    }

    /** Maps a 0–51 quality value (lower is better) onto a bits-per-pixel target. */
    private fun qualityDerivedMbps(info: VideoInfo, quality: Int): Double {
        val q = quality.coerceIn(ProConfig.MIN_QUALITY, ProConfig.MAX_QUALITY)
        val t = q.toDouble() / ProConfig.MAX_QUALITY.toDouble()
        val bpp = BEST_BPP - (BEST_BPP - WORST_BPP) * t
        val fps = if (info.fps > 0) info.fps else 30.0
        val pixels = info.width.toDouble() * info.height.toDouble()
        if (pixels <= 0) return ProConfig.MIN_BITRATE_MBPS
        return pixels * fps * bpp / 1_000_000.0
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

    private companion object {
        /** Bits-per-pixel at quality 0 (best) and 51 (worst) for CQ mode. */
        const val BEST_BPP = 0.20
        const val WORST_BPP = 0.03
    }
}
