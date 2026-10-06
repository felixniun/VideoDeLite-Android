package com.videodelite.app.data

import android.content.Context
import java.security.SecureRandom

/**
 * Stable per-installation id (random 16 hex chars), same scheme as the
 * desktop app's installationId for device activation.
 */
object DeviceId {

    private const val PREFS = "vd_device"
    private const val KEY = "installationId"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val chars = "0123456789abcdef"
        val rnd = SecureRandom()
        val id = buildString { repeat(16) { append(chars[rnd.nextInt(chars.length)]) } }
        prefs.edit().putString(KEY, id).apply()
        return id
    }
}
