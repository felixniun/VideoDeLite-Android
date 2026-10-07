package com.videodelite.app.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.videodelite.app.AppGraph
import com.videodelite.app.R
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps compression alive while the app is in the
 * background and shows a progress notification. Task logic itself lives in
 * TaskManager (app-scoped), the service only reflects its state.
 */
class ExportService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(0))
        lifecycleScope.launch {
            AppGraph.taskManager.tasks.collect { list ->
                val active = list.count {
                    it.state == TaskState.QUEUED || it.state == TaskState.ANALYZING || it.state == TaskState.COMPRESSING
                }
                if (active == 0) {
                    ServiceCompat.stopForeground(this@ExportService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(active))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            // Some platform images reject the mediaProcessing type; fall back
            // to dataSync (declared in the manifest for every API level).
            android.util.Log.e("VdExport", "FGS type=$type rejected, falling back to dataSync", e)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
    }

    private fun buildNotification(activeCount: Int): Notification {
        val text = getString(R.string.notif_text, activeCount)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vd)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, currentOverallProgress(), activeCount > 0)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun currentOverallProgress(): Int {
        val running = AppGraph.taskManager.tasks.value.filter { it.state == TaskState.COMPRESSING }
        if (running.isEmpty()) return 0
        return running.sumOf { it.progress } / running.size
    }

    private fun contentIntent() =
        android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.videodelite.app.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notif_channel_desc) }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "vd_exports"
        private const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ExportService::class.java))
        }
    }
}
