package com.videodelite.app.media

/** Simple Mode options, matching the desktop UI's frozen choices. */
enum class VideoCodec(val id: String, val mime: String) {
    H264("h264", "video/avc"),
    H265("h265", "video/hevc"),
}

enum class Quality(val id: String) {
    LOW("low"),
    MID("mid"),
    HIGH("high"),
}
