package ykws.android.maro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the list popups' height bound — the one derivation the filter and sort popups share.
 *
 * The bound is the window's own height less the reserve a popup leaves for the chrome it hangs below,
 * so a long axis set — the track filter's three axes, thirteen rows — wraps its own height while it
 * fits and scrolls once it does not, instead of being clipped off the screen edge in landscape.
 */
class ListPopupHeightTest {

    @Test
    fun `the bound is the window height less the reserve`() {
        // A 400dp-tall landscape window keeps 304dp once the header it hangs from is reserved.
        assertEquals(304f, popupMaxHeightDp(400), 0.01f)
    }

    @Test
    fun `a window shorter than the reserve keeps the popup usable`() {
        assertEquals(120f, popupMaxHeightDp(100), 0.01f)
    }
}
