package com.videodelite.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persisted login session (access/refresh tokens + account snapshot). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val accountId: Long,
    val username: String,
    val email: String,
)

/**
 * Tokens live in EncryptedSharedPreferences (Android Keystore-backed). On the
 * small set of devices where Keystore misbehaves we degrade to plain private
 * prefs rather than crash — same pragmatic tradeoff as the desktop's
 * Windows Credential Manager fallback.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "vd_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w("TokenStore", "keystore unavailable, falling back to plain prefs", e)
        context.getSharedPreferences("vd_fallback_prefs", Context.MODE_PRIVATE)
    }

    fun load(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMs = prefs.getLong(KEY_EXPIRES, 0L),
            accountId = prefs.getLong(KEY_ACCOUNT_ID, 0L),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
        )
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES, session.expiresAtMs)
            .putLong(KEY_ACCOUNT_ID, session.accountId)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    /** Refresh rotation: persist the new pair before retrying any request. */
    fun updateTokens(accessToken: String, refreshToken: String, expiresAtMs: Long) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putLong(KEY_EXPIRES, expiresAtMs)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EXPIRES = "expiresAtMs"
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
    }
}
