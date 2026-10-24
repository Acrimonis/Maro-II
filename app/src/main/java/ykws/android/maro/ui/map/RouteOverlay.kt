package ykws.android.maro.ui.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.config.VerdictDial
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.route.PaceSource
import ykws.android.maro.data.track.Track
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteProvisional
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.RouteVerdict
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
    /**
     * **The stage this rung is in while it computes** — the word its row prints in the rank's slot,
     * `null` before its first boundary and from its landing or refusal on.
     */
    val stage: RouteStage? = null,
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
 * **A route's own average speed, all inclusive** — the distance it covers over the whole time it allots,
 * so slow zones, ramps and every cap are already inside it. Nautical miles per hour are knots, so the
 * reading is [distanceNm] over [durationSec]; zero where there is no time to average over. One home for
 * the row's speed line and the plan's [RoutePlan.plannedPaceKn].
 */
internal fun routeAvgSpeedKn(distanceNm: Double, durationSec: Double): Double =
    if (durationSec > 0.0) distanceNm / (durationSec / 3600.0) else 0.0

/**
 * **One printable entry of the Speed limits line** — the limit's whole-second figure, the 300 m band
 * standing apart ([isBand]). The seconds a route reports per limit, rounded to the second, an entry
 * rounding to zero dropped so the line never prints a zero.
 */
internal data class RouteSlowLimitEntry(val limitKn: Double, val seconds: Int, val isBand: Boolean)

/**
 * **The Speed limits entries of a plan** — [RoutePlan.slowLimitSeconds] rounded to the second, a zero
 * dropped and the band kept apart. Empty when nothing slowed the route, which is what leaves the line
 * off the panel.
 */
internal fun routeSlowLimitEntries(plan: RoutePlan): List<RouteSlowLimitEntry> =
    plan.slowLimitSeconds
        .map { RouteSlowLimitEntry(it.limitKn, it.seconds.roundToInt(), it.isBand) }
        .filter { it.seconds >= 1 }


/**
 * **One acquisition row's own figures, settled or provisional** — what the table's middle column
 * prints: the time the line takes, its own average speed and its length in nautical miles.
 *
 * A landed [RoutePage.plan] wins outright: its own length and its `remainingFrom` time are the settled
 * answer, and any provisional pair the page still carries is ignored — *replaced*, never merged. A page
 * with no plan but a provisional pair prints that pair; one with neither has nothing to print, and the
 * table falls back to its `--` placeholder. One home for the settled and the provisional reading alike,
 * so the panel never branches on which it holds.
 */
internal data class RouteRowFigures(val distanceNm: Double, val durationSec: Double) {
    /** The route's own average speed (kn), all inclusive — one [routeAvgSpeedKn] for the row and the plan. */
    val avgSpeedKn: Double get() = routeAvgSpeedKn(distanceNm, durationSec)
}

internal fun routeRowFigures(page: RoutePage): RouteRowFigures? {
    val plan = page.plan
    if (plan != null) {
        return RouteRowFigures(plan.distanceNm, plan.remainingFrom(plan.start).durationSec)
    }
    val provisional = page.provisional ?: return null
    return RouteRowFigures(Units.metresToNauticalMiles(provisional.distanceM), provisional.durationSec)
}

/** Which of a row's figures a line carries — the time, the average speed or the distance. */
internal enum class RouteRowKind { DURATION, SPEED, DISTANCE }

/** One line of a row's figures column, with its value — or `null` where the row is still waiting. */
internal data class RouteRowLine(val kind: RouteRowKind, val value: Double?)

/**
 * **A row's three figure lines, in the panel's own order — the time, the average speed, the distance**
 * (the speed seated second on 2026-10-22). A waiting row ([figures] null) holds the same order, so no
 * pending row reads in a different one beside a landed row.
 */
internal fun routeRowLines(figures: RouteRowFigures?): List<RouteRowLine> = listOf(
    RouteRowLine(RouteRowKind.DURATION, figures?.durationSec),
    RouteRowLine(RouteRowKind.SPEED, figures?.avgSpeedKn),
    RouteRowLine(RouteRowKind.DISTANCE, figures?.distanceNm)
)

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

// ─────────────────────────────────────────────────────────────────────────────
// The followed route's own progression (R99) — the time-to-go history, the loss
// toggle and the arrival cue
// ─────────────────────────────────────────────────────────────────────────────

/** One timestamped reading of the followed route's own time-to-go, on `SystemClock` elapsed-realtime. */
internal data class RouteEtaSample(val etaSec: Double, val atMs: Long)

/**
 * **The followed route's own progression reading** (R99): the gathered time-to-go samples, whether the
 * arrival cue is armed, and when the line last flipped. [routeEtaStep] advances it by one fix, and the
 * detector holds one — cleared on entering `Following` and at every flip.
 */
internal data class RouteEtaState(
    val samples: List<RouteEtaSample> = emptyList(),
    val cueArmed: Boolean = false,
    val lastFlipMs: Long? = null
)

/** What one fix decides: the advanced [state], whether the line flips, and whether the arrival cue fires. */
internal data class RouteEtaDecision(
    val state: RouteEtaState,
    val flip: Boolean,
    val arrivalCue: Boolean
)

/** The history buckets to about one sample a second, on elapsed-realtime, so a demo pan cannot churn it. */
internal const val ROUTE_ETA_BUCKET_MS = 1_000L

/**
 * **The look-back is derived, not a dial** (R99): twice the loss, so the loss must accrue within its
 * span and the real gate is *losing ground at least half as fast as the route expects to gain it*.
 */
internal fun routeEtaLookBackMs(lossSec: Double): Long = (lossSec * 2.0 * 1_000.0).toLong()

/**
 * The smallest time-to-go inside the look-back ending at [nowMs], or null when the window holds none.
 * Samples older than the look-back are ignored, so a background pause leaves no stale low behind.
 */
internal fun routeEtaLow(samples: List<RouteEtaSample>, nowMs: Long, lookBackMs: Long): Double? {
    val floor = nowMs - lookBackMs
    var low: Double? = null
    for (sample in samples) {
        if (sample.atMs < floor) continue
        low = if (low == null) sample.etaSec else minOf(low, sample.etaSec)
    }
    return low
}

/**
 * **The history after one sample** (R99) — bucketed to about one a second ([ROUTE_ETA_BUCKET_MS]), the
 * samples older than the look-back dropped. A reading inside the last second's own bucket **replaces**
 * its sample rather than adding one, so a fast stream of fixes stays one-a-second.
 */
internal fun routeEtaHistory(
    samples: List<RouteEtaSample>,
    etaSec: Double,
    nowMs: Long,
    lookBackMs: Long,
    bucketMs: Long = ROUTE_ETA_BUCKET_MS
): List<RouteEtaSample> {
    val floor = nowMs - lookBackMs
    val fresh = samples.filter { it.atMs >= floor }
    val last = fresh.lastOrNull()
    return if (last != null && nowMs - last.atMs < bucketMs) {
        fresh.dropLast(1) + RouteEtaSample(etaSec, last.atMs)
    } else {
        fresh + RouteEtaSample(etaSec, nowMs)
    }
}

/**
 * **Whether the followed route's reading now toggles** (R99) — `eta(now) - low >= lossSec`, where the
 * low is the smallest sample inside the look-back of twice the loss ([routeEtaLow]). A fall or a flat
 * reading is the ordinary progressing state; a missing window reads nothing in it, so this answers
 * false. Pure and positional, so demo and GPS behave alike.
 */
internal fun routeEtaToggle(
    samples: List<RouteEtaSample>,
    etaSec: Double,
    nowMs: Long,
    lossSec: Double
): Boolean {
    if (!etaSec.isFinite()) return false
    val low = routeEtaLow(samples, nowMs, routeEtaLookBackMs(lossSec)) ?: return false
    return etaSec - low >= lossSec
}

/**
 * **The followed route's progression, advanced by one fix** (R99, R100) — the one reading behind both
 * the loss toggle and the arrival cue.
 *
 * A loss of [lossSec] against the look-back low flips the line, unless the [debounceSec] since the last
 * flip has not yet elapsed; a flip **clears the history**, since the time-to-go jumps to the other
 * end's own value and the samples behind it belong to the other orientation — without the clear that
 * stale low would make the jump read as a loss and turn the line straight back. Otherwise the sample
 * joins the history and the arrival cue fires when the time-to-go has fallen below [arrivalEtaSec] as a
 * **new look-back low** with the cue armed: the cue arms on any reading above the threshold **and on
 * every flip**, so a route taken up already inside the zone raises no prompt while its mirrored end
 * does, and disarms when it fires, so a boat that leaves and comes back down legitimately re-prompts.
 * Pure: the caller holds [state] and reads the flip and the cue off the answer.
 */
internal fun routeEtaStep(
    state: RouteEtaState,
    etaSec: Double,
    nowMs: Long,
    lossSec: Double,
    debounceSec: Double,
    arrivalEtaSec: Double
): RouteEtaDecision {
    if (!etaSec.isFinite()) return RouteEtaDecision(state, flip = false, arrivalCue = false)
    val lookBackMs = routeEtaLookBackMs(lossSec)
    val debounceMs = (debounceSec * 1_000.0).toLong()
    val debounced = state.lastFlipMs?.let { nowMs - it < debounceMs } == true
    if (!debounced && routeEtaToggle(state.samples, etaSec, nowMs, lossSec)) {
        // A flip clears the history and arms the cue, so the mirrored end prompts as the first end does;
        // the cue it outranks is the caller's to close.
        return RouteEtaDecision(
            state = RouteEtaState(samples = emptyList(), cueArmed = true, lastFlipMs = nowMs),
            flip = true,
            arrivalCue = false
        )
    }
    val low = routeEtaLow(state.samples, nowMs, lookBackMs)
    val history = routeEtaHistory(state.samples, etaSec, nowMs, lookBackMs)
    val armed = state.cueArmed || etaSec > arrivalEtaSec
    val crossing = armed && etaSec < arrivalEtaSec && (low == null || etaSec < low)
    return RouteEtaDecision(
        state = RouteEtaState(samples = history, cueArmed = armed && !crossing, lastFlipMs = state.lastFlipMs),
        flip = false,
        arrivalCue = crossing
    )
}

/**
 * **The auto-pick's one-shot, as a reading of the machine** (R80) — the fan's *Route (auto)* child
 * armed with the intent to take the **final best** of the settled set.
 *
 * It is true the moment the intent is armed and the set is **settled** — nothing is still searching
 * and at least one line has landed ([anyLanded]) — so it waits for the ladder's last rung rather than
 * for the main's line alone, which is what lets it take the ranking's own winner instead of index 0.
 * The caller then seats the running best, so this child's promise and the seat agree on every arming
 * the user has not touched.
 */
internal fun routeAutoPickReady(autoPick: Boolean, state: RouteState, anyLanded: Boolean): Boolean =
    autoPick && state is RouteState.Choosing && !state.searching && anyLanded

/**
 * **Whether the fan's *Route (auto)* child is live** — in two situations. With nothing armed
 * ([displayArmed] false, which includes a pending discard window) it is the arming, as before; **while a
 * search runs** it is live too, and a press then arms nothing: it only sets the auto-follow intent
 * ([autoPickSet]), the winner being followed when the last route lands. Once the search has ended, an
 * early select is committed or the intent already stands, the child is off — `Follow` takes a settled set.
 */
internal fun routeAutoChildEnabled(displayArmed: Boolean, state: RouteState, autoPickSet: Boolean): Boolean {
    if (!displayArmed) return true
    val choosing = state as? RouteState.Choosing ?: return false
    return choosing.searching && !choosing.committed && !autoPickSet
}

/** The ladder's rung count — the acquisition's pages, and the map's line pool. */
internal const val ROUTE_LADDER_RUNG_COUNT = 3

/**
 * **The verdict's dial read as its own word** — the chip the Settings row, the drawer's quick access and
 * the followed trip line all print: `Fastest` at the weight 0 end, `1x … 4x` through the middle and
 * `Ignore time` at the top. Every word is a `@StringRes` id read through [verdictDialWord], so both
 * locales carry it and no model holds a literal — and no surface names the same stop differently.
 */
@Composable
internal fun verdictDialLabel(stop: VerdictDial): String = verdictDialWord(LocalContext.current, stop)

/**
 * The verdict dial's word read from [context] — the non-composable core [verdictDialLabel] prints, so a
 * saved route's comment reads the very chip at save time without a composable.
 */
internal fun verdictDialWord(context: Context, stop: VerdictDial): String = when (stop) {
    VerdictDial.FASTEST -> context.getString(R.string.settings_route_slow_water_fastest)
    VerdictDial.IGNORE_TIME -> context.getString(R.string.settings_route_slow_water_ignore_time)
    else -> context.getString(R.string.settings_route_slow_water_value_fmt, stop.weight!!.roundToInt().toString())
}

/**
 * The pace segment the panel header and a saved route's comment both print — `Now (pace, source)` under
 * the `Any` stop, else the plain pace reading. One home, so the two surfaces name the same speed the
 * same way, the comment reading the locale active at save time.
 */
internal fun paceHeaderWord(context: Context, paceSetKn: Float, paceKn: Double, source: PaceSource): String =
    if (AppConfig.isFreeWaterPaceAny(paceSetKn)) {
        context.getString(
            R.string.route_header_pace_any_fmt,
            context.getString(R.string.settings_route_pace_value_fmt, paceKn),
            context.getString(source.labelRes())
        )
    } else {
        context.getString(R.string.settings_route_pace_value_fmt, paceSetKn)
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
 * **The ranks the panel prints, counted over the pages that stand** — the engine's verdict read through
 * the folds. A rung folded into a survivor is not a rung of its own, so each surviving page takes the
 * best (lowest) engine rank among itself and the lookups folded into it, and the ranks are counted over
 * the pages present, `1..k`, never `1, 3`. [crownLookupId] is the survivor that holds the engine's crown,
 * so the check never vanishes with a swallowed twin; whether it is shown is the panel's, at the end.
 */
data class RouteRankView(
    val ranks: Map<RouteId, Int> = emptyMap(),
    val crownLookupId: RouteId? = null
)

/**
 * **The rank view of [pages]** under [verdict] and [folded] (*dropped lookup → the survivor it folded
 * into*). Pure: it maps ids and renumbers, and re-derives no rule of the ranking.
 */
internal fun routeRankView(
    pages: List<RoutePage>,
    verdict: RouteVerdict?,
    folded: Map<RouteId, RouteId>
): RouteRankView {
    if (verdict == null) return RouteRankView()
    val present = pages.mapNotNull { it.lookupId }.toSet()
    val best = mutableMapOf<RouteId, Int>()
    for (entry in verdict.order) {
        val owner = folded[entry.lookupId] ?: entry.lookupId
        if (owner !in present) continue
        best[owner] = minOf(best[owner] ?: entry.rank, entry.rank)
    }
    val counted = best.entries.sortedBy { it.value }
        .mapIndexed { index, entry -> entry.key to index + 1 }
        .toMap()
    val crownOwner = verdict.best.lookupId.let { folded[it] ?: it }
    return RouteRankView(counted, crownOwner.takeIf { it in counted })
}

/**
 * **The order the panel's rows should stand in, as the original indices** — the landed rows by rank,
 * then the rest in the ladder's own (computation) order. With no rank at all (an engine that ranks
 * nothing) the landed rows go by time, fastest-first, ahead of the rest. A provisional figure moves no
 * row: the order is rewritten only when a route lands, is refused or is merged away.
 *
 * The order is a pure view over [pages]: nothing reorders the page set itself, so the main — index 0
 * of the canonical list — stays index 0 for the map's pool and the provisional line no matter where
 * the sort puts it in the panel.
 */
internal fun routeTargetOrder(pages: List<RoutePage>, ranks: Map<RouteId, Int>): List<Int> {
    fun rankOf(index: Int): Int? = pages[index].lookupId?.let { ranks[it] }
    fun timeOf(index: Int): Double? = pages[index].plan?.durationSec
    return pages.indices.sortedWith(
        compareBy<Int>(
            { if (rankOf(it) != null) 0 else if (timeOf(it) != null) 1 else 2 },
            { rankOf(it) ?: Int.MAX_VALUE },
            { timeOf(it) ?: Double.POSITIVE_INFINITY },
            { it }
        )
    )
}

/**
 * **The order a held snapshot gives the current [pages], as the original indices** — the snapshot's
 * lookups that still stand, in its order, then any page it has not seen (a new one) in page order. The
 * snapshot is kept as lookup ids, never page indices, because a fold removes a page and re-maps the rest;
 * an empty snapshot is the natural page order.
 */
internal fun routeApplyOrder(pages: List<RoutePage>, snapshot: List<RouteId>): List<Int> {
    val byLookup = pages.indices.filter { pages[it].lookupId != null }.associateBy { pages[it].lookupId }
    val held = snapshot.mapNotNull { byLookup[it] }
    return held + pages.indices.filter { it !in held }
}

/**
 * **The seat, answered as an original page index** — the preferred rung resolved against the displayed
 * [order]. While nothing has landed the preference's row stays selected as computing; once any row has
 * landed the seat takes the preference's rung when it is landed, otherwise the nearest landed row in the
 * displayed view. [routeSeatedIndex] keeps its one home: it is handed the displayed view's plan flags
 * and the preferred rung's position in that view.
 */
internal fun routeSeatedPageIndex(pages: List<RoutePage>, preferredRungIndex: Int, order: List<Int>): Int {
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
 * The trip figure: what is left of a followed route, in the units the dashboard cell shows.
 *
 * Its [RouteTripFigure.etaSeconds] is the **plan's own remaining time** — [`RoutePlan.remainingFrom`]'s
 * `durationSec`, which prices each leg at the limit the search resolved for its water — and never
 * distance ÷ pace, which would understate every line carrying any slow water.
 */
data class RouteTripFigure(
    val distanceNm: Double,
    val etaSeconds: Double,
    /**
     * The **verdict dial's own word** in force, already resolved — the stop the crown was weighed by,
     * read through [verdictDialLabel] so the card prints the very chip the Settings row carries. @see
     * verdictDialLabel
     */
    val preferenceLabel: String,
    /** The **cruising pace in force**, in knots — the set pace the plan was read at. */
    val paceKn: Double,
    /** When the plan the figure is read from was computed. */
    val computedAtMs: Long
)

/**
 * **The followed figure, read from the plan's own remaining time** (2026-10-05) — the one home the
 * dashboard trip cell, the drawer summary and the acquisition row all read is
 * [`RoutePlan.remainingFrom`], and this reads its `durationSec` and nothing else. **There is no
 * distance ÷ pace here**: that division survives only where no plan times a line — the acquisition's
 * provisional pair, which the engine already prices at the limit in force — and never on a followed,
 * planned line, where it would discard the plan's slow-water time. @see RouteTripFigure
 */
internal fun routeTripFigure(
    plan: RoutePlan,
    from: RoutePoint,
    preferenceLabel: String,
    paceKn: Double
): RouteTripFigure {
    val remaining = plan.remainingFrom(from)
    return RouteTripFigure(
        distanceNm = Units.metresToNauticalMiles(remaining.distanceM),
        etaSeconds = remaining.durationSec,
        preferenceLabel = preferenceLabel,
        paceKn = paceKn,
        computedAtMs = plan.computedAtMs
    )
}

/**
 * **A saved route read back as a plan** — the inverse of the save, so the track card's follow door
 * has no second arithmetic. Points and per-leg times come from the vertices
 * [`TrackFromCourse.build`] wrote; the acquisition-only facts are absent ([forcedCrossingZoneNames]
 * and [crossedSlowZoneNames] empty, [destinationMoved] false), and the total duration is the track's
 * own figure rather than a sum of the truncated per-leg milliseconds.
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
        crossedSlowZoneNames = emptyList(),
        computedAtMs = track.startTimeMs
    )
}

/**
 * **Is this line already a track?** — the one read of the session's route-to-track link, shared by the
 * selected line's and the followed line's save facts (R16, R17). The read direction is not an identity
 * (R99): the link is bridged across the loss mirror, so a plan saved in either orientation answers its
 * own track, and a plan with no link answers false.
 */
internal fun routeLinkSaved(links: Map<RoutePlan, String?>, plan: RoutePlan?): Boolean {
    if (plan == null) return false
    return links[plan] != null || links[plan.reversed()] != null
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
        crossedSlowZoneNames = emptyList(),
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

/**
 * The route toggle: the map control stack's own square, beside the sleuth's.
 *
 * **It is never gated.** An engine that cannot answer does not gate the mode: the toggle arms, and
 * the status line carries the reason. The square is therefore **never dead**, and no `enabled` knob
 * exists to make it so.
 *
 * `routeFace` (`MapToggleFace.kt`) resolves the square's two channels — off, the acquiring amber while
 * the search has not answered, the nominal blue once the line is followed — and the square reads the
 * user's **line colour no longer**: that colour stays on the line alone, and the tile and the mark carry
 * the mode's own state.
 *
 * [tileStoreBusy] is the warm-up's own busy window: while the background walk is really building tiles,
 * the mark turns white — a loading tell for the warm-up's work, carried by the face and held a grace
 * after its last tile so a run of them reads as one window. It is dark while the walk waits at its gate.
 */
@Composable
internal fun RouteToggleButton(
    armed: Boolean,
    following: Boolean,
    searching: Boolean,
    tileStoreBusy: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = routeFace(armed, following, searching, tileStoreBusy)
    val description = stringResource(R.string.cd_route_toggle)
    MapToggleSquare(
        face = face.toSurfaceFace(),
        onClick = onToggle,
        modifier = modifier,
        contentDescription = description
    ) {
        // Hard-coded like the row's other glyphs: a compass, which reads as "where to go".
        Text(text = "\uD83E\uDDED", fontSize = TOP_TOGGLE_ICON_SIZE)
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
