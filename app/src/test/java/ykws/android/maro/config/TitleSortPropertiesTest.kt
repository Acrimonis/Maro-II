package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * The shipped ignored words and the code's own default, held against each other — the habit every property
 * suite in this package follows: a key misspelled on one side, or a value changed on one side only, fails
 * here rather than shipping with the code's default standing quietly in its place.
 */
class TitleSortPropertiesTest {

    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    @Test
    fun theShippedWordsParseToTheCodesOwnDefault() {
        val raw = shippedProperties().getProperty("title.sort.ignoredPrefixes")

        assertNotNull("maro.properties must carry title.sort.ignoredPrefixes", raw)
        assertEquals(
            "the shipped words and AppConfig.titleSortIgnoredPrefixes must be one list",
            AppConfig.titleSortIgnoredPrefixes,
            raw!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        )
    }
}
