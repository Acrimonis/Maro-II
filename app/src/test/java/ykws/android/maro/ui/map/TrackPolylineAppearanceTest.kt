package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPolylineAppearanceTest {

    /** Extract the alpha channel (0..255) from an ARGB int. */
    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private fun appearance(
        index: Int,
        transparencyNewest: Int,
        transparencyOldest: Int
    ) = computeTrackPolylineAppearance(
        index = index, total = 2,
        transparencyNewest = transparencyNewest, transparencyOldest = transparencyOldest,
        colorFrom = 0xFFFFFF, colorTo = 0x000000
    )

    @Test
    fun normalValues_newestGetsHigherAlpha() {
        // newest = 20, oldest = 80 transparency -> newest alpha ~0.8, oldest alpha ~0.2
        val newest = appearance(index = 0, transparencyNewest = 20, transparencyOldest = 80)
        val oldest = appearance(index = 1, transparencyNewest = 20, transparencyOldest = 80)
        // (100-20)/100 = 0.8 * 255 = 204 (exact); (100-80)/100 = 0.2 * 255 = 51 but float truncation may yield 50
        assertEquals(204, alphaOf(newest.argb))
        assertTrue(alphaOf(oldest.argb) in 50..51)
        assertTrue(alphaOf(newest.argb) > alphaOf(oldest.argb))
    }

    @Test
    fun reversedValues_newestStillGetsHigherAlpha() {
        // stored reversed: newest = 100, oldest = 50 -> newest should get 50 (alpha 0.5), oldest 100 (alpha 0)
        val newest = appearance(index = 0, transparencyNewest = 100, transparencyOldest = 50)
        val oldest = appearance(index = 1, transparencyNewest = 100, transparencyOldest = 50)
        // min(100,50)=50 -> (100-50)/100 = 0.5 * 255 = 127.5 truncates to 127; max=100 -> (100-100)/100 = 0
        assertEquals(127, alphaOf(newest.argb))
        assertEquals(0, alphaOf(oldest.argb))
        assertTrue(alphaOf(newest.argb) > alphaOf(oldest.argb))
    }

    @Test
    fun equalValues_uniformAlpha() {
        val newest = appearance(index = 0, transparencyNewest = 40, transparencyOldest = 40)
        val oldest = appearance(index = 1, transparencyNewest = 40, transparencyOldest = 40)
        // (100-40)/100 = 0.6 * 255 = 153 (exact)
        assertEquals(153, alphaOf(newest.argb))
        assertEquals(153, alphaOf(oldest.argb))
        assertEquals(alphaOf(newest.argb), alphaOf(oldest.argb))
    }
}
