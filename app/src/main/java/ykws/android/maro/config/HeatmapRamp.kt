package ykws.android.maro.config

/**
 * Bound on the family read: the loop walks 1..this, and the first index missing a key ends it.
 */
const val HEATMAP_MAX_FAMILIES = 9

/** Bound on `track.heatmap.scaleTicks`: at most this many rows are read, as the families are bounded. */
const val HEATMAP_MAX_SCALE_TICKS = 12

/**
 * One speed family of the ramp: every speed up to [maxKn] grades from [fromArgb] to [toArgb] on
 * [stepKn]'s own draw grid, so each family declares its own resolution rather than a shared one.
 *
 * A family whose `from` and `to` are equal is a flat zone and collapses to a single draw band
 * whatever its [stepKn]; a family whose step is zero paints its unquantised colour. Boundaries carry
 * no special case: where one family ends on a hue and the next begins on another, the jump the eye
 * reads (the 15 kn hard edge, say) is produced by the two colours alone.
 *
 * Both colours are opaque ARGB; a family declares hue alone, and the line's own alpha is applied
 * where the stroke is assembled.
 */
data class HeatmapFamily(val maxKn: Float, val fromArgb: Int, val toArgb: Int, val stepKn: Float)

/**
 * Parsed speed ramp — families in ascending [maxKn] order, held as a *list* so six, seven, eight or
 * nine families are a data change alone. Zero to [spanKn] is the ramp's own domain and is not a key: the
 * window is the ramp, so the top is written once, as the last family's own boundary.
 */
data class HeatmapRamp(
    val families: List<HeatmapFamily>,
    val unknownArgb: Int
) {
    /** Top of the ramp's own domain: zero to the highest family that parses. */
    val spanKn: Float get() = families.lastOrNull()?.maxKn ?: 0f
}

/**
 * Assemble the ramp's family list from the flat `track.heatmap.familyN.*` keys, read as written:
 * there is no count key — families run from 1 upward, the loop is bounded by [HEATMAP_MAX_FAMILIES],
 * and a missing or unparseable index, of any of its four keys, ends the ramp with the families already
 * read, so the ramp's length is whatever the file holds and a truncated one cannot pass as complete.
 *
 * Both lookups are parameters rather than Android calls, which is what makes the bound and the grid
 * unit-testable on the JVM; `AppConfig` supplies `Properties::getProperty` and its colour parser.
 *
 * @return the parsed families, or an empty list when the first index is unreadable so the caller's
 *         shipped default ramp stands.
 */
fun parseHeatmapFamilies(
    lookup: (String) -> String?,
    parseColorHex: (String) -> Int?
): List<HeatmapFamily> {
    val families = mutableListOf<HeatmapFamily>()
    for (index in 1..HEATMAP_MAX_FAMILIES) {
        val maxKn = lookup("track.heatmap.family$index.maxKn")?.toFloatOrNull() ?: break
        val from = lookup("track.heatmap.family$index.from")?.let(parseColorHex) ?: break
        val to = lookup("track.heatmap.family$index.to")?.let(parseColorHex) ?: break
        val stepKn = lookup("track.heatmap.family$index.stepKn")?.toFloatOrNull() ?: break
        families += HeatmapFamily(maxKn = maxKn, fromArgb = from, toArgb = to, stepKn = stepKn)
    }
    return families
}

/**
 * One row of the legend's tick table: a position on the bar's linear scale, printed as [label].
 * Position and text are held apart on purpose — the shipped table prints 5 at the 7 kn row, for one —
 * so the position is what the bar measures and the text is what the reader is told, and the two are
 * free to differ. Nothing is drawn on the bar itself (§17); the position reaches the eye through the
 * label's own placement, and every row prints, so close rows overlap rather than disappear (§18).
 */
data class HeatmapScaleTick(val positionKn: Float, val label: String)

/**
 * Assemble the legend's tick table from `track.heatmap.scaleTicks` — comma-separated `position:label`
 * rows in knots — read as written: the position before the first colon is taken as its float and the
 * text after it as the label, with no validation and no fallback. Rows are assumed *ascending* by
 * position, which is what the drawn ladder relies on: §18 removed the label-drop rule, so a descending
 * table draws its labels out of order instead of hiding one.
 *
 * **This subsumes the former `parseHeatmapScaleMaxKn`:** the bar's top is now the last row's own
 * position, so the scale's top is written once — in this table — rather than in a key beside it. The
 * read is bounded by [HEATMAP_MAX_SCALE_TICKS], as the family read is bounded, and stops at the first
 * row that is not a readable pair, so a malformed row truncates the table instead of vanishing.
 *
 * The lookup is a parameter rather than an Android call, the same shape as [parseHeatmapFamilies], so
 * the table's shape is unit-testable on the JVM.
 *
 * @return the parsed rows, or an empty list when the key is absent or opens with an unreadable row,
 *         so the caller's shipped default table stands.
 */
fun parseHeatmapScaleTicks(lookup: (String) -> String?): List<HeatmapScaleTick> {
    val rows = lookup("track.heatmap.scaleTicks")?.split(',') ?: return emptyList()
    val ticks = mutableListOf<HeatmapScaleTick>()
    for (row in rows) {
        if (ticks.size >= HEATMAP_MAX_SCALE_TICKS) break
        ticks += parseScaleTick(row) ?: break
    }
    return ticks
}

/** One `position:label` row, or null when either half is missing — which ends the read. */
private fun parseScaleTick(row: String): HeatmapScaleTick? {
    val separator = row.indexOf(':')
    if (separator < 0) return null
    val position = row.substring(0, separator).trim().toFloatOrNull() ?: return null
    val label = row.substring(separator + 1).trim()
    return if (label.isEmpty()) null else HeatmapScaleTick(positionKn = position, label = label)
}

/**
 * Foot of the legend's scale (kn): the value `track.heatmap.scaleMinKn` writes, or [fallbackKn] when
 * the key is absent or unreadable, so the caller's shipped default stands. The top needs no twin — the
 * tick table's last row is the top — but the foot has no row to ride on, which is why it is a key of
 * its own.
 *
 * The lookup is a parameter rather than an Android call, the same shape as [parseHeatmapFamilies], so
 * the rule is unit-testable on the JVM beside it.
 */
fun parseHeatmapScaleMinKn(lookup: (String) -> String?, fallbackKn: Float): Float =
    lookup("track.heatmap.scaleMinKn")?.toFloatOrNull() ?: fallbackKn
