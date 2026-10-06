package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import java.util.Locale
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.Track
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteProvisional
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.ui.components.OptionRow

// ─────────────────────────────────────────────────────────────────────────────
// The route's own rules — the anchor's lead, the page set, the seat and the delta
//
// This file owns the route's own arithmetic and the sentences it prints: the acquisition anchor's
// lead, the page set the selection walks and the seat it takes, the delta the table prints against
// the selected route, and the trip figure the dashboard's distance cell reads while a route is
// followed. The map objects — the lines, the pin and the provisional line — live in RouteHost.kt,
// which is the one file that touches osmdroid for this feature.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The lead's own reading: where the boat is, and what it is doing, as one value.
 */
data class RouteFix(val position: RoutePoint, val courseDeg: Double?, val speedKn: Double?)

/** Below this speed (kn) a reported course is jitter rather than a heading, and the lead stands down. */
private const val ROUTE_ANCHOR_MIN_SPEED_KN = 0.5

/**
 * **One page of the acquisition** — a started lookup, walked with next/previous.
 *
 * The page carries the lookup's id and the id of the computation it was started from, the description
 * a user reads for it, the plan once the lookup lands, and the reason when it cannot be answered.
 * Index 0 is the main; the selected page is what the buttons act on.
 *
 * **A stored route's page sets only [plan]** (R82): it came from no lookup, so it has no lookup id, no
 * computation id and no description to print. All three are therefore nullable with null defaults, and
 * the readers that key on a lookup id — the update landing and the disposal — simply never see one.
 * Such a page is always the **sole** page, so the candidate rows and the per-page description are
 * never rendered for it.
 */
data class RoutePage(
    val lookupId: RouteId? = null,
    val computationId: RouteId? = null,
    val descriptionResId: Int? = null,
    val plan: RoutePlan? = null,
    val reason: RouteReason? = null,
    /**
     * **The provisional figures the row prints while this page is still settling** — the pair the
     * boundary update carried the moment this rung's line was first taut, or `null` where none has
     * arrived. The table prints it **where its waiting placeholder stands today**; a landed [plan]
     * replaces it, and the terminal update clears it.
     */
    val provisional: RouteProvisional? = null,
    /** True when this page stands for every collapsed rung — the same line at every preference. */
    val collapsed: Boolean = false,
    /**
     * The rung labels this page stands for — its own [descriptionResId] first, then each folded rung in
     * fold order. Empty until a fold.
     */
    val foldedDescriptionResIds: List<Int> = emptyList()
)

/**
 * **The delta a page shows against the selected route** — the flow's own arithmetic of each page's
 * duration against the route the selection stands on, stated as "x less" or "x more".
 *
 * `null` on the selected page itself, where **either** duration has not landed, or on a tie; otherwise
 * [pageDurationSec] minus [selectedDurationSec] — **positive means slower than the selection**. The
 * selection arrives as [isSelected] rather than as a second index: the caller already knows which page
 * it is drawing, and one boolean cannot disagree with itself the way two index arguments can.
 */
internal fun routeDeltaSec(
    pageDurationSec: Double?,
    selectedDurationSec: Double?,
    isSelected: Boolean
): Double? {
    if (isSelected) return null
    val page = pageDurationSec ?: return null
    val selected = selectedDurationSec ?: return null
    val delta = page - selected
    if (delta == 0.0) return null
    return delta
}

/**
 * **One printable entry of the Speed limits line** — the limit's whole-minute figure, the 300 m band
 * standing apart ([isBand]). The seconds a route reports per limit, as the panel reads them: whole
 * minutes only, an entry under a minute dropped so the line never prints a zero.
 */
internal data class RouteSlowLimitEntry(val limitKn: Double, val minutes: Int, val isBand: Boolean)

/**
 * **The Speed limits entries of a plan** — [RoutePlan.slowLimitSeconds] in whole minutes, sub-minute
 * entries dropped and the band kept apart. Empty when nothing slowed the route, which is what leaves
 * the line off the panel.
 */
internal fun routeSlowLimitEntries(plan: RoutePlan): List<RouteSlowLimitEntry> =
    plan.slowLimitSeconds
        .map { RouteSlowLimitEntry(it.limitKn, (it.seconds / 60.0).toInt(), it.isBand) }
        .filter { it.minutes >= 1 }

/**
 * **The Speed limits entries wrapped into the table's lines** — a zone shares the 300 m band's first
 * line only when it makes the table no longer than it must be: with an **odd** count of three or more
 * the first line carries the band and the first zone and every line after is a full pair, while a
 * **single** zone and every **even** count leave the band alone on the first line, the zones then two
 * per line. The entries arrive band first then ascending limit ([routeSlowLimitEntries]); this fixes
 * only how they wrap — empty in, empty out, and a list carrying no band falls back to plain pairs.
 */
internal fun routeSlowLimitRows(
    entries: List<RouteSlowLimitEntry>
): List<List<RouteSlowLimitEntry>> {
    if (entries.isEmpty()) return emptyList()
    val band = entries.firstOrNull { it.isBand } ?: return entries.chunked(2)
    val zones = entries.filterNot { it.isBand }
    // A lone zone stays on its own line, and an even count leaves the band alone: only an odd count of
    // three or more pairs the first zone with the band, so every zone line below is a full pair.
    return if (zones.size > 1 && zones.size % 2 == 1) {
        listOf(listOf(band, zones.first())) + zones.drop(1).chunked(2)
    } else {
        listOf(listOf(band)) + zones.chunked(2)
    }
}

/**
 * **One acquisition row's own figures, settled or provisional** — the pair the table's middle column
 * prints, in the units its two lines carry: the route's length in nautical miles and the time it takes
 * over the whole line.
 *
 * A landed [RoutePage.plan] wins outright: its own length and its `remainingFrom` time are the settled
 * answer, and any provisional pair the page still carries is ignored — *replaced*, never merged. A page
 * with no plan but a provisional pair prints that pair; one with neither has nothing to print, and the
 * table falls back to its `--` placeholder. One home for the settled and the provisional reading alike,
 * so the panel never branches on which it holds.
 */
internal data class RouteRowFigures(val distanceNm: Double, val durationSec: Double)

internal fun routeRowFigures(page: RoutePage): RouteRowFigures? {
    val plan = page.plan
    if (plan != null) {
        return RouteRowFigures(plan.distanceNm, plan.remainingFrom(plan.start).durationSec)
    }
    val provisional = page.provisional ?: return null
    return RouteRowFigures(Units.metresToNauticalMiles(provisional.distanceM), provisional.durationSec)
}

/**
 * **The acquisition's anchor** (R3): [fix]'s position led by [leadSec] along its own course and speed,
 * or the live fix itself where there is nothing trustworthy to project from.
 *
 * One pure helper, one call site — the acquisition's own entry edge — so no frame can move the anchor
 * afterwards. The engine repairs exactly the point it is handed; nothing of the lead migrates.
 */
internal fun routeAnchorLead(fix: RouteFix, leadSec: Int = AppConfig.routeAnchorLeadSec): RoutePoint {
    val course = fix.courseDeg
    val speed = fix.speedKn
    if (leadSec <= 0 || course == null || speed == null) return fix.position
    if (!course.isFinite() || !speed.isFinite() || speed < ROUTE_ANCHOR_MIN_SPEED_KN) return fix.position
    val metres = Units.knotsToMps(speed) * leadSec
    val moved = SpatialOperations.pointAlongBearing(
        fix.position.latitude,
        fix.position.longitude,
        course,
        metres
    )
    return RoutePoint(moved.latitude, moved.longitude)
}

/**
 * **The auto-pick's one-shot, as a reading of the machine** (R80) — the fan's *Route (auto)* child
 * armed with the intent to take the settled answer.
 *
 * It is true the moment the intent is armed **and the main line has landed**, and false either side of
 * that instant. It keys on `plan != null` rather than on a non-empty page set on purpose:
 * `Choosing.plan` **is the main's** (index 0), so the readiness never follows the seat — a candidate
 * that lands first is not the answer this child promises and cannot make this fire. The caller then
 * names **index 0** through `selectMainRoute()`, since the seat may have followed onto that
 * candidate.
 */
internal fun routeAutoPickReady(autoPick: Boolean, state: RouteState): Boolean =
    autoPick && state is RouteState.Choosing && state.plan != null

/** The ladder's rung count — the acquisition's pages, and the map's line pool. */
internal const val ROUTE_LADDER_RUNG_COUNT = 3

/**
 * **The ladder's rung for a configured aversion** (D12): the three rungs sit at λ = 5, 2.5 and 0, listed
 * most-fun first — 0 around, 1 balanced, 2 through. A stored value snaps to the nearest rung's index.
 * The thresholds are the midpoints between the evenly spaced rungs.
 */
internal fun routeRungIndex(aversionKn: Double): Int = when {
    aversionKn <= 1.25 -> 2
    aversionKn <= 3.75 -> 1
    else -> 0
}

/** **The λ a rung index carries** — the one home for the ladder's three values, [routeRungIndex]'s inverse. */
internal fun routeRungLambdaOf(index: Int): Double = when (index) {
    0 -> 5.0
    1 -> 2.5
    else -> 0.0
}

/** The rung λ a configured aversion snaps to — the inverse of [routeRungIndex], one home for the thresholds. */
internal fun routeRungLambda(aversionKn: Double): Double = routeRungLambdaOf(routeRungIndex(aversionKn))

/**
 * **The word a rung is read by**: `Fun` at the λ 5 end, `Balanced` at 2.5 and `Fast` at 0 — the order
 * [routeRungIndex] lists them in. One home for the mapping, so the Settings page's slider and the drawer's
 * quick access name a rung the same way.
 */
@StringRes
internal fun routeRungLabelRes(index: Int): Int = when (index) {
    2 -> R.string.route_computation_through
    1 -> R.string.route_computation_balanced
    else -> R.string.route_computation_around
}

/**
 * **The page the seat stands on** — the preferred page when it holds a plan, otherwise the page
 * **holding a plan** that lies nearest the preferred one, otherwise the preferred index unchanged.
 *
 * `hasPlan` is the page set as the one fact the seat reads: a page holds a line or it does not. The
 * search walks outward from [preferredIndex], the lower index winning an even split, so the seat never
 * parks on an empty row while a landed line stands beside it and a single landed page is the identity
 * when it is the preferred one.
 */
internal fun routeSeatedIndex(hasPlan: List<Boolean>, preferredIndex: Int): Int {
    if (preferredIndex in hasPlan.indices && hasPlan[preferredIndex]) return preferredIndex
    for (distance in 1..hasPlan.size) {
        val below = preferredIndex - distance
        if (below >= 0 && hasPlan[below]) return below
        val above = preferredIndex + distance
        if (above < hasPlan.size && hasPlan[above]) return above
    }
    return preferredIndex
}

/**
 * **The ETA order of [pages], as the original indices** — landed pages fastest-first, ties keeping the
 * ladder's natural (computation) order, and pages that have not landed last in that same order.
 *
 * The order is a pure view over [pages]: nothing reorders the page set itself, so the main — index 0
 * of the canonical list — stays index 0 for the map's pool and the provisional line no matter where
 * the sort puts it in the panel.
 */
internal fun routeEtaOrder(pages: List<RoutePage>): List<Int> =
    pages.indices.sortedBy { pages[it].plan?.durationSec ?: Double.POSITIVE_INFINITY }

/** The page set the panel reads — the ETA-ordered view over the unchanged [pages]. */
internal fun routePagesByEta(pages: List<RoutePage>): List<RoutePage> =
    routeEtaOrder(pages).map { pages[it] }

/**
 * **The seat, answered as an original page index** — the preferred rung resolved against the
 * ETA-ordered view. While nothing has landed the preference's row stays selected as computing; once
 * any row has landed the seat takes the preference's rung when it is landed, otherwise the nearest
 * landed row in the sorted view. [routeSeatedIndex] keeps its one home: it is handed the sorted
 * view's plan flags and the preferred rung's position in that view.
 */
internal fun routeEtaSeatedIndex(pages: List<RoutePage>, preferredRungIndex: Int): Int {
    val order = routeEtaOrder(pages)
    val preferred = preferredRungIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val preferredView = order.indexOf(preferred)
    val seated = routeSeatedIndex(order.map { pages[it].plan != null }, preferredView)
    return order.getOrElse(seated) { preferred }
}

/**
 * **The dispersion between two routes** — the largest distance from any point of one polyline to the
 * other, taken both ways so the reading is symmetric. It is the "how far apart do they ever get"
 * measure the ladder's collapse reads: two rungs within the tolerance are driven the same way and fold
 * into one route, rather than being pressed together only when their points are identical.
 */
internal fun routeDispersionM(a: List<RoutePoint>, b: List<RoutePoint>): Double {
    if (a.size < 2 || b.size < 2) return Double.MAX_VALUE
    val ab = oneWayDeviationM(a, b)
    val ba = oneWayDeviationM(b, a)
    return if (ab > ba) ab else ba
}

/** The largest distance from a point of [from] to the [to] polyline — one direction of the dispersion. */
private fun oneWayDeviationM(from: List<RoutePoint>, to: List<RoutePoint>): Double {
    var worst = 0.0
    for (p in from) {
        var nearest = Double.MAX_VALUE
        for (i in 0 until to.size - 1) {
            val d = SpatialOperations.pointToSegmentDistance(p.toLatLng(), to[i].toLatLng(), to[i + 1].toLatLng())
            if (d < nearest) nearest = d
        }
        if (nearest > worst) worst = nearest
    }
    return worst
}

/**
 * The trip figure: what is left of a followed route, in the two units the dashboard cell shows.
 *
 * Its [RouteTripFigure.etaSeconds] is the **plan's own remaining time** — [`RoutePlan.remainingFrom`]'s
 * `durationSec`, which prices each leg at the limit the search resolved for its water — and never
 * distance ÷ pace, which would understate every line carrying any slow water.
 */
data class RouteTripFigure(
    val distanceNm: Double,
    val etaSeconds: Double,
    /**
     * The zone share of the trip's own time, set only where the slow-water budget was missed — `null`
     * means the line is inside the budget. Reported, never refused.
     */
    val budgetUnmetZoneShare: Double? = null,
    /**
     * The zones a forced crossing entered, by name — empty on an ordinary route.
     */
    val forcedCrossingZoneNames: List<String> = emptyList(),
    /** When the plan the figure is read from was computed. */
    val computedAtMs: Long
)

/**
 * **The followed figure, read from the plan's own remaining time** (2026-10-05) — the one home the
 * dashboard trip cell, the drawer summary and the acquisition row all read is
 * [`RoutePlan.remainingFrom`], and this reads its `durationSec` and nothing else. **There is no
 * distance ÷ pace here**: that division survives only where no plan timest a line — the acquisition's
 * provisional pair, which the engine already prices at the limit in force — and never on a followed,
 * planned line, where it would discard the plan's slow-water time. @see RouteTripFigure
 */
internal fun routeTripFigure(
    plan: RoutePlan,
    from: RoutePoint,
    nowMs: Long
): RouteTripFigure {
    val remaining = plan.remainingFrom(from)
    return RouteTripFigure(
        distanceNm = Units.metresToNauticalMiles(remaining.distanceM),
        etaSeconds = remaining.durationSec,
        budgetUnmetZoneShare = plan.budgetUnmetZoneShare,
        forcedCrossingZoneNames = plan.forcedCrossingZoneNames,
        computedAtMs = plan.computedAtMs
    )
}

/**
 * **A saved route read back as a plan** — the inverse of the save, so the track card's follow door
 * has no second arithmetic. Points and per-leg times come from the vertices
 * [`TrackFromCourse.build`] wrote; the acquisition-only facts are absent ([forcedCrossingZoneNames]
 * empty, [destinationMoved] false), and the total duration is the track's own figure rather than a
 * sum of the truncated per-leg milliseconds.
 */
internal fun routePlanOf(track: Track): RoutePlan? {
    val points = track.trackPoints
    if (points.size < 2) return null
    val routePoints = points.map { RoutePoint(it.lat, it.lon) }
    val legTimesSec = points.zipWithNext { from, to ->
        ((to.timeOffsetMs - from.timeOffsetMs).coerceAtLeast(0L)) / 1000.0
    }
    return RoutePlan(
        start = routePoints.first(),
        destination = routePoints.last(),
        destinationMoved = false,
        points = routePoints,
        legTimesSec = legTimesSec,
        distanceM = Units.nauticalMilesToMetres(track.distanceNm.toDouble()),
        durationSec = track.navigatingDurationSec.toDouble(),
        forcedCrossingZoneNames = emptyList(),
        computedAtMs = track.startTimeMs
    )
}


/**
 * **A partial plan from the main lookup's provisional points** — the early-save's own line.
 *
 * The points are the provisional line as drawn so far, the start is the acquisition's anchor
 * (falling back to the line's first point), and the plan is dated [nowMs], which the draft's whole
 * life reuses so its name and id stay one identity.
 *
 * **It carries no time** (2026-10-05): a partial line has no plan's times yet, and the distance ÷ pace
 * the legs once held invented a plausible-but-wrong figure for water the settled plan has yet to
 * price — the same cruise pace stamped on every leg whatever the slow water under it. So every leg is
 * `0.0` and the duration is zero: **a draft that never sees a plan is a track that reports no time
 * rather than one that lies.** The landing overwrites it with the plan's own times at the same id, so
 * the two orders of that race — plan-lands-then-save and save-then-plan-lands — end on the same track.
 */
internal fun partialPlanOf(
    points: List<RoutePoint>,
    start: RoutePoint?,
    nowMs: Long
): RoutePlan? {
    if (points.size < 2) return null
    var distanceM = 0.0
    for (i in 0 until points.size - 1) {
        distanceM += SpatialOperations.haversine(points[i].toLatLng(), points[i + 1].toLatLng())
    }
    return RoutePlan(
        start = start ?: points.first(),
        destination = points.last(),
        destinationMoved = false,
        points = points,
        legTimesSec = List(points.size - 1) { 0.0 },
        distanceM = distanceM,
        durationSec = 0.0,
        forcedCrossingZoneNames = emptyList(),
        computedAtMs = nowMs
    )
}


/**
 * **The short-pair guard** — whether the two resolved ends clear the minimum distance the acquisition
 * arms on. Null ends clear it: a missing end is the trigger's own refusal path, not a distance one.
 */
internal fun routeEndsClearMinimum(start: RoutePoint?, destination: RoutePoint?, minLengthM: Double): Boolean {
    if (start == null || destination == null) return true
    return SpatialOperations.haversine(start.toLatLng(), destination.toLatLng()) >= minLengthM
}

/**
 * **One point as the panel prints it** — three decimals, with a dot decimal separator in every locale.
 */
internal fun routeCoordinate(point: RoutePoint): String =
    String.format(Locale.US, "%.3f, %.3f", point.latitude, point.longitude)

/**
 * **The ETA as both surfaces print it** — the remaining course's seconds split into whole minutes and
 * seconds.
 */
@Composable
internal fun routeEtaText(etaSeconds: Double): String {
    val whole = etaSeconds.toInt()
    return stringResource(R.string.route_eta_value_fmt, whole / 60, whole % 60)
}

/**
 * **A span as the panel prints it** — whole minutes above a minute, whole seconds below it.
 */
@Composable
internal fun routeSpanText(seconds: Double): String {
    val whole = seconds.toInt().coerceAtLeast(0)
    return if (whole < 60) stringResource(R.string.route_age_sec, whole)
    else stringResource(R.string.route_age_min, whole / 60)
}

/** Route age as a short read-out: seconds under a minute, whole minutes above it. */
@Composable
internal fun routeAgeText(ageSeconds: Long): String {
    val span = if (ageSeconds < 60) {
        stringResource(R.string.route_age_sec, ageSeconds)
    } else {
        stringResource(R.string.route_age_min, ageSeconds / 60)
    }
    return stringResource(R.string.route_trip_ago, span)
}

/**
 * The route toggle: the map control stack's own square, beside the sleuth's.
 *
 * **It is never gated.** An engine that cannot answer does not gate the mode: the toggle arms, and
 * the status line carries the reason. The square is therefore **never dead**, and no `enabled` knob
 * exists to make it so.
 */
@Composable
internal fun RouteToggleButton(
    armed: Boolean,
    following: Boolean,
    searching: Boolean,
    lineColor: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = when {
        !armed -> mapSurfaceFaceInactive()
        following -> mapSurfaceFaceActive(ComposeColor(AppConfig.routeNavigateColor))
        else -> mapSurfaceFaceActive(ComposeColor(lineColor))
    }
    val description = stringResource(R.string.cd_route_toggle)
    MapToggleSquare(
        face = face,
        onClick = onToggle,
        modifier = modifier,
        contentDescription = description
    ) {
        // Hard-coded like the row's other glyphs: a compass, which reads as "where to go".
        Text(text = "\uD83E\uDDED", fontSize = TOP_TOGGLE_ICON_SIZE)

        // The mark is the shared one: the geometry and the colour both live in MapPulseDot (R69), and
        // it beats while the search runs as well as while the route is followed (R51).
        if (armed && (following || searching)) {
            MapPulseDot(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/**
 * The panel's one checkbox: the pin state the saved track starts with, default off.
 */
@Composable
internal fun RoutePinOption(
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit
) {
    OptionRow(
        label = stringResource(R.string.route_pin_label),
        checked = pinned,
        onCheckedChange = onPinnedChange,
        modifier = Modifier.fillMaxWidth(),
        labelFontSize = 15.sp
    )
}
