package ykws.android.maro.ui.map

import kotlinx.coroutines.delay

/** One immediate update, then the existing cadence while recording. Payload is read at each tick. */
internal suspend fun notificationTicker(recording: Boolean, update: () -> Unit) {
    do {
        update()
        if (!recording) return
        delay(5_000L)
    } while (true)
}
