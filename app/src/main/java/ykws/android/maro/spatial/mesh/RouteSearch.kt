package ykws.android.maro.spatial.mesh

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RouteFillet
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device A* over the prebaked navigation mesh, house-prefixed like `CoastlineSpatialIndex`.
 *
 * **This is the mesh engine's own search, not the app's route.** It is reached through
 * [`MeshRouteEngine`], the one door [`ykws.android.maro.spatial.RouteEngine`] opens, and nothing
 * outside this package may depend on it: every other engine would answer the same question from its
 * own path, and a caller that knew this class would have to know the fabric it walks.
 *
 * Everything it walks is the packed array form: no per-node or per-edge object is created, and the
 * scratch arrays are allocated once and reused, so a re-plan is arithmetic rather than garbage.
 *
 * **The two ends are not treated alike.** The corridor is the mesh's own box, and a position
 * outside it is reported rather than dragged onto the nearest node inside it. Within the box the
 * start keeps the radius gate — the boat must actually stand on covered water. The aim does not:
 * it is bounded by the box alone, because a destination that resolves onto land or into another
 * stretch of water moves to the closest point of the boat's own stretch however far that point
 * is. The rule the mesh is built to serve is that both ends of a route sit in one stretch.
 *
 * **The cost of an edge** is `lengthM ÷ min(cruiseSpeed, liveLimit)`, both in metres per second.
 * The limit is resolved from [queries] per search and cached per edge, so a corrected regulation or
 * a changed cruise speed never re-bakes anything; an edge marked in band is priced at the band's own
 * live limit even when both of its ends sit outside the band, which is what prices a crossing
 * exactly.
 *
 * **A priced zone's interior is not traversable.** A node standing inside a priced speed zone prices
 * every edge it carries at [`FORBIDDEN_LIMIT_KN`], so the speed test below drops those edges and the
 * zone's interior leaves the graph: a crossing stops being an outcome of a weight and becomes an
 * invariant, and no factor needs sweeping. **The 300 m band is never forbidden** — it is a separate
 * clause of the same seam, priced by its own live limit, and the line already lives inside it for
 * much of a coastal route. **The exception is per zone, against the nodes the search actually
 * resolved, and it covers both ends**: a boat armed inside a zone, or aimed into one, must still be
 * able to leave or arrive, so the zones the two resolved nodes stand in are excepted once per search
 * and only for that zone.
 *
 * **And a crossing is a fallback, not a failure.** The traversal rule is tried first; where no way
 * around exists the search runs again with the zones priced as they always were, takes the crossing,
 * and **says so** — the result carries the zones it entered, so a forced crossing is reported rather
 * than presented as an ordinary route. Two runs rather than a second objective in the heap, because
 * the heap is keyed on one `Double`.
 *
 * **A turn costs time too**, added to the edge cost in the same seconds: the fillet's own arc at the
 * vertex, priced as its **arc-minus-chord**. That is a price and not the geometry — the arc cuts the
 * corner's two legs by `2·R·tan(θ / 2)` and gives back only `R·θ`, so the drawn line is *shorter*
 * than the corner it replaces — and it is derived from that arc's radius
 * [`AppConfig.routeTurnLateralAccelMps2`]: see [turnPenaltySec], and [`RouteTurnGeometry`] for the
 * angle, the radius and the cutback the fillet, this price and the bake-time probe all read. It is a
 * price on the **shape** of the path and never the shape itself: a bend is made dearer, so of two
 * lines the straighter is chosen, but the vertices stay the mesh's own and the chain is therefore as
 * node-by-node as the fabric is. A price is not a geometry, and the study's fourth round is where
 * those two were confused.
 *
 * **And the chain is straightened before it is rounded.** That is [`RouteShortcut`], run here on the
 * search's own chain, between the collinear merge and the fillet: it keeps only the vertices the
 * water forces, tested by the bake's own kept triangles ([`RouteMeshContainment`]) together with the
 * boat's own stretch — the exact test the study's first, sampled string-pull could not make. It runs
 * inside the search's own result construction, so the preview, the confirmed route and the saved
 * course are one line, and what it returns is the chain the fillet then rounds.
 *
 * **Every pass re-prices what it changes.** The drawn line is produced by passes that used to ask no
 * price at all: the merge dropped a vertex, the shortcut cut a corner and the fillet rounded one, and
 * each of them changed the line the plan is reported on without changing the seconds it reported. So
 * each of them is admissible-only — a candidate is taken **only** when its cost, at the limits in
 * force over the chain edges it replaces, is no worse than the chain it replaces — computed by the
 * one home that also reads the plan's clock, [`RoutePlanTiming`]. What that buys is an invariant
 * rather than a review: *the drawn line's own cost must never exceed the seconds the search
 * accumulated*, asserted in [buildSuccess] where every run reaches it, so a pass that changes the
 * line without re-pricing it is caught rather than shipped as a route that looks fast and is not.
 *
 * **And the price a candidate is admitted on is the price of the line it leaves, turns included.** A
 * pass that moves a corner moves the turn the clock will charge there, so the quantity every
 * admission compares is the one [`RoutePlanTiming.drawnLegSeconds`] reads off the drawn line — the
 * legs **and** the turn at each corner the candidate rotates — rather than the legs alone. A
 * candidate whose legs are cheaper but whose rotated corners are dearer is refused, which is what
 * makes the invariant above a property of the code rather than a hope about the geometry.
 *
 * **A breach costs a smoother line, never the route.** Where the invariant does fire the assembly
 * falls back to the **raw node chain** — no merge, no shortcut, no fillet — warns, and answers with a
 * line whose clock is the search's own accumulation. A defect in a pass is worth fixing, not worth
 * taking the boat's route off the screen for; see [buildSuccess] and
 * [`RouteMeshDetails.rawChainFallback`].
 *
 * **A near miss costs too, and never blocks.** Passing inside the zone berth
 * ([`AppConfig.routeZoneBerthM`]) multiplies the leg's own time by a factor rising with how deep
 * inside the berth it lies, never past [BERTH_MAX_PRICE] — see [berthPenaltySec]. It is a **price,
 * not a wall**: a zone stays crossable at its limit where no way around exists, a destination inside
 * one stays reachable, and a passage narrower than twice the berth stays open, none of which a berth
 * baked into the mesh could offer. The bake's own `Tuning.zoneClearanceM` is that other mechanism,
 * shipped at zero.
 *
 * **A price buys preference, never minutes.** The search carries two accumulators: the priced cost it
 * minimises, and the route's own time — the leg's real seconds plus the turn's, with the berth's
 * courtesy left out of it. That second one is the **ceiling** the drawn line's own cost is asserted
 * against; the plan's own duration is read off the drawn line by [`RoutePlanTiming`], so a route that
 * hugs a zone is chosen more reluctantly but is never *reported* as slower than it is.
 *
 * **The heuristic** is the straight-line distance to the resolved destination divided by the cruise
 * speed. It is admissible by construction and must stay that way: an edge is never traversed faster
 * than the cruise speed, so dividing by the cruise speed can never *overstate* the time left.
 * Speeding the estimate up by dividing by something larger would silently break A*'s optimality.
 *
 * **Cancellation** is the caller's: [search] calls [ensureActive] on every node it expands, so a
 * flung map drops a search in flight. Cooperative cancellation cannot stop a walk mid-expansion, and
 * a caller cancels its predecessor **without joining it** — the UI must never wait on a search — so
 * **one search is *started* at a time and never one *running* at a time**: the predecessor can still
 * be finishing the expansion it was in when the next search begins. The scratch arrays above are
 * shared by those two runs, and they are made safe the way every run is: [run] re-initialises each
 * of them before it reads one, and each cached lookup carries the run's own stamp, so a run only
 * ever trusts a value it wrote itself.
 */
internal class RouteSearch(
    private val mesh: RouteMeshArrays,
    private val queries: RoutePointQueries,
    /**
     * Beyond this distance (m) from any mesh node the **start** is reported outside the mesh. The
     * aim is not bounded by it: it resolves into the boat's own stretch however far away it is.
     */
    private val snapRadiusM: Double = DEFAULT_SNAP_RADIUS_M,
    /**
     * The zone berth (m) each search is priced at, read live from its one home
     * ([AppConfig.routeZoneBerthM]) unless a caller names its own.
     *
     * It is a provider rather than a value for one reason: a reading of the mechanism has to be able
     * to price a berth at the distance it exists for without turning the shipped key on.
     */
    private val zoneBerthM: () -> Double = { AppConfig.routeZoneBerthM.toDouble() },
    /**
     * The comfort cap (m/s²) each search is priced and smoothed at, read live from its one home
     * ([AppConfig.routeTurnLateralAccelMps2]) unless a caller names its own.
     *
     * A provider for the same reason as the berth: the sweep that chose the shipped value has to be
     * able to price one search at a cap the app does not ship.
     */
    private val turnLateralAccelMps2: () -> Double = { AppConfig.routeTurnLateralAccelMps2.toDouble() },
    /**
     * What a leg standing on a zone's own boundary pays, as a multiple of its own time —
     * [BERTH_MAX_PRICE] shipped. The price fades linearly to one at the berth's edge, and a value at
     * or below one is no price at all, which is how a reading takes the berth out of the picture.
     */
    private val berthMaxPrice: Double = BERTH_MAX_PRICE,
    /**
     * Whether the chain is straightened by [`RouteShortcut`] before the fillet rounds it.
     *
     * A switch rather than a constant for the same reason the berth and the cap are providers: the
     * reading that measures what the pass bought has to take the **same** search with it off — that
     * line is the one the device draws today — where comparing two revisions would compare the bake,
     * the fabric and everything else the two revisions moved as well. The app never sets it.
     */
    private val shortcutPass: Boolean = true,
    /**
     * Where a warning goes. It exists for one case — the invariant's fallback, which is a defect in a
     * pass and not a failure of the route (see [buildSuccess]) — and it is a parameter for the same
     * reason the berth and the cap are providers: the reading that has to prove the fallback never
     * fires in practice must be able to *hear* it when it does, and a JVM test cannot call
     * `android.util.Log` at all.
     */
    private val warn: (String) -> Unit = { message -> android.util.Log.w(TAG, message) }
) {

    private val nodeCount = mesh.nodeCount
    private val edgeCount = mesh.edgeCount

    /**
     * The mesh's own containment test, built on first use.
     *
     * Lazy on purpose: the index costs a pass over every kept triangle, so it is paid by the first
     * search that asks — on the search's own dispatcher — rather than when this object is built on
     * the frame the route mode opens. One search lives per mesh, so the index is built once.
     */
    private val containment by lazy { RouteMeshContainment(mesh) }

    // Scratch — one set per search would be a large allocation on every re-plan.
    private val gScore = DoubleArray(nodeCount)
    private val cameFrom = IntArray(nodeCount)
    private val cameEdge = IntArray(nodeCount)
    private val closed = BooleanArray(nodeCount)
    private var heapNode = IntArray(2 * edgeCount + 16)
    private var heapKey = DoubleArray(heapNode.size)
    private var heapSize = 0

    // Per-edge effective limit, valid only for the search that stamped it.
    private val edgeLimitKn = DoubleArray(edgeCount)
    private val edgeLimitStamp = IntArray(edgeCount)

    // Per-node distance to the nearest zone boundary, same stamp discipline as the limits: the zone
    // layer's nearest-boundary query is not free, and every edge asks about both of its ends.
    private val nodeZoneClearanceM = DoubleArray(nodeCount)
    private val nodeClearanceStamp = IntArray(nodeCount)

    // The zone each node stands in, stamped like the limits and asked once per node per run: it is
    // the one live zone answer, and it carries both the price the node is bound by and the identity
    // the traversal rule keys on. Caching it here is what keeps that rule off the hot path's query
    // count — and what keeps a zone query off every sample of a candidate shortcut.
    private val nodeZone = arrayOfNulls<PricedZoneRef>(nodeCount)
    private val nodeZoneStamp = IntArray(nodeCount)

    // The traversal verdict per kept triangle, stamped per run: a triangle lies wholly inside or
    // wholly outside a zone, because a zone ring is a triangulation constraint, so a sample's own
    // triangle answers for the sample. See [forbiddenTriangle].
    private val triangleForbidden = BooleanArray(mesh.triangleCount)
    private val triangleStamp = IntArray(mesh.triangleCount)

    // The route's own time per node, beside the priced cost the search minimises: the two differ by
    // exactly the berth's courtesy, and it is this one — the real seconds the search accumulated —
    // that the drawn line's own cost is asserted against.
    private val actualSec = DoubleArray(nodeCount)

    // The same walk with the turn's own price left out — legs alone, at the speeds the geometry and
    // the limits give them. It is **not** reported: it exists so the fillet can read a leg's true
    // speed, because `actualSec` carries a comfort charge that a short leg is mostly made of, and a
    // radius derived from it shrinks exactly where the corner is sharpest.
    private val plainSec = DoubleArray(nodeCount)

    private var searchStamp = 0

    /**
     * How many nodes this run expanded — [`RouteMeshDetails.nodesExpanded`], the cost a comparison
     * between engines is read on beside the wall clock. It is a count and nothing else: no admission,
     * no price and no comparison reads it.
     */
    private var expandedNodes = 0

    // The zones the two resolved ends stand in, resolved once per run and reused by every edge: the
    // exception the traversal rule is stated with.
    private val exceptZoneIds = HashSet<String>()

    /** Whether the run in flight forbids the zones' interiors — see [limitAtNode]. */
    private var forbidZoneInteriors = false

    /** One run's own parameters, so the loop and the result construction read one object. */
    private class Attempt(
        val startNode: Int,
        val destinationNode: Int,
        val destinationMoved: Boolean,
        val boatComponent: Int,
        val cruiseSpeedKn: Double,
        val lateralAccelMps2: Double,
        val berthM: Double,
        val forbidInteriors: Boolean,
        val forcedCrossing: Boolean
    )

    /**
     * Finds the fastest water route from [start] to [destination] at [cruiseSpeedKn].
     *
     * The start is bounded twice — by the mesh's box, then by [snapRadiusM] — because a boat off the
     * covered water is reported, never silently dragged onto it. The destination is bounded once:
     * the box rejects what is off the corridor, and past that gate the aim always resolves, moving
     * to the closest node of the boat's own stretch when it landed on land or in another stretch.
     * The pin can then be drawn where the route really ends.
     *
     * **Two runs, in one order.** The zones' interiors are not traversable first; where that finds no
     * way through, the same search runs with them priced as they always were and its result is marked
     * as the forced crossing it is. The order is deliberate: a crossing is only ever taken when going
     * around is impossible, and which of the two happened is readable off the result rather than
     * inferred from its geometry.
     *
     * @param ensureActive called on every expansion; throw from it to cancel.
     * @return a [RouteResult.Success], or [RouteResult.OutsideMesh] / [RouteResult.NoPath].
     */
    fun search(
        start: RoutePoint,
        destination: RoutePoint,
        cruiseSpeedKn: Double,
        ensureActive: () -> Unit = {}
    ): RouteResult {
        if (nodeCount == 0) return RouteResult.OutsideMesh
        val cruiseMps = cruiseSpeedKn * Units.MPS_PER_KNOT
        if (cruiseMps <= 0.0) return RouteResult.NoPath
        // The comfort cap and the berth are read once per search, from their one home: a change to
        // either property reaches the next search and never a bake.
        val lateralAccelMps2 = turnLateralAccelMps2()
        val berthM = zoneBerthM()

        if (!inBox(start)) return RouteResult.OutsideMesh
        val startNode = nearestNode(start, component = null, maxDistanceM = snapRadiusM)
            ?: return RouteResult.OutsideMesh
        val boatComponent = mesh.nodeComponent[startNode]

        // The aim's own radius gate is gone on purpose: it would reject a land aim before the
        // one-stretch rule could answer it, which is the behaviour the rule forbids.
        if (!inBox(destination)) return RouteResult.OutsideMesh
        val aimed = resolveDestination(destination, boatComponent) ?: return RouteResult.NoPath
        val destinationNode = aimed.index
        val destinationMoved = aimed.moved ||
            !queries.isWater(destination.latitude, destination.longitude)

        // **The crossing is an invariant first, and a fallback second.** No way around, or an end
        // standing inside the zone itself, and a boat has to be able to go — so the second run prices
        // the zones as they always were and the result says the crossing was forced.
        val forbidden = run(
            Attempt(
                startNode, destinationNode, destinationMoved, boatComponent, cruiseSpeedKn,
                lateralAccelMps2, berthM, forbidInteriors = true, forcedCrossing = false
            ),
            ensureActive
        )
        if (forbidden !is RouteResult.NoPath) return forbidden
        val priced = run(
            Attempt(
                startNode, destinationNode, destinationMoved, boatComponent, cruiseSpeedKn,
                lateralAccelMps2, berthM, forbidInteriors = false, forcedCrossing = true
            ),
            ensureActive
        )
        return if (priced is RouteResult.Success) priced else RouteResult.NoPath
    }

    /**
     * One A* walk, under the parameters [attempt] names.
     *
     * The exception the traversal rule is stated with is resolved **here, once**: the zones the two
     * resolved nodes stand in are read before the walk starts and every edge's test reads that set,
     * so no edge re-asks a question whose answer the search already knows.
     *
     * @return the [RouteResult.Success] the walk reached, or [RouteResult.NoPath].
     */
    private fun run(attempt: Attempt, ensureActive: () -> Unit): RouteResult {
        searchStamp++
        expandedNodes = 0
        forbidZoneInteriors = attempt.forbidInteriors
        exceptZoneIds.clear()
        zoneAt(attempt.startNode)?.let { exceptZoneIds.add(it.id) }
        zoneAt(attempt.destinationNode)?.let { exceptZoneIds.add(it.id) }

        java.util.Arrays.fill(gScore, Double.POSITIVE_INFINITY)
        java.util.Arrays.fill(actualSec, Double.POSITIVE_INFINITY)
        java.util.Arrays.fill(plainSec, Double.POSITIVE_INFINITY)
        java.util.Arrays.fill(cameFrom, -1)
        java.util.Arrays.fill(cameEdge, -1)
        java.util.Arrays.fill(closed, false)
        heapSize = 0

        val destinationNode = attempt.destinationNode
        val cruiseMps = attempt.cruiseSpeedKn * Units.MPS_PER_KNOT
        val destLat = mesh.pointsLat[destinationNode]
        val destLon = mesh.pointsLon[destinationNode]

        gScore[attempt.startNode] = 0.0
        actualSec[attempt.startNode] = 0.0
        plainSec[attempt.startNode] = 0.0
        push(attempt.startNode, heuristicAt(attempt.startNode, destLat, destLon, cruiseMps))

        while (heapSize > 0) {
            ensureActive()
            val current = pop()
            if (closed[current]) continue
            closed[current] = true
            expandedNodes++
            if (current == destinationNode) return buildSuccess(attempt)
            val firstEdge = mesh.nodeOffsets[current]
            val endEdge = mesh.nodeOffsets[current + 1]
            for (k in firstEdge until endEdge) {
                val edge = mesh.nodeEdges[k]
                val other =
                    if (mesh.edgeFrom[edge] == current) mesh.edgeTo[edge] else mesh.edgeFrom[edge]
                if (closed[other]) continue
                val speedMps =
                    min(attempt.cruiseSpeedKn, effectiveLimitKn(edge, current, other)) * Units.MPS_PER_KNOT
                if (speedMps <= 0.0) continue
                val legSec = mesh.edgeLengthM[edge] / speedMps
                // The turn's price is real time the plan reports — it is the slack the arc's own
                // chord leaves — while the berth's courtesy is not, so only the first joins the
                // route's clock, and the leg's own seconds stay on the plain one beside it.
                val realSec = legSec +
                    turnPenaltySec(cameFrom[current], current, other, speedMps, attempt.lateralAccelMps2)
                val tentative = gScore[current] + realSec +
                    berthPenaltySec(zoneClearanceAt(current), zoneClearanceAt(other), legSec, attempt.berthM)
                if (tentative < gScore[other]) {
                    gScore[other] = tentative
                    actualSec[other] = actualSec[current] + realSec
                    plainSec[other] = plainSec[current] + legSec
                    cameFrom[other] = current
                    cameEdge[other] = edge
                    push(other, tentative + heuristicAt(other, destLat, destLon, cruiseMps))
                }
            }
        }
        return RouteResult.NoPath
    }

    // ── Snapping ──────────────────────────────────────────────────────────────

    /** @return true when [point] lies inside the mesh's own water box — the corridor, nothing else. */
    private fun inBox(point: RoutePoint): Boolean =
        point.latitude in mesh.box.latSouth..mesh.box.latNorth &&
            point.longitude in mesh.box.lonWest..mesh.box.lonEast

    /** A resolved aim: [index] is the node the route ends on, [moved] whether the aim resolved there. */
    private class Aim(val index: Int, val moved: Boolean)

    /**
     * Resolves the aim into the boat's own stretch in a single pass.
     *
     * The closest node of [component] is the destination whatever its distance — the two ends of a
     * route always share one stretch, so a land aim and a foreign-stretch aim take the same path
     * here. [Aim.moved] is true when the closest node *overall* is a different one, which is the
     * sign that the aim resolved somewhere other than where it was placed.
     *
     * The node it answers with is also the node the traversal rule's exception is read against — see
     * [run] — which is why the aim is resolved into a node rather than held as a position.
     *
     * @return null only when the boat's stretch holds no node, which cannot happen for a component
     *         the start itself belongs to.
     */
    private fun resolveDestination(point: RoutePoint, component: Int): Aim? {
        var stretchNode = -1
        var stretchM = Double.MAX_VALUE
        var anyNode = -1
        var anyM = Double.MAX_VALUE
        for (n in 0 until nodeCount) {
            val d = metresBetween(
                point.latitude, point.longitude, mesh.pointsLat[n], mesh.pointsLon[n]
            )
            if (d < anyM) {
                anyM = d
                anyNode = n
            }
            if (mesh.nodeComponent[n] == component && d < stretchM) {
                stretchM = d
                stretchNode = n
            }
        }
        return if (stretchNode < 0) null else Aim(stretchNode, moved = stretchNode != anyNode)
    }

    private fun nearestNode(point: RoutePoint, component: Int?, maxDistanceM: Double): Int? {
        var best = -1
        var bestDistance = Double.MAX_VALUE
        for (n in 0 until nodeCount) {
            if (component != null && mesh.nodeComponent[n] != component) continue
            val d = metresBetween(
                point.latitude, point.longitude, mesh.pointsLat[n], mesh.pointsLon[n]
            )
            if (d < bestDistance) {
                bestDistance = d
                best = n
            }
        }
        return if (best >= 0 && bestDistance <= maxDistanceM) best else null
    }

    // ── Cost ──────────────────────────────────────────────────────────────────

    /**
     * The effective limit (kn) for an edge, cached for the search that stamped it.
     *
     * A crossing is priced at `min(limitA, limitB)` and an edge marked in band at the band's own
     * live limit, even when both of its ends sit outside the band. Two clauses make an edge leave the
     * graph: a limit at or below [`FORBIDDEN_LIMIT_KN`], which is what an interior node prices its
     * edges at while the traversal rule is in force, and the band's own price — which is a price and
     * never a refusal, the band being no part of the forbidden set.
     */
    private fun effectiveLimitKn(edge: Int, a: Int, b: Int): Double {
        if (edgeLimitStamp[edge] == searchStamp) return edgeLimitKn[edge]
        var limit = min(limitAtNode(a), limitAtNode(b))
        if (mesh.edgeInBand[edge]) limit = min(limit, queries.coastalBandSpeedLimitKn)
        edgeLimitKn[edge] = limit
        edgeLimitStamp[edge] = searchStamp
        return limit
    }

    /**
     * The limit (kn) the node is bound by — **and the whole of the traversal rule**.
     *
     * A node standing inside a priced speed zone returns [`FORBIDDEN_LIMIT_KN`] while the rule is in
     * force and the zone is not one the two resolved ends stand in, so the speed test in [run] drops
     * every edge touching it: entering a zone's interior is impossible, not expensive. A node inside
     * an excepted zone answers with the zone's own live limit, which is what lets a boat leave the
     * zone it is in at the speed that zone allows.
     */
    private fun limitAtNode(node: Int): Double {
        val zone = zoneAt(node) ?: return Double.MAX_VALUE
        return if (forbidZoneInteriors && zone.id !in exceptZoneIds) FORBIDDEN_LIMIT_KN else zone.limitKn
    }

    /**
     * The priced zone the node stands in, asked once per node per run.
     *
     * The cache is what makes the traversal rule affordable: the same answer carries the price and
     * the identity, so a node is queried once however many edges it carries, and the triangle-based
     * form below never has to ask the live layer about a point.
     */
    private fun zoneAt(node: Int): PricedZoneRef? {
        if (nodeZoneStamp[node] == searchStamp) return nodeZone[node]
        val zone = queries.pricedZoneAt(mesh.pointsLat[node], mesh.pointsLon[node])
        nodeZone[node] = zone
        nodeZoneStamp[node] = searchStamp
        return zone
    }

    /** Whether the node stands in a priced zone the run may not enter — the per-node half of the rule. */
    private fun zoneForbiddenAt(node: Int): Boolean {
        val zone = zoneAt(node) ?: return false
        return forbidZoneInteriors && zone.id !in exceptZoneIds
    }

    /** Straight-line metres ÷ cruise m/s — admissible, see the class note. */
    private fun heuristicAt(node: Int, destLat: Double, destLon: Double, cruiseMps: Double): Double =
        metresBetween(mesh.pointsLat[node], mesh.pointsLon[node], destLat, destLon) / cruiseMps

    /**
     * The price (s) of the turn at [current], between the leg that arrived there and the leg leaving
     * for [next].
     *
     * It is the fillet's arc read as a **cost, and deliberately not as its geometry**. Inserting that
     * arc makes the drawn line *shorter* than the corner: the two legs give up `2 · R · tan(θ / 2)`
     * from the vertex to the tangent points and the arc returns `R · θ`, so the corner is cut by
     * `R · (2 · tan(θ / 2) − θ)`. Charging that would make every bend cheaper than straight water, so
     * what is charged is the **arc-minus-chord** — `R · (θ − 2 · sin(θ / 2))` with `R = v² / a`, which
     * at speed `v` is `(v / a) · (θ − 2 · sin(θ / 2))` seconds: a 90° turn at 28 kn costs 4.5 s at the
     * shipped cap (2.3 s at 1.0) against a 4.2 s 60 m leg, so a bend is priced as roughly a leg —
     * enough to make the straighter line the cheaper one, and a small fraction of the seconds a detour
     * spends, so it can never close a channel the water allows. The angle, the radius and the price all
     * live in [`RouteTurnGeometry`]; this method only supplies the three nodes.
     *
     * It is never negative, which is what keeps the heuristic admissible: a price can only make a leg
     * cost more than its own length over its own speed.
     */
    private fun turnPenaltySec(
        previous: Int,
        current: Int,
        next: Int,
        speedMps: Double,
        lateralAccelMps2: Double
    ): Double {
        if (previous < 0 || lateralAccelMps2 <= 0.0) return 0.0
        val turn = RouteTurnGeometry.turnRadians(
            mesh.pointsLat[previous], mesh.pointsLon[previous],
            mesh.pointsLat[current], mesh.pointsLon[current],
            mesh.pointsLat[next], mesh.pointsLon[next]
        )
        return RouteTurnGeometry.turnPriceSec(speedMps, lateralAccelMps2, turn)
    }

    /**
     * The extra time (s) a leg pays for passing inside the berth.
     *
     * **The price is in the cost's own unit — seconds — and it has a stated ceiling.** A leg's depth
     * in the berth is the mean of its two ends', 0 on the berth's edge and 1 on a zone's own
     * boundary, and its time is multiplied by `1 + ([berthMaxPrice] − 1) × depth`: a leg a hair
     * inside pays a hair more, and a leg standing on the boundary is priced as [berthMaxPrice] times
     * its own time at the shipped value, never more. The ceiling is what keeps the preference real
     * without letting it become absurd — a route can always outweigh it with a detour it judges
     * shorter, which is the whole difference between a price and a wall.
     *
     * It is never negative — so the heuristic stays admissible — and it never forbids an edge: a leg
     * that must hug a zone to reach a passage is made expensive, never impossible.
     */
    private fun berthPenaltySec(
        clearanceA: Double,
        clearanceB: Double,
        legSec: Double,
        berthM: Double
    ): Double {
        if (berthM <= 0.0 || legSec <= 0.0 || berthMaxPrice <= 1.0) return 0.0
        if (clearanceA >= berthM && clearanceB >= berthM) return 0.0
        val depth = (depthInBerth(clearanceA, berthM) + depthInBerth(clearanceB, berthM)) / 2.0
        return legSec * (berthMaxPrice - 1.0) * depth.coerceIn(0.0, 1.0)
    }

    /** How deep inside the berth a point of the given clearance stands: 0 at its edge, 1 on a zone. */
    private fun depthInBerth(clearanceM: Double, berthM: Double): Double =
        ((berthM - clearanceM) / berthM).coerceIn(0.0, 1.0)

    /** The node's own distance to the nearest zone boundary (m), cached for the search that asked. */
    private fun zoneClearanceAt(node: Int): Double {
        if (nodeClearanceStamp[node] == searchStamp) return nodeZoneClearanceM[node]
        val clearance = queries.distanceToZoneM(mesh.pointsLat[node], mesh.pointsLon[node])
        nodeZoneClearanceM[node] = clearance
        nodeClearanceStamp[node] = searchStamp
        return clearance
    }

    /**
     * The heading change (deg) at node [b], between the leg a→b and the leg b→c.
     *
     * Read through [`RouteTurnGeometry`], at the vertex's own frame: the merge must measure the same
     * angle the fillet will round, or a vertex kept as a real bend would be rounded as a straight one.
     */
    private fun turnDegreesAt(a: Int, b: Int, c: Int): Double =
        RouteTurnGeometry.turnRadians(
            mesh.pointsLat[a], mesh.pointsLon[a],
            mesh.pointsLat[b], mesh.pointsLon[b],
            mesh.pointsLat[c], mesh.pointsLon[c]
        ) * 180.0 / PI

    // ── Path ──────────────────────────────────────────────────────────────────

    /**
     * The stamped per-triangle verdict, read from its three vertices' own zones — **the zone rule
     * asked the triangle, never the point**.
     *
     * A zone ring is a triangulation constraint, so no kept triangle straddles one: every triangle
     * lies wholly inside or wholly outside every zone, and a triangle that touches one at all has a
     * vertex inside it. The verdict is therefore read from the triangle's own three vertices — nodes
     * the search has already priced — and the live zone layer is never asked per sample, which is what
     * keeps the rule affordable on the shortcut's tens of thousands of samples.
     */
    private fun forbiddenTriangle(triangle: Int): Boolean {
        if (triangleStamp[triangle] == searchStamp) return triangleForbidden[triangle]
        val verdict = zoneForbiddenAt(mesh.triA[triangle]) ||
            zoneForbiddenAt(mesh.triB[triangle]) ||
            zoneForbiddenAt(mesh.triC[triangle])
        triangleForbidden[triangle] = verdict
        triangleStamp[triangle] = searchStamp
        return verdict
    }

    /**
     * The one test a smoothed point must pass: water to the app's own oracle **and** inside the
     * mesh's kept triangles **and** off every zone interior the run may not enter.
     *
     * The second half is what makes it exact. The mesh's edges cannot answer it — they have no
     * interior — and a sampled test cannot be trusted with it, because a shallow patch smaller than
     * the sample step hides between two nodes without ever being seen. A kept triangle is the bake's
     * own assertion that its inside cleared both gates, so a point inside one is water the mesh
     * itself vetted. The third is what keeps an arc from cutting into a zone the chain went around.
     */
    private fun onKeptWater(latitude: Double, longitude: Double): Boolean {
        if (!queries.isWater(latitude, longitude)) return false
        val triangle = containment.triangleAt(latitude, longitude)
        return triangle >= 0 && !forbiddenTriangle(triangle)
    }

    /**
     * The test a **shortcut's own sample** must pass: inside the mesh, **in the boat's own stretch**,
     * **and off every forbidden interior** — all three read off the sample's own triangle, so the
     * zone rule costs no extra lookup at all.
     *
     * It is the exact containment test on its own, without the water oracle beside it, and the reason
     * is half what the pass is for and half what it costs. What it is for: a straight run is taken only
     * where the **mesh** vouches for the water under it, and a kept triangle is that assertion already
     * — the bake kept it because its interior cleared the depth gate and the channel floor. What it
     * costs: this call is made once per sample, tens of thousands of times on a 100 km pair, where the
     * fillet asks once per arc point; asking the coastline oracle again on that path **measured ten
     * seconds** against the search's own half-second budget, and it asks a question the mesh has
     * already answered. The oracle is not discarded — [`onKeptWater`] still guards every arc point —
     * it is simply not what a shortcut is measured against: a shortcut's question is "is this the
     * mesh's own water", and the stretch is what keeps the answer the boat's.
     */
    private fun onBoatWater(latitude: Double, longitude: Double, stretch: Int): Boolean {
        val triangle = containment.triangleIn(latitude, longitude, stretch)
        return triangle >= 0 && !forbiddenTriangle(triangle)
    }

    /**
     * Whether a candidate shortcut may replace the chain legs it spans.
     *
     * **The pass re-prices what it changes.** The candidate's time is its own straight length over
     * the **most restrictive** limit in force over the chain legs it replaces — the rule the drawn
     * line's clock is read with, and the same [`RoutePlanTiming.legSeconds`] that prices each of
     * those legs — and it is admissible only when that is **no worse** than the legs themselves. So
     * "around" returns wherever around is faster, which is the search's own decision preserved, and
     * "through" survives only where through really is cheaper at the limits in force.
     *
     * **The comparison is on the whole window the candidate changes, turns included — never the legs
     * alone.** The vertices the span loses take their own turns with them, and the two corners that
     * survive are **rotated** by the shorter line: `from` is drawn towards `to` instead of towards the
     * chain's next vertex, and `to` is drawn from `from` instead of from its own predecessor. Both are
     * charged here at exactly the angles and speeds [`RoutePlanTiming.drawnLegSeconds`] will charge
     * them at, so the quantity the candidate is admitted on is the quantity the drawn clock will read.
     * Admitting on the legs alone left those two rotations uncharged: a shortcut could be cheaper by
     * its legs and dearer by its corners, and the invariant then caught what this should have refused.
     */
    private fun shortcutNoWorse(
        from: Int,
        to: Int,
        chain: List<RoutePoint>,
        legM: DoubleArray,
        legLimitKn: DoubleArray,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double
    ): Boolean {
        if (to <= from + 1) return true
        var lowestLimitKn = Double.MAX_VALUE
        var replacedSec = 0.0
        for (i in from until to) {
            if (legLimitKn[i] < lowestLimitKn) lowestLimitKn = legLimitKn[i]
            replacedSec += RoutePlanTiming.legSeconds(legM[i], legLimitKn[i], cruiseSpeedKn) +
                cornerSecAt(chain, i, legLimitKn[i], cruiseSpeedKn, lateralAccelMps2)
        }
        // The corner at `to` is outside the span, but the leg arriving there rotates with it: the
        // chain came in along `to - 1` and the shortened line comes in along `from`.
        val outgoingLimitKn = legLimitKn.getOrElse(to) { Double.MAX_VALUE }
        replacedSec += cornerSecAt(chain, to, outgoingLimitKn, cruiseSpeedKn, lateralAccelMps2)
        val candidateM = metresBetween(
            chain[from].latitude, chain[from].longitude, chain[to].latitude, chain[to].longitude
        )
        val candidateSec = RoutePlanTiming.legSeconds(candidateM, lowestLimitKn, cruiseSpeedKn) +
            cornerSecAt(chain, from, lowestLimitKn, cruiseSpeedKn, lateralAccelMps2, next = to) +
            cornerSecAt(chain, to, outgoingLimitKn, cruiseSpeedKn, lateralAccelMps2, previous = from)
        return candidateSec <= replacedSec + RE_PRICE_TOLERANCE_SEC
    }

    /**
     * The seconds the clock charges at the corner [vertex] of [chain] — the turn between the legs
     * `previous → vertex → next`, priced at [limitKn], the limit of the leg **leaving** the corner.
     *
     * It is the same quantity [`RoutePlanTiming.drawnLegSeconds`] adds to a drawn leg, so an admission
     * that charges it compares the drawn line against the chain rather than an estimate of it. A
     * neighbour outside the chain — neither end vertex has one — is a turn the drawn line does not
     * have, so it is charged nothing, which is the condition the clock reads as well.
     */
    private fun cornerSecAt(
        chain: List<RoutePoint>,
        vertex: Int,
        limitKn: Double,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double,
        previous: Int = vertex - 1,
        next: Int = vertex + 1
    ): Double {
        if (previous < 0 || next >= chain.size) return 0.0
        val speedMps = min(cruiseSpeedKn, limitKn) * Units.MPS_PER_KNOT
        val turn = RouteTurnGeometry.turnRadians(
            chain[previous].latitude, chain[previous].longitude,
            chain[vertex].latitude, chain[vertex].longitude,
            chain[next].latitude, chain[next].longitude
        )
        return RouteTurnGeometry.turnPriceSec(speedMps, lateralAccelMps2, turn)
    }

    /**
     * The seconds the clock charges at the **mesh vertex** [vertex], between the nodes [previous] and
     * [next] — [`cornerSecAt`] for the merge, which works in node indices rather than drawn points.
     *
     * The same condition holds: a neighbour index below zero is a turn the drawn line does not have.
     */
    private fun turnSecAtNodes(
        previous: Int,
        vertex: Int,
        next: Int,
        limitKn: Double,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double
    ): Double {
        if (previous < 0 || next < 0) return 0.0
        val speedMps = min(cruiseSpeedKn, limitKn) * Units.MPS_PER_KNOT
        val turn = RouteTurnGeometry.turnRadians(
            mesh.pointsLat[previous], mesh.pointsLon[previous],
            mesh.pointsLat[vertex], mesh.pointsLon[vertex],
            mesh.pointsLat[next], mesh.pointsLon[next]
        )
        return RouteTurnGeometry.turnPriceSec(speedMps, lateralAccelMps2, turn)
    }

    /**
     * Whether an arc may replace the corner at [vertex] — **the fillet re-prices what it changes too**.
     *
     * The corner's own cost is what the chain spent on it: the two cutback runs, each at its own
     * leg's limit, plus the turn price this search charged at the outgoing leg's speed. The arc's cost
     * is its own length at the limit it inherits — the most restrictive of the two legs, see
     * [`RouteFillet`] — plus the price of the chords it will be drawn as, and both halves come from the
     * clock's own file ([`RoutePlanTiming.arcPriceSec`]) rather than from a second spelling beside this
     * caller. An arc that is dearer is refused and the corner stays sharp.
     *
     * **It is not a formality.** A corner between a fast leg and a slow one — a band edge, or the edge
     * of a zone the route rounds at its limit — pays its whole arc at the slow limit where the chain
     * paid only that side's cutback, so the arc costs about `R·θ/2·(1/v_slow − 1/v_fast)` more than
     * the corner. The invariant in [buildSuccess] fired on exactly that, on the shipped mesh, before
     * this test existed: 352.2 s drawn against 350.9 s accumulated on one probe pair.
     */
    private fun arcNoWorse(
        vertex: Int,
        radiusM: Double,
        cutM: Double,
        turnRad: Double,
        legLimitKn: List<Double>,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double
    ): Boolean {
        val incomingLimitKn = legLimitKn.getOrElse(vertex - 1) { Double.MAX_VALUE }
        val outgoingLimitKn = legLimitKn.getOrElse(vertex) { Double.MAX_VALUE }
        val arcLimitKn = min(incomingLimitKn, outgoingLimitKn)
        val arcSpeedMps = min(cruiseSpeedKn, arcLimitKn) * Units.MPS_PER_KNOT
        val outgoingSpeedMps = min(cruiseSpeedKn, outgoingLimitKn) * Units.MPS_PER_KNOT
        // A closed leg is a cost no route survives; refusing here keeps the infinities out of the
        // comparison below, where `INF <= INF` would admit an arc on water no route may use at all.
        if (arcSpeedMps <= 0.0) return false
        val arcLengthM = radiusM * turnRad
        val arcSec = RoutePlanTiming.arcPriceSec(
            arcLengthM,
            arcLimitKn,
            cruiseSpeedKn,
            lateralAccelMps2,
            turnRad,
            RouteFillet.chordCount(arcLengthM)
        )
        val cornerSec =
            RoutePlanTiming.legSeconds(cutM, incomingLimitKn, cruiseSpeedKn) +
                RoutePlanTiming.legSeconds(cutM, outgoingLimitKn, cruiseSpeedKn) +
                RouteTurnGeometry.turnPriceSec(outgoingSpeedMps, lateralAccelMps2, turnRad)
        return arcSec <= cornerSec + RE_PRICE_TOLERANCE_SEC
    }

    private fun buildSuccess(attempt: Attempt): RouteResult.Success {
        val startNode = attempt.startNode
        val destinationNode = attempt.destinationNode
        val cruiseSpeedKn = attempt.cruiseSpeedKn
        // The chain itself, collected destination-first like the search left it and reversed with the
        // nodes. **The only clock carried beside it is the plain one**, which the fillet reads its leg
        // speeds from: the plan's own seconds are read off the drawn line ([`RoutePlanTiming`]), so the
        // accumulated route time is not copied per node — it is read at the destination, where the
        // ceiling the drawn clock is asserted against lives.
        val nodes = ArrayList<Int>()
        val cumulativePlainSec = ArrayList<Double>()
        var inBand = false
        var n = destinationNode
        nodes.add(n)
        cumulativePlainSec.add(plainSec[n])
        while (n != startNode) {
            val previous = cameFrom[n]
            if (previous < 0) break
            val edge = cameEdge[n]
            if (edge >= 0 && mesh.edgeInBand[edge]) inBand = true
            n = previous
            nodes.add(n)
            cumulativePlainSec.add(plainSec[n])
        }
        nodes.reverse()
        cumulativePlainSec.reverse()

        // **The limit each chain edge carries, one per leg** — the quantity every pass over the chain
        // inherits and re-prices with. It is the search's own cached resolution, so nothing here asks
        // the live layer a second time, and an in-band edge carries the band's own limit into it.
        val chainEdgeLimitKn = DoubleArray(maxOf(0, nodes.size - 1))
        for (i in 0 until nodes.size - 1) {
            val edge = cameEdge[nodes[i + 1]]
            chainEdgeLimitKn[i] =
                if (edge >= 0) effectiveLimitKn(edge, nodes[i], nodes[i + 1]) else Double.MAX_VALUE
        }

        // **The collinear merge.** A node whose heading change is under [MERGE_TURN_DEG] is a vertex
        // the geometry does not need, so it goes and the chain is one leg shorter, the limits
        // telescoping with it — a merged leg taking the most restrictive of the two it replaces.
        // **It re-prices too, and the price is the drawn window's own**: a merge is admissible only
        // where the merged leg, at that limit, **plus the two turns the merge rotates**, is no dearer
        // than the two legs it replaces plus the three turns it removes. Charging the legs alone was
        // the defect the review found: a merge moves the surviving neighbour's corner by the angle it
        // merged on, and the drawn clock charges that angle, so an admission that ignored it admitted
        // a line the plan then could not afford. It needs no water test of any kind: the two legs it
        // replaces are within a couple of degrees of straight, so the chord between them cannot leave
        // the water they both already lay in.
        val keptNodes = ArrayList<Int>(nodes.size)
        val keptPlainTimes = ArrayList<Double>(nodes.size)
        // The leg **arriving** at keptNodes[k]: its limit and its length as the drawn line sees it.
        val keptLimits = ArrayList<Double>(nodes.size)
        val keptLegM = ArrayList<Double>(nodes.size)
        for (i in nodes.indices) {
            var arrivingLimitKn = if (i == 0) Double.MAX_VALUE else chainEdgeLimitKn[i - 1]
            var arrivingM = if (i == 0) {
                0.0
            } else {
                metresBetween(
                    mesh.pointsLat[nodes[i - 1]], mesh.pointsLon[nodes[i - 1]],
                    mesh.pointsLat[nodes[i]], mesh.pointsLon[nodes[i]]
                )
            }
            var arrivingSec =
                RoutePlanTiming.legSeconds(arrivingM, arrivingLimitKn, cruiseSpeedKn)
            // The leg that will leave `nodes[i]`, and the limit in force over it: the corner there
            // rotates with the leg that arrives at it, and the clock charges that corner at the speed
            // of the leg leaving. Both come from the chain, never from the merged line.
            val nextNode = if (i + 1 < nodes.size) nodes[i + 1] else NO_NODE
            val outgoingLimitKn = if (i + 1 < nodes.size) chainEdgeLimitKn[i] else Double.MAX_VALUE
            while (keptNodes.size >= 2 &&
                turnDegreesAt(keptNodes[keptNodes.size - 2], keptNodes[keptNodes.size - 1], nodes[i])
                <= MERGE_TURN_DEG
            ) {
                val last = keptNodes.size - 1
                val survivor = keptNodes[last - 1]
                val removed = keptNodes[last]
                val beforeSurvivor = keptNodes.getOrElse(last - 2) { NO_NODE }
                val mergedM = metresBetween(
                    mesh.pointsLat[survivor], mesh.pointsLon[survivor],
                    mesh.pointsLat[nodes[i]], mesh.pointsLon[nodes[i]]
                )
                val mergedLimitKn = min(keptLimits[last], arrivingLimitKn)
                val mergedLegSec =
                    RoutePlanTiming.legSeconds(mergedM, mergedLimitKn, cruiseSpeedKn)
                // **The rotated corners are charged, both sides of the comparison.** Losing the
                // vertex turns the survivor's own corner towards `nodes[i]`, rebuilds the corner at
                // the vertex being added from the survivor rather than from the vertex removed, and
                // takes the removed vertex's own turn — which the chain paid — with it. Those turns
                // and the legs are the whole of what a merge changes, so this is what the drawn clock
                // would charge each way.
                val mergedSec =
                    mergedLegSec +
                        turnSecAtNodes(
                            beforeSurvivor, survivor, nodes[i], mergedLimitKn, cruiseSpeedKn,
                            attempt.lateralAccelMps2
                        ) +
                        turnSecAtNodes(
                            survivor, nodes[i], nextNode, outgoingLimitKn, cruiseSpeedKn,
                            attempt.lateralAccelMps2
                        )
                val replacedSec =
                    RoutePlanTiming.legSeconds(keptLegM[last], keptLimits[last], cruiseSpeedKn) +
                        arrivingSec +
                        turnSecAtNodes(
                            beforeSurvivor, survivor, removed, keptLimits[last], cruiseSpeedKn,
                            attempt.lateralAccelMps2
                        ) +
                        turnSecAtNodes(
                            survivor, removed, nodes[i], arrivingLimitKn, cruiseSpeedKn,
                            attempt.lateralAccelMps2
                        ) +
                        turnSecAtNodes(
                            removed, nodes[i], nextNode, outgoingLimitKn, cruiseSpeedKn,
                            attempt.lateralAccelMps2
                        )
                if (mergedSec > replacedSec + RE_PRICE_TOLERANCE_SEC) break
                arrivingM = mergedM
                arrivingLimitKn = mergedLimitKn
                arrivingSec = mergedLegSec
                keptNodes.removeAt(last)
                keptPlainTimes.removeAt(last)
                keptLimits.removeAt(last)
                keptLegM.removeAt(last)
            }
            keptNodes.add(nodes[i])
            keptPlainTimes.add(cumulativePlainSec[i])
            keptLimits.add(arrivingLimitKn)
            keptLegM.add(arrivingM)
        }

        // The merged chain's legs on their own: what a shortcut candidate is measured against, and
        // what the drawn line's limits are read from.
        val merged = keptNodes.map { mesh.point(it) }
        val mergedLegCount = maxOf(0, keptNodes.size - 1)
        val mergedLegM = DoubleArray(mergedLegCount)
        val mergedLegLimitKn = DoubleArray(mergedLegCount)
        for (k in 0 until mergedLegCount) {
            mergedLegM[k] = keptLegM[k + 1]
            mergedLegLimitKn[k] = keptLimits[k + 1]
        }

        // **The shortcut pass**, on the merged chain and before the fillet — the study's own order,
        // straighten the chain first and round only what is left of it. It keeps the vertices the
        // water forces, every candidate shortcut tested by the bake's own kept triangles together with
        // the boat's own stretch, so a straight run is taken only where the mesh itself vouches for
        // the water beneath it — and, since the zones' interiors are not traversable, only where it
        // stays out of them. Its price is the caller's third answer, so what it keeps is the farthest
        // vertex that is both on vetted water and no worse than the chain it replaces. Running it here
        // is also why the fillet's legs are longer than the fabric's own spacing: the corners it has
        // to round are the ones that survived this pass.
        val shortcut = if (shortcutPass) {
            RouteShortcut.keep(
                merged,
                insideWater = { latitude, longitude ->
                    onBoatWater(latitude, longitude, attempt.boatComponent)
                },
                admissible = { from, to ->
                    shortcutNoWorse(
                        from, to, merged, mergedLegM, mergedLegLimitKn, cruiseSpeedKn,
                        attempt.lateralAccelMps2
                    )
                }
            )
        } else {
            RouteShortcut.chainIntact(merged.size)
        }
        // No time telescopes through this pass — the plan's clock is read off the drawn line, and the
        // only figure carried beside the vertices is the plain one the fillet's speeds come from. What
        // does telescope is the **limit**, a shortcut leg taking the most restrictive limit in force
        // over the legs it replaced.
        val routedNodes = ArrayList<Int>(shortcut.positions.size)
        val routedPlainTimes = ArrayList<Double>(shortcut.positions.size)
        val routedLegLimitKn = ArrayList<Double>(maxOf(0, shortcut.positions.size - 1))
        for (position in shortcut.positions) {
            routedNodes.add(keptNodes[position])
            routedPlainTimes.add(keptPlainTimes[position])
        }
        for (m in 1 until shortcut.positions.size) {
            var lowestLimitKn = Double.MAX_VALUE
            for (leg in shortcut.positions[m - 1] until shortcut.positions[m]) {
                if (mergedLegLimitKn[leg] < lowestLimitKn) lowestLimitKn = mergedLegLimitKn[leg]
            }
            routedLegLimitKn.add(lowestLimitKn)
        }

        val chain = routedNodes.map { mesh.point(it) }

        // **Each leg's own speed**, its length over the time the geometry and the limits alone give
        // it. This is the speed the fillet's radius is read from: `actualSec` carries the turn's own
        // price, which on a short leg is most of the leg, so a radius derived from it would shrink
        // exactly at the sharp corners the arc exists for.
        val legSpeeds = ArrayList<Double>(maxOf(0, routedPlainTimes.size - 1))
        for (i in 0 until routedPlainTimes.size - 1) {
            val legLengthM = metresBetween(
                chain[i].latitude, chain[i].longitude, chain[i + 1].latitude, chain[i + 1].longitude
            )
            val plainLegSec = routedPlainTimes[i + 1] - routedPlainTimes[i]
            legSpeeds.add(if (plainLegSec > 0.0) legLengthM / plainLegSec else 0.0)
        }

        // **The fillet runs here**, in the search's own result construction, so the preview, the
        // confirmed route and the saved course are one line: a caller cannot draw an unsmoothed
        // route, because there is no unsmoothed route to draw. Its constraint test is the same one
        // the shortcut was held to, the zones' interiors included, so an arc that would enter a zone
        // the chain avoided is refused and the corner stays sharp and counted.
        val smoothed = RouteFillet.apply(
            chain,
            legSpeeds,
            attempt.lateralAccelMps2,
            routedLegLimitKn,
            arcAdmissible = { vertex, radiusM, cutM, turnRad ->
                arcNoWorse(
                    vertex, radiusM, cutM, turnRad, routedLegLimitKn, cruiseSpeedKn,
                    attempt.lateralAccelMps2
                )
            },
            insideWater = { latitude, longitude -> onKeptWater(latitude, longitude) }
        )

        // **The plan's clock, and the guard that keeps it honest.** Every pass above re-prices what it
        // changes — the legs **and** the turns it rotates — so the line that comes out of them cannot
        // have cost more than the chain the search walked, and that is tested here, where every run
        // reaches it, rather than in a probe that skips unless someone remembers to ask.
        //
        // **And a breach never costs the route.** It is a defect in a pass, and the honest answer to a
        // defect is not to take the boat's line off the screen: the run falls back to the **raw node
        // chain** — no merge, no shortcut, no fillet — warns, and answers with a line whose clock is
        // the search's own accumulation by construction. The corrected admissions should mean this
        // never fires; the warning and the flag exist so that "never" is a reading rather than a hope.
        val drawnLegTimesSec = RoutePlanTiming.drawnLegSeconds(
            smoothed.points, smoothed.legLimitKn, cruiseSpeedKn, attempt.lateralAccelMps2
        )
        val drawnSec = drawnLegTimesSec.sum()
        val accumulatedSec = actualSec[destinationNode]
        if (drawnSec > accumulatedSec + drawnCostSlackSec(accumulatedSec)) {
            return buildRawChainFallback(
                attempt = attempt,
                nodes = nodes,
                chainEdgeLimitKn = chainEdgeLimitKn,
                drawnSec = drawnSec,
                accumulatedSec = accumulatedSec,
                inBand = inBand
            )
        }

        return RouteResult.Success(
            points = smoothed.points,
            legTimesSec = drawnLegTimesSec,
            // The drawn line's own length, so the figure the trip row shows is the line it draws.
            // Inserting an arc makes that line a shade *shorter* than the chain it rounds — the two
            // legs give up `2 · R · tan(θ / 2)` and the arc returns only `R · θ` — never longer.
            distanceM = polylineLengthM(smoothed.points),
            durationSec = drawnSec,
            inBand = inBand,
            destinationMoved = attempt.destinationMoved,
            forcedCrossingZoneNames = if (attempt.forcedCrossing) {
                crossingZoneNames(smoothed.points)
            } else {
                emptyList()
            },
            // **The mesh engine's own readings, in its own dossier** — and the limits the clock above
            // was handed, published as the engine's supply rather than as a fact about the clock.
            details = RouteMeshDetails(
                nodeIndices = routedNodes.toList(),
                pricedSec = accumulatedSec,
                legLimitKn = smoothed.legLimitKn,
                // The pass's own two numbers, so "the line straightened" is a reading and not a claim:
                // the chain's count before it ran, and how many shortcuts the water refused.
                verticesBeforeShortcut = keptNodes.size,
                shortcutRefusedSegments = shortcut.refused,
                shortcutRefusedByCostSegments = shortcut.refusedByCost,
                smoothedVertices = smoothed.smoothed,
                reducedVertices = smoothed.reduced,
                sharpVertices = smoothed.sharp,
                largestTurnRadiusM = smoothed.largestRadiusM,
                sharpNoRoomVertices = smoothed.sharpNoRoom,
                sharpNoWaterVertices = smoothed.sharpNoWater,
                sharpNoGeometryVertices = smoothed.sharpNoGeometry,
                sharpNoPriceVertices = smoothed.sharpNoPrice,
                nodesExpanded = expandedNodes
            )
        )
    }

    /** The slack the drawn clock may exceed the search's own accumulation by — float noise, one home. */
    private fun drawnCostSlackSec(accumulatedSec: Double): Double =
        maxOf(DRAWN_COST_SLACK_SEC, accumulatedSec * DRAWN_COST_SLACK_RELATIVE)

    /**
     * **The line the invariant falls back to, and the reason a defect never costs the route.**
     *
     * It answers with the **raw node chain** — the nodes the search itself walked, with no merge, no
     * shortcut and no fillet — priced at the limits the search resolved. That is the clock the
     * accumulation was built from, so the line cannot breach the invariant, and the boat keeps a route
     * where a pass's defect would otherwise have cost it one. It warns, and it marks the result, so the
     * reading that has to prove the fallback never fires in practice can count the times it did.
     */
    private fun buildRawChainFallback(
        attempt: Attempt,
        nodes: List<Int>,
        chainEdgeLimitKn: DoubleArray,
        drawnSec: Double,
        accumulatedSec: Double,
        inBand: Boolean
    ): RouteResult.Success {
        val points = nodes.map { mesh.point(it) }
        val legLimitKn = chainEdgeLimitKn.toList()
        val legTimesSec = RoutePlanTiming.drawnLegSeconds(
            points, legLimitKn, attempt.cruiseSpeedKn, attempt.lateralAccelMps2
        )
        warn(
            "the drawn line's own cost ${"%.3f".format(drawnSec)}s exceeds the " +
                "${"%.3f".format(accumulatedSec)}s the search accumulated — a pass over the chain " +
                "changed the line without re-pricing it; answering the raw node chain of " +
                "${points.size} vertices instead, with no merge, no shortcut and no fillet"
        )
        return RouteResult.Success(
            points = points,
            legTimesSec = legTimesSec,
            distanceM = polylineLengthM(points),
            durationSec = legTimesSec.sum(),
            inBand = inBand,
            destinationMoved = attempt.destinationMoved,
            forcedCrossingZoneNames = if (attempt.forcedCrossing) {
                crossingZoneNames(points)
            } else {
                emptyList()
            },
            details = RouteMeshDetails(
                nodeIndices = nodes.toList(),
                pricedSec = accumulatedSec,
                legLimitKn = legLimitKn,
                rawChainFallback = true,
                nodesExpanded = expandedNodes
            )
        )
    }

    /**
     * The priced zones a drawn line enters, by name — what makes a forced crossing say so.
     *
     * It is read off the **drawn** polyline, sampled at [CROSSING_SAMPLE_M], so a shortcut's own leg
     * and an arc's own chord are asked about exactly as the chain's legs are. A sampled reading is
     * honest here because it is a *report* and not a rule: the traversal rule never sees this
     * function, and the step is small against any zone carrying a limit on this coast. Zones the two
     * resolved ends stand in are left out — a boat leaving its own harbour zone has not crossed
     * anything.
     *
     * **And a leg shorter than the step is still walked.** The step alone sampled every leg at
     * `ceil(length / step) + 1` points, so a leg of 50 m or less — which is *every* arc chord this
     * line is drawn from — was sampled at its two ends and nowhere else, and a zone clipped between
     * them went unreported: a forced crossing rendered as an ordinary route, which is exactly what
     * this report exists to prevent. So a leg is walked in at least two intervals, and each interval
     * is halved — a sample every quarter of a short leg, and never more than half the step apart on a
     * long one.
     */
    private fun crossingZoneNames(points: List<RoutePoint>): List<String> {
        val names = LinkedHashSet<String>()
        for (i in 0 until points.size - 1) {
            val lengthM = metresBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
            val steps = 2 * maxOf(2, ceil(lengthM / CROSSING_SAMPLE_M).toInt())
            for (k in 0..steps) {
                val t = k.toDouble() / steps
                val latitude = points[i].latitude + (points[i + 1].latitude - points[i].latitude) * t
                val longitude =
                    points[i].longitude + (points[i + 1].longitude - points[i].longitude) * t
                val zone = queries.pricedZoneAt(latitude, longitude) ?: continue
                if (zone.id !in exceptZoneIds) names.add(zone.name)
            }
        }
        return names.toList()
    }

    /** The polyline's own length in metres — what the drawn line measures, not what its edges summed. */
    private fun polylineLengthM(points: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += metresBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return total
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private fun metresBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * DEG_TO_RAD) * cos(lat2 * DEG_TO_RAD) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    // ── Binary heap with lazy deletion ────────────────────────────────────────

    private fun push(node: Int, key: Double) {
        if (heapSize == heapNode.size) grow()
        var i = heapSize++
        heapNode[i] = node
        heapKey[i] = key
        while (i > 0) {
            val parent = (i - 1) / 2
            if (heapKey[parent] <= heapKey[i]) break
            swap(parent, i)
            i = parent
        }
    }

    private fun pop(): Int {
        val top = heapNode[0]
        heapSize--
        if (heapSize > 0) {
            heapNode[0] = heapNode[heapSize]
            heapKey[0] = heapKey[heapSize]
            var i = 0
            while (true) {
                val left = 2 * i + 1
                val right = left + 1
                var smallest = i
                if (left < heapSize && heapKey[left] < heapKey[smallest]) smallest = left
                if (right < heapSize && heapKey[right] < heapKey[smallest]) smallest = right
                if (smallest == i) break
                swap(smallest, i)
                i = smallest
            }
        }
        return top
    }

    private fun swap(a: Int, b: Int) {
        val n = heapNode[a]; heapNode[a] = heapNode[b]; heapNode[b] = n
        val k = heapKey[a]; heapKey[a] = heapKey[b]; heapKey[b] = k
    }

    private fun grow() {
        val nodeGrown = IntArray(heapNode.size * 2)
        val keyGrown = DoubleArray(heapKey.size * 2)
        heapNode.copyInto(nodeGrown)
        heapKey.copyInto(keyGrown)
        heapNode = nodeGrown
        heapKey = keyGrown
    }

    companion object {
        /**
         * Default snap radius (m) for the start. Past this the boat is reported outside the mesh
         * rather than snapped onto a node it does not really occupy. The aim is not bounded by it.
         */
        const val DEFAULT_SNAP_RADIUS_M = 250.0

        /**
         * The heading change (deg) under which a node is a vertex the geometry does not need — the
         * collinear merge's threshold. Two degrees over a 60 m leg displaces the line by about 2 m,
         * well inside the mesh's own resolution, while anything above it is a bend the eye reads.
         */
        const val MERGE_TURN_DEG = 2.0

        /**
         * What a leg standing on a zone's own boundary pays, as a multiple of its own time: twice,
         * so hugging a zone is priced as a leg of double its length and the price fades to one at
         * the berth's edge. Stated rather than derived, because it is the policy — the berth is a
         * courtesy the route pays for, and this is the ceiling on what the courtesy costs.
         */
        const val BERTH_MAX_PRICE = 2.0

        /**
         * The reserved limit (kn) that closes an edge — **and it is used now**.
         *
         * Nothing reads a prohibition out of the mesh — the mesh carries geometry only — so a zone
         * whose interior may not be entered expresses itself as a limit at or below this value when
         * the live layer resolves it, and the edge leaves the graph through the speed test in [run].
         * The band is never priced at it, and neither is a zone either resolved end stands in.
         */
        const val FORBIDDEN_LIMIT_KN = 0.0

        /** The log tag the fallback's warning carries, and the reader that wants to hear it. */
        const val TAG = "RouteSearch"

        /** No node: the neighbour a drawn corner does not have, at either end of the chain. */
        const val NO_NODE = -1

        /**
         * The slack (s) a re-priced candidate leg may exceed the legs it replaces by, and the slack
         * the drawn line's own cost may exceed the search's accumulation by at zero length: float
         * noise. A merged chord is a hair either side of the sum of two collinear legs, and a report
         * that fired on that would be a false alarm rather than a finding.
         */
        const val RE_PRICE_TOLERANCE_SEC = 1e-6

        /** The same slack for the drawn clock, as an absolute floor. */
        const val DRAWN_COST_SLACK_SEC = 1e-6

        /** And as a fraction of the route's own time, so a long route is not held to a millisecond. */
        const val DRAWN_COST_SLACK_RELATIVE = 1e-9

        /** The crossing report's own sampling step (m) — see [crossingZoneNames]. */
        const val CROSSING_SAMPLE_M = 50.0

        private const val DEG_TO_RAD = PI / 180.0
    }
}
