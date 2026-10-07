package com.videodelite.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.videodelite.app.data.AppSettings
import com.videodelite.app.media.Quality
import com.videodelite.app.media.VideoCodec
import com.videodelite.app.ui.AppRoot
import com.videodelite.app.ui.theme.VdTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleOpenIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /**
     * "Open with VideoDelite" on a video file (system file managers, adb am
     * start) enqueues it directly — Simple mode works signed-out, same as the
     * desktop's drag&drop import.
     */
    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return
        val uri = intent.data ?: return
        val codec = if (BuildConfig.DEBUG && intent.getStringExtra("codec") == "h265") {
            VideoCodec.H265
        } else {
            VideoCodec.H264
        }
        val quality = when (intent.getStringExtra("quality")) {
            "low" -> Quality.LOW
            "high" -> Quality.HIGH
            else -> Quality.MID
        }
        AppGraph.taskManager.enqueue(listOf(uri), codec, quality)
        Toast.makeText(this, getString(R.string.home_start_toast), Toast.LENGTH_SHORT).show()
    }
}
