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
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.ui.components.OptionRow

// ─────────────────────────────────────────────────────────────────────────────
// The route's own rules — the anchor's lead, the ladder and the trip figure
//
// This file owns the route's own arithmetic and the sentences it prints: the acquisition anchor's
// lead, the ladder's caps and their band, the ends' own print, and the trip figure the dashboard's
// distance cell reads while a route is followed. The map objects — the lines, the pin and the aim
// ring — live in RouteHost.kt, which is the one file that touches osmdroid for this feature.
// ─────────────────────────────────────────────────────────────────────────────

/** Opacity of the **oldest** line of the stale ladder (R14). */
internal const val ROUTE_LADDER_ALPHA_OLDEST = 0.20f

/** Opacity of the **newest** line of the stale ladder — the one just replaced (R14). */
internal const val ROUTE_LADDER_ALPHA_NEWEST = 0.80f

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
