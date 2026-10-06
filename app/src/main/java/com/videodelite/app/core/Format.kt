package com.videodelite.app.core

import java.util.Locale
import kotlin.math.roundToInt

/** Display formatting helpers shared across screens. */
object Format {

    fun bytes(v: Long): String {
        if (v < 0) return "0 B"
        val b = v.toDouble()
        return when {
            b >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", b / 1_073_741_824)
            b >= 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576)
            b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024)
            else -> "$v B"
        }
    }

    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    /** Estimated output size from the frozen bitrate table. */
    fun estimateOutputBytes(width: Int, height: Int, fps: Double, codec: String, quality: String, durationMs: Long): Long {
        val mbps = Bitrate.simpleBitrateMbps(width, height, fps, codec, quality)
        return (mbps * 1_000_000 / 8.0 * (durationMs / 1000.0)).toLong()
    }

    fun percentSaved(before: Long, after: Long): String {
        if (before <= 0 || after >= before) return "0%"
        val pct = ((before - after).toDouble() / before * 100).roundToInt()
        return "$pct%"
    }

    fun shortDate(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMs
        return String.format(
            Locale.US,
            "%04d-%02d-%02d %02d:%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }

    /** "video_20260101_120000" style base name for outputs. */
    fun outputBaseName(sourceName: String, epochMs: Long): String {
        val base = sourceName.substringBeforeLast('.').ifBlank { "video" }.take(60)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMs
        val stamp = String.format(
            Locale.US,
            "%04d%02d%02d_%02d%02d%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
        )
        return "${base}_${stamp}"
    }
}
