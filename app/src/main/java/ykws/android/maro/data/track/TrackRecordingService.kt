package ykws.android.maro.data.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the track recording alive when the app is backgrounded.
 *
 * Lifecycle:
 * - Started via [startForegroundService] when recording begins (IDLE → RECORDING)
 * - Stopped via [stopService] when recording finalizes (FINALIZING → IDLE)
 * - Shows a low-importance notification: 👣 "Maro II — Recording track"
 *
 * The actual [TrackRecorder] state machine lives in [TrackViewModel].
 * This service only holds the wake lock and notification.
 */
class TrackRecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // fallback until custom icon is added
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "track_recording"
        private const val CHANNEL_NAME = "Track Recording"
        private const val CHANNEL_DESC = "Notification shown while a track is being recorded"
        private const val NOTIFICATION_TITLE = "Maro II"
        private const val NOTIFICATION_TEXT = "Recording track"
        private const val NOTIFICATION_ID = 1001
    }
}
