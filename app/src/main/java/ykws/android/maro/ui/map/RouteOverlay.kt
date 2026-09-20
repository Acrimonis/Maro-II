package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

// ─────────────────────────────────────────────────────────────────────────────
// The aimed destination — the target, the preview gate and the trip figure
//
// This file owns the route's own chrome and its arithmetic: the screen-centred target the user aims
// with, the two pure rules that decide when a preview is asked for, and the trip figure the
// dashboard's distance cell reads while a route is confirmed. The map objects — the line and the
// pin — live in RouteHost.kt, which is the one file that touches osmdroid for this feature.
// ─────────────────────────────────────────────────────────────────────────────

/** Side (dp) of the tappable square the aim target is drawn in, centred on the map. */
internal const val ROUTE_TARGET_SIZE_DP = 96f

/** Radius (dp) of the target's outer ring. */
internal const val ROUTE_TARGET_RADIUS_DP = 22f

/** Radius (dp) of the target's inner mark — the exact point the aim resolves to. */
internal const val ROUTE_TARGET_INNER_RADIUS_DP = 3f

/** Stroke (dp) of the outer ring. */
internal const val ROUTE_TARGET_STROKE_DP = 2f

/**
 * How far (m) the aim must move before a new preview is asked for.
 *
 * The preview is driven by movement, not by a clock: below this the target has not really left the
 * water it was pointing at, and the search on screen is still the right answer.
 */
internal const val ROUTE_AIM_THRESHOLD_M = 25.0

/**
 * The floor between two previews (ms) — near three a second.
 *
 * A fling emits an aim per frame, so the threshold alone would still ask for sixty searches a
 * second. This is the cap the epic names, and it is a *floor* rather than a debounce: the aim is
 * asked for on the leading edge, so a settled map is answered at once.
 */
internal const val ROUTE_PREVIEW_MIN_INTERVAL_MS = 333L

/**
 * Whether the aim has moved far enough to be worth a new search.
 *
 * The first aim of a session always passes: there is nothing on screen yet, and a threshold
 * measured against nothing would refuse the very preview the mode opens on.
 */
internal fun routeAimPassed(
    previous: RoutePoint?,
    next: RoutePoint,
    thresholdM: Double = ROUTE_AIM_THRESHOLD_M
): Boolean {
    if (previous == null) return true
    val moved = SpatialOperations.haversine(
        LatLng(previous.latitude, previous.longitude),
        LatLng(next.latitude, next.longitude)
    )
    return moved >= thresholdM
}

/**
 * The trip figure: what is left of a confirmed route, in the two units the dashboard cell shows.
 *
 * The time is recomputed at the pace in force, which is what makes the observed pace a live
 * replacement and what makes the cell reach zero at the destination: what is left is a distance, and
 * a distance over a pace is a time. Where no pace is in force it falls back on the **plan's own drawn
 * seconds** ([`RoutePlan.legTimesSec`]) — the time of the line on the screen, never the seconds the
 * search's own key was built from.
 */
data class RouteTripFigure(
    val distanceNm: Double,
    val etaSeconds: Double,
    val stale: Boolean,
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
    nowMs: Long,
    stale: Boolean
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
        stale = stale,
        forcedCrossingZoneNames = plan.forcedCrossingZoneNames,
        computedAtMs = plan.computedAtMs
    )
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
 * It is the mode's **single control**, and it carries both edges: on aims and previews, off ends the
 * route and cancels an unconfirmed draft. Like the inspect square it stays tappable while it is on —
 * a gate must never trap the user in a mode they cannot switch off — so [enabled] only ever comes
 * from the disarmed-and-no-mesh gate.
 */
@Composable
internal fun RouteToggleButton(
    armed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = if (armed) mapSurfaceFaceActive(ComposeColor(AppConfig.routeLineColor))
    else mapSurfaceFaceInactive()
    val description = stringResource(R.string.cd_route_toggle)
    MapToggleSquare(
        face = face,
        onClick = if (enabled || armed) onToggle else null,
        modifier = modifier,
        contentDescription = description
    ) {
        // Hard-coded like the row's other glyphs: a compass, which reads as "where to go".
        Text(text = "\uD83E\uDDED", fontSize = TOP_TOGGLE_ICON_SIZE)
    }
}

/**
 * The aim target: a screen-centred ring and dot the map is dragged and zoomed under, painted in the
 * pin's own colour so the target and the pin that replaces it read as one thing.
 *
 * It is a marker, not an affordance. The confirmation rises with the mode itself and carries every
 * outcome, so there is nothing left for a tap here to raise — and the square now holds no pointer
 * input at all, which leaves every gesture that starts on it to pan and zoom the map under it.
 */
@Composable
internal fun RouteAimTarget(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val color = ComposeColor(AppConfig.routePinColor)
    val outerRadiusPx = with(density) { ROUTE_TARGET_RADIUS_DP.dp.toPx() }
    val innerRadiusPx = with(density) { ROUTE_TARGET_INNER_RADIUS_DP.dp.toPx() }
    val strokePx = with(density) { ROUTE_TARGET_STROKE_DP.dp.toPx() }

    Box(
        modifier = modifier.size(ROUTE_TARGET_SIZE_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(ROUTE_TARGET_SIZE_DP.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color,
                radius = outerRadiusPx,
                center = centre,
                style = Stroke(width = strokePx)
            )
            drawCircle(color = color, radius = innerRadiusPx, center = centre)
        }
    }
}

/**
 * The dialog's one checkbox: the pin state the saved track starts with, default off.
 *
 * A checkbox for a state and buttons for outcomes is the epic's split, which is why the three save
 * actions carry their own text and this is the only tick in the dialog.
 */
@Composable
internal fun RoutePinOption(
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = pinned, onCheckedChange = onPinnedChange)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.route_pin_label),
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = 15.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

