package ykws.android.maro.ui.map

import ykws.android.maro.config.HeatmapRamp
import kotlin.math.roundToInt

/**
 * One quantised band: the appearance it paints every one of its points with, beside the source
 * geometry it covers. Paired geometry is the point of this type — `buildSegmentOverlays` applies one
 * appearance to a whole point list, so a band that carried only a colour would paint full-length
 * lines instead of bands.
 *
 * [pointIndices] is contiguous and, at a band boundary, shares its last index with the next band's
 * first, so the drawn polylines meet at the transition instead of leaving a one-segment hole.
 */
data class SpeedBand(val pointIndices: List<Int>, val appearance: TrackPolylineAppearance)

/**
 * The ramp's colour for one speed (knots): an opaque hue inside [ramp]'s families, and the neutral
 * tint for a null speed. Continuous inside a family and monotone across the domain, with the hue
 * jumps landing exactly on the family boundaries — the legend fills itself with this same function,
 * which is what keeps legend and line from drifting apart.
 */
internal fun colorAt(speedKn: Float?, ramp: HeatmapRamp): Int {
    if (speedKn == null || speedKn.isNaN()) return ramp.unknownArgb
    val families = ramp.families
    if (families.isEmpty()) return ramp.unknownArgb
    val speed = speedKn.coerceAtLeast(0f)
    val index = families.indexOfFirst { speed <= it.maxKn }.let { if (it >= 0) it else families.lastIndex }
    val family = families[index]
    val start = if (index == 0) 0f else families[index - 1].maxKn
    val t = if (family.maxKn <= start) 1f else ((speed - start) / (family.maxKn - start)).coerceIn(0f, 1f)
    return blendOpaque(family.fromArgb, family.toArgb, t)
}

/**
 * Quantise the seam's own per-point speeds into draw bands: one [SpeedBand] per maximal run of equal
 * appearance, each carrying its own geometry, with the band's alpha baked into every band —
 * including the neutral ones, so a GAP seam and a speed band read at the same weight.
 *
 * The speed is read straight off each [RenderPoint], because the adapters resolve it at the seam — a
 * stored speed wins, a derivable one is derived there, and a value still absent stays null, which the
 * ramp paints neutral. The alpha is [fade] alone — the line's own recency reading — so a banded stroke
 * is drawn at the transparency the user's sliders ask for.
 *
 * The width is a parameter rather than a constant (D11), so every line takes the width its own key
 * earns and a mode switch never restyles the map's density, while the shipped file alone decides the
 * numbers.
 *
 * Each band's colour comes from the family it sits in, since every family declares its own step; a
 * flat family collapses to one band whatever that step, because its colours are equal and adjacent
 * equal appearances merge. Merging applies to consecutive equal appearances only — never across a
 * break, whose seam stays its own neutral band.
 */
internal fun bandedAppearances(
    points: List<RenderPoint>,
    ramp: HeatmapRamp,
    strokeWidth: Float,
    fade: Float = 1f
): List<SpeedBand> {
    if (points.isEmpty()) return emptyList()
    val alpha = (fade.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    val appearances = points.map { point ->
        val quantised = quantiseKn(point.speedKn, ramp)
        TrackPolylineAppearance(withAlpha(colorAt(quantised, ramp), alpha), strokeWidth)
    }

    val bands = mutableListOf<SpeedBand>()
    var runStart = 0
    for (i in 1..points.size) {
        if (i < points.size && appearances[i] == appearances[runStart]) continue
        // Share the boundary point with the next band so the two polylines meet.
        val runEnd = if (i < points.size) i else i - 1
        bands += SpeedBand((runStart..runEnd).toList(), appearances[runStart])
        runStart = i
    }
    return bands
}

/**
 * The arrow resolver's band table: for each point index, the band that owns the segment leaving it —
 * which is the segment an anchor on that index is drawn on. [bands] are written in order, so the
 * shared boundary index, the last point of one band and the first of the next, keeps the *later*
 * band: the segment leaving that boundary is painted in that band's colour, which is exactly where a
 * hue change lands. An index no band claims answers null, and the caller falls back to the metrics.
 *
 * Extracted from the effect so the mapping is a pure function the unit test can cover, including that
 * shared-boundary case.
 */
internal fun bandTable(pointCount: Int, bands: List<SpeedBand>): List<SpeedBand?> {
    if (pointCount <= 0) return emptyList()
    val table = arrayOfNulls<SpeedBand>(pointCount)
    for (band in bands) {
        for (index in band.pointIndices) {
            if (index in 0 until pointCount) table[index] = band
        }
    }
    return table.asList()
}

/**
 * The ramp's colour for one speed (knots), read as the band the speed belongs to: the quantised band
 * first, its colour second.
 *
 * This is the draw-safe form of [colorAt] for a caller that runs once per frame — it returns an `Int`
 * and adds no list, no colour object and no second speed-to-colour rule, so a speed-coloured stroke
 * costs a lookup rather than garbage per frame. A null or unusable speed answers the ramp's own
 * neutral tint, the rule [colorAt] already carries.
 */
internal fun rampColorForSpeed(speedKn: Float?, ramp: HeatmapRamp): Int =
    colorAt(quantiseKn(speedKn, ramp), ramp)

/**
 * Snap one speed (knots) onto its family's own step grid, so each family's resolution follows its
 * `stepKn` and a re-cut grid is a data change.
 *
 * A band never snaps down onto its family's lower boundary — that would paint the lower family's
 * hue and move a jump off the edge it belongs to — which is why a non-first family can only land on
 * its boundary from above. A family whose step is zero is left unquantised.
 */
private fun quantiseKn(speedKn: Float?, ramp: HeatmapRamp): Float? {
    if (speedKn == null || speedKn.isNaN()) return null
    val families = ramp.families
    if (families.isEmpty()) return null
    val speed = speedKn.coerceIn(0f, ramp.spanKn)
    val index = families.indexOfFirst { speed <= it.maxKn }.let { if (it >= 0) it else families.lastIndex }
    val family = families[index]
    val start = if (index == 0) 0f else families[index - 1].maxKn
    val step = family.stepKn
    if (step <= 0f) return speed.coerceIn(start, family.maxKn)
    val offset = ((speed - start) / step).roundToInt()
    val snapped = start + (if (index == 0) offset.coerceAtLeast(0) else offset.coerceAtLeast(1)) * step
    return snapped.coerceIn(start, family.maxKn)
}

private fun withAlpha(argb: Int, alpha: Int): Int = (alpha shl 24) or (argb and 0x00FFFFFF)

private fun blendOpaque(fromArgb: Int, toArgb: Int, t: Float): Int {
    val r = lerpChannel(fromArgb shr 16 and 0xFF, toArgb shr 16 and 0xFF, t)
    val g = lerpChannel(fromArgb shr 8 and 0xFF, toArgb shr 8 and 0xFF, t)
    val b = lerpChannel(fromArgb and 0xFF, toArgb and 0xFF, t)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun lerpChannel(from: Int, to: Int, t: Float): Int =
    (from + (to - from) * t).roundToInt().coerceIn(0, 255)
