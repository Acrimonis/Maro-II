package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **One walk's grid, and the values that are its own.** A walk is the grid the A\* expands, the two
 * cells it starts and aims at and the cell size it was rasterized at; carrying them as one value is
 * what lets the coarse walk and the two-layer first walk travel through the same door.
 */
internal data class GridWalk(
    val grid: MultipassGrid,
    val startCell: CellIndex,
    val aimCell: CellIndex,
    val cellM: Double,
    /**
     * The water this walk may use where it is **several windows on one lattice** — `null` for the uniform
     * pass, whose single grid is its own walk. The path answers the walk's own coordinates either way: the
     * grid's for one window, the lattice's for a chain, and this is what resolves a path back to points.
     */
    val windows: WalkWindows? = null
)

/**
 * One tangent corner set: the offset points and, **per point**, the radius within which that corner may
 * move a bend. The radius is a function of the point because a two-layer walk resolves different water at
 * different sizes, and each corner's reach is the **local** cell's own (Phase 6) — a fine 20 m corner near
 * the coast moves a bend 40 m, a coarse one in open water 200 m — while a single grid answers one radius.
 */
internal data class CornerSet(val points: List<LatLng>, val radiusM: (LatLng) -> Double)

/**
 * The λ-free solve context the ladder's rungs share: the corridor, the grid and every reading the
 * solve-at-λ tail needs. Built once per arm, so three rungs cost one rasterise and three A* passes.
 *
 * **[windows] is the two-layer first walk's seam**, added with named construction rather than a 27th
 * positional field: it is `null` for the uniform pass, whose single grid is its own walk, and a family of
 * windows where the plan answered more than one tile. [grid], [startCell], [aimCell] and [cellM] stay the
 * **interior** layer's, so every single-grid read site keeps its own answer.
 */
internal data class GridContext(
    val world: MultipassWorld,
    val from: RoutePoint,
    val to: RoutePoint,
    val box: BBox,
    val edges: List<MultipassEdge>,
    val openCoast: List<List<LatLng>>,
    val capLatNorth: Double,
    val cellM: Double,
    /**
     * The **fine cell (m)** — the plan's metres answer for this engine, carried on the context so the
     * refinement and the clock read one value instead of deriving a size each.
     */
    val fineCellM: Double,
    val marginM: Double,
    val zoneOutsideMarginM: Double,
    val pace: Double,
    /**
     * The marker price's **live strength** — the Routing row's factor, carried beside [pace] so every
     * field this arm builds prices the marker law at one value.
     */
    val markerStrength: Double = 1.0,
    val grid: MultipassGrid,
    val startCell: CellIndex,
    val aimCell: CellIndex,
    val start: LatLng,
    val aim: LatLng,
    val sets: List<CornerSet>,
    val limitAt: (LatLng) -> Double?,
    val zones: List<SpeedZone>,
    val priced: List<ZoneRing>,
    /**
     * **The arm's priced zones, indexed** — [zones] as a [ZoneSet], the share split's own interior read,
     * built once rather than per call.
     */
    val pricedZoneSet: ZoneSet = ZoneSet(zones),
    /**
     * **The corridor's zones whatever the price switch says, indexed** — the clock's and the crossed-zone
     * report's read (`limitAtFor` is built over it), kept beside the gated set because the two differ when
     * the price switch is off and the crown reads the gated one.
     */
    val zoneSet: ZoneSet = pricedZoneSet,
    /**
     * **The world's whole non-excluded zone list, indexed** — the corner pass's read: a bow or a merge
     * crossing can stand a leg beyond the corridor, so its zone test is bounded by no box, and the set
     * answers the world's own `zoneLimitKnAt` with a box reject ahead of every ring walk.
     */
    val worldZoneSet: ZoneSet = zoneSet,
    val depthGateActive: Boolean,
    val minDepthM: Double,
    val regionSaturated: Boolean,
    /**
     * **The algorithm's own plan** — carried so every arm reads the one berth the plan prices
     * ([RouteGridPlan.pricesBerth]) rather than a flag: `avoid` prices none and keeps its wall at the gate's
     * own margin.
     */
    val plan: RouteGridPlan = UniformGridPlan,
    /**
     * The two-layer first walk's windows, or `null` where the plan answered one tile — the uniform pass's
     * case, whose single grid is its own walk.
     */
    val windows: WalkWindows? = null,
    /**
     * **The depth gate's rule for this arm** — the measured edge, the wall, the preferred standoff and the
     * ends' waiver — built once with the grid so every pass, the fine pass and the corner pass read one
     * rule; `null` where the gate is out of force. **Never defaulted**: a construction site that forgot it
     * would draw a line no guard measured.
     */
    val gate: GateRule?,
    /**
     * **The forced-crossing probe's answer, shared by the rungs over this grid** — the probe reads nothing
     * of a rung's λ, so one answer serves the three. Fresh per context by default; never part of the
     * context's own identity.
     */
    val probe: ProbeMemo = ProbeMemo(),
    /**
     * **The crossing re-solves' rasters, shared by the rungs over this grid** — a zone's local fine raster
     * reads nothing of a rung's λ either, so the first rung builds it and its siblings copy it
     * ([CrossingRasterMemo]). Fresh per context; never part of its identity.
     */
    val crossingRasters: CrossingRasterMemo = CrossingRasterMemo()
)

/** One crossing raster's identity within an arm: the zone, the clamped box it is rastered over and the cell. */
internal data class CrossingRasterKey(val zoneId: String, val box: BBox, val fineCellM: Double)

/**
 * **One arm's crossing rasters, each built once** — keyed on the zone, its clamped box and the fine cell,
 * the three things the raster is a function of beside the arm's own pace, gate, markers and harvest, which
 * every rung of the arm shares. The first asker builds under the lock and stores the finished grid; every
 * asker, the builder included, takes **its own copy** ([MultipassGrid.copy]), since a rung then frees its
 * own ends on it. A build cancelled under the lock stores nothing, so the next rung builds for itself and
 * nothing half-built is ever shared. The stored grid lives as long as the context — one grid per zone an
 * arm crosses — beside the rungs' copies.
 */
internal class CrossingRasterMemo {
    private val lock = Mutex()
    private val grids = HashMap<CrossingRasterKey, MultipassGrid>()

    /** The grid for [key], built by [build] at most once, answered as a fresh copy. */
    suspend fun gridFor(key: CrossingRasterKey, build: () -> MultipassGrid): MultipassGrid =
        lock.withLock { grids[key] ?: build().also { grids[key] = it } }.copy()
}

/**
 * **One grid's forced-crossing probe, computed once** — keyed on the crossing zones it was asked for, so a
 * rung whose set differs (a λ of zero, which prices no zone) still gets its own answer. The first asker
 * computes under the lock; its siblings wait on the lock and read the answer.
 */
internal class ProbeMemo {
    private val lock = Mutex()
    private var key: List<String>? = null
    private var avoiding: Boolean = false

    suspend fun avoiding(crossingZoneIds: List<String>, compute: suspend () -> Boolean): Boolean =
        lock.withLock {
            if (key == crossingZoneIds) return@withLock avoiding
            val answer = compute()
            key = crossingZoneIds
            avoiding = answer
            answer
        }
}

/**
 * One pass's own readings, returned rather than logged inside it: the caller that owns the decision
 * the pass leads to — the ladder's rank fold — emits the pass's one line where that decision is made,
 * so the line carries the pass beside the rung it decided.
 */
internal data class PassReading(
    /** The A\*'s own outcome: the path, or the exhaustion reading that says why there is none. */
    val search: SearchOutcome,
    /**
     * The pass's own line — the **last pull's** waypoints, the very list the pipeline hands on and
     * never the clock's split copy beside it, so the pass's answer is the one every later stage
     * reads. `emptyList()` where the A\* answered nothing.
     */
    val line: List<LatLng>,
    /**
     * **This pass's own refusal tally** — the chords its two pulls refused, counted by this pass alone. It
     * was one object per arm before, shared by the three rungs' pulls on three threads, so a rung's count
     * carried its siblings' refusals and the fine pass's sweep gate read a timing-dependent number; now a
     * pass owns its tally, and the fine pass opens one of its own.
     */
    val refusals: PullRefusals,
    /** How many waypoints the first pull left — the pass's own pulled count. */
    val pulledCount: Int,
    /** How many the corner snap left. */
    val snappedCount: Int
) {
    /** The A\*'s own cell path length, 0 where it found nothing. */
    val pathCells: Int get() = search.path?.size ?: 0

    /** The settled line's own waypoint count, 0 where the pass failed. */
    val finalCount: Int get() = line.size
}
