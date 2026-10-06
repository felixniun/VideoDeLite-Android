package com.videodelite.app.media

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * Saves finished MP4 files into Movies/VideoDelite. On Android 10+ this goes
 * through MediaStore (no permission, collision names auto-renamed by the
 * system); on Android 8–9 it writes the public Movies dir directly, which
 * requires WRITE_EXTERNAL_STORAGE granted up front.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object OutputSaver {

    const val ALBUM = "VideoDelite"

    /** Returns (uri or null on legacy, final display name). */
    fun save(context: Context, baseName: String, tempFile: File): Pair<Uri?, String> {
        val name = "$baseName.mp4"
        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("cannot open output stream")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri to name
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                ALBUM,
            )
            if (!dir.exists()) dir.mkdirs()
            val target = uniqueLegacyFile(dir, name)
            tempFile.copyTo(target, overwrite = false)
            android.media.MediaScannerConnection.scanFile(
                context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null,
            )
            null to target.name
        }
    }

    fun hasLegacyWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    private fun uniqueLegacyFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.removeSuffix(".mp4")
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).mp4")
            n++
        }
        return candidate
    }
}
