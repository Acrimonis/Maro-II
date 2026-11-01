package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The search panel's two marks** — the crowned row's check and the rank's place under the name. Both live in
 * Compose code with no pure seam, so they are pinned as text, the file-first convention `SpeedDisplayChipsTest`
 * and `TrackRenderStringsTest` use: the check is sized by its own `ui.route.table.crown.size` key, which ships
 * above the name's own size, and it closes the name's line while the rank stands on the line below.
 */
class RouteCrownMarksTest {

    private val panelPath = "src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt"

    /** The summary table's own composable — the slice the row's cells are drawn in. */
    private fun summaryTable(): String = sourceText(panelPath)
        .substringAfter("private fun RouteSummaryTable(")
        .substringBefore("private fun RouteValueLine(")

    /** The name line's own `Row` — from its opening to the brace that closes it. */
    private fun nameLine(table: String): String {
        val open = "Row(verticalAlignment = Alignment.CenterVertically) {"
        val from = table.indexOf(open)
        assertTrue("the name cell opens its first line on one Row", from >= 0)
        var depth = 0
        for (i in from + open.length - 1 until table.length) {
            when (table[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return table.substring(from, i + 1)
            }
        }
        throw AssertionError("the name line's Row never closes")
    }

    @Test
    fun theCheckIsSizedByItsOwnKeyAboveTheName() {
        val panel = sourceText(panelPath)
        assertTrue(
            "the glyph's size is the key's accessor",
            panel.contains("ROUTE_CROWN_GLYPH_SIZE get() = AppConfig.uiRouteTableCrownSize.sp")
        )
        val config = sourceText("src/main/java/ykws/android/maro/config/AppConfig.kt")
        assertTrue(
            "the accessor reads the shipped key",
            config.contains("uiRouteTableCrownSize = sp(\"ui.route.table.crown.size\", uiRouteTableCrownSize)")
        )
        val line = nameLine(summaryTable())
        val glyph = line.substringAfter("text = ROUTE_CROWN_GLYPH,").substringBefore("fontWeight")
        assertTrue("the check is drawn at that size", glyph.contains("fontSize = ROUTE_CROWN_GLYPH_SIZE"))
        val nameSp = Regex("""text = label,\s*color = textColor,\s*fontSize = ([0-9.]+)\.sp""")
            .find(line)?.groupValues?.get(1)?.toDouble()
            ?: throw AssertionError("the name's own size is not read on its line")
        val shippedSp = Regex("""(?m)^ui\.route\.table\.crown\.size=([0-9.]+)sp""")
            .find(sourceText("src/main/assets/ui.properties"))?.groupValues?.get(1)?.toDouble()
            ?: throw AssertionError("ui.properties ships no ui.route.table.crown.size")
        assertTrue("the shipped check ($shippedSp sp) stands above the name ($nameSp sp)", shippedSp > nameSp)
    }

    @Test
    fun theCheckClosesTheNamesLineAndTheRankStandsBelowIt() {
        val table = summaryTable()
        val line = nameLine(table)
        assertTrue("the name opens its line", line.contains("text = label,"))
        assertTrue("the check is drawn on the name's line, after the name", line.indexOf("text = ROUTE_CROWN_GLYPH,") > line.indexOf("text = label,"))
        assertTrue("and only on the crowned row", line.contains("if (isCrown)"))
        assertEquals("the rank is not on the name's line", -1, line.indexOf("\"#\$rank\""))
        val afterLine = table.substringAfter(line)
        val rankAt = afterLine.indexOf("text = \"#\$rank\"")
        assertTrue("the rank is drawn after the name's line closes", rankAt >= 0)
        // Still inside the name cell's Column: no brace between the line's close and the rank closes that Column.
        var depth = 0
        for (c in afterLine.substring(0, rankAt)) {
            if (c == '{') depth++
            if (c == '}') depth--
            assertTrue("the rank stands in the same column, the line below the name", depth >= 0)
        }
    }
}
