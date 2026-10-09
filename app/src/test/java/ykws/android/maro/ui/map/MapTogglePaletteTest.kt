package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File
import java.util.Properties

/**
 * The shipped palette agrees with the toggle normalization.
 *
 * The six deleted keys are gone (the four unread off-tokens, off being the shared pale square, plus the
 * two readerless active-alphas), the tracking dot pair retires, the new and renamed keys are present, and
 * the three surface weights sit at the values the 2026-10-09 pass settled. Reads
 * `app/src/main/assets/colors.properties` in the shape `BannerStartInsetTest` uses, so a key the code
 * stopped shipping, one the file still carries, or a surface number moved on one side only, fails here
 * rather than silently.
 */
class MapTogglePaletteTest {

    private val colorsFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/colors.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/colors.properties")

    private fun shipped(): Properties {
        assumeTrue("colors.properties not found", colorsFile.isFile)
        return Properties().apply { colorsFile.inputStream().use { load(it) } }
    }

    @Test
    fun theSixDeletedKeysAreAbsent() {
        val props = shipped()
        for (key in listOf(
            "status.gps.demo",
            "status.tracking.off",
            "status.lock.off",
            "status.earthWater.inactive",
            "status.tracking.alpha.active",
            "status.lock.alpha.active"
        )) {
            assertNull("$key must be deleted — no code reads it", props.getProperty(key))
        }
    }

    @Test
    fun theTrackingDotPairIsAbsent() {
        val props = shipped()
        assertNull(
            "status.tracking.dot.recording retires onto ui.map.pulse.dot",
            props.getProperty("status.tracking.dot.recording")
        )
        assertNull(
            "status.tracking.dot.idle retires onto ui.map.pulse.dot",
            props.getProperty("status.tracking.dot.idle")
        )
    }

    @Test
    fun theRenamedTrackingFillKeyNoLongerCarriesItsOldName() {
        val props = shipped()
        assertNull(
            "status.tracking.healthy is renamed to status.tracking.recording",
            props.getProperty("status.tracking.healthy")
        )
    }

    @Test
    fun theSurfaceWeightsSitAtTheSettledValuesAndTheCodeFollows() {
        val props = shipped()
        assertEquals("base white at 55 %", "#8CFFFFFF", props.getProperty("ui.map.surface.inactive"))
        assertEquals(
            "inactive content alpha",
            "0.6",
            props.getProperty("ui.map.surface.inactive.content.alpha")
        )
        assertEquals("active tint", "0.5", props.getProperty("ui.map.surface.active.alpha"))
        // `AppConfig` follows the palette, which is the source of truth, so a moved number cannot pass.
        assertEquals(AppConfig.uiMapSurfaceInactive, 0x8CFFFFFF.toInt())
        assertEquals(AppConfig.uiMapSurfaceInactiveContentAlpha, 0.6f, 1e-6f)
        assertEquals(AppConfig.uiMapSurfaceActiveAlpha, 0.5f, 1e-6f)
    }

    @Test
    fun theNewAndRenamedKeysAreShipped() {
        val props = shipped()
        for (key in listOf(
            "status.gps.estimating",
            "status.tracking.recording",
            "status.tracking.idle",
            "status.earthWater.water",
            "status.earthWater.land",
            "status.gps.healthy",
            "status.gps.idle"
        )) {
            assertNotNull("colors.properties must carry $key", props.getProperty(key))
        }
    }
}
