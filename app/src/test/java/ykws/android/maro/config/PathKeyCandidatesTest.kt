package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * The resolver's cascade (D5), pinned at each precedence level: the four candidates most specific
 * first, and the three rules that fall out of them — a class outranks the kind, an absent class drops
 * the two class-qualified candidates, and an empty group reads a top-level leaf. Pure, so the
 * precedence is read without a `Properties` bag; the resolver that walks these keys at runtime lives
 * in `AppConfig`. The last test reads the real `maro.properties`, so the two axes' three tiers and the
 * retired gates are tied to the file they are written in.
 */
class PathKeyCandidatesTest {

    /** The shipped `maro.properties`: `maro.repoDir` first, else the `app` module's CWD. */
    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    @Test
    fun theFourCandidatesRunMostSpecificFirst() {
        assertEquals(
            listOf(
                "path.route.line.color.pinned.from",
                "path.line.color.pinned.from",
                "path.route.line.color.from",
                "path.line.color.from"
            ),
            pathKeyCandidates(
                kind = PathKind.ROUTE,
                group = "line",
                field = "color",
                sub = "from",
                pathClass = PathClass.PINNED
            )
        )
    }

    @Test
    fun aClassOutranksTheKind() {
        val keys = pathKeyCandidates(PathKind.TRACK, "line", "width", pathClass = PathClass.SELECTED)

        assertEquals(
            listOf(
                "path.track.line.width.selected",
                "path.line.width.selected",
                "path.track.line.width",
                "path.line.width"
            ),
            keys
        )
        // The class candidate sits above the kind candidate, which is the precedence itself: a kind
        // override the class beats.
        assertTrue(keys.indexOf("path.line.width.selected") < keys.indexOf("path.track.line.width"))
    }

    @Test
    fun anAbsentClassDropsTheTwoClassCandidates() {
        assertEquals(
            listOf("path.track.line.width", "path.line.width"),
            pathKeyCandidates(PathKind.TRACK, "line", "width")
        )
    }

    @Test
    fun anEmptyGroupReadsATopLevelLeaf() {
        assertEquals(
            listOf("path.route.count", "path.count"),
            pathKeyCandidates(PathKind.ROUTE, "", "count")
        )
    }

    @Test
    fun aSubBuildsItsOwnTrailingSegment() {
        assertEquals(
            listOf("path.track.line.dash.on", "path.line.dash.on"),
            pathKeyCandidates(PathKind.TRACK, "line", "dash", "on")
        )
    }

    @Test
    fun theClassLeafSitsAboveTheTwoSeedsOnATopLevelAxis() {
        // The class-bearing candidates alone, which is what the runtime override walks: no kind and no
        // global seed appended, a class being an override rather than a fallback (2026-10-07).
        // The axis is the group and `enabled` its leaf, so the class qualifier lands after the leaf —
        // `path.arrow.enabled.acquisition`, as the file writes it.
        assertEquals(
            listOf("path.route.arrow.enabled.acquisition", "path.arrow.enabled.acquisition"),
            pathClassKeyCandidates(PathKind.ROUTE, "arrow", "enabled", null, PathClass.ACQUISITION)
        )
        // And the full cascade appends the two seeds beneath the class leaf: the kind, then the global.
        assertEquals(
            listOf(
                "path.route.arrow.enabled.acquisition",
                "path.arrow.enabled.acquisition",
                "path.route.arrow.enabled",
                "path.arrow.enabled"
            ),
            pathKeyCandidates(PathKind.ROUTE, "arrow", "enabled", pathClass = PathClass.ACQUISITION)
        )
    }

    @Test
    fun theShippedFileCarriesBothAxesThreeTiersAndNoGate() {
        val props = shippedProperties()

        // Both axes carry the global, the kind and the class leaf (2026-10-07): a key misspelled on
        // either side would leave the other's value standing and fail here.
        listOf(
            "path.arrow.enabled",
            "path.track.arrow.enabled",
            "path.route.arrow.enabled",
            "path.arrow.enabled.acquisition",
            "path.heatmap.enabled",
            "path.track.heatmap.enabled",
            "path.route.heatmap.enabled",
            "path.heatmap.enabled.acquisition"
        ).forEach { key ->
            assertNotNull("maro.properties must carry $key", props.getProperty(key))
        }
        // The acquisition's own rung is silent on both axes, and the two retired gates are gone.
        assertEquals("false", props.getProperty("path.arrow.enabled.acquisition").trim())
        assertEquals("false", props.getProperty("path.heatmap.enabled.acquisition").trim())
        assertNull("path.gate.speedColor is retired", props.getProperty("path.gate.speedColor"))
        assertNull("path.gate.speedArrows is retired", props.getProperty("path.gate.speedArrows"))
    }
}
