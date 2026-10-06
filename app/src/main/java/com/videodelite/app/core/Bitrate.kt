package com.videodelite.app.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Simple Mode bitrate policy, ported 1:1 from the desktop app's
 * `internal/encoder/bitrate.go` (plan §17–§21):
 *
 *  - H.264 table (Mbps), buckets = resolution class × FPS bucket
 *  - H.265 = H.264 × 0.75, rounded to 0.5 Mbps granularity
 *  - below 720p: 720p baseline scaled by pixel ratio, floor 1 Mbps
 *  - above 4K:   4K baseline scaled by pixel ratio, capped 120 Mbps
 *  - non-standard FPS keeps the source FPS; nearest bucket only picks bitrate
 */
object Bitrate {

    // [class|bucket][quality] in Mbps. quality index: 0 low, 1 mid, 2 high.
    private val h264Table = mapOf(
        "720|24-30" to doubleArrayOf(1.8, 3.0, 4.5),
        "720|48-60" to doubleArrayOf(2.5, 4.0, 6.0),
        "1080|24-30" to doubleArrayOf(3.5, 5.0, 8.0),
        "1080|48-60" to doubleArrayOf(5.0, 7.0, 10.0),
        "1440|24-30" to doubleArrayOf(6.0, 10.0, 16.0),
        "1440|48-60" to doubleArrayOf(8.0, 14.0, 20.0),
        "2160|24-30" to doubleArrayOf(10.0, 16.0, 25.0),
        "2160|48-60" to doubleArrayOf(14.0, 20.0, 30.0),
    )

    private const val MAX_BITRATE_CAP_MBPS = 120.0
    private const val MIN_BITRATE_MBPS = 1.0

    /**
     * Classifies by the short side so portrait phone video (1080×1920)
     * lands in the 1080p bucket, same as the desktop implementation.
     */
    fun resolutionClass(width: Int, height: Int): Int {
        val short = min(width, height)
        return when {
            short >= 2160 -> 2160
            short >= 1440 -> 1440
            short >= 1080 -> 1080
            short >= 720 -> 720
            else -> short // sub-720p: caller applies pixel-ratio scaling
        }
    }

    /** Picks the nearest frozen FPS tier; source FPS is never modified. */
    fun fpsBucket(fps: Double): String = if (fps > 40) "48-60" else "24-30"

    private fun qualityIndex(quality: String): Int = when (quality) {
        "low" -> 0
        "high" -> 2
        else -> 1
    }

    private fun bucketKey(classValue: Int, bucket: String): String {
        return when (classValue) {
            720, 1080, 1440, 2160 -> "$classValue|$bucket"
            else -> if (classValue > 2160) "2160|$bucket" else "720|$bucket"
        }
    }

    /** Computes the Simple Mode target bitrate in Mbps. */
    fun simpleBitrateMbps(
        width: Int,
        height: Int,
        fps: Double,
        codec: String,
        quality: String,
    ): Double {
        val q = qualityIndex(quality)
        val classValue = resolutionClass(width, height)
        val bucket = fpsBucket(fps)
        val key = bucketKey(classValue, bucket)

        var mbps: Double
        if (classValue > 2160) {
            // above 4K: 4K baseline × pixel ratio, capped
            val base = h264Table.getValue("2160|$bucket")[q]
            mbps = base * (width * height).toDouble() / (2160.0 * 2160.0)
            mbps = min(mbps, MAX_BITRATE_CAP_MBPS)
        } else if (classValue < 720) {
            // below 720p: 720p baseline × pixel ratio, floor 1 Mbps
            val base = h264Table.getValue("720|$bucket")[q]
            val pixels = (width * height).toDouble()
            val basePixels = 1280.0 * 720.0
            mbps = base * (pixels / basePixels)
            mbps = max(mbps, MIN_BITRATE_MBPS)
        } else {
            mbps = h264Table.getValue(key)[q]
        }

        if (codec == "h265" || codec == "hevc") {
            // H.265 ≈ H.264 × 0.75, rounded to 0.5 Mbps granularity
            mbps = (mbps * 0.75 * 2).roundToInt() / 2.0
        }
        // Keep at least 0.5 Mbps so nothing degenerates to zero.
        return max(mbps, 0.5)
    }
}
