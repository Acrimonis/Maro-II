package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The resolver's cascade (D5), pinned at each precedence level: the four candidates most specific
 * first, and the three rules that fall out of them — a class outranks the kind, an absent class drops
 * the two class-qualified candidates, and an empty group reads a top-level leaf. Pure, so the
 * precedence is read without a `Properties` bag; the resolver that walks these keys at runtime lives
 * in `AppConfig`.
 */
class PathKeyCandidatesTest {

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
}
