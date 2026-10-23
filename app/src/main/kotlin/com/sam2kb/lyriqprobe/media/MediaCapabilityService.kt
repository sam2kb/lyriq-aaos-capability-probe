package com.sam2kb.lyriqprobe.media

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.sam2kb.lyriqprobe.R

/**
 * Short-lived probe for the foreground-service path SqueezeClient uses for local playback.
 * It does not play audio or create a media session; it only starts a mediaPlayback FGS,
 * posts its notification, then stops it. The user must explicitly trigger the test.
 */
class MediaCapabilityService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_TEST) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        val permissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val enabled = notificationManager?.areNotificationsEnabled() == true
        var channelCreated = false
        var started = false
        var failure: String? = null
        try {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "AAOS capability probe",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Temporary foreground-service capability test"
                    setShowBadge(false)
                },
            )
            channelCreated = true
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_probe)
                .setContentTitle("Media playback capability test")
                .setContentText("Temporary foreground service; stopping in a moment")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            started = true
        } catch (throwable: Throwable) {
            failure = "${throwable.javaClass.simpleName}: ${throwable.message}"
        }

        Handler(Looper.getMainLooper()).postDelayed({
            var stopped = false
            if (started) {
                runCatching {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopped = true
                }.onFailure { failure = "${it.javaClass.simpleName}: ${it.message}" }
            }
            stopSelf(startId)
            MediaProbeBroker.publish(
                MediaForegroundOutcome(
                    started = started,
                    stopped = stopped,
                    notificationPermissionGranted = permissionGranted,
                    notificationsEnabled = enabled,
                    notificationChannelCreated = channelCreated,
                    error = failure,
                ),
            )
        }, 1_200)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_TEST = "com.sam2kb.lyriqprobe.media.TEST_FOREGROUND"
        private const val CHANNEL_ID = "lyriq_probe_media"
        private const val NOTIFICATION_ID = 7301
    }
}
