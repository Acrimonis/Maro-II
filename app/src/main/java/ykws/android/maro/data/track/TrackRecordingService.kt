package ykws.android.maro.data.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ykws.android.maro.R

/**
 * Foreground service that keeps Maro II alive when backgrounded.
 *
 * Lifecycle:
 * - Started via [startForegroundService] when the app opens ([MainActivity.onCreate]).
 * - Stopped via [stopService] when the user explicitly exits (double-back).
 * - Always shows a low-importance notification with a 5-segment collapsed title
 *   and an InboxStyle expanded view with stat rows.
 *
 * Notification content is updated via [ACTION_UPDATE] intents sent from the UI layer
 * (MapScreen) with live recording stats. The [TrackRecorder] state machine runs in
 * [TrackViewModel]; this service only holds the foreground notification.
 *
 * ## Tasker integration
 * Stores the boat's water-state ([lastKnownOnWater]) from incoming [ACTION_UPDATE]
 * intents. On toggle, fires [ACTION_WATER_STATE_CHANGED] so Tasker can react
 * immediately. Also answers [ACTION_QUERY_WATER_STATE] on demand.
 */
class TrackRecordingService : Service() {

    /** Last known boat water state — persisted across Activity lifecycle for query support. */
    private var lastKnownOnWater: Boolean = false

    /** Dynamically registered receiver for [ACTION_QUERY_WATER_STATE]. */
    private var waterQueryReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Register query receiver so Tasker can poll water state on demand
        waterQueryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_QUERY_WATER_STATE) {
                    val result = Intent(ACTION_WATER_STATE_RESULT).apply {
                        putExtra(EXTRA_ON_WATER, lastKnownOnWater)
                    }
                    sendBroadcast(result)
                }
            }
        }
        registerReceiver(waterQueryReceiver, IntentFilter(ACTION_QUERY_WATER_STATE),
            Context.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        waterQueryReceiver?.let { unregisterReceiver(it) }
        waterQueryReceiver = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ── Water state update (may ride along with notification update or arrive standalone) ──
        if (intent != null && intent.hasExtra(EXTRA_ON_WATER)) {
            val newOnWater = intent.getBooleanExtra(EXTRA_ON_WATER, false)
            if (newOnWater != lastKnownOnWater) {
                lastKnownOnWater = newOnWater
                // Push broadcast to Tasker on every land↔water toggle
                sendBroadcast(Intent(ACTION_WATER_STATE_CHANGED).apply {
                    putExtra(EXTRA_ON_WATER, newOnWater)
                })
            }
        }

        val isOnWater = lastKnownOnWater
        val notification = if (intent?.action == ACTION_UPDATE) {
            buildNotification(intent, isOnWater)
        } else {
            buildNotification(null, isOnWater)
        }
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification building ──────────────────────────────────────────────

    private fun buildNotification(intent: Intent?, isOnWater: Boolean): Notification {
        val recording = intent?.getBooleanExtra(EXTRA_RECORDING, false) ?: false
        val isDemo = intent?.getBooleanExtra(EXTRA_IS_DEMO, false) ?: false
        val isMoving = intent?.getBooleanExtra(EXTRA_IS_MOVING, false) ?: false
        val speedKn = intent?.getFloatExtra(EXTRA_SPEED_KN, 0f) ?: 0f
        val elapsedSec = intent?.getLongExtra(EXTRA_ELAPSED_SEC, 0L) ?: 0L
        val distanceNm = intent?.getFloatExtra(EXTRA_DISTANCE_NM, 0f) ?: 0f
        val idleSec = intent?.getLongExtra(EXTRA_IDLE_SEC, 0L) ?: 0L
        val avgSpeedKn = intent?.getFloatExtra(EXTRA_AVG_SPEED_KN, 0f) ?: 0f
        val maxSpeedKn = intent?.getFloatExtra(EXTRA_MAX_SPEED_KN, 0f) ?: 0f
        val pointCount = intent?.getIntExtra(EXTRA_POINT_COUNT, 0) ?: 0

        // 5-segment title: "Maro II • [GPS|Demo] • [Navigating|Idle|Moving] • [Recording|Ready] • [On Water|On Land]"
        val modeLabel = if (isDemo) "Demo" else "GPS"
        val recLabel = if (recording) "Recording" else "Ready"
        val navLabel = when {
            !isMoving -> "Idle"
            isOnWater -> "Navigating"
            else -> "Moving"
        }
        val waterLabel = if (isOnWater) "On Water" else "On Land"
        val title = "Maro II • $modeLabel • $navLabel • $recLabel • $waterLabel"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Maro II")
            .setContentText(title)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        return builder.build()
    }

    // ── Channel / formatting ───────────────────────────────────────────────

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

    // ── Constants ──────────────────────────────────────────────────────────

    companion object {
        private const val CHANNEL_ID = "maro_persistent"
        private const val CHANNEL_NAME = "Maro II"
        private const val CHANNEL_DESC = "Persistent notification while Maro II is running"
        private const val NOTIFICATION_ID = 1001

        /** Intent action: update the foreground notification with current recording stats. */
        const val ACTION_UPDATE = "ykws.android.maro.action.UPDATE_NOTIFICATION"
        const val EXTRA_RECORDING = "recording"
        const val EXTRA_IS_DEMO = "is_demo"

        // Always-sent extras
        const val EXTRA_IS_MOVING = "is_moving"
        const val EXTRA_SPEED_KN = "speed_kn"
        const val EXTRA_ON_WATER = "on_water"

        // Recording-only extras
        const val EXTRA_ELAPSED_SEC = "elapsed_sec"
        const val EXTRA_DISTANCE_NM = "distance_nm"
        const val EXTRA_IDLE_SEC = "idle_sec"
        const val EXTRA_AVG_SPEED_KN = "avg_speed_kn"
        const val EXTRA_MAX_SPEED_KN = "max_speed_kn"
        const val EXTRA_POINT_COUNT = "point_count"

        // ── Tasker water-state integration ─────────────────────────────────────

        /** Push broadcast: fired when boat water state toggles (land↔water). */
        const val ACTION_WATER_STATE_CHANGED = "ykws.android.maro.action.WATER_STATE_CHANGED"

        /** Query broadcast: Tasker sends this to poll current water state. */
        const val ACTION_QUERY_WATER_STATE = "ykws.android.maro.action.QUERY_WATER_STATE"

        /** Query response: Maro II answers with [EXTRA_ON_WATER]. */
        const val ACTION_WATER_STATE_RESULT = "ykws.android.maro.action.WATER_STATE_RESULT"
    }
}
