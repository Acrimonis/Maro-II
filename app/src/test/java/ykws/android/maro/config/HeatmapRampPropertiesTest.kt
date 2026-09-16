package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ykws.android.maro.ui.map.tickOffsetDp
import java.io.File
import java.util.Properties

/**
 * Drives [parseHeatmapFamilies] and [parseHeatmapScaleTicks] from the real `maro.properties` text, so
 * the shipped ramp and the shipped scale are tied to the file they are written in: a typo in a key name
 * — `family3.too=`, say — truncates the ramp and this test goes red, where a value-only fixture would
 * have stayed green.
 *
 * The test CWD is the `app` module (the convention the asset-baking tests follow); `maro.repoDir`
 * is honoured first so the file is found from a repo-root run too.
 */
class HeatmapRampPropertiesTest {

    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    /** The same `#RRGGBB` / `#AARRGGBB` forms `AppConfig`'s colour parser accepts. */
    private fun hexToArgb(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        return when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> null
        }
    }

    private fun parseFrom(props: Properties): List<HeatmapFamily> =
        parseHeatmapFamilies({ props.getProperty(it) }, ::hexToArgb)

    private fun ticksFrom(props: Properties): List<HeatmapScaleTick> =
        parseHeatmapScaleTicks({ props.getProperty(it) })

    private fun table(vararg rows: String): (String) -> String? = { key ->
        if (key == "track.heatmap.scaleTicks") rows.joinToString(",") else null
    }

    @Test
    fun theShippedFileParsesToItsEightFamilies() {
        val families = parseFrom(shippedProperties())

        assertEquals("every family must carry maxKn, from, to and stepKn", 8, families.size)
        assertEquals(listOf(5f, 7f, 10f, 13f, 15f, 25f, 32f, 70f), families.map { it.maxKn })
        assertEquals(
            listOf(0.5f, 0.25f, 0.5f, 0.5f, 1.0f, 3.0f, 3.0f, 5.0f),
            families.map { it.stepKn }
        )
        assertEquals(
            listOf(
                0xFF135FA2, 0xFF135FA2, 0xFF409443, 0xFF409443,
                0xFFDADAAD, 0xFFFFC53D, 0xFFEF6C00, 0xFF751212
            ).map { it.toInt() },
            families.map { it.fromArgb }
        )
        assertEquals(
            listOf(
                0xFF135FA2, 0xFF409443, 0xFF409443, 0xFFDADAAD,
                0xFFFFC53D, 0xFFEF6C00, 0xFF751212, 0xFF6A1B9A
            ).map { it.toInt() },
            families.map { it.toArgb }
        )
    }

    /**
     * The code's own defaults — `AppConfig`'s eight families, its neutral tint and its five scale
     * rows — are tied to the file here: a drift between the two now fails this suite instead of
     * shipping silently.
     */
    @Test
    fun theShippedRampAndScaleParseToTheCodesOwnDefaults() {
        val props = shippedProperties()
        val parsed = HeatmapRamp(
            families = parseFrom(props),
            unknownArgb = hexToArgb(props.getProperty("track.heatmap.unknownColor")!!)!!
        )

        assertEquals(AppConfig.trackHeatmapRamp, parsed)
        assertEquals(AppConfig.trackHeatmapScaleTicks, ticksFrom(props))
        assertEquals(
            AppConfig.trackHeatmapScaleMinKn,
            props.getProperty("track.heatmap.scaleMinKn")!!.toFloat(),
            0f
        )
    }

    @Test
    fun aTruncatingKeyTypoCutsTheRampAndThisTestSeesIt() {
        val props = shippedProperties()
        // The typo §11 names: family3's `to` key misspelled, so the ramp stops after two families.
        props.remove("track.heatmap.family3.to")
        props.setProperty("track.heatmap.family3.too", "#1E88E5")

        assertEquals(2, parseFrom(props).size)
    }

    /**
     * The shipped table's five rows as shipped, including the two rows that carry a compliance limit
     * off its own boundary: 5 prints at the 7 kn row and 10 at the 13 kn row, so the position and the
     * text must be asserted apart.
     */
    @Test
    fun theShippedTickTableParsesToFiveRowsWithItsPositionsAndTexts() {
        val ticks = ticksFrom(shippedProperties())

        assertEquals(listOf(7f, 13f, 22f, 30f, 35f), ticks.map { it.positionKn })
        assertEquals(listOf("5", "10", "20", "30", "35"), ticks.map { it.label })
    }

    /**
     * The whole point of the key: a row's text is not its position. The shipped table's first two rows
     * label the compliance limits while sitting under the boundaries they read against, so reading a row
     * as one number would lose exactly the read the table exists to give.
     */
    @Test
    fun aRowsTextLivesApartFromItsPosition() {
        val ticks = parseHeatmapScaleTicks(table("7:5", "12:10"))

        assertEquals(listOf(7f, 12f), ticks.map { it.positionKn })
        assertEquals(listOf("5", "10"), ticks.map { it.label })
    }

    @Test
    fun anAbsentTickTableParsesToNothingSoTheShippedDefaultStands() {
        assertTrue(parseHeatmapScaleTicks({ null }).isEmpty())
    }

    @Test
    fun aTickRowWithoutALabelEndsTheRead() {
        assertEquals(1, parseHeatmapScaleTicks(table("7:5", "12:", "15:15")).size)
    }

    /**
     * The label half of the legend's geometry now that the crowd rule is gone (§18): the bar runs from
     * the shipped minimum to the table's last position, each end inset by half a label so the endmost
     * labels are drawn whole, and the ladder descends between them. A position below the minimum clamps
     * onto the foot rather than leaving the bar. The bar's own fill re-derives the same linear map
     * inline in its Canvas, so this function is one of that arithmetic's two homes, not its only one.
     */
    @Test
    fun theOffsetArithmeticPlacesTheShippedRowsBetweenTheEndInsets() {
        val ticks = ticksFrom(shippedProperties())
        val topKn = ticks.last().positionKn
        val minKn = 2f
        val insetDp = 6f
        val barHeightDp = 150f

        assertEquals(insetDp, tickOffsetDp(topKn, minKn, topKn, barHeightDp, insetDp), 1e-4f)
        assertEquals(barHeightDp - insetDp, tickOffsetDp(minKn, minKn, topKn, barHeightDp, insetDp), 1e-4f)
        // Below the foot: 0 kn clamps onto the minimum rather than falling off the bar.
        assertEquals(barHeightDp - insetDp, tickOffsetDp(0f, minKn, topKn, barHeightDp, insetDp), 1e-4f)

        val offsets = ticks.map { tickOffsetDp(it.positionKn, minKn, topKn, barHeightDp, insetDp) }
        assertEquals(offsets.sortedDescending(), offsets)
        assertTrue(offsets.all { it in insetDp..(barHeightDp - insetDp) })
    }
}
