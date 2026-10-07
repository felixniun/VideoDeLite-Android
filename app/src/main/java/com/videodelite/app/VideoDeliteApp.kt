package com.videodelite.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.videodelite.app.media.Quality
import com.videodelite.app.media.VideoCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoDeliteApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Apply the persisted parallel-task limit to the queue.
        appScope.launch {
            AppGraph.taskManager.setParallelism(AppGraph.settings.current().parallelTasks)
        }
        if (BuildConfig.DEBUG) {
            registerDebugEnqueueReceiver()
        }
    }

    /**
     * Debug-only hook for emulator E2E testing: bypasses the photo picker
     * (flaky on emulators) and enqueues a content URI directly, e.g.
     * adb shell am broadcast -a com.videodelite.DEBUG_ENQUEUE -d <content uri>
     */
    private fun registerDebugEnqueueReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                android.util.Log.d("VdDebug", "enqueue broadcast received: ${intent.data}")
                val uri = intent.data ?: return
                val codec = if (intent.getStringExtra("codec") == "h265") VideoCodec.H265 else VideoCodec.H264
                val quality = when (intent.getStringExtra("quality")) {
                    "low" -> Quality.LOW
                    "high" -> Quality.HIGH
                    else -> Quality.MID
                }
                AppGraph.taskManager.enqueue(listOf(uri), codec, quality)
            }
        }
        ContextCompat.registerReceiver(
            this, receiver, IntentFilter("com.videodelite.DEBUG_ENQUEUE"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        android.util.Log.d("VdDebug", "debug enqueue receiver registered")
    }
}
