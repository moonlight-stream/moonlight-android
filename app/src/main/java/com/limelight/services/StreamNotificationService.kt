package com.limelight.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.limelight.Game
import com.limelight.LimeLog
import com.limelight.R

class StreamNotificationService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || ACTION_STOP == intent.action) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            releaseWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }

        val pcName = intent.getStringExtra(EXTRA_PC_NAME) ?: "Unknown"
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Desktop"
        val notification = buildNotification(pcName, appName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                enableLights(false)
            }
            manager.createNotificationChannel(channel)
            LimeLog.info("StreamNotificationService: Notification channel created with LOW importance")
        }
    }

    private fun buildNotification(pcName: String?, appName: String?): Notification {
        val intent = Intent(this, Game::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        val contentIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val title = "Moonlight-V+"
        val content = getString(
            R.string.notification_content_streaming,
            appName ?: "Desktop",
            pcName ?: "Unknown"
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun initWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Moonlight:StreamKeepAlive").apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L)
                }
                LimeLog.info("StreamNotificationService: WakeLock acquired with 24h timeout")
            }
        } catch (e: Exception) {
            LimeLog.warning("Failed to initialize WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        val wl = wakeLock
        if (wl != null && wl.isHeld) {
            try {
                wl.release()
                LimeLog.info("StreamNotificationService: WakeLock released")
            } catch (e: Exception) {
                LimeLog.warning("Error releasing WakeLock: ${e.message}")
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "stream_keep_alive"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_PC_NAME = "extra_pc_name"
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val ACTION_STOP = "ACTION_STOP"

        fun start(context: Context, pcName: String?, appName: String?) {
            val intent = Intent(context, StreamNotificationService::class.java).apply {
                putExtra(EXTRA_PC_NAME, pcName)
                putExtra(EXTRA_APP_NAME, appName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                LimeLog.severe("Failed to start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // 如果服务本来就没跑，正好不需要停
            }
        }
    }
}
