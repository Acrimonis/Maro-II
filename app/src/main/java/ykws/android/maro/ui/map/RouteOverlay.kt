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
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.ui.components.OptionRow

// ─────────────────────────────────────────────────────────────────────────────
// The route's own rules — the anchor's lead, the candidate set and the trip figure
//
// This file owns the route's own arithmetic and the sentences it prints: the acquisition anchor's
// lead, the candidate set the selection walks, the ends' own print, and the trip figure the
// dashboard's distance cell reads while a route is followed. The map objects — the lines, the pin and
// the aim ring — live in RouteHost.kt, which is the one file that touches osmdroid for this feature.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The lead's own reading: where the boat is, and what it is doing, as one value.
 *
 * Public rather than internal because the interface it crosses — the acquisitions a view model opens
 * — is public too; the helper that reads it ([`routeAnchorLead`]) stays module-internal.
 */
data class RouteFix(val position: RoutePoint, val courseDeg: Double?, val speedKn: Double?)

/** Below this speed (kn) a reported course is jitter rather than a heading, and the lead stands down. */
private const val ROUTE_ANCHOR_MIN_SPEED_KN = 0.5

/**
 * **The acquisition's anchor** (R3): [fix]'s position led by [leadSec] along its own course and speed,
 * or the live fix itself where there is nothing trustworthy to project from.
 *
 * One pure helper, one call site — the acquisition's own entry edge — so no frame can move the anchor
 * afterwards, which is the defect the per-phase anchor exists to avoid. It answers the live fix
 * whenever the projection has nothing to stand on: no course, no speed, a course or speed that is not
 * a number, a speed under [ROUTE_ANCHOR_MIN_SPEED_KN], or a lead of zero. **Demo mode takes no lead at
 * all** by handing in a fix with no course and no speed, its position being the map centre rather than
 * a moving boat's.
 *
 * A predicted point that is not water is the caller's to fall back from: this helper is arithmetic
 * over the fix and asks the engine nothing.
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
 * **The lines the acquisition draws, and the selection walks** (R53, R54) — the settled answer first,
 * then the engine's offers, each as a plan of its own in the order its pass ran.
 *
 * Index 0 is the **settled** line, so the set has one entry the moment an answer lands and grows only
 * as the offers arrive: that is what makes the candidate row appear only as the engine's background job
 * publishes, and what makes "save what you see" read the same list the map paints.
 *
 * The offers become plans through [`RoutePlan.of`], which takes the settled plan's own anchor and
 * instant — a candidate is another line between the same two ends, and it is dated and named with the
 * route it belongs to. Its distance, clock and leg times are the engine's own figures, recomputed
 * nowhere (R72).
 */
internal fun routeCandidateLines(settled: RoutePlan?, offers: List<RouteOffer>): List<RoutePlan> {
    if (settled == null) return emptyList()
    return buildList {
        add(settled)
        offers.forEach { add(RoutePlan.of(settled.start, it, settled.computedAtMs)) }
    }
}

/**
 * **The auto-pick's one-shot, as a reading of the machine** (R80) — the fan's *Route (auto)* child
 * armed with the intent to take the first answer.
 *
 * It is true the moment the intent is armed **and the acquisition's settled line has landed**, and false
 * either side of that instant. It keys on `plan != null` rather than on a non-empty candidate set on
 * purpose: the settled answer is what this child promises, and "a non-empty set" is the same moment one
 * emission later — waiting for an offer would make the promise depend on the engine's own lane rather
 * than on the route it drew. The caller takes **index 0** of the drawn set, which
 * [`routeCandidateLines`] puts the settled line at and every arming resets, so no second selection path
 * exists and `selectRoute()` is called unchanged.
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
 *
 * The time is recomputed at the pace in force, which is what makes the observed pace a live
 * replacement and what makes the cell reach zero at the destination: what is left is a distance, and
 * a distance over a pace is a time. Where no pace is in force it falls back on the **plan's own drawn
 * seconds** ([`RoutePlan.legTimesSec`]) — the time of the line on the screen.
 *
 * There is no `stale` reading any more (R13): a failed refresh changes nothing on the map and says so
 * by toast, so the figure has nothing to mark and the badge that used to carry it is gone.
 */
data class RouteTripFigure(
    val distanceNm: Double,
    val etaSeconds: Double,
    /**
     * The zones a forced crossing entered, by name — empty on an ordinary route. The card is the one
     * place a following boat reads the figure, so a forced crossing has to be visible there rather
     * than hidden behind an ETA that looks like every other one.
     */
    val forcedCrossingZoneNames: List<String> = emptyList(),
    /**
     * When the plan the figure is read from was computed. The age is derived from it where it is
     * shown, so a card that ticks can age the figure without the whole shell recomposing.
     */
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
 * **One point as the panel prints it** — three decimals, with a dot decimal separator in every locale
 * whatever the device says.
 *
 * Three rather than four because the panel prints the **pair on one row** beside its label: the row's
 * own width sets the precision, and two four-decimal points would run past it once the destination's
 * note is with them. One home, read by the panel's data table alone — a second print would drift with
 * the locale.
 */
internal fun routeCoordinate(point: RoutePoint): String =
    String.format(Locale.US, "%.3f, %.3f", point.latitude, point.longitude)

/**
 * **The ETA as both surfaces print it** — the remaining course's seconds split into whole minutes and
 * seconds.
 *
 * One home for the split, read by the panel's data table and by the drawer's summary alike, so the two
 * surfaces cannot drift into printing the same route's time two ways.
 */
@Composable
internal fun routeEtaText(etaSeconds: Double): String {
    val whole = etaSeconds.toInt()
    return stringResource(R.string.route_eta_value_fmt, whole / 60, whole % 60)
}

/**
 * **A span as the panel prints it** — whole minutes above a minute, whole seconds below it.
 *
 * The two fragments it reads are the age line's own (`route_age_sec` · `route_age_min`), because the
 * unit words live once per locale: a candidate's saving and a route's age are the same span, and a
 * second pair of keys would be one value written twice.
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
 * It is the mode's **single control**, and it carries both edges: on arms the mode on the drawer's
 * standing pair, off ends the route — and, once a route is *followed*, off asks first, through the one
 * exit dialog (R59). Like the inspect square it stays tappable while it is on: a gate must never trap
 * the user in a mode they cannot switch off.
 *
 * **It shows three faces** (R51): off; **acquiring**, the route's own green
 * ([`AppConfig.routeLineColor`]) with the pulsing dot; and **navigating**, the palette's blue
 * ([`AppConfig.routeNavigateColor`]) with the same dot (R58).
 *
 * **One pulsing dot serves every toggle** (R69): the mark is a UI token of its own —
 * `ui.map.pulse.dot`, read by [`MapPulseDot`] rather than handed in — so the recording square and this
 * one wear literally the same colour. R51 widens **when** it shows too: it used to be drawn only while
 * a route was followed, and the searching phase joins the following one.
 *
 * **It stays tappable while the engine is not ready too, and that is the point.** Readiness is the
 * mode's real gate, but it is the **engine's** answer and it arrives late, so the tap is the user's own
 * retry: it asks for one more preparation and, when the engine still cannot arm, the refusal it answers
 * with is shown where this feature's own chrome lives. The square is therefore **never dead**, and no
 * `enabled` knob exists to make it so.
 */
@Composable
internal fun RouteToggleButton(
    armed: Boolean,
    following: Boolean,
    searching: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = when {
        !armed -> mapSurfaceFaceInactive()
        following -> mapSurfaceFaceActive(ComposeColor(AppConfig.routeNavigateColor))
        else -> mapSurfaceFaceActive(ComposeColor(AppConfig.routeLineColor))
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
 *
 * A checkbox for a state and buttons for outcomes is the epic's split, which is why the save actions
 * carry their own text and this is the only tick in the panel.
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
