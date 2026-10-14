package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import ykws.android.maro.spatial.DirectoryFineTileStore

/**
 * Holds the fine-tile store's disk ceiling to its one home.
 *
 * `route.fineTile.maxBytes` is the value's home and its two code copies mirror it — [AppConfig]'s
 * accessor read at the app edge and [DirectoryFineTileStore.DEFAULT_MAX_BYTES] standing for a caller
 * that names none. All three move in one write, so they can never disagree: a key misspelled on one
 * side, which would leave the code's default standing, or a value changed on one side only, fails here.
 *
 * The test CWD is the `app` module (the convention `CoastlineAppearancePropertiesTest` follows);
 * `maro.repoDir` is honoured first so the file is found from a repo-root run too.
 */
class FineTileStoreCeilingPropertiesTest {

    /** The ceiling shipped today — 48 MiB, the value of 2026-10-14 (D8). */
    private val expectedBytes = 48L * 1024L * 1024L

    private fun assetFile(relative: String): File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/$relative") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/$relative")

    private fun shipped(relative: String): Properties {
        val file = assetFile(relative)
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theCeilingKeyShipsAtFortyEightMiBAndItsTwoCodeCopiesMirrorIt() {
        val props = shipped("maro.properties")

        assertEquals(
            "route.fineTile.maxBytes must ship at 48 MiB",
            expectedBytes,
            props.getProperty("route.fineTile.maxBytes").trim().toLong()
        )
        assertEquals(
            "the app edge's accessor must mirror the key",
            expectedBytes,
            AppConfig.routeFineTileMaxBytes
        )
        assertEquals(
            "the store's own default must mirror the key",
            expectedBytes,
            DirectoryFineTileStore.DEFAULT_MAX_BYTES
        )
    }
}
