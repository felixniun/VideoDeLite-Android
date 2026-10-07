package com.videodelite.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

            // Driving AppCompatDelegate.setDefaultNightMode for an in-app theme
            // choice recreates the Activity on every settings change; combined
            // with the Compose theme recomposition that produced a rapid
            // recreate storm (visible flicker, and the window was untouchable
            // until it settled). The theme is therefore resolved here, in
            // Compose, and the delegate is left on FOLLOW_SYSTEM so the system
            // dark-mode signal still reaches isSystemInDarkTheme().
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.theme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
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
                // Keep the system bars legible against the chosen theme; the
                // XML window background can only follow the system, so the
                // Compose root paints over it.
                LaunchedEffect(dark) {
                    val controller = androidx.core.view.WindowCompat
                        .getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppRoot()
                }
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
