package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The saved route comment's head line after the preference rung left it (settled 2026-10-24): the
 * rank, the pace and the slow-water preference, with no rung and no dangling bullet.
 */
class RouteCommentHeadTest {

    @Test
    fun `the head reads rank pace pref with no rung`() {
        assertEquals("#2 • 12.0 kn • Avoid", routeCommentHead(2, "12.0 kn", "Avoid"))
    }

    @Test
    fun `a null rank leaves no leading bullet`() {
        assertEquals("12.0 kn • Avoid", routeCommentHead(null, "12.0 kn", "Avoid"))
    }
}
