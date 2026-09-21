package ykws.android.maro.route

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZoneQuery
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the cost function needs to know about the world — the single seam that keeps the
 * objective testable without the app's repositories, and the read-only window the epic's
 * `AppSpatialAdapter` would implement at runtime.
 */
interface RouteContext {

    fun isWater(lat: Double, lon: Double): Boolean

    /** Distance to the nearest coastline (m). */
    fun distanceToCoastM(lat: Double, lon: Double): Double

    /** Speed zones containing the point, most restrictive first (§2, `SpeedZoneIndex.query`). */
    fun zoneQuery(lat: Double, lon: Double): SpeedZoneQuery

    /** Depth below datum (m, positive down) — NaN where there is none. */
    fun depthM(lat: Double, lon: Double): Float

    /** The exclusion set's own verdict, standoff included: true where a leg may not pass. */
    fun isForbidden(lat: Double, lon: Double): Boolean

    /**
     * Fraction of the standoff still free at the point — 1.0 well clear, 0.0 on the excluded set.
     * Read only when D3 prices the standoff instead of walling it off (§11.2).
     */
    fun exclusionStandoffRatio(lat: Double, lon: Double): Double

    /** The raw gate before any standoff: land, NoData, water shallower than the minimum (§4). */
    fun isBlocked(lat: Double, lon: Double): Boolean

    /** Whether the point sits inside the corridor at all — the bound §10.5 requires be stated. */
    fun isInsideCorridor(lat: Double, lon: Double): Boolean

    companion object {
        /** Width of the coastal band the French 5 kn default stands for (m). */
        const val BAND_300_M = 300.0
    }
}

/**
 * The objective, as plan §3 states it: time-primary, `cost(edge) = length ÷ effectiveSpeed`, with the
 * multipliers of objectives B and C folded in as prices rather than as extra gates.
 *
 * - **A** is the engine: the leg's time is integrated along the leg, so a zone slows the route
 *   exactly as much as it actually costs.
 * - **B**'s zone aversion is [`RouteConfig.zoneAversionK`] — extra seconds traded per second inside a
 *   zone — and the shallow band's price is [`RouteConfig.shallowBandPrice`], the metres-per-metre of
 *   §4. Both slide the same single objective between "fastest" and "straightest and legal".
 * - **C**'s tiers are feasibility, not cost: they live in the exclusion mask and in
 *   [`RouteCostModel.isClear`], so the search can never trade a rock for a shorter path (§4).
 *
 * Sampling rather than polygon clipping is deliberate — it is exact enough for a 25 m grid, needs no
 * new geometry engine, and gives the profile the same numbers the search used, which is what §12.4
 * asks for.
 */
class RouteCostModel(
    val config: RouteConfig,
    private val context: RouteContext,
) {

    /** Most restrictive limit at a point (kn), or null in free water. The 300 m band counts when D4 says so. */
    fun limitKnAt(lat: Double, lon: Double): Double? {
        val query = context.zoneQuery(lat, lon)
        var limit = query.mostRestrictiveSpeedKn
        if (config.band300IsZone &&
            context.isWater(lat, lon) &&
            context.distanceToCoastM(lat, lon) <= RouteContext.BAND_300_M
        ) {
            limit = if (limit == null) config.band300LimitKn else min(limit, config.band300LimitKn)
        }
        return limit
    }

    /** Effective speed at a point: `min(cruise, zone limit)` (§3, objective A). */
    fun speedMpsAt(lat: Double, lon: Double): Double {
        val limit = limitKnAt(lat, lon) ?: return config.cruiseMps
        return min(config.cruiseMps, limit * RouteConfig.KNOT_TO_MPS)
    }

    /**
     * Time multiplier at a point — the prices of §3 and §11.2. 1.0 in free, deep, clear water.
     *
     * The zone term is the aversion itself, the shallow term is §4's 2–3 m band, and the standoff
     * term is a band only where the standoff is *priced*: around a zone always (a zone stays
     * crossable), around the exclusion geometry only when D3 chooses the soft variant.
     */
    fun penaltyAt(lat: Double, lon: Double): Double {
        var penalty = 1.0

        if (config.shallowBandPrice > 0.0 && config.preferredDepthM > config.minDepthM) {
            val depth = context.depthM(lat, lon)
            if (!depth.isNaN() && depth < config.preferredDepthM) {
                val shortfall = (config.preferredDepthM - depth) / (config.preferredDepthM - config.minDepthM)
                penalty += config.shallowBandPrice * shortfall.coerceIn(0.0, 1.0)
            }
        }

        val query = context.zoneQuery(lat, lon)
        if (query.insideAnyZone && config.zoneAversionK > 0.0) {
            penalty += config.zoneAversionK
        } else if (config.zoneStandoffPrice > 0.0 && config.standoffM > 0.0) {
            val d = query.distanceToBoundaryM
            if (d != null && d >= 0.0 && d < config.standoffM) {
                penalty += config.zoneStandoffPrice * (1.0 - d / config.standoffM)
            }
        }

        if (!config.hardStandoff && config.zoneStandoffPrice > 0.0) {
            val ratio = context.exclusionStandoffRatio(lat, lon)
            if (ratio < 1.0) penalty += config.zoneStandoffPrice * (1.0 - ratio)
        }

        return penalty
    }

    /** Time to run the straight line [a]→[b] (s), sampled at `config.costSampleStepM`. */
    fun legTimeS(a: LatLng, b: LatLng): Double {
        val length = SpatialOperations.haversine(a, b)
        if (length <= 0.0) return 0.0
        val samples = max(2, ceil(length / config.costSampleStepM).toInt())
        val step = length / samples
        var time = 0.0
        for (i in 0 until samples) {
            val p = interpolate(a, b, (i + 0.5) / samples)
            time += step / speedMpsAt(p.latitude, p.longitude) * penaltyAt(p.latitude, p.longitude)
        }
        return time
    }

    /**
     * Feasibility of a candidate edge: no sample sits in the forbidden set. This is the hard gate of
     * §4 — an impassable cell is not an expensive one — and it is why a smoothed or filleted polyline
     * is re-validated rather than trusted (§10.3 step 6).
     */
    fun isClear(a: LatLng, b: LatLng): Boolean {
        val length = SpatialOperations.haversine(a, b)
        if (length <= 0.0) return !context.isForbidden(a.latitude, a.longitude)
        val samples = max(2, ceil(length / config.clearanceStepM).toInt())
        for (i in 0..samples) {
            val p = interpolate(a, b, i.toDouble() / samples)
            if (context.isForbidden(p.latitude, p.longitude)) return false
        }
        return true
    }

    /** Metres of [a]→[b] spent inside each zone, keyed by the zone's own name. */
    fun legZoneDistanceM(a: LatLng, b: LatLng): Map<String, Double> {
        val length = SpatialOperations.haversine(a, b)
        if (length <= 0.0) return emptyMap()
        val samples = max(2, ceil(length / config.costSampleStepM).toInt())
        val step = length / samples
        val out = HashMap<String, Double>()
        for (i in 0 until samples) {
            val p = interpolate(a, b, (i + 0.5) / samples)
            for (zone in context.zoneQuery(p.latitude, p.longitude).allInsideZones) {
                out[zone.name] = (out[zone.name] ?: 0.0) + step
            }
        }
        return out
    }

    /** Total length of a polyline (m). */
    fun pathLengthM(points: List<LatLng>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += SpatialOperations.haversine(points[i], points[i + 1])
        return sum
    }

    /** Metres of a polyline spent inside each zone, keyed by zone name. */
    fun pathZoneDistanceM(points: List<LatLng>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (i in 0 until points.size - 1) {
            legZoneDistanceM(points[i], points[i + 1]).forEach { (name, m) ->
                out[name] = (out[name] ?: 0.0) + m
            }
        }
        return out
    }

    private fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng = LatLng(
        latitude = a.latitude + (b.latitude - a.latitude) * t,
        longitude = a.longitude + (b.longitude - a.longitude) * t,
    )
}

/** One straight leg between two graph vertices — the only thing the search walks. */
data class RouteEdge(
    val a: Int,
    val b: Int,
    val lengthM: Double,
    val timeS: Double,
) {
    val speedMps: Double get() = if (timeS <= 0.0) 0.0 else lengthM / timeS
}

/**
 * The corridor's visibility graph (§10.6): nodes are obstacle vertices, edges are mutually visible
 * pairs that clear the hard constraints.
 *
 * Directed edges are numbered `2·edge + side`, so the turn-aware search of §11.5 can carry the entry
 * direction as part of its state without copying the graph.
 */
class RouteGraph(
    val points: List<LatLng>,
    val edges: List<RouteEdge>,
) {

    /** `2·edge` is a→b, `2·edge + 1` is b→a. */
    val directedCount: Int = edges.size * 2

    val fromVertex: IntArray = IntArray(directedCount)
    val toVertex: IntArray = IntArray(directedCount)
    val outEdges: Array<IntArray>

    private val edgeIndex = HashMap<Long, Int>(edges.size * 2)

    init {
        val out = Array(points.size) { ArrayList<Int>(4) }
        for ((i, e) in edges.withIndex()) {
            fromVertex[2 * i] = e.a; toVertex[2 * i] = e.b
            fromVertex[2 * i + 1] = e.b; toVertex[2 * i + 1] = e.a
            out[e.a].add(2 * i)
            out[e.b].add(2 * i + 1)
            edgeIndex[edgeKey(e.a, e.b)] = i
        }
        outEdges = Array(points.size) { out[it].toIntArray() }
    }

    private fun edgeKey(i: Int, j: Int): Long =
        minOf(i, j).toLong() * points.size.toLong() + maxOf(i, j).toLong()

    /** The edge joining two vertices, or null when the prune dropped it. */
    fun edgeBetween(i: Int, j: Int): RouteEdge? = edgeIndex[edgeKey(i, j)]?.let { edges[it] }

    val vertexCount: Int get() = points.size

    val edgeCount: Int get() = edges.size

    /** Length of a directed edge (m). */
    fun directedLengthM(directed: Int): Double = edges[directed / 2].lengthM

    /** Time to run a directed edge at its own speed (s) — corner costs are the search's business. */
    fun directedTimeS(directed: Int): Double = edges[directed / 2].timeS
}

/**
 * Builds the graph: corridor vertices, then the edges that are visible, clear and worth carrying.
 *
 * Candidate pairs are the [`RouteConfig.maxEdgeNeighbours`] nearest neighbours of each vertex, every
 * pair closer than [`RouteConfig.localEdgeM`], and both endpoints against every vertex — a stated
 * prune, because the full `V²` visibility graph is what makes this class unusable at corridor scale.
 * Its cost is that a taut path needing a jump longer than its ten nearest neighbours is not found;
 * the harness prints the straight-line ratio so that risk is visible in the numbers rather than
 * hidden in the code.
 */
class RouteGraphBuilder(
    private val config: RouteConfig,
    private val context: RouteContext,
    private val obstacles: RouteObstacles,
    private val cost: RouteCostModel,
) {

    fun build(start: LatLng, end: LatLng): RouteGraph {
        val points = ArrayList<LatLng>(obstacles.vertices.size + 2)
        points.add(start)
        points.add(end)
        for (v in obstacles.vertices) {
            if (context.isForbidden(v.latitude, v.longitude)) continue
            if (v == start || v == end) continue
            points.add(v)
        }

        val candidates = candidatePairs(points)
        val edges = ArrayList<RouteEdge>(candidates.size)
        for ((i, j) in candidates) {
            val a = points[i]
            val b = points[j]
            if (obstacles.index.blocked(a, b)) continue
            if (!cost.isClear(a, b)) continue
            val length = SpatialOperations.haversine(a, b)
            if (length <= 0.0) continue
            edges.add(RouteEdge(i, j, length, cost.legTimeS(a, b)))
        }
        return RouteGraph(points, edges)
    }

    /** The pruned candidate set, deduplicated and ordered for determinism. */
    private fun candidatePairs(points: List<LatLng>): List<Pair<Int, Int>> {
        val n = points.size
        if (n < 2) return emptyList()
        val seen = HashSet<Long>()

        fun add(i: Int, j: Int) {
            if (i == j) return
            val lo = min(i, j)
            val hi = max(i, j)
            seen.add(lo.toLong() * n.toLong() + hi.toLong())
        }

        for (i in 0 until n) {
            val nearest = (0 until n)
                .filter { it != i }
                .sortedWith(compareBy({ SpatialOperations.haversine(points[i], points[it]) }, { it }))
                .take(config.maxEdgeNeighbours)
            nearest.forEach { add(i, it) }
            if (config.localEdgeM > 0.0) {
                for (j in i + 1 until n) {
                    if (SpatialOperations.haversine(points[i], points[j]) <= config.localEdgeM) add(i, j)
                }
            }
        }
        // Both endpoints reach every vertex, so the corridor cannot be cut off by the neighbour prune.
        for (i in 0 until n) {
            add(0, i)
            add(i, 1)
        }

        return seen.sorted().map { key -> Pair((key / n).toInt(), (key % n).toInt()) }
    }
}
