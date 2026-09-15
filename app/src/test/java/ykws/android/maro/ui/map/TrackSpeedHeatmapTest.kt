package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.HEATMAP_MAX_FAMILIES
import ykws.android.maro.config.HeatmapFamily
import ykws.android.maro.config.HeatmapRamp
import ykws.android.maro.config.parseHeatmapFamilies
import ykws.android.maro.config.parseHeatmapScaleTicks
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.deriveSpeedMps

/**
 * The speed mapping's own unit test, beside [TrackPolylineAppearanceTest]: ramp v4's two flat zones,
 * the one-knot changeover between them, the anchors at 5 / 6 / 10 / 15 / 35 / 70 kn, the hard edge at
 * 15, each family's own draw step, the family bound, the neutral tint path, the band grouping and the
 * two band counts the v4 grid measures. Pure functions only — the osmdroid drawing stays in the
 * Compose shell.
 */
class TrackSpeedHeatmapTest {

    // ── Ramp v4, a pinned fixture — the shipped file is v5 ───────────────

    private val green = 0xFF4CAF50.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val lightOrange = 0xFFFFB74D.toInt()
    private val orange = 0xFFEF6C00.toInt()
    private val red = 0xFFB71C1C.toInt()
    private val purple = 0xFF6A1B9A.toInt()

    /** The width fixture the banded tests pass through, kept local: it is the parameter, not a shipped value. */
    private val fixtureStrokeWidth = 12f

    /** Six families with per-family steps, pinned as a fixture: v4 of the grid, not what the file holds. */
    private val ramp = HeatmapRamp(
        families = listOf(
            HeatmapFamily(5f, green, green, 0.5f),          // flat compliant green to the 5 kn limit
            HeatmapFamily(6f, green, blue, 0.25f),          // the whole changeover, in one knot
            HeatmapFamily(10f, blue, blue, 0.5f),           // flat blue to the 10 kn limit
            HeatmapFamily(15f, blue, lightOrange, 0.5f),    // warming across 10 to 15
            HeatmapFamily(35f, orange, red, 1.0f),          // the hard edge at 15, deepening to 35
            HeatmapFamily(70f, red, purple, 5.0f)           // the range almost nothing occupies
        ),
        unknownArgb = 0xFF90A4AE.toInt()
    )

    /** m/s for a speed expressed in knots, so the fixtures read in the ramp's own unit. */
    private fun mps(kn: Float): Float = kn / 1.94384f

    private fun p(
        speedMps: Float? = null,
        timeMs: Long = 0L,
        lat: Double = 0.0,
        type: PointType = PointType.NORMAL
    ) = TrackPoint(lat = lat, lon = 7.0, speedMps = speedMps, timeOffsetMs = timeMs, type = type)

    private fun pointsAtKn(vararg kn: Float): List<TrackPoint> =
        kn.mapIndexed { i, speed -> p(speedMps = mps(speed), timeMs = i * 1000L) }

    private fun rgb(argb: Int): Int = argb and 0x00FFFFFF

    private fun alphaOf(argb: Int): Int = argb ushr 24 and 0xFF

    private fun bandedBands(
        points: List<TrackPoint>,
        strokeWidth: Float = fixtureStrokeWidth,
        fade: Float = 1f
    ) = bandedAppearances(points, resolveSpeeds(points), ramp, strokeWidth, fade)

    /** The expected gradient output, written independently of the production interpolation. */
    private fun blend(fromArgb: Int, toArgb: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val from = fromArgb shr shift and 0xFF
            val to = toArgb shr shift and 0xFF
            return Math.round(from + (to - from) * t)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private val familyIndex = Regex("""family(\d+)""")

    private fun knownColor(hex: String): Int? = when (hex) {
        "#4CAF50" -> green
        "#1E88E5" -> blue
        else -> null
    }

    /** A lookup offering every index it is asked about, each with its own `maxKn`. */
    private fun familyLookup(maxKnOf: (Int) -> Int): (String) -> String? = { key ->
        when {
            key.endsWith(".maxKn") -> familyIndex.find(key)
                ?.groupValues?.get(1)?.toIntOrNull()?.let { maxKnOf(it).toString() }
            key.endsWith(".from") -> "#4CAF50"
            key.endsWith(".to") -> "#1E88E5"
            key.endsWith(".stepKn") -> "1.0"
            else -> null
        }
    }

    // ── The shared primitive ─────────────────────────────────────────────

    @Test
    fun derivePrimitiveAnswersNullWhereItWouldInventData() {
        val moving = listOf(p(timeMs = 0L, lat = 0.0), p(timeMs = 1000L, lat = 0.001))
        assertTrue((deriveSpeedMps(moving, 0) ?: 0f) > 100f)                       // ~111 m over 1 s
        assertNull(deriveSpeedMps(listOf(p(timeMs = 7L), p(timeMs = 7L)), 0))      // zero delta
        assertNull(
            deriveSpeedMps(listOf(p(timeMs = 0L, type = PointType.GAP), p(timeMs = 1000L)), 0)
        )                                                                          // GAP pair
        assertNull(deriveSpeedMps(emptyList(), 0))
    }

    @Test
    fun resolveSpeedsConvertsStoredMetresPerSecondToKnots() {
        val speeds = resolveSpeeds(listOf(p(speedMps = 5f, timeMs = 0L)))
        assertEquals(5f * 1.94384f, speeds[0]!!, 0.001f)
    }

    // ── Ramp v4: the two flat zones and the anchors ──────────────────────

    @Test
    fun theCompliantZoneIsFlatFromZeroToFiveKnots() {
        listOf(0f, 0.5f, 1.5f, 2.5f, 4f, 5f).forEach { kn ->
            assertEquals("$kn kn should be flat compliant green", green, colorAt(kn, ramp))
        }
    }

    @Test
    fun theBlueZoneIsFlatFromSixToTenKnots() {
        listOf(6f, 7.5f, 9f, 10f).forEach { kn ->
            assertEquals("$kn kn should be flat blue", blue, colorAt(kn, ramp))
        }
    }

    @Test
    fun theFlatZoneCollapsesToASingleBand() {
        // Five speeds spread across the compliant zone, kept clear of the 5 kn boundary itself.
        val bands = bandedBands(pointsAtKn(0.2f, 1.0f, 2.5f, 4.0f, 4.5f))

        assertEquals(1, bands.size)
        assertEquals(listOf(0, 1, 2, 3, 4), bands[0].pointIndices)
        assertEquals(rgb(green), rgb(bands[0].appearance.argb))
    }

    @Test
    fun theBlueZoneCollapsesToASingleBandWhateverItsStep() {
        val bands = bandedBands(pointsAtKn(6.2f, 7.5f, 9f, 10f))

        assertEquals(1, bands.size)
        assertEquals(listOf(0, 1, 2, 3), bands[0].pointIndices)
        assertEquals(rgb(blue), rgb(bands[0].appearance.argb))
    }

    @Test
    fun everyAnchorIsPureAtItsLimit() {
        assertEquals(green, colorAt(5f, ramp))                 // green to the 5 kn limit
        assertEquals(blue, colorAt(6f, ramp))                  // the changeover is complete at 6
        assertEquals(blue, colorAt(10f, ramp))                 // blue at the 10 kn limit
        assertEquals(lightOrange, colorAt(15f, ramp))
        assertEquals(red, colorAt(35f, ramp))                  // the readable range's top
        assertEquals(purple, colorAt(70f, ramp))               // the ramp's own top
        assertEquals(purple, colorAt(90f, ramp))               // above the span, clamped
    }

    @Test
    fun theRampWarmsInsideEachGradientFamily() {
        assertEquals(blend(green, blue, 0.5f), colorAt(5.5f, ramp))
        assertEquals(blend(blue, lightOrange, 0.5f), colorAt(12.5f, ramp))
        assertEquals(blend(orange, red, 0.25f), colorAt(20f, ramp))
        assertEquals(blend(red, purple, 0.5f), colorAt(52.5f, ramp))

        // Each mid-family colour is distinct from the anchor that opens its family.
        assertTrue(colorAt(5.5f, ramp) != green)
        assertTrue(colorAt(12.5f, ramp) != blue)
        assertTrue(colorAt(20f, ramp) != orange)
        assertTrue(colorAt(52.5f, ramp) != red)
    }

    @Test
    fun theOneKnotChangeoverCarriesTheWholeTransition() {
        assertEquals(blend(green, blue, 0.25f), colorAt(5.25f, ramp))
        assertEquals(blend(green, blue, 0.5f), colorAt(5.5f, ramp))
        assertEquals(blend(green, blue, 0.75f), colorAt(5.75f, ramp))

        // Its quarter-knot grid: green, three blends, then blue.
        val bands = bandedBands(pointsAtKn(5f, 5.25f, 5.5f, 5.75f, 6f))

        assertEquals(5, bands.size)
        assertEquals(rgb(green), rgb(bands.first().appearance.argb))
        assertEquals(rgb(blue), rgb(bands.last().appearance.argb))
    }

    @Test
    fun theHardEdgeAtFifteenKnNeedsNoSpecialCase() {
        assertEquals(lightOrange, colorAt(15f, ramp))
        // Family 5 opens on orange; half a knot past the edge rounds onto its first knot.
        assertEquals(blend(orange, red, 1f / 20f), colorAt(16f, ramp))
        assertTrue(colorAt(15f, ramp) != colorAt(16f, ramp))

        val bands = bandedBands(pointsAtKn(14f, 14.5f, 15f, 15.5f, 16f))

        assertEquals(4, bands.size)
        assertEquals(rgb(lightOrange), rgb(bands[2].appearance.argb))
        assertEquals(rgb(blend(orange, red, 1f / 20f)), rgb(bands[3].appearance.argb))
        assertEquals(listOf(3, 4), bands[3].pointIndices)      // 15.5 kn and 16 kn share the second knot
    }

    // ── The per-family quantiser ─────────────────────────────────────────

    @Test
    fun eachFamilyQuantisesOnItsOwnStep() {
        // Family 5 draws on knots: two speeds inside one knot are a single band.
        assertEquals(1, bandedBands(pointsAtKn(20.2f, 20.4f)).size)
        assertEquals(2, bandedBands(pointsAtKn(20.2f, 20.4f, 20.6f)).size)

        // Family 6 draws on five knots: 37 and 39 both land on 40.
        assertEquals(1, bandedBands(pointsAtKn(37f, 39f)).size)
        assertEquals(2, bandedBands(pointsAtKn(37f, 39f, 43f)).size)

        // Family 4 draws on half knots, so a knot apart is two bands.
        assertEquals(2, bandedBands(pointsAtKn(12f, 13f)).size)
    }

    @Test
    fun crossingTheComplianceEdgeSplitsIntoBandsThatShareTheBoundaryPoint() {
        val points = pointsAtKn(4.5f, 4.5f, 4.5f, 5.5f, 5.5f, 5.5f)

        val bands = bandedBands(points)

        assertEquals(2, bands.size)
        assertEquals(listOf(0, 1, 2, 3), bands[0].pointIndices)   // extended into the first past-edge point
        assertEquals(listOf(3, 4, 5), bands[1].pointIndices)
        assertEquals(rgb(green), rgb(bands[0].appearance.argb))
        assertEquals(rgb(blend(green, blue, 0.5f)), rgb(bands[1].appearance.argb))
    }

    @Test
    fun aTrackInsideOneBandIsASingleBand() {
        // 1.94 kn quantises to the half-knot grid's 2 kn, one value for the whole track.
        val points = (0..4).map { p(speedMps = mps(1.94f), timeMs = it * 1000L) }

        val bands = bandedBands(points)

        assertEquals(1, bands.size)
        assertEquals(listOf(0, 1, 2, 3, 4), bands[0].pointIndices)
        assertEquals(rgb(green), rgb(bands[0].appearance.argb))
    }

    // ── The band counts the v4 grid measures ─────────────────────────────

    @Test
    fun aGaplessClimbAcrossTheWholeRampYieldsTwentySevenBands() {
        // 0 → 35 kn on the knot: 0–5 green, 6–10 blue, 11–15 across four half-knot steps, 16–35 one
        // band per knot.
        val points = pointsAtKn(*(0..35).map { it.toFloat() }.toFloatArray())

        assertEquals(27, bandedBands(points).size)
    }

    @Test
    fun aClimbFromEightToThirtyKnotsYieldsTwentyOneBands() {
        // 8–10 blue, 11–15 the five half-knot steps of family 4, 16–30 one band per knot.
        val points = pointsAtKn(*(8..30).map { it.toFloat() }.toFloatArray())

        assertEquals(21, bandedBands(points).size)
    }

    // ── The family bound ─────────────────────────────────────────────────

    @Test
    fun theFamilyCapBoundsTheRampRead() {
        // Every index is offered; the read loop stops at the stated bound.
        val parsed = parseHeatmapFamilies(familyLookup { it * 5 }, ::knownColor)

        assertEquals(HEATMAP_MAX_FAMILIES, parsed.size)
        assertEquals(40f, parsed.last().maxKn, 0.001f)          // family 8
    }

    @Test
    fun aMissingFamilyIndexEndsTheRamp() {
        val lookup: (String) -> String? = { key ->
            when {
                key.startsWith("track.heatmap.family1") -> familyKey(key, maxKn = "5")
                key.startsWith("track.heatmap.family2") -> familyKey(key, maxKn = "6")
                key.startsWith("track.heatmap.family") -> null   // family3 is absent
                else -> null
            }
        }

        val parsed = parseHeatmapFamilies(lookup, ::knownColor)

        assertEquals(2, parsed.size)
    }

    @Test
    fun aMissingFamilyStepEndsTheRampRatherThanGuessingOne() {
        val lookup: (String) -> String? = { key ->
            when {
                key.startsWith("track.heatmap.family2") && key.endsWith(".stepKn") -> null
                key.startsWith("track.heatmap.family") -> familyKey(key, maxKn = "5")
                else -> null
            }
        }

        val parsed = parseHeatmapFamilies(lookup, ::knownColor)

        assertEquals(1, parsed.size)
    }

    /** One fully-formed family's key, so a truncation test only removes the key it means to. */
    private fun familyKey(key: String, maxKn: String): String = when {
        key.endsWith(".maxKn") -> maxKn
        key.endsWith(".from") -> "#4CAF50"
        key.endsWith(".to") -> "#1E88E5"
        key.endsWith(".stepKn") -> "0.5"
        else -> ""
    }

    @Test
    fun anAbsentOrUnreadableRampLeavesTheShippedDefaultInPlace() {
        assertTrue(parseHeatmapFamilies({ null }, ::knownColor).isEmpty())
        // The first index missing any of its keys reads as no ramp at all, so the default stands.
        assertTrue(
            parseHeatmapFamilies(
                { if (it.endsWith(".from")) null else "5" },
                ::knownColor
            ).isEmpty()
        )
    }

    // ── The neutral tint, and the two null branches ──────────────────────

    @Test
    fun anUnknownSpeedAnswersTheNeutralTint() {
        assertEquals(0xFF90A4AE.toInt(), colorAt(null, ramp))
    }

    @Test
    fun aTrackWithoutDerivableSpeedIsNeutralEndToEnd() {
        // No stored speed and no positive time delta: nothing is derivable, so nothing is invented.
        val points = (0..4).map { p(timeMs = 0L, lat = it * 0.0001) }
        val speeds = resolveSpeeds(points)
        assertTrue(speeds.all { it == null })

        val bands = bandedBands(points)
        assertEquals(1, bands.size)
        assertEquals(listOf(0, 1, 2, 3, 4), bands[0].pointIndices)
        assertEquals(rgb(ramp.unknownArgb), rgb(bands[0].appearance.argb))
    }

    @Test
    fun aGapSeamAndThePointAfterItBothReadNeutral() {
        val points = listOf(
            p(speedMps = 5f, timeMs = 0L),
            p(timeMs = 1000L, type = PointType.GAP),
            p(timeMs = 1000L)
        )
        val speeds = resolveSpeeds(points)

        assertEquals(5f * 1.94384f, speeds[0]!!, 0.001f)
        assertNull(speeds[1])
        assertNull(speeds[2])
    }

    // ── Band grouping, the alpha and the GAP split ───────────────────────

    @Test
    fun gapSeamIsSingleNeutralAndNeverDegenerate() {
        val points = listOf(
            p(speedMps = 1f, timeMs = 0L, lat = 0.0),
            p(speedMps = 1f, timeMs = 1000L, lat = 0.001),
            p(timeMs = 2000L, lat = 0.002, type = PointType.GAP),
            p(speedMps = 1f, timeMs = 3000L, lat = 0.003),
            p(speedMps = 1f, timeMs = 4000L, lat = 0.004)
        )

        val bands = bandedBands(points)

        val neutral = bands.single { rgb(it.appearance.argb) == rgb(ramp.unknownArgb) }
        assertTrue(neutral.pointIndices.contains(2))                // the seam owns the GAP point

        // Exactly one drawable dashed seam across every band — never one per appearance, and never
        // the zero-length bridge the band ending on the GAP point would otherwise contribute. The
        // indices are band-local, as the banded overflow builder reads them against its own points.
        val drawableSeams = bands.flatMap { band ->
            drawableBandSegments(band.pointIndices.map { points[it] }).filter { it.dashed }
        }
        assertEquals(1, drawableSeams.size)
        assertEquals(listOf(0, 1), drawableSeams.single().pointIndices)
    }

    // ── The arrow resolver's band table ──────────────────────────────────

    @Test
    fun theBandTableMapsEachSegmentToTheBandThatOwnsIt() {
        // Two bands, the second sharing index 1 with the first, exactly as the quantiser emits them.
        val first = SpeedBand(listOf(0, 1), TrackPolylineAppearance(green, 8f))
        val second = SpeedBand(listOf(1, 2, 3), TrackPolylineAppearance(blue, 8f))

        val table = bandTable(4, listOf(first, second))

        assertEquals(4, table.size)
        assertEquals(first, table[0])       // the segment leaving index 0 is the first band's
        assertEquals(second, table[1])      // the shared boundary keeps the later band
        assertEquals(second, table[2])
        assertEquals(second, table[3])
    }

    @Test
    fun theSharedBoundaryOfTwoRealBandsKeepsTheLaterBand() {
        // 4.5 kn sits in the flat green family, 5.5 kn in the one-knot changeover, so index 3 is the
        // boundary point the two bands share — the case §11 finding 7 named as untested.
        val bands = bandedBands(pointsAtKn(4.5f, 4.5f, 4.5f, 5.5f, 5.5f, 5.5f))
        assertEquals(listOf(0, 1, 2, 3), bands[0].pointIndices)
        assertEquals(listOf(3, 4, 5), bands[1].pointIndices)

        val table = bandTable(6, bands)

        assertEquals(bands[1], table[3])
        assertEquals(rgb(green), rgb(table[2]!!.appearance.argb))                  // inside the first band
        assertEquals(rgb(blend(green, blue, 0.5f)), rgb(table[3]!!.appearance.argb)) // leaving the boundary
    }

    @Test
    fun anIndexNoBandClaimsAnswersNullAndAnEmptyTableIsEmpty() {
        val bands = bandedBands(pointsAtKn(1f, 2f))
        val table = bandTable(9, bands)

        assertEquals(9, table.size)
        assertEquals(bands[0], table[0])
        assertNull(table[7])                 // past the last segment: the caller keeps the metrics
        assertTrue(bandTable(0, bands).isEmpty())
    }

    @Test
    fun everyBandCarriesTheSameAlphaAtAnUnfadedRow() {
        val points = listOf(
            p(speedMps = 1f, timeMs = 0L),
            p(timeMs = 1000L, type = PointType.GAP),
            p(speedMps = 1f, timeMs = 2000L)
        )

        val bands = bandedBands(points)

        assertTrue(bands.isNotEmpty())
        assertEquals(255, alphaOf(bands[0].appearance.argb))        // the fade alone: round(1.0 × 255)
        bands.forEach { assertEquals(255, alphaOf(it.appearance.argb)) }
        bands.forEach { assertEquals(fixtureStrokeWidth, it.appearance.strokeWidth, 0.001f) }
    }

    // ── The track's own fade as the band's alpha, and D11's width ────────

    @Test
    fun theBandAlphaIsTheTracksOwnFadeAlone() {
        val points = pointsAtKn(1f, 2f, 3f)                         // one flat green band

        val full = bandedBands(points, fade = 1f)
        val half = bandedBands(points, fade = 0.5f)
        val oldest = bandedBands(points, fade = 0f)

        assertEquals(255, alphaOf(full[0].appearance.argb))         // round(1.0 × 255)
        assertEquals(128, alphaOf(half[0].appearance.argb))         // round(0.5 × 255)
        assertEquals(0, alphaOf(oldest[0].appearance.argb))         // the heaviest fade disappears
        // The fade moves the alpha alone: the hue the ramp resolved is untouched.
        assertEquals(rgb(full[0].appearance.argb), rgb(half[0].appearance.argb))
    }

    @Test
    fun everyBandCarriesTheWidthItWasGiven() {
        val points = pointsAtKn(0.5f, 6f)                           // green then blue: two bands

        val older = bandedBands(points, strokeWidth = 6f)
        val newest = bandedBands(points, strokeWidth = 8f)

        assertTrue(older.size >= 2)
        older.forEach { assertEquals(6f, it.appearance.strokeWidth, 0.001f) }
        newest.forEach { assertEquals(8f, it.appearance.strokeWidth, 0.001f) }
    }

    // ── The legend's tick table ──────────────────────────────────────────

    /** A three-row table through the real parser, with the bar's top as the last row's own position. */
    @Test
    fun theWrittenTableCarriesItsPositionsAndItsTexts() {
        val written: (String) -> String? = { key ->
            if (key == "track.heatmap.scaleTicks") "7:5,12:10,35:35" else null
        }

        val ticks = parseHeatmapScaleTicks(written)

        assertEquals(listOf(7f, 12f, 35f), ticks.map { it.positionKn })
        assertEquals(35f, ticks.last().positionKn, 0f)
        assertEquals(listOf("5", "10", "35"), ticks.map { it.label })
    }

    /** An absent key leaves the caller's shipped default standing, as an absent family index does. */
    @Test
    fun anAbsentTableParsesToNothing() {
        assertTrue(parseHeatmapScaleTicks({ null }).isEmpty())
        assertTrue(parseHeatmapScaleTicks({ "" }).isEmpty())
    }

    /** A row that carries no readable position — or no text — ends the read rather than being skipped. */
    @Test
    fun anUnreadableRowEndsTheTable() {
        val noPosition: (String) -> String? = { key ->
            if (key == "track.heatmap.scaleTicks") "seven:5,12:10" else null
        }
        val noText: (String) -> String? = { key ->
            if (key == "track.heatmap.scaleTicks") "7:,12:10" else null
        }

        assertTrue(parseHeatmapScaleTicks(noPosition).isEmpty())
        assertTrue(parseHeatmapScaleTicks(noText).isEmpty())
    }
}
