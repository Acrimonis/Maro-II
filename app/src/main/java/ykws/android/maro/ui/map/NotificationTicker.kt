package ykws.android.maro.ui.map

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** One immediate update, then the existing cadence while recording. Payload is read at each tick. */
internal suspend fun notificationTicker(recording: Boolean, update: () -> Unit) {
    do {
        update()
        if (!recording) return
        delay(5_000L)
    } while (true)
}

/** Only meaningful notification transitions restart the ticker, independently of composition. */
internal data class NotificationTickerKey(val recording: Boolean, val gpsMode: Boolean, val onWater: Boolean, val moving: Boolean)

internal suspend fun notificationUpdates(keys: Flow<NotificationTickerKey>, update: (NotificationTickerKey) -> Unit) {
    keys.distinctUntilChanged().collectLatest { key -> notificationTicker(key.recording) { update(key) } }
}
