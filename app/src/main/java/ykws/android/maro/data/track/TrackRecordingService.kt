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
 * Foreground service that keeps Maro II alive when backgrounded.
 *
 * Lifecycle:
 * - Started via [startForegroundService] when the app opens ([MainActivity.onCreate]).
 * - Stopped via [stopService] when the user explicitly exits (double-back).
 * - Always shows a low-importance notification:
 *   - **Ready:** "Maro II — Ready" (or "Maro II — Ready (Demo)" in demo mode)
 *   - **Recording:** "Maro II — Recording • 12.3 kn • 00:05:23 • 1.2 nm"
 *
 * Notification content is updated via [ACTION_UPDATE] intents sent from the UI layer
 * (MapScreen) with live recording stats. The [TrackRecorder] state machine runs in
 * [TrackViewModel]; this service only holds the foreground notification.
 */
class TrackRecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = if (intent?.action == ACTION_UPDATE) {
            val recording = intent.getBooleanExtra(EXTRA_RECORDING, false)
            val isDemo = intent.getBooleanExtra(EXTRA_IS_DEMO, false)
            if (recording) {
                val speedKn = intent.getFloatExtra(EXTRA_SPEED_KN, 0f)
                val elapsedSec = intent.getLongExtra(EXTRA_ELAPSED_SEC, 0L)
                val distanceNm = intent.getFloatExtra(EXTRA_DISTANCE_NM, 0f)
                buildRecordingNotification(speedKn, elapsedSec, distanceNm, isDemo)
            } else {
                buildReadyNotification(isDemo)
            }
        } else {
            buildReadyNotification(false)
        }
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildReadyNotification(isDemo: Boolean): Notification {
        val title = "Maro II"
        val text = if (isDemo) "Ready (Demo)" else "Ready"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun buildRecordingNotification(
        speedKn: Float,
        elapsedSec: Long,
        distanceNm: Float,
        isDemo: Boolean
    ): Notification {
        val demoLabel = if (isDemo) " (Demo)" else ""
        val elapsed = formatElapsed(elapsedSec)
        val speed = "%.1f".format(speedKn)
        val dist = "%.1f".format(distanceNm)
        val text = "Recording$demoLabel • $speed kn • $elapsed • $dist nm"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Maro II")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun formatElapsed(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    companion object {
        private const val CHANNEL_ID = "maro_persistent"
        private const val CHANNEL_NAME = "Maro II"
        private const val CHANNEL_DESC = "Persistent notification while Maro II is running"
        private const val NOTIFICATION_ID = 1001

        /** Intent action: update the foreground notification with current recording stats. */
        const val ACTION_UPDATE = "ykws.android.maro.action.UPDATE_NOTIFICATION"
        const val EXTRA_RECORDING = "recording"
        const val EXTRA_SPEED_KN = "speed_kn"
        const val EXTRA_ELAPSED_SEC = "elapsed_sec"
        const val EXTRA_DISTANCE_NM = "distance_nm"
        const val EXTRA_IS_DEMO = "is_demo"
    }
}
