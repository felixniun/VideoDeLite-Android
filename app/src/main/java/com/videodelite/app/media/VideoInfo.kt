package com.videodelite.app.media

import android.net.Uri

/** Probe result, the Android counterpart of the desktop's ffprobe analysis. */
data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    /** "h264" | "h265" | "other" */
    val codec: String,
    val bitrateBps: Long,
)
