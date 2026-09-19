package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import kotlin.math.hypot

class TrackDirectionOverlayTest {

    private fun pt(
        lon: Double,
        lat: Double = 0.0,
        type: PointType = PointType.NORMAL,
        speedMps: Float? = null,
        bearingDeg: Float? = null
    ) = TrackPoint(
        lat = lat,
        lon = lon,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
        timeOffsetSec = 0,
        timeOffsetMs = 0L,
        type = type
    )

    private val identityProject: (TrackPoint) -> ScreenPt = { p -> ScreenPt(p.lon.toFloat(), p.lat.toFloat()) }

    @Test
    fun uniformSpacingOnStraightLine() {
        val points = listOf(pt(0.0), pt(1000.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 100f }, maxArrows = 100)
        assertEquals(10, anchors.size)
        assertTrue(anchors.all { it.segmentIndex == 0 })
        assertEquals(0.1f, anchors[0].t, 0.001f)
        assertEquals(1.0f, anchors[9].t, 0.001f)
    }

    @Test
    fun gapSegmentsAreSkipped() {
        val points = listOf(
            pt(0.0), pt(100.0),
            pt(100.0, type = PointType.GAP),
            pt(200.0), pt(300.0)
        )
        val anchors = sampleArrowAnchors(points, identityProject, { 50f }, maxArrows = 100)
        assertEquals(4, anchors.size)
        assertTrue(anchors.all { it.segmentIndex == 0 || it.segmentIndex == 3 })
    }

    @Test
    fun bearingFallsBackToSegmentVector() {
        val north = sampleArrowAnchors(listOf(pt(0.0, 0.0), pt(0.0, 1.0)), identityProject, { 1f }, 10)
        assertEquals(1, north.size)
        assertEquals(0f, north[0].bearingDeg, 0.001f)

        val east = sampleArrowAnchors(listOf(pt(0.0, 0.0), pt(1.0, 0.0)), identityProject, { 1f }, 10)
        assertEquals(1, east.size)
        assertEquals(90f, east[0].bearingDeg, 0.001f)
    }

    @Test
    fun recordedBearingIsPreferred() {
        val points = listOf(pt(0.0, 0.0, bearingDeg = 45f), pt(1.0, 0.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 1f }, 10)
        assertEquals(1, anchors.size)
        assertEquals(45f, anchors[0].bearingDeg, 0.001f)
    }

    @Test
    fun spacingPxClampsAndInterpolates() {
        val floor = 3f
        val ceiling = 35f
        val min = 24f
        val max = 120f
        assertEquals(min, spacingPxForSpeed(0f, floor, ceiling, min, max), 0.001f)
        assertEquals(min, spacingPxForSpeed(3f, floor, ceiling, min, max), 0.001f)
        assertEquals(max, spacingPxForSpeed(35f, floor, ceiling, min, max), 0.001f)
        assertEquals(max, spacingPxForSpeed(50f, floor, ceiling, min, max), 0.001f)
        // Exponential midpoint: t = 0.5 → min × (max/min)^0.5
        val mid = min * kotlin.math.sqrt(max / min)
        assertEquals(mid, spacingPxForSpeed(19f, floor, ceiling, min, max), 0.001f)
    }

    @Test
    fun maxArrowsCapsOutput() {
        val points = listOf(pt(0.0), pt(1000.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 10f }, maxArrows = 5)
        assertEquals(5, anchors.size)
    }

    // ── The chevron's relations, and the ceiling over them ───────────────

    @Test
    fun theChevronKeepsTheRelationsOfTheCoreItIsDrawnOver() {
        val core = AppConfig.trackWidthSelectedDp
        val length = chevronLength(core, widestStoredTrackWidth())

        assertEquals("the length is 2.5 × the core", core * 2.5f, length, 0.001f)
        assertEquals("the half-width is 0.6 × the length", length * 0.6f, chevronHalfWidth(length), 0.001f)
        assertEquals("the coloured stroke is 0.5 × the core", core * 0.5f, chevronStrokeWidth(core), 0.001f)
    }

    // ── Chevron tempering, above the knee only ───────────────────────────

    @Test
    fun theClassesAtOrBelowTheKneeKeepTheirOwnCore() {
        val knee = AppConfig.trackArrowScaleKneeDp
        val temper = AppConfig.trackArrowTemper

        // The knee is a core width in dp now, like the widths it is compared against.
        assertEquals("the shipped knee, in dp — the 10 px of the 3× reference", 10f / 3f, knee, 0.001f)
        assertEquals("the shipped temper", 0.5f, temper, 0.001f)
        // The shipped table's thinner classes sit at or below the knee. Which classes those are is the
        // file's own relation and not this pass's: the pins below state the rule, not a class list.
        listOf(
            "history" to AppConfig.trackWidthHistoryDp,
            "pinned" to AppConfig.trackWidthPinnedDp
        ).forEach { (what, core) ->
            assertTrue("the $what core must sit at or below the knee", core <= knee)
            assertEquals(
                "the $what core is used as it is",
                core,
                temperedCore(core, knee, temper),
                0.001f
            )
        }
        assertEquals("the knee itself is untouched", knee, temperedCore(knee, knee, temper), 0.001f)
    }

    @Test
    fun theCoreAboveTheKneeIsTemperedAndItsThreeMultiplesReadTheTemperedOne() {
        val knee = AppConfig.trackArrowScaleKneeDp
        val temper = AppConfig.trackArrowTemper
        // The class above the knee is the widest one the shipped table hands out, whatever its name:
        // taking it from the table rather than naming a class keeps this a rule, not a class list.
        val core = widestStoredTrackWidth()
        val tempered = knee + (core - knee) * temper

        assertTrue("the widest shipped core sits above the knee", core > knee)
        assertEquals(tempered, temperedCore(core, knee, temper), 0.001f)
        assertTrue("the tempered core must shrink the oversized arrows", tempered < core)

        // The length, the half-width and the coloured stroke all follow the tempered core, so that
        // class's arrows stop reading oversized while the classes at or below the knee are untouched.
        val length = chevronLength(tempered, widestStoredTrackWidth())
        assertEquals("the length is 2.5 × the tempered core", tempered * 2.5f, length, 0.001f)
        assertTrue(
            "the raw core would have drawn the longer chevron",
            chevronLength(core, widestStoredTrackWidth()) > length
        )
        assertEquals("the half-width is 0.6 × that length", length * 0.6f, chevronHalfWidth(length), 0.001f)
        assertEquals(
            "the coloured stroke is 0.5 × the tempered core",
            tempered * 0.5f,
            chevronStrokeWidth(tempered),
            0.001f
        )
    }

    @Test
    fun theCasingArmRunsOutsideTheColouredOneAtTheRimThickness() {
        val core = AppConfig.trackWidthSelectedDp
        val casing = AppConfig.trackWidthSelectedCasingDp
        // The rim belongs to the selection rather than to the stroke it edges, so it is read against the
        // line's own width — never against the tempered core the coloured V's metrics come from, which
        // would float the band further out.
        val tempered = temperedCore(core, AppConfig.trackArrowScaleKneeDp, AppConfig.trackArrowTemper)
        val length = chevronLength(tempered, widestStoredTrackWidth())
        val halfW = chevronHalfWidth(length)

        // Read from first principles rather than from the implementation's arithmetic: half the casing's
        // excess over the line's own width.
        val rim = (casing - core) / 2f
        // The shipped selected core sits at the knee, so the selection is drawn with its own core.
        assertEquals("the tempered core is the one the coloured V is drawn from", core, tempered, 0.001f)
        assertEquals("half the casing's excess over the line's own width", 1f, rim, 0.001f)
        assertEquals("the function agrees with that rim", rim, chevronCasingOffset(casing, core), 0.001f)
        assertEquals(
            "the shipped 5.333-over-3.333 pair, by hand",
            1f,
            chevronCasingOffset(16f / 3f, 10f / 3f),
            0.001f
        )
        assertTrue("the dark V must sit outside the coloured one", rim > 0f)

        val coloured = chevronV(0f, length, halfW)
        val dark = chevronV(rim, length, halfW, CHEVRON_CAP_OVERLAP_DP)

        // Collinear with the coloured arms pushed out, and never scaled: both endpoints of each dark
        // arm measure exactly the rim from the coloured arm's own line.
        listOf("apex" to dark.apex, "left tip" to dark.leftTip).forEach { (what, point) ->
            assertEquals(
                "the dark $what sits the rim from the coloured left arm",
                rim,
                perpendicularDistance(point, coloured.apex, coloured.leftTip),
                0.01f
            )
        }
        listOf("apex" to dark.apex, "right tip" to dark.rightTip).forEach { (what, point) ->
            assertEquals(
                "the dark $what sits the rim from the coloured right arm",
                rim,
                perpendicularDistance(point, coloured.apex, coloured.rightTip),
                0.01f
            )
        }

        // The apex is where the two shifted arms meet, so it stays on the bearing axis, and each tip
        // wraps the coloured corner instead of stopping short of it.
        assertEquals("the dark apex stays on the bearing", 0f, dark.apex.x, 0.001f)
        assertTrue("the dark left tip must reach past the coloured one", dark.leftTip.x < coloured.leftTip.x)
        assertTrue("the dark right tip must reach past the coloured one", dark.rightTip.x > coloured.rightTip.x)

        // Both strokes are the same width, so the dark band runs from the coloured centreline outward
        // and never crosses to the inside of the V — the rim is outside by construction, and at the
        // shipped pair the casing's excess over the line's own width leaves that inner edge a sixth of a
        // dp clear of the centreline, still inside the coloured stroke's own half-width and so covered.
        val innerEdge = rim - chevronStrokeWidth(tempered) / 2f
        assertTrue("the dark stroke's inner edge must never cross the coloured centreline", innerEdge >= 0f)
        assertEquals(
            "the inner edge clears the coloured centreline by 0.167 dp — 0.5 px — at the shipped pair",
            1f / 6f,
            innerEdge,
            0.001f
        )

        // A casing no wider than the line pins the dark V exactly under the coloured one: no rim, and no
        // inversion of the two strokes either.
        assertEquals(0f, chevronCasingOffset(core, core), 0.001f)
        assertEquals(0f, chevronCasingOffset(core, core + 10f), 0.001f)
        assertEquals("no rim means no wrapping", coloured, chevronV(0f, length, halfW, CHEVRON_CAP_OVERLAP_DP))
    }

    @Test
    fun theRimIsMeasuredFromTheLinesWidthHoweverTheKneeAndTemperMove() {
        // A line above the knee — which the shipped selected pair is not, sitting exactly at it — so the
        // two references can be told apart: the casing is 6 over the line's 4 dp width, and the coloured
        // stroke is the tempered core's own half. Read from that tempered core the band would be floated
        // further out, which is why the line's own width is the reference.
        val line = 4f
        val casing = 6f
        val knee = 10f / 3f
        val temper = 0.5f
        val colouredStroke = chevronStrokeWidth(temperedCore(line, knee, temper))
        val innerEdgeOf = { reference: Float ->
            chevronCasingOffset(casing, reference) - colouredStroke / 2f
        }

        val tempered = temperedCore(line, knee, temper)
        assertEquals("a 4 dp line tempers down to 3.667 dp", 11f / 3f, tempered, 0.001f)
        assertEquals(
            "measured from the line's own width the inner edge lands at a twelfth of a dp",
            1f / 12f,
            innerEdgeOf(line),
            0.001f
        )
        assertEquals(
            "the tempered core instead floats the band's inner edge further out",
            1f / 4f,
            innerEdgeOf(tempered),
            0.001f
        )
        assertTrue(
            "the line's own width keeps the band the nearer of the two to the coloured centreline",
            innerEdgeOf(line) < innerEdgeOf(tempered)
        )

        // The knee and the temper move the coloured core, and the coloured stroke with it, never the rim:
        // that reads the line's own width alone, so one casing keeps one offset whatever the two settings
        // are set to, and it is the coloured stroke that decides how near the coloured centreline the dark
        // band's inner edge falls. The tempered core never rises above the line's own width, so reading it
        // instead can only float the rim outward.
        listOf(1f, 2f, 4f, 8f).forEach { core ->
            listOf(1f, 10f / 3f, 6f).forEach { knee ->
                listOf(0f, 0.25f, 0.5f, 1f).forEach { temper ->
                    val rimFromTheLine = chevronCasingOffset(casing, core)
                    val rimFromTheTemperedCore = chevronCasingOffset(casing, temperedCore(core, knee, temper))

                    assertEquals(
                        "core=$core knee=$knee temper=$temper: half the casing's excess over the line's width",
                        (casing - core).coerceAtLeast(0f) / 2f,
                        rimFromTheLine,
                        0.001f
                    )
                    assertTrue(
                        "core=$core knee=$knee temper=$temper: the tempered core instead never pulls the rim inward",
                        rimFromTheTemperedCore >= rimFromTheLine
                    )
                }
            }
        }
    }

    /** Perpendicular distance from [point] to the line through [a] and [b], sign ignored. */
    private fun perpendicularDistance(point: ScreenPt, a: ScreenPt, b: ScreenPt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        return kotlin.math.abs((point.x - a.x) * dy - (point.y - a.y) * dx) / hypot(dx, dy)
    }

    @Test
    fun theCeilingIsTheWidestStoredWidthSoItCannotBiteInsideTheTable() {
        val ceiling = widestStoredTrackWidth()
        val widths = listOf(
            AppConfig.trackWidthSelectedDp,
            AppConfig.trackWidthNewestDp,
            AppConfig.trackWidthPinnedDp,
            AppConfig.trackWidthHistoryDp
        )

        assertEquals(
            "the reference is the widest of the table, not the selected width alone",
            maxOf(
                AppConfig.trackWidthSelectedDp,
                AppConfig.trackWidthNewestDp,
                AppConfig.trackWidthPinnedDp,
                AppConfig.trackWidthHistoryDp
            ),
            ceiling,
            0.001f
        )
        assertTrue("a retuned file must not push the reference under the widest core", ceiling >= widths.max())
        widths.forEach { width ->
            assertEquals(
                "a $width dp core keeps the pure relation: the ceiling cannot bite at it",
                width * 2.5f,
                chevronLength(width, ceiling),
                0.001f
            )
        }
        // Beyond the table it still caps, so a widened class cannot outgrow the file's own reference.
        assertEquals(ceiling * 2.5f, chevronLength(ceiling * 4f, ceiling), 0.001f)
        // The old 12 px floor flattened the thin end; the relations alone govern it now.
        assertEquals(2.5f, chevronLength(1f, ceiling), 0.001f)
    }
}
