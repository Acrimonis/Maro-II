package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * The notice family's shipped figures held against the code's own defaults, and the three property files held
 * against each other: `AppConfig` loads them into one bag in the order maro, ui, colors, so a key left behind in
 * a later file silently overrides the one that moved — this suite fails on that instead.
 */
class NoticePropertiesTest {

    private fun assetFile(name: String): File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/$name") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/$name")

    private fun load(name: String): Properties {
        val file = assetFile(name)
        assumeTrue("$name not found", file.isFile)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    private fun shipped(key: String): String =
        load("maro.properties").getProperty(key)?.trim() ?: error("maro.properties must carry $key")

    @Test
    fun noKeyIsDefinedInTwoOfTheThreeFiles() {
        val files = listOf("maro.properties", "ui.properties", "colors.properties")
        val owners = files.flatMap { name -> load(name).stringPropertyNames().map { it to name } }
            .groupBy({ it.first }, { it.second })
        val duplicated = owners.filterValues { it.size > 1 }

        assertTrue("keys defined in more than one file (the later file wins): $duplicated", duplicated.isEmpty())
    }

    @Test
    fun theMovedBandGeometryLivesInMaroProperties() {
        val maro = load("maro.properties")
        val colors = load("colors.properties")
        listOf("ui.map.toggle.square", "ui.map.toggle.gutter", "ui.map.overlay.gap").forEach { key ->
            assertTrue("$key must be in maro.properties", maro.containsKey(key))
            assertTrue("$key must have left colors.properties", !colors.containsKey(key))
        }
    }

    @Test
    fun theShippedFiguresAreTheCodesOwnDefaults() {
        assertEquals(AppConfig.noticeDwellShortMs, shipped("notice.dwell.shortMs").toLong())
        assertEquals(AppConfig.noticeDwellReadableMs, shipped("notice.dwell.readableMs").toLong())
        assertEquals(AppConfig.noticeDwellErrorMs, shipped("notice.dwell.errorMs").toLong())
        assertEquals(AppConfig.noticeRowTimeoutMs, shipped("notice.row.timeoutMs").toLong())
        assertEquals(AppConfig.noticePillMaxVisible, shipped("notice.pill.maxVisible").toInt())
        assertEquals(AppConfig.noticeRowMaxVisible, shipped("notice.row.maxVisible").toInt())
        assertEquals(AppConfig.noticeRowSwipeDp, shipped("notice.row.swipeDp").toFloat(), 0f)
        assertEquals(AppConfig.noticeSkinCornerDp, shipped("notice.skin.cornerDp").toFloat(), 0f)
        assertEquals(AppConfig.noticeSkinBorderDp, shipped("notice.skin.borderDp").toFloat(), 0f)
        assertEquals(AppConfig.noticeSkinShadowDp, shipped("notice.skin.shadowDp").toFloat(), 0f)
        assertEquals(AppConfig.noticeTextSizeSp, shipped("notice.text.sizeSp").toFloat(), 0f)
        assertEquals(AppConfig.noticePillPadHorizontalDp, shipped("notice.pill.padHorizontalDp").toFloat(), 0f)
        assertEquals(AppConfig.noticePillPadVerticalDp, shipped("notice.pill.padVerticalDp").toFloat(), 0f)
        assertEquals(AppConfig.noticeStackGapDp, shipped("notice.stack.gapDp").toFloat(), 0f)
        assertEquals(AppConfig.exitBackWindowMs, shipped("map.exit.backWindowMs").toLong())
    }

    @Test
    fun theBandGeometryDefaultsAreTheShippedValues() {
        assertEquals(AppConfig.uiMapToggleSquare, shipped("ui.map.toggle.square").toFloat(), 0f)
        assertEquals(AppConfig.uiMapToggleGutter, shipped("ui.map.toggle.gutter").toFloat(), 0f)
    }
}
