package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Holds inspect mode's quiet time to its two homes: the shipped `map.inspect.dwellMs` and the
 * [AppConfig] accessor that mirrors it.
 *
 * The key is pinned in the shape the other `config/` property suites use, so a key misspelled on one
 * side or a value moved on one side alone fails here. The test CWD is the `app` module (the convention
 * these suites follow); `maro.repoDir` is honoured first so the file is found from a repo-root run too.
 */
class InspectModePropertiesTest {

    /** The quiet time shipped today, in ms. */
    private val expectedDwellMs = 1499L

    private fun shipped(relative: String): Properties {
        val file = System.getProperty("maro.repoDir")
            ?.let { File(it, "app/src/main/assets/$relative") }
            ?.takeIf { it.isFile }
            ?: File("src/main/assets/$relative")
        assumeTrue("$relative not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    @Test
    fun theQuietTimeShipsAtItsValueAndTheAccessorMirrorsIt() {
        val props = shipped("maro.properties")

        assertEquals(
            "map.inspect.dwellMs must ship at its settled value",
            expectedDwellMs.toString(),
            props.getProperty("map.inspect.dwellMs").trim()
        )
        assertEquals(
            "the app edge's accessor must mirror the quiet time",
            expectedDwellMs,
            AppConfig.uiMapInspectDwellMs
        )
    }
}
