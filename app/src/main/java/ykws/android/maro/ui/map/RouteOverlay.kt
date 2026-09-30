package ykws.android.maro.ui.map

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
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.ui.components.OptionRow

// ─────────────────────────────────────────────────────────────────────────────
// The route's own rules — the anchor's lead, the page set, the comparison and the trip figure
//
// This file owns the route's own arithmetic and the sentences it prints: the acquisition anchor's
// lead, the page set the selection walks, the comparison the panel's top area prints, and the trip
// figure the dashboard's distance cell reads while a route is followed. The map objects — the lines,
// the pin and the provisional line — live in RouteHost.kt, which is the one file that touches
// osmdroid for this feature.
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
 */
data class RoutePage(
    val lookupId: RouteId,
    val computationId: RouteId,
    val descriptionResId: Int,
    val plan: RoutePlan? = null,
    val reason: RouteReason? = null
)

/**
 * **The comparison one finished page shows against the main** — the flow's arithmetic of each route's
 * own duration against the selected route's, stated as "Route #n is taking x less or more".
 *
 * Only a page that is not the main (index > 0) has a comparison, and a tie says nothing. [deltaSec] is
 * the selected page's duration minus the main's: negative is faster, positive is slower.
 */
data class RouteComparison(
    val routeNumber: Int,
    val deltaSec: Double
)

/** The comparison [selectedIndex] shows against the main page, or null where there is nothing to say. */
internal fun routeComparison(
    selectedIndex: Int,
    selectedDurationSec: Double?,
    mainDurationSec: Double?
): RouteComparison? {
    if (selectedIndex <= 0) return null
    val selected = selectedDurationSec ?: return null
    val main = mainDurationSec ?: return null
    val delta = selected - main
    if (delta == 0.0) return null
    return RouteComparison(selectedIndex + 1, delta)
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
 * armed with the intent to take the first answer.
 *
 * It is true the moment the intent is armed **and the acquisition's main line has landed**, and false
 * either side of that instant. It keys on `plan != null` rather than on a non-empty page set on
 * purpose: the main answer is what this child promises. The caller takes **index 0** of the page set,
 * which the arming puts the main at, so no second selection path exists and `selectRoute()` is called
 * unchanged.
 */
internal fun routeAutoPickReady(autoPick: Boolean, state: RouteState): Boolean =
    autoPick && state is RouteState.Choosing && state.plan != null

/**
 * **Next/prev over a set of [count] entries** (R54): [index] stepped by [delta] and **looped**, so a
 * press past either end comes back on the other.
 *
 * One home for the wrap, so the row the panel prints at full strength and the line the map paints are
 * read from the same arithmetic. A set of one, or none, has nowhere to step and the index stays.
 */
internal fun routeStepIndex(index: Int, delta: Int, count: Int): Int {
    if (count <= 1 || delta == 0) return index.coerceIn(0, (count - 1).coerceAtLeast(0))
    return ((index + delta) % count + count) % count
}

/**
 * The trip figure: what is left of a followed route, in the two units the dashboard cell shows.
 */
data class RouteTripFigure(
    val distanceNm: Double,
    val etaSeconds: Double,
    /**
     * The zones a forced crossing entered, by name — empty on an ordinary route.
     */
    val forcedCrossingZoneNames: List<String> = emptyList(),
    /** When the plan the figure is read from was computed. */
    val computedAtMs: Long
)

/** @see RouteTripFigure */
internal fun routeTripFigure(
    plan: RoutePlan,
    from: RoutePoint,
    paceKn: Double,
    nowMs: Long
): RouteTripFigure {
    val remaining = plan.remainingFrom(from)
    val etaSeconds = if (paceKn > 0.0) {
        remaining.distanceM / Units.knotsToMps(paceKn)
    } else {
        remaining.durationSec
    }
    return RouteTripFigure(
        distanceNm = Units.metresToNauticalMiles(remaining.distanceM),
        etaSeconds = etaSeconds,
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
