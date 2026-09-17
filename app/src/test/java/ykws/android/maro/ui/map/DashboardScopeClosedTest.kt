package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the R2 guard ([scopeClosed]): the world an open dashboard's Prev/Next walk reads decides
 * whether a referential change closes it.
 *
 * The two flags answer for the world a change landed in — a list filter, sort or reset answers for the
 * list world; a map filter or reset answers for the map world.
 */
class DashboardScopeClosedTest {

    @Test
    fun `list opened walk closes only on a list world change`() {
        assertTrue(scopeClosed(DrawerSource.LIST, inListWorld = true, inMapWorld = false))
        assertFalse(scopeClosed(DrawerSource.LIST, inListWorld = false, inMapWorld = true))
    }

    @Test
    fun `map opened walk closes only on a map world change`() {
        assertTrue(scopeClosed(DrawerSource.MAP, inListWorld = false, inMapWorld = true))
        assertFalse(scopeClosed(DrawerSource.MAP, inListWorld = true, inMapWorld = false))
    }

    @Test
    fun `where am i walk is never closed by a referential change`() {
        assertFalse(scopeClosed(DrawerSource.WHERE_AM_I, inListWorld = true, inMapWorld = true))
        assertFalse(scopeClosed(DrawerSource.WHERE_AM_I, inListWorld = false, inMapWorld = false))
    }

    @Test
    fun `a linked write into both worlds closes either walk`() {
        assertTrue(scopeClosed(DrawerSource.LIST, inListWorld = true, inMapWorld = true))
        assertTrue(scopeClosed(DrawerSource.MAP, inListWorld = true, inMapWorld = true))
    }
}
