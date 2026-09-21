package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File
import java.util.Properties

/**
 * Unit tests for [bannerStartInset] — the bottom band's clearance.
 *
 * The rule (plan §4 D1/D5, `xTrack/Ui_General/260921_FEAT_PLN_Ui_General_bottom-banner-centring.md`):
 * with no tag drawn the band starts at its own gutter; with one drawn it also clears the tag column,
 * one `ui.map.toggle.square` wide plus the `ui.map.overlay.gap` the info text sits at. The right
 * control column is deliberately not part of this inset — `MapBanner` reserves it from the placement
 * its `reservesControlColumn` states.
 *
 * The numbers the arithmetic ships with are the ones the `ui.map.*` keys carry in
 * `app/src/main/assets/colors.properties`, read here in the shape `CoastlineAppearancePropertiesTest`
 * uses: the code holds the same values as defaults, so a key misspelled on either side, or a value moved
 * on one side only, fails here instead of leaving the code's default standing unnoticed.
 */
class BannerStartInsetTest {

    private val colorsFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/colors.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/colors.properties")

    /**
     * The three shipped keys the inset's arithmetic reads, with `${key}` references resolved the way
     * `AppConfig` resolves them — `ui.map.overlay.gap` is written as the gutter, not as a literal.
     */
    private fun shippedInsets(): Triple<Float, Float, Float> {
        assumeTrue("colors.properties not found", colorsFile.isFile)
        val props = Properties().apply { colorsFile.inputStream().use { load(it) } }

        fun value(key: String): Float {
            val raw = props.getProperty(key)?.trim() ?: error("colors.properties must carry $key")
            val resolved = REF.replace(raw) { m -> props.getProperty(m.groupValues[1])?.trim() ?: m.value }
            return resolved.toFloat()
        }

        return Triple(
            value("ui.map.toggle.gutter"),
            value("ui.map.toggle.square"),
            value("ui.map.overlay.gap")
        )
    }

    @Test
    fun withNoTagDrawnTheBandStartsAtItsOwnGutter() {
        assertEquals(TOP_TOGGLE_GUTTER.value, bannerStartInset(tagsDrawn = false).value, 0f)
    }

    @Test
    fun withATagDrawnTheInsetAlsoClearsTheTagColumn() {
        val tagColumn = TOP_TOGGLE_SQUARE.value + AppConfig.uiMapOverlayGap
        assertEquals(TOP_TOGGLE_GUTTER.value + tagColumn, bannerStartInset(tagsDrawn = true).value, 0f)
    }

    @Test
    fun theTwoStatesDifferByExactlyTheTagColumn() {
        val delta = bannerStartInset(tagsDrawn = true).value - bannerStartInset(tagsDrawn = false).value
        assertEquals(TOP_TOGGLE_SQUARE.value + AppConfig.uiMapOverlayGap, delta, 0f)
    }

    @Test
    fun theShippedKeysAgreeWithTheCodesOwnDefaults() {
        val (gutter, square, gap) = shippedInsets()

        assertEquals(AppConfig.uiMapToggleGutter, gutter, 1e-6f)
        assertEquals(AppConfig.uiMapToggleSquare, square, 1e-6f)
        assertEquals(AppConfig.uiMapOverlayGap, gap, 1e-6f)
    }

    @Test
    fun theShippedNumbersPutATagUpAtFiftySixDp() {
        // 6 gutter + 44 square + 6 overlay gap: the numbers the plan's §4 arithmetic is built on — the
        // same ones `docs/ui-component-guidelines.md` §5.7's table carries.
        val (gutter, square, gap) = shippedInsets()

        assertEquals(56f, gutter + square + gap, 1e-6f)
        assertEquals(gutter + square + gap, bannerStartInset(tagsDrawn = true).value, 1e-6f)
    }

    @Test
    fun theTagStateOnlyEverPushesTheInsetFurtherIn() {
        assertTrue(bannerStartInset(tagsDrawn = false).value > 0f)
        assertTrue(bannerStartInset(tagsDrawn = true).value > bannerStartInset(tagsDrawn = false).value)
    }

    private companion object {
        /** `AppConfig`'s own interpolation, so a referenced value reads as the loader reads it. */
        val REF = Regex("""\$\{([^}]+)\}""")
    }
}
