package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File
import java.util.Properties

/**
 * **The berth is one number in two places, and this is what keeps them from drifting apart.**
 *
 * The hybrid berth's distance is carried twice: by the bake, as
 * [`RouteMeshBuilder.Tuning.zoneBerthM`] — the strip it cuts where the water has the room — and by the
 * search, as `route.zoneBerthM` in `maro.properties`, read live through
 * [`AppConfig.routeZoneBerthM`](app/src/main/java/ykws/android/maro/config/AppConfig.kt:133), the price
 * it pays where the water is tight. A bake that cut 25 m of water while the search priced 30 m would be
 * two rules wearing one name, and neither half's own tests could see it — so the shipped file is what
 * is read here, and all three are tied together: the file's value, the loader's own default for it, and
 * the bake's tuning.
 *
 * The test CWD is the `app` module (the convention the other `…PropertiesTest`s follow); `maro.repoDir`
 * is honoured first, so the file is found from a repo-root run too.
 */
class RouteZoneBerthPropertiesTest {

    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    @Test
    fun theBakesBerthAndTheSearchesOwnKeyAreTheSameDistance() {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        val shipped = Properties().apply { propertiesFile.inputStream().use { load(it) } }
            .getProperty("route.zoneBerthM")
        val property = shipped?.toDoubleOrNull()
        assertNotNull("route.zoneBerthM must be a number, got '$shipped'", property)

        assertEquals(
            "the loader's own default must be the shipped key's value",
            property!!,
            AppConfig.routeZoneBerthM.toDouble(),
            1e-6
        )
        assertEquals(
            "the bake's berth and the search's must be the same distance",
            property,
            RouteMeshBuilder.Tuning().zoneBerthM,
            1e-9
        )
    }
}
