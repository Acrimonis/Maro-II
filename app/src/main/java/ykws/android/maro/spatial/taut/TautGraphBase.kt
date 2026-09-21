package ykws.android.maro.spatial.taut

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint

/**
 * **The corridor's graph kept beside its terrain: every corner a line may bend on, and every line between
 * two of them that the water allows** (§19.6) — with the two ends left out.
 *
 * **Why it may be kept at all.** `TautGraph.build`'s pair loop reads **only the pair** — `allowed`,
 * `zones.limitSpans`, `legLimits.priceOf`, `zones.berthFraction` — and `cruiseSpeedKn` enters once, in
 * [LegLimits]. So the corner-to-corner edge set is a function of the **terrain**, the **box**, the
 * **zones** and the **pace**, and never of the ends: a drag moves the aim by metres while the corridor is
 * kilometres wide, and every edge between two corners is the same edge for the new aim as for the old.
 * That is the licence [licenses] states, and this class is the store the engine keeps one of.
 *
 * **What it costs and what it buys.** [of] is the corner harvest and the quadratic pair scan — the O(N²)
 * half, paid once for a terrain and then kept. [over] is the other half, and it does two things: it adds
 * the two ends and scans **their rows alone** — O(N) for rows where that scan is O(N²) — and it
 * **assembles** the graph, `finish()` copying the sink's arrays out, indexing every edge by its pair and
 * building the adjacency, an O(V + E) rebuild **every** graph pays on its way out, cold or kept. A reuse
 * therefore pays the rows *plus* that assembly rather than the rows alone, and it is [of] — never [over] —
 * that the keeping removes. Nothing else changes: the terrain's identity carries the box, the zones and
 * the world's generation that the corners were harvested under, so a base kept for a terrain is a base
 * whose own answers are still the water's answers.
 *
 * **The reused graph is the cold graph, field for field**, and that is the whole reason the guard can be
 * an equality rather than a tolerance. The vertex list is the same because the ends are added first in
 * both and the corners follow in their own order — with the two corners that stand **on** an end dropped,
 * which is what the build's own dedupe does to them; the edge list is the same because the ends' rows are
 * scanned in the order a cold build scans them and the kept edges follow in the order they were created,
 * so an edge carries the index a cold build would have given it; and the numbers each edge carries were
 * computed from the pair alone, under this licence, by the very arithmetic both call sites share
 * ([EdgeSink]). A differing line would therefore be a defect in this class rather than a property of
 * keeping anything.
 *
 * **It is read by whoever is running**, like the terrain beside it (§19.4's rule 2): a search and the
 * cancelled, unjoined predecessor it overlaps with share the base, and every field here is a `val` or an
 * array never written after construction, so the sharing is read-only by construction rather than by
 * convention. The `@Volatile` entry on the engine publishes it whole.
 */
internal class TautGraphBase private constructor(

    /** The terrain this base's corners were harvested over — the licence's first half. */
    private val terrain: TautTerrain,

    /** The pace the kept edges were priced at — the licence's second half, `cruiseSpeedKn`'s one home. */
    private val cruiseSpeedKn: Double,

    /** Every traversable corner, in the build's own order — the graph vertex `position + 2`. */
    private val corners: List<RoutePoint>,

    /**
     * How many of those came from the obstacles rather than from a zone's or a hole's own ring — the raw
     * harvest's own prefix of [corners], read by [over] to report the graph's count of those **still
     * standing after the ends' own dedupe** rather than carried to the graph as the harvest left it.
     */
    private val cornerCount: Int,

    /** The limit in force at each corner, in [corners]' order — the corner's own reference speed. */
    private val cornerLimitKn: DoubleArray,

    /** Each corner's vertex key, in [corners]' order — read by [over] for the ends' own dedupe. */
    private val cornerKeys: LongArray,

    /** The corner-to-corner edges, in the build's own order: `edgeFrom` is always the lower corner. */
    private val edgeFrom: IntArray,
    private val edgeTo: IntArray,
    private val edgeLengthM: DoubleArray,
    private val edgePriceSec: DoubleArray,
    private val edgeZone: IntArray,
    private val edgeBerthFraction: DoubleArray,

    /** How many vertex pairs the corner scan examined — counted into a phase only where it built this. */
    private val cornerPairs: Int,

    /** The graph's own pricer, built once with the base and published by the graph it answers. */
    private val pricing: LegLimits
) {

    /**
     * **Whether this base may answer for [terrain] at [cruiseSpeedKn]** — the keeping's whole licence
     * (§19.6).
     *
     * **Identity** on the terrain rather than a key compare, and deliberately: an instance *is* the answer
     * its own harvest gave, so the same instance carries the same box, the same zones, the same band, the
     * berth and shore offset that shaped it and the world generation it was harvested under. A key compare
     * would license a base harvested for one box against a terrain rebuilt for another that happens to
     * carry equal numbers, which is a licence on a coincidence; a new instance is a new set of obstacles,
     * and the corners this base holds are then not the corners it was priced on.
     *
     * The pace is the second half because the edges' prices are **times**: a base priced at 28 kn answers
     * at 28 kn, and reusing it at another pace would hand the search seconds belonging to a boat that is
     * not there. The box and the zones are inside the terrain's identity and need no clause of their own.
     */
    fun licenses(terrain: TautTerrain, cruiseSpeedKn: Double): Boolean =
        this.terrain === terrain && this.cruiseSpeedKn == cruiseSpeedKn

    /**
     * **The graph over this base for one pair of ends** — the start, the resolved aim, the rows that leave
     * them, and the kept corners and edges between.
     *
     * The vertex set is the **bends a taut line can take**: the start and the aim, then every corner of
     * every wall that protrudes into the water, every such corner of a zone's outer ring, and every such
     * corner of a **hole** — a hole is water, so threading one is legal and its corners are where a line
     * through it bends. A corner where an obstacle bends away from the water is not a node, because a line
     * touching it would run inside the obstacle; and a corner standing **on** an end is not a node either,
     * the build adding its two ends first and deduping them, which is the one place the corner set depends
     * on the ends and the reason [cornerKeys] exists.
     *
     * @param reused whether this base was **kept** from an earlier search rather than built by the caller.
     *        It changes exactly one thing: the pair count the caller's phase reports, the base's own pairs
     *        having been paid in the search that ran [of]. The geometry is the same either way.
     * @param cancelCheck asked **per row** — once for every pair offered from the start's row and from
     *        the aim's, and once for every kept edge copied — so an abandoned build stops inside the
     *        assembly rather than only before it.
     */
    fun over(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint,
        reused: Boolean = false,
        cancelCheck: () -> Unit = {}
    ): TautGraph {
        val obstacles = terrain.obstacles
        val zones = terrain.zones
        val frame = obstacles.frame
        val box = terrain.box

        val resolvedAim = if (obstacles.traversable(aim.latitude, aim.longitude)) {
            aim
        } else {
            TautGraph.nearestTraversable(obstacles, aim, box) ?: aim
        }
        val destinationMoved = resolvedAim != aim

        // The corner list a cold build would keep is this one minus the corners standing on an end: the
        // build adds its two ends first, so a corner whose key is one of theirs is not a vertex of its own.
        // The ends themselves are added whatever their keys — a zero-length aim is two vertices, which is
        // what the build has always answered.
        val startKey = vertexKey(start)
        val aimKey = vertexKey(resolvedAim)
        val global = IntArray(corners.size) { -1 }
        var vertexCount = 2
        for (i in corners.indices) {
            val key = cornerKeys[i]
            if (key == startKey || key == aimKey) continue
            global[i] = vertexCount
            vertexCount++
        }
        val vertices = ArrayList<RoutePoint>(vertexCount)
        vertices.add(start)
        vertices.add(resolvedAim)
        for (i in corners.indices) if (global[i] >= 0) vertices.add(corners[i])

        // **The corner's own reference speed, taken once per vertex at the build** — recomputed for the
        // two ends, whose standing place is the only thing about them that is new; the corners' own were
        // read once and are a function of the terrain.
        val vertexLimits = DoubleArray(vertexCount)
        vertexLimits[0] = limitInForceAt(world, zones, start)
        vertexLimits[1] = limitInForceAt(world, zones, resolvedAim)
        for (i in corners.indices) {
            val vertex = global[i]
            if (vertex >= 0) vertexLimits[vertex] = cornerLimitKn[i]
        }

        // **The two ends' rows, in the order a cold build examines them** — the pair of the two ends, then
        // every other vertex from the start, then every other vertex from the aim — and the kept edges
        // after them, in the order they were created. An edge's index is therefore the index a cold build's
        // own creation order would have given it, which is what lets the two graphs be compared whole.
        val sink = EdgeSink(obstacles, zones, pricing, frame)
        var examined = if (reused) 0 else cornerPairs
        cancelCheck()
        for (j in 1 until vertexCount) {
            cancelCheck()
            examined++
            sink.offer(0, j, vertices[0], vertices[j])
        }
        for (j in 2 until vertexCount) {
            cancelCheck()
            examined++
            sink.offer(1, j, vertices[1], vertices[j])
        }
        for (edge in edgeFrom.indices) {
            cancelCheck()
            val from = global[edgeFrom[edge]]
            val to = global[edgeTo[edge]]
            if (from < 0 || to < 0) continue
            sink.append(
                from, to, edgeLengthM[edge], edgePriceSec[edge], edgeZone[edge], edgeBerthFraction[edge]
            )
        }
        // **The corners that are vertices** — the harvest's own minus the ones standing on an end, so the
        // count the graph reports says how many of *its* vertices are harvested corners rather than how
        // many the harvest found. The end-dedupe above is the only thing that can remove one.
        var cornersKept = 0
        for (i in 0 until cornerCount) if (global[i] >= 0) cornersKept++
        return finish(
            vertices = vertices,
            vertexLimits = vertexLimits,
            verticesExamined = examined,
            cornersKept = cornersKept,
            destinationMoved = destinationMoved,
            zones = zones,
            sink = sink
        )
    }

    /** The finished graph — the arrays, the pair index and the adjacency, assembled once. */
    private fun finish(
        vertices: List<RoutePoint>,
        vertexLimits: DoubleArray,
        verticesExamined: Int,
        /** How many of [vertices] are harvested corners — the base's own count minus the ends' dedupe. */
        cornersKept: Int,
        destinationMoved: Boolean,
        zones: TautZoneSet,
        sink: EdgeSink
    ): TautGraph {
        val edgeCount = sink.from.size
        val from = sink.from.toIntArray()
        val to = sink.to.toIntArray()
        val byPair = HashMap<Long, Int>(edgeCount * 2)
        for (edge in 0 until edgeCount) {
            byPair[pairKey(from[edge], to[edge])] = edge
        }
        // The adjacency is the edges in creation order, and each vertex's block holds the edges it is the
        // **lower** end of first and then the ones it is the higher end of — the order the build has always
        // published, so a search walking `edgesFrom` walks the same list it walked before any of this.
        val vertexCount = vertices.size
        val degree = IntArray(vertexCount)
        for (edge in 0 until edgeCount) {
            degree[from[edge]]++
            degree[to[edge]]++
        }
        val nodeOffset = IntArray(vertexCount + 1)
        for (vertex in 0 until vertexCount) nodeOffset[vertex + 1] = nodeOffset[vertex] + degree[vertex]
        val nodeEdges = IntArray(nodeOffset[vertexCount])
        val cursor = IntArray(vertexCount) { nodeOffset[it] }
        for (edge in 0 until edgeCount) nodeEdges[cursor[from[edge]]++] = edge
        for (edge in 0 until edgeCount) nodeEdges[cursor[to[edge]]++] = edge
        return TautGraph(
            vertices = vertices,
            edgeCount = edgeCount,
            cornerCount = cornersKept,
            candidatePairs = verticesExamined,
            startIndex = 0,
            aimIndex = 1,
            destinationMoved = destinationMoved,
            zones = zones,
            pricing = pricing,
            bandSegmentCount = terrain.band?.segmentCount ?: 0,
            nodeOffset = nodeOffset,
            nodeEdges = nodeEdges,
            edgeFrom = from,
            edgeTo = to,
            edgeLengthM = sink.lengthM.toDoubleArray(),
            edgePriceSec = sink.priceSec.toDoubleArray(),
            vertexLimitKn = vertexLimits,
            edgeZone = sink.zone.toIntArray(),
            edgeBerthFraction = sink.berthFraction.toDoubleArray(),
            edgeByPair = byPair
        )
    }

    companion object {

        /**
         * **The cold entrance: the corner harvest and the quadratic scan** (§19.6).
         *
         * This is the half of the build the ends have no part in — every traversable corner of the walls,
         * the zone rings and the holes, deduped by vertex key, and every pair of them the water allows,
         * priced as it is admitted. It is the O(N²) work the phone spends 4 816 ms on for the acceptance
         * pair's corridor, and the phase that a reuse removes.
         *
         * [cancelCheck] is asked per outer corner, so an abandoned build stops inside the scan rather than
         * after it.
         */
        fun of(
            world: TautWorld,
            terrain: TautTerrain,
            cruiseSpeedKn: Double,
            cancelCheck: () -> Unit = {}
        ): TautGraphBase {
            val obstacles = terrain.obstacles
            val zones = terrain.zones
            val frame = obstacles.frame
            val legLimits = LegLimits(world, zones, terrain.band, frame, cruiseSpeedKn)

            // One node per distinct corner: two obstacles that meet at a point share it, and a shared
            // corner counted twice would double the edges that leave it without adding a single bend.
            val corners = ArrayList<RoutePoint>(obstacles.corners.size + 8)
            val keys = ArrayList<Long>(obstacles.corners.size + 8)
            val seen = HashSet<Long>(obstacles.corners.size * 2)
            var cornerCount = 0
            for (corner in obstacles.corners) {
                if (!obstacles.traversable(corner.latitude, corner.longitude)) continue
                val key = vertexKey(corner)
                if (!seen.add(key)) continue
                corners.add(corner)
                keys.add(key)
                cornerCount++
            }
            for (zone in zones.all()) {
                for (corner in zone.corners) {
                    val key = vertexKey(corner)
                    if (!seen.add(key)) continue
                    corners.add(corner)
                    keys.add(key)
                }
            }

            val limits = DoubleArray(corners.size) { limitInForceAt(world, zones, corners[it]) }
            val sink = EdgeSink(obstacles, zones, legLimits, frame)
            var candidatePairs = 0
            for (i in corners.indices) {
                cancelCheck()
                for (j in i + 1 until corners.size) {
                    candidatePairs++
                    sink.offer(i, j, corners[i], corners[j])
                }
            }
            return TautGraphBase(
                terrain = terrain,
                cruiseSpeedKn = cruiseSpeedKn,
                corners = corners,
                cornerCount = cornerCount,
                cornerLimitKn = limits,
                cornerKeys = keys.toLongArray(),
                edgeFrom = sink.from.toIntArray(),
                edgeTo = sink.to.toIntArray(),
                edgeLengthM = sink.lengthM.toDoubleArray(),
                edgePriceSec = sink.priceSec.toDoubleArray(),
                edgeZone = sink.zone.toIntArray(),
                edgeBerthFraction = sink.berthFraction.toDoubleArray(),
                cornerPairs = candidatePairs,
                pricing = legLimits
            )
        }
    }
}

/**
 * **The pair key, one spelling for both sides** — the graph's own lookup and the index the build fills
 * from it read this function rather than the same shift and or written twice, which is exactly how the
 * two would come to disagree about which key a pair has.
 *
 * The pair is **ordered here rather than by the caller**: the lower index takes the high half whichever
 * order the ends are named in, so a pair has one key and no call site can key one edge twice.
 */
internal fun pairKey(a: Int, b: Int): Long {
    val low = if (a < b) a else b
    val high = if (a < b) b else a
    return (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
}

/**
 * **What an edge is, priced once** — the one home the corner scan and the ends' rows share (§19.6).
 *
 * A leg becomes an edge when the water allows it, and it is priced the moment it is admitted: **one** read
 * of the zone's intervals per candidate pair, which the price, the rule's own entry test and the margin's
 * exclusion all read (item 5 of the level above); the leg's time summed over its own limit regions — the
 * number the search's step and the drawn clock both charge (§17 item 1); the zone whose interior the leg
 * enters; and the berth's own fraction for its closest approach.
 *
 * It is one class rather than a block in each caller because a kept corner-to-corner edge and an edge
 * leaving an end are the same kind of object: a second copy of this arithmetic is how the two would come
 * to be priced differently, which is the defect the two graphs could never be compared across.
 */
private class EdgeSink(
    private val obstacles: TautObstacles,
    private val zones: TautZoneSet,
    private val legLimits: LegLimits,
    private val frame: Frame
) {

    val from = ArrayList<Int>()
    val to = ArrayList<Int>()
    val lengthM = ArrayList<Double>()
    val priceSec = ArrayList<Double>()
    val zone = ArrayList<Int>()
    val berthFraction = ArrayList<Double>()

    /**
     * The pair's own edge, priced and stored — a pair the water refuses is simply dropped, there being no
     * edge to hand back and no caller that would read one.
     */
    fun offer(i: Int, j: Int, a: RoutePoint, b: RoutePoint) {
        if (!TautGraph.allowed(obstacles, a, b)) return
        val pa = frame.pt(LatLng(a.latitude, a.longitude))
        val pb = frame.pt(LatLng(b.latitude, b.longitude))
        val legM = metresBetween(a, b)
        val zoneSpans = zones.limitSpans(pa, pb)
        append(
            i = i,
            j = j,
            legM = legM,
            price = legLimits.priceOf(a, b, zoneSpans),
            owner = if (zoneSpans.isEmpty()) -1 else zoneSpans.first().zone,
            berth = zones.berthFraction(pa, pb, legM, obstacles.berthM, zoneSpans)
        )
    }

    /** The same, for numbers already computed — a kept edge's own, copied rather than re-derived. */
    fun append(i: Int, j: Int, legM: Double, price: Double, owner: Int, berth: Double) {
        from.add(i)
        to.add(j)
        lengthM.add(legM)
        priceSec.add(price)
        zone.add(owner)
        berthFraction.add(berth)
    }
}
