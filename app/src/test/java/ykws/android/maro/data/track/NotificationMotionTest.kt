package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationMotionTest {
    @Test fun gpsRecordingIgnoresFrozenDisplayThroughStopAndRestart() {
        val frozenDisplay = NotificationMotion(true, 12f)
        listOf(NotificationMotion(true, 8f), NotificationMotion(false, 0f), NotificationMotion(true, 3f))
            .forEach { live ->
                assertEquals(live, notificationMotion(true, false, live, frozenDisplay))
            }
    }

    @Test fun readyAndDemoKeepDisplayMotion() {
        val service = NotificationMotion(false, 0f)
        val display = NotificationMotion(true, 9f)
        listOf(false to false, false to true, true to true).forEach { (recording, demo) ->
            assertEquals(display, notificationMotion(recording, demo, service, display))
        }
    }
}
