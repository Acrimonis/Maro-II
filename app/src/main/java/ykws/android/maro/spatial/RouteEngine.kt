package ykws.android.maro.spatial

import kotlinx.coroutines.flow.Flow
import ykws.android.maro.R
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The route's engine seam: a declaration of the routes an engine can compute, and one flow of
 * updates per started lookup.**
 *
 * The seam was rebuilt on 2026-09-29 because the shape it had — a mode that placed a destination on
 * the map and re-asked on a clock — is no longer the mode. Arming reads the drawer's pair once, so an
 * engine is no longer a *session* told one end at a time; it is asked **which routes it can compute
 * between two points**, and it answers a set of declared computations, each with its own id. Starting
 * any of them needs only that id, because the whole set runs on the one pair the engine was handed.
 *
 * **The pair is repaired first.** The first step of [routesToCompute] is the invalid-end repair — a
 * point that is land or shallower than the minimum depth is moved to the nearest valid water on the
 * sea side — and a pair that cannot be repaired is refused by name rather than searched. The flow
 * never sees the unrepaired points: they exist only inside the engine.
 *
 * **One callback shape carries everything the flow learns.** [updates] is the engine's own `Flow` of
 * [RouteUpdate] values, each carrying the lookup's id, the stage just finished and the one about to
 * run, the line computed so far, the finished result and the reason a lookup cannot be answered. A
 * listener is rejected here on purpose: a listener would put unregister state in the flow, while a
 * `Flow` is collected and the correlation is the id alone.
 *
 * **Disposal.** [cancelLookup] is the only disposal an engine performs; every other disposal is the
 * flow asking. After a cancel, no further update for that id may arrive.
 *
 * **No engine holds a clock or a gate.** All three calls are plain, not `suspend` — [routesToCompute]
 * is the repair plus a local configuration read, [startLookup] launches the job and returns, and
 * [cancelLookup] cancels it. An engine that cannot answer does not gate the mode: the toggle arms and
 * the status line says why. Coroutines and `Flow` only.
 */
interface RouteEngine {

    /**
     * **The routes this engine can compute between [origin] and [destination]**, after repairing the
     * pair.
     *
     * The return is a sealed shape because the repair can refuse, and a refusal has to be said:
     * [RouteDeclarations.Available] carries the computations — **first = main**, running all the
     * default values — and [RouteDeclarations.Refused] carries the [RouteReason]. Each computation
     * carries its own id, and [startLookup] needs only that id.
     */
    fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations

    /**
     * **Starts one declared computation by its id**, and returns the id of the lookup it just
     * launched. No points are passed: the whole set shares the repaired pair [routesToCompute] was
     * handed, so the id is the only correlation the flow ever reads.
     */
    fun startLookup(computationId: RouteId): RouteId

    /**
     * **Ends a lookup by id**, and no further update for it may arrive afterwards. This is the only
     * disposal an engine performs; every other disposal is the flow asking.
     */
    fun cancelLookup(id: RouteId)

    /** The one channel every lookup's updates ride on, correlated by [RouteUpdate.routeId]. */
    val updates: Flow<RouteUpdate>
}

/**
 * **The id of one declared computation** — the handle [RouteEngine.startLookup] takes. A value class
 * over a `Long`, minted by the engine that owns the declaration.
 */
@JvmInline
value class RouteId(val value: Long)

/**
 * **One declared computation** — its own id and the id of the line a user reads as its description,
 * minted and held by the engine. The `@StringRes` shape is the `CustomSortField` contract, so no
 * engine holds user-facing text and both locales carry the key.
 */
data class RouteComputation(
    val id: RouteId,
    val descriptionResId: Int
)

/**
 * **What [RouteEngine.routesToCompute] answers** — a sealed shape so a refusal is a value and never
 * an exception or a null the caller has to guess at.
 */
sealed interface RouteDeclarations {

    /** The computations the engine can run, in the order it declares them — **first = main**. */
    data class Available(val computations: List<RouteComputation>) : RouteDeclarations

    /** The pair could not be repaired, or the world is not ready: [reason] names it. */
    data class Refused(val reason: RouteReason) : RouteDeclarations
}

/**
 * **Why a lookup cannot be answered** — the closed set the engine reports with, one variant per
 * failure the flow has to tell apart.
 *
 * Each entry carries the id of the line a user reads ([labelResId]), the `CustomSortField` shape, so
 * no engine holds user-facing text and both locales carry the key. [CANNOT_REPAIR] and
 * [WORLD_NOT_READY] answer at [RouteEngine.routesToCompute] — the repair's two — and [NO_PATH] and
 * [OFF_WATER] answer on the update flow — the lookup's two.
 */
enum class RouteReason(val labelResId: Int) {

    /** The repair could not move an end to valid water within the sweep's radius. */
    CANNOT_REPAIR(R.string.route_reason_cannot_repair),

    /** The coastline or the depth the repair judges by has not loaded. */
    WORLD_NOT_READY(R.string.route_reason_world_not_ready),

    /**
     * **No line could be drawn for the pair (D38).** The search found no route between the repaired ends —
     * and a lookup whose build failed is answered with this same surface, because a failed build has no line
     * to report either. One label covers both: the reason names the **absence of a line**, never the
     * mechanism behind it, and no separate variant exists for the build failure.
     */
    NO_PATH(R.string.route_reason_no_path),

    /** The pair or the region the engine covers leaves no water to search. */
    OFF_WATER(R.string.route_reason_off_water)
}

/**
 * **A provisional reading of a line a rung is still settling** — the distance and the time the line
 * already supports the moment it is first taut, and nothing the pipeline may still move.
 *
 * It is engine-neutral on purpose: a pair of figures any engine can stand behind, taken before the
 * corner pass has rounded the line and before the profile clock has timed it. It is not an answer —
 * [RouteUpdate.result] is — so it never decides anything, and it is replaced by the settled figures the
 * instant the rung's terminal update lands.
 */
data class RouteProvisional(
    /** The pulled line's own length, in metres. */
    val distanceM: Double,
    /** That same line timed by the enforced-limit clock, in seconds. */
    val durationSec: Double
)

/**
 * **The rung a running ranking currently names, and how many it has compared** — the ladder's own
 * running best, folded at every rung's terminal so a surface can read *so far, the winner is…* rather
 * than waiting for the last rung to settle.
 *
 * It is a **running best, never a verdict**: the engine folds each settled rung through the
 * preference's own rule ([ykws.android.maro.spatial.multipass.RoutePassRanking]) and reports the best
 * of those landed. A rung that found no path is no candidate, and its terminal carries the running
 * best unchanged. Only a terminal carries a finished line, so no mid-pass update can produce one.
 *
 * It names the winning rung by the **lookup that owns its line**, so the flow seats it by the page
 * that lookup owns and never by a page index the engine does not hold.
 */
data class RouteRunningBest(
    /** The lookup that owns the winning rung's line — the page the flow seats. */
    val lookupId: RouteId,
    /** How many landed rungs the running best has compared so far. */
    val compared: Int
)

/**
 * **One update the flow learns about a lookup** — the id, the stage pair, the line so far, the
 * finished result and the reason a lookup cannot be answered.
 *
 * The stage pair is **finished-then-next**: [stageDone] names the boundary the pipeline just left,
 * and [nextStage] the one it is about to enter — or `null` when the job is done, which is the
 * terminal update that also carries [result] or [reason]. [stageDone] is `null` for an engine with no
 * stage to report (the dummy). [line] is the line computed so far, a value for the flow to paint —
 * never a drawing. [result] is `null` until done, and [reason] is `null` on success.
 */
data class RouteUpdate(
    val routeId: RouteId,
    val stageDone: RouteStage?,
    val nextStage: RouteStage?,
    val line: List<RoutePoint>,
    val result: RouteResult.Success?,
    val reason: RouteReason?,
    /**
     * **The figures the stage that just finished can stand behind**, or empty where it counts nothing.
     *
     * The default is honest here where a defaulted value would not be: an engine that measures nothing —
     * the dummy reads no layer at all — reports nothing, and the empty list is that statement rather than
     * a gap. The list belongs to [stageDone], never to [nextStage]: a stage reports what it *did*.
     */
    val readings: List<RouteStepReading> = emptyList(),
    /**
     * **The provisional pair a rung's line already supports**, or `null` where the engine has none to
     * report — the absent default states that plainly, so an engine that measures nothing says so.
     *
     * It rides the boundary update that already carries [line] and [readings], and it belongs to the
     * **pulled** line the boundary just made taut: the figures a row can print while the rung is still
     * settling, replaced by the settled answer when the rung's terminal update lands. An engine that
     * takes no such reading leaves it null everywhere.
     */
    val provisional: RouteProvisional? = null,
    /**
     * **The ladder's running best at this terminal**, or `null` where the engine ranks nothing — the
     * dummy, or a ladder whose rungs have all failed. It rides **every** terminal (a settled rung, a
     * no-path one alike) and is folded afresh each time, so the last terminal carries the final winner
     * and a surface can tell a running best from a settled one by whether anything is still searching.
     */
    val runningBest: RouteRunningBest? = null
)

/**
 * **One figure a stage reports about its own work** — a count, a time or a length, with the id of the line
 * a user reads and the id of the unit it carries.
 *
 * The `CustomSortField` shape again: [labelResId] and [unitResId] are `@StringRes`, so no engine holds
 * user-facing text and both locales carry every key. [value] is a plain number in that unit, and the
 * surface decides how to round and lay it out — the engine never formats.
 *
 * It is deliberately **not** on [`RouteResult`]: that type records that the two engines carrying dossiers
 * were removed on 2026-09-22 and that no instrumentation field and no engine-specific vocabulary are left
 * on an answer. A reading is per **stage**, while a lookup runs, and it dies with the lookup — so it rides
 * the seam's own update, where the stage pair already is.
 */
data class RouteStepReading(
    /** The stage this figure is about — the one that just finished. */
    val stage: RouteStage,
    /** The id of the line a user reads, e.g. `Expansions`. */
    val labelResId: Int,
    /** The figure itself, in [unitResId]'s unit. */
    val value: Double,
    /** The id of the unit's line, e.g. `cells` — or 0 where the figure is a bare count. */
    val unitResId: Int
)

/**
 * **The stage a search crosses** — the closed set an engine publishes while a lookup runs, shaped
 * like [RouteReason] so the label is an id the surface resolves and no engine holds user-facing
 * text.
 *
 * The five entries are the five boundaries the avoidance pipeline crosses — the corridor it bounds,
 * the grid it rasterizes, the search it runs, the taut pull and the corner snap.
 */
enum class RouteStage(val labelResId: Int) {

    /** The corridor box the search is bounded to is being cut from the two ends. */
    CORRIDOR(R.string.route_stage_corridor),

    /** The corridor is being rasterized into the grid the A* walks. */
    GRID(R.string.route_stage_grid),

    /** The A* is expanding the grid between the two anchored cells. */
    SEARCH(R.string.route_stage_search),

    /** The cell path is being pulled taut against the coastline. */
    PULL(R.string.route_stage_pull),

    /** Each bend is being moved onto its nearest tangent corner. */
    SNAP(R.string.route_stage_snap)
}
