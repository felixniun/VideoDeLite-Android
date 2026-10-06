package com.videodelite.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.videodelite.app.data.AppSettings
import com.videodelite.app.ui.AppRoot
import com.videodelite.app.ui.theme.VdTheme
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by AppGraph.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val dark = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(settings.theme) {
                val mode = when (settings.theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
            LaunchedEffect(settings.language) {
                val wanted = if (settings.language.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(settings.language)
                }
                if (AppCompatDelegate.getApplicationLocales() != wanted) {
                    AppCompatDelegate.setApplicationLocales(wanted)
                }
            }

            VdTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }
}
