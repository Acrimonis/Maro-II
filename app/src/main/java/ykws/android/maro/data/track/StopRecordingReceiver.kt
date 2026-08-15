package ykws.android.maro.data.track

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-registered receiver that routes a "stop recording" broadcast
 * to [TrackRecordingService]. The service owns the notification and the
 * [TrackRecorder] reference — this receiver is a thin bridge with no
 * business logic.
 *
 * Chain: Notification "Stop" tapped → this receiver → startService(ACTION_STOP_RECORDING)
 *        → TrackRecordingService.onStartCommand() → recorder?.stop()
 */
class StopRecordingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_STOP_RECORDING
        }
        context.startService(serviceIntent)
    }
}
