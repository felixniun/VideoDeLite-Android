package com.videodelite.app.media

/**
 * Professional-mode encoding options, mirroring the desktop app's
 * `encoder.EncodingConfig` (plan §63) as far as the native Media3 stack can
 * express it.
 *
 * Documented differences from the desktop panel (media3 1.5.1 exposes only
 * `setBitrate` / `setBitrateMode` on the video encoder, and `setBitrate` on
 * the audio encoder):
 *  - VBR has no separately settable maximum bitrate; the average carries it.
 *  - Audio VBR has no quality knob; the chosen bitrate is applied as-is.
 * The desktop's ffmpeg-only knobs (MKV container, subtitle / chapter /
 * metadata preservation) are intentionally absent.
 */
enum class RateControl(val id: String) {
    /** Constant bitrate. */
    CBR("cbr"),

    /** Variable bitrate. */
    VBR("vbr"),

    /** Constant quality: platform BITRATE_MODE_CQ. */
    CQ("cq"),
}

/** AAC audio rate control, same two modes as the desktop panel. */
enum class AudioRateControl(val id: String) {
    CBR("cbr"),
    VBR("vbr"),
}

data class ProConfig(
    val codec: VideoCodec = VideoCodec.H264,
    val rateControl: RateControl = RateControl.CBR,
    /** Mbps, desktop range 0.5–120 (plan §31). */
    val bitrateMbps: Double = 8.0,
    /** 0–51, lower is better; only meaningful for CQ. */
    val quality: Int = 23,
    val audioRateControl: AudioRateControl = AudioRateControl.CBR,
    /** kbps, clamped to the desktop's 64–512 range. */
    val audioBitrateKbps: Int = 128,
    /** 1–5, desktop plan §26; only meaningful for audio VBR. */
    val audioQuality: Int = 3,
) {
    companion object {
        const val MIN_BITRATE_MBPS = 0.5
        const val MAX_BITRATE_MBPS = 120.0
        const val MIN_QUALITY = 0
        const val MAX_QUALITY = 51

        /** Desktop parity: audio bitrate choices 64–512 kbps. */
        val AUDIO_BITRATE_CHOICES = listOf(64, 96, 128, 160, 192, 224, 256, 320, 384, 512)

        /**
         * Returns an error tag for an out-of-range configuration, or null when
         * the config is submittable. Mirrors the desktop's pre-flight check.
         */
        fun error(cfg: ProConfig): String? = when {
            cfg.bitrateMbps < MIN_BITRATE_MBPS || cfg.bitrateMbps > MAX_BITRATE_MBPS -> "bitrate"
            cfg.rateControl == RateControl.CQ &&
                (cfg.quality < MIN_QUALITY || cfg.quality > MAX_QUALITY) -> "quality"
            else -> null
        }
    }
}
