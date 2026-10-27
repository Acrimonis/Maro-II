package ykws.android.maro.data.track

/** GPS recording owns its motion even when the display sends an older notification payload. */
internal data class NotificationMotion(val moving: Boolean, val speedKn: Float)

internal fun notificationMotion(
    recording: Boolean,
    demo: Boolean,
    service: NotificationMotion,
    display: NotificationMotion
): NotificationMotion = if (recording && !demo) service else display
