package com.videodelite.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the Android port against golden values produced by the desktop's
 * Go implementation (tools/goldenbitrate). Every row must match exactly —
 * the frozen bitrate table is the product's core contract.
 */
class BitrateTest {

    private data class Row(
        val w: Int, val h: Int, val fps: Double,
        val codec: String, val quality: String, val expected: Double,
    )

    private fun loadGolden(): List<Row> {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden_bitrate.csv")
            ?: error("golden_bitrate.csv missing from test resources")
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split(',')
                Row(p[0].toInt(), p[1].toInt(), p[2].toDouble(), p[3], p[4], p[5].toDouble())
            }
    }

    @Test
    fun matchesDesktopGoldenValues() {
        val rows = loadGolden()
        assertEquals(540, rows.size)
        for (r in rows) {
            val got = Bitrate.simpleBitrateMbps(r.w, r.h, r.fps, r.codec, r.quality)
            assertEquals(
                "w=${r.w} h=${r.h} fps=${r.fps} codec=${r.codec} quality=${r.quality}",
                r.expected, got, 0.0001,
            )
        }
    }

    @Test
    fun portraitVideoLandsInLandscapeBucket() {
        assertEquals(1080, Bitrate.resolutionClass(1080, 1920))
        assertEquals(720, Bitrate.resolutionClass(720, 1280))
        assertEquals(2160, Bitrate.resolutionClass(2160, 3840))
    }

    @Test
    fun fpsBucketsFollowDesktopTiers() {
        assertEquals("24-30", Bitrate.fpsBucket(23.976))
        assertEquals("24-30", Bitrate.fpsBucket(29.97))
        assertEquals("48-60", Bitrate.fpsBucket(50.0))
        assertEquals("48-60", Bitrate.fpsBucket(120.0))
    }

    @Test
    fun hevcIs75PercentAtHalfMbpsGranularity() {
        val h264 = Bitrate.simpleBitrateMbps(1920, 1080, 30.0, "h264", "mid")
        val h265 = Bitrate.simpleBitrateMbps(1920, 1080, 30.0, "h265", "mid")
        assertEquals(5.0, h264, 0.0001)
        assertEquals(4.0, h265, 0.0001) // 5 × 0.75 = 3.75 → rounds half-away-from-zero to 4.0
    }
}
