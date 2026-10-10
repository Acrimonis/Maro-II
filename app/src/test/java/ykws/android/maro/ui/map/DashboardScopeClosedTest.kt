package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the R2 guard ([scopeClosed]) and its track twin ([trackScopeClosed]): the world an open
 * dashboard's walk reads decides whether a referential change closes it.
 *
 * The two flags answer for the world a change landed in — a list filter, sort or reset answers for the
 * list world; a map filter or reset answers for the map world. Since 2026-09-28 the sources read three
 * ways: a list card closes on a list write; a spy (inspect) card closes on a map write alone; and a map
 * click — a single item whose standing is not the filter's business — never closes this way. Since
 * 2026-10-10 the menu chevron's card walks the map-filtered set **in the list's own order**, so its own
 * kind's list write (the sort that order reads) closes it as well as its own kind's map write. The track
 * half repeats the split through its own [TrackCardSource], because the two cards live in different state
 * owners, and since the lists split (2026-10-10) it also matches the write's **kind** against the kind the
 * card itself holds, so one list's write leaves the other list's card standing.
 */
class DashboardScopeClosedTest {

    @Test
    fun `list opened walk closes only on a list world change`() {
        assertTrue(scopeClosed(DrawerSource.LIST, inListWorld = true, inMapWorld = false))
        assertFalse(scopeClosed(DrawerSource.LIST, inListWorld = false, inMapWorld = true))
    }

    @Test
    fun `a map click seats one item whose standing is not the filter's business`() {
        assertFalse(scopeClosed(DrawerSource.MAP, inListWorld = false, inMapWorld = true))
        assertFalse(scopeClosed(DrawerSource.MAP, inListWorld = true, inMapWorld = true))
    }

    @Test
    fun `an inspect-opened walk closes on a map world change, its ladder being the map's own`() {
        assertTrue(scopeClosed(DrawerSource.INSPECT, inListWorld = false, inMapWorld = true))
        assertFalse(scopeClosed(DrawerSource.INSPECT, inListWorld = true, inMapWorld = false))
    }

    @Test
    fun `a menu chevron's card walks the map referential in the list order and closes on either write`() {
        assertTrue(scopeClosed(DrawerSource.MENU, inListWorld = false, inMapWorld = true))
        assertTrue(scopeClosed(DrawerSource.MENU, inListWorld = true, inMapWorld = false))
    }

    @Test
    fun `a list sort edit closes the menu-opened marker card`() {
        // The menu walk is order-bound to the list sort, so the sweep that rewrites it closes the card
        // rather than letting its walk re-order under it (2026-10-10).
        assertTrue(scopeClosed(DrawerSource.MENU, inListWorld = true, inMapWorld = false))
    }

    @Test
    fun `a linked write into both worlds still closes a list walk`() {
        assertTrue(scopeClosed(DrawerSource.LIST, inListWorld = true, inMapWorld = true))
    }

    // ── The track card's twin (plan §4, kind-aware 2026-10-10) ───────────────

    @Test
    fun `a list-opened track closes on its own kind's list write and stands on a bare map write`() {
        assertTrue(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = false, listWorld = ListScope.TRACKS, mapWorld = null))
        assertFalse(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = false, listWorld = null, mapWorld = ListScope.TRACKS))
    }

    @Test
    fun `a list-opened track stands on the other kind's list write`() {
        assertFalse(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = false, listWorld = ListScope.ROUTES, mapWorld = null))
    }

    @Test
    fun `a list-opened route closes on the routes write and stands on the tracks one`() {
        assertTrue(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = true, listWorld = ListScope.ROUTES, mapWorld = null))
        assertFalse(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = true, listWorld = ListScope.TRACKS, mapWorld = null))
    }

    @Test
    fun `a menu-opened track closes on its own kind's map write and, its walk now reading the list order, the list write`() {
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = false, listWorld = null, mapWorld = ListScope.TRACKS))
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = false, listWorld = ListScope.TRACKS, mapWorld = null))
    }

    @Test
    fun `a menu-opened route stands on the other kind's writes and closes on its own`() {
        assertFalse(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = null, mapWorld = ListScope.TRACKS))
        assertFalse(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = ListScope.TRACKS, mapWorld = null))
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = null, mapWorld = ListScope.ROUTES))
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = ListScope.ROUTES, mapWorld = null))
    }

    @Test
    fun `a list sort edit closes the menu-opened card of its own kind`() {
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = false, listWorld = ListScope.TRACKS, mapWorld = null))
        assertTrue(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = ListScope.ROUTES, mapWorld = null))
    }

    @Test
    fun `a spy-opened track closes on its own kind's map write, its ladder being the map's own`() {
        assertTrue(trackScopeClosed(TrackCardSource.INSPECT, cardIsRoute = false, listWorld = null, mapWorld = ListScope.TRACKS))
        assertFalse(trackScopeClosed(TrackCardSource.INSPECT, cardIsRoute = false, listWorld = null, mapWorld = ListScope.ROUTES))
    }

    @Test
    fun `a linked write into both of its kind's worlds closes a list-opened track`() {
        assertTrue(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = false, listWorld = ListScope.TRACKS, mapWorld = ListScope.TRACKS))
    }

    @Test
    fun `a write that moves no world closes nothing`() {
        assertFalse(trackScopeClosed(TrackCardSource.LIST, cardIsRoute = false, listWorld = null, mapWorld = null))
        assertFalse(trackScopeClosed(TrackCardSource.MENU, cardIsRoute = true, listWorld = null, mapWorld = null))
    }
}
