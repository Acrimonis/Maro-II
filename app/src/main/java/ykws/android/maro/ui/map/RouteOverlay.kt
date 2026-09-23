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
import kotlin.math.roundToInt
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.ui.components.OptionRow

// ─────────────────────────────────────────────────────────────────────────────
// The route's own rules — the asks, the refresh gate, the ladder and the trip figure
//
// This file owns the route's own arithmetic and the sentences it prints: the ask policy's rule, the
// following mode's refresh gate, the ladder's caps and their band, the failure lines, and the trip
// figure the dashboard's distance cell reads while a route is followed. The map objects — the lines,
// the pin and the aim ring — live in RouteHost.kt, which is the one file that touches osmdroid for
// this feature.
// ─────────────────────────────────────────────────────────────────────────────

/** Opacity of the **oldest** line of the stale ladder (R14). */
internal const val ROUTE_LADDER_ALPHA_OLDEST = 0.20f

/** Opacity of the **newest** line of the stale ladder — the one just replaced (R14). */
internal const val ROUTE_LADDER_ALPHA_NEWEST = 0.80f

/**
 * **Whether the aim has moved far enough to be worth a search** — the first half of the ask policy.
 *
 * The threshold is the configured ground move ([`AppConfig.routeAskMinTargetMoveM`], the 25 m that
 * already shipped) and it is measured from the **last aim that was asked for**, so a drag that never
 * really leaves the water it was pointing at costs nothing. A session with no baseline at all — the
 * map not yet readable on the arming frame — lets the first real aim through, there being nothing to
 * measure it against.
 */
internal fun routeAimPassed(
    previous: RoutePoint?,
    next: RoutePoint,
    thresholdM: Double = AppConfig.routeAskMinTargetMoveM
): Boolean {
    if (previous == null) return true
    val moved = SpatialOperations.haversine(
        LatLng(previous.latitude, previous.longitude),
        LatLng(next.latitude, next.longitude)
    )
    return moved >= thresholdM
}

/**
 * **Whether the following mode may ask for a refresh** (R10).
 *
 * The app owns this gate and the engine may only veto the call it decides on. **Either threshold alone
 * opens the moment** — the clock since the standing answer was computed, or the distance the boat
 * stands off its own route — because each one alone answers a case the other cannot: a slow drift off a
 * long leg, and a boat holding station on the line while the world around it changes.
 */
internal fun routeRefreshDue(
    lastAnswerAtMs: Long,
    nowMs: Long,
    distanceOffRouteM: Double,
    intervalSec: Int = AppConfig.routeRefreshIntervalSec,
    offRouteM: Double = AppConfig.routeRefreshOffRouteM
): Boolean =
    nowMs - lastAnswerAtMs >= intervalSec * 1_000L || distanceOffRouteM >= offRouteM

/**
 * **The point one refresh tick asks from** (R10) — the boat's **own live reading**, or null while the
 * moment has not come.
 *
 * It takes a **provider**, not a point, and that is the rule rather than a convenience: a tick that
 * captured the boat's position once — in the composition that opened the following phase — would fire
 * every later refresh from where the boat stood when the phase began, and read the off-route threshold
 * off that same stale point. Reading through the provider on every call is what makes "the boat's own
 * current reading" true of a tick that runs a minute after the phase opened.
 *
 * A null reading from the provider answers null — no boat, no ask — and so does a shut gate, so the
 * caller has one reading to branch on.
 */
internal fun routeRefreshOrigin(
    livePosition: () -> RoutePoint?,
    standingPlan: RoutePlan,
    nowMs: Long
): RoutePoint? {
    val boat = livePosition() ?: return null
    val due = routeRefreshDue(
        lastAnswerAtMs = standingPlan.computedAtMs,
        nowMs = nowMs,
        distanceOffRouteM = routeDistanceOffRouteM(standingPlan, boat)
    )
    return if (due) boat else null
}

/**
 * **How many of the replaced routes the display keeps** (R14) — the configured oldest plus newest,
 * and nothing else.
 *
 * [stale] is the session's own stale set, oldest first. It is capped for **drawing** only: the session
 * itself is not trimmed, so the all-scope save still writes every route it produced. The middle is what
 * goes — the two ends are the ones the eye reads, the line just replaced and the one that opened the
 * session.
 */
internal fun routeLadderForDrawing(
    stale: List<RoutePlan>,
    oldestNb: Int = AppConfig.routeLadderOldestNb,
    latestNb: Int = AppConfig.routeLadderLatestNb
): List<RoutePlan> {
    if (stale.size <= oldestNb + latestNb) return stale
    return stale.take(oldestNb) + stale.takeLast(latestNb)
}

/**
 * **The ladder's opacity band** (R14): [ROUTE_LADDER_ALPHA_OLDEST] at the oldest entry and
 * [ROUTE_LADDER_ALPHA_NEWEST] at the newest, spread evenly between them.
 *
 * The band is spread over the **stale set alone** — the front route is drawn at its own full opacity
 * and is never an input here, so a new answer never dims the line being followed. It is an **absolute**
 * band, too: 20 % to 80 % *of full opacity*, never a fraction of the front line's own transparency, so
 * the book's two figures are what reaches the map whatever the front line happens to carry.
 */
internal fun routeLadderAlpha(index: Int, total: Int): Float {
    if (total <= 1) return ROUTE_LADDER_ALPHA_NEWEST
    val step = (ROUTE_LADDER_ALPHA_NEWEST - ROUTE_LADDER_ALPHA_OLDEST) / (total - 1)
    return ROUTE_LADDER_ALPHA_OLDEST + step * index
}

/**
 * **The ladder's drawn opacity** (R14): the band's fraction as the ARGB alpha a line is painted with —
 * **20 % for the oldest, 80 % for the newest**, the book's own figures.
 *
 * It is the one conversion from the band to a paint value, so a drawing cannot quietly multiply the
 * band by another line's alpha and land at 43/255 and 172/255 — a fraction of a fraction that is
 * neither of the book's two numbers.
 */
internal fun routeLadderDrawAlpha(index: Int, total: Int): Int =
    (routeLadderAlpha(index, total) * 255f).roundToInt().coerceIn(0, 255)

/**
 * **The line a user reads when a refresh could not answer** (R13), as a resource id — null when the
 * answer was a route.
 *
 * The failure reaches the user as a toast on the app's own snackbar surface, carrying the engine's own
 * reason; the standing line is untouched and **not** marked stale, because stale means *replaced*.
 */
internal fun routeFailureReasonResId(result: RouteResult?): Int? = when (result) {
    RouteResult.OutsideWater -> R.string.route_failure_outside_water
    RouteResult.NoPath -> R.string.route_failure_no_path
    is RouteResult.Success, null -> null
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
 * How far (m) the boat stands off the route it is following — the refresh gate's other threshold.
 *
 * Measured to the **nearest vertex**, the same snap the trip figure reads the remainder with, so the
 * two readings of "where the boat is on this line" can never disagree. An empty polyline answers an
 * infinite offset, which is the honest reading of a line with nothing to stand off.
 */
internal fun routeDistanceOffRouteM(plan: RoutePlan, from: RoutePoint): Double {
    val nearest = plan.points.getOrNull(plan.nearestVertexIndex(from)) ?: return Double.MAX_VALUE
    return SpatialOperations.haversine(
        LatLng(from.latitude, from.longitude),
        LatLng(nearest.latitude, nearest.longitude)
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
 * It is the mode's **single control**, and it carries both edges: on arms the mode, off ends the route
 * and cancels an unconfirmed aim — and, once a route is *followed*, off asks first, through the same
 * dialog the panel's own Exit raises (R23). Like the inspect square it stays tappable while it is on:
 * a gate must never trap the user in a mode they cannot switch off.
 *
 * **Its two on-phases look different** (R19): a plain active face while the destination is being
 * chosen, and the same face carrying the **recording toggle's own pulsing dot** while a route is
 * followed — the same disc, the same corner inset, the same 1 → 0.3 beat, in the route's own colour,
 * through the one shared home ([`MapPulseDot`]).
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = if (armed) mapSurfaceFaceActive(ComposeColor(AppConfig.routeLineColor))
    else mapSurfaceFaceInactive()
    val description = stringResource(R.string.cd_route_toggle)
    MapToggleSquare(
        face = face,
        onClick = onToggle,
        modifier = modifier,
        contentDescription = description
    ) {
        // Hard-coded like the row's other glyphs: a compass, which reads as "where to go".
        Text(text = "\uD83E\uDDED", fontSize = TOP_TOGGLE_ICON_SIZE)

        // The following on-phase is the recording toggle's own treatment, in the route's colour.
        if (armed && following) {
            MapPulseDot(
                color = ComposeColor(AppConfig.routeLineColor),
                modifier = Modifier.align(Alignment.TopEnd)
            )
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
