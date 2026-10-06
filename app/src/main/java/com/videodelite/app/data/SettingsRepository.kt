package com.videodelite.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** "" follow system | zh-CN | en */
    val language: String = "",
    /** "" follow system | light | dark */
    val theme: String = "",
    /** h264 | h265 */
    val defaultCodec: String = "h264",
    /** low | mid | high */
    val defaultQuality: String = "mid",
    /** 1–3, desktop parity (plan §35) */
    val parallelTasks: Int = 1,
)

/**
 * Strictly local settings (never synced), mirroring internal/settings on the
 * desktop side. Stored in DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_CODEC = stringPreferencesKey("defaultCodec")
        val DEFAULT_QUALITY = stringPreferencesKey("defaultQuality")
        val PARALLEL = intPreferencesKey("parallelTasks")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            language = p[Keys.LANGUAGE] ?: "",
            theme = p[Keys.THEME] ?: "",
            defaultCodec = p[Keys.DEFAULT_CODEC] ?: "h264",
            defaultQuality = p[Keys.DEFAULT_QUALITY] ?: "mid",
            parallelTasks = (p[Keys.PARALLEL] ?: 1).coerceIn(1, 3),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setLanguage(v: String) = context.dataStore.edit { it[Keys.LANGUAGE] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[Keys.THEME] = v }
    suspend fun setDefaultCodec(v: String) = context.dataStore.edit { it[Keys.DEFAULT_CODEC] = v }
    suspend fun setDefaultQuality(v: String) = context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = v }
    suspend fun setParallelTasks(v: Int) = context.dataStore.edit { it[Keys.PARALLEL] = v.coerceIn(1, 3) }
}
