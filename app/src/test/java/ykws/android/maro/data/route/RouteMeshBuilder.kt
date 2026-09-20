package ykws.android.maro.data.route

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.operation.distance.IndexedFacetDistance
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.poly2tri.Poly2Tri
import org.poly2tri.geometry.polygon.Polygon as P2tPolygon
import org.poly2tri.geometry.polygon.PolygonPoint
import org.poly2tri.triangulation.point.TPoint
import ykws.android.maro.data.model.RouteEdge
import ykws.android.maro.data.model.RouteMesh
import ykws.android.maro.data.model.RouteNode
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteTriangle
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **Build-time only — never in the APK.** Builds the adaptive navigation mesh the app searches.
 *
 * The pipeline is deliberately the same one the coastline band uses, extended by a triangulation:
 *
 * 1. **Planar overlay.** The coastline, the regulated-zone rings and the 300 m band's boundary are
 *    noded together with the box into a single line network, and [Polygonizer] turns that network
 *    into the disjoint faces it divides the box into. Every face is then classified by its own
 *    interior point — water from land, in band from out — which is what makes the overlay carry no
 *    limit of any kind: a face is a shape, and the limit is resolved live at search time.
 * 2. **Constraints.** Because the band boundary and the zone rings *are* edges of that network, no
 *    triangle can ever cross one. A leg that crosses a limit boundary is therefore priced on the
 *    edge that stops at the boundary, which is what "priced exactly" means here.
 * 3. **Triangulation.** Each water face is triangulated on its own with poly2tri, so the constraints
 *    are respected by construction rather than repaired afterwards.
 * 4. **Bounded adaptive refinement.** Steiner points are laid down on a graded grid — a cell is
 *    subdivided while it is still coarser than the local target, and the target itself grades from
 *    [Tuning.coastalSpacingM] at a constraint out to [Tuning.offshoreSpacingM] over
 *    [Tuning.refineSpanM]. The recursion is bounded three ways: by the two spacings, by the number
 *    of levels between them, and by [Tuning.nodeBudget].
 * 5. **The depth gate, and the bridging pass behind it.** A triangle whose vertices are all sounded
 *    deeper than [Tuning.minDepthM] is kept; one holding a vertex the soundings call *shallower* is
 *    dropped outright, so no node ever stands in water a boat of this size cannot use and a passage
 *    too shallow to cross is absent from the mesh rather than priced. A triangle that is merely
 *    **unsounded** is neither kept nor dropped: it goes to the **bridging pass** of
 *    [bridgesBetweenStretches], which keeps it only where it reconnects two stretches the strict gate
 *    would otherwise leave apart (D4, 2026-09-20, narrowed from a rule that admitted all unsounded
 *    water). Testing the vertices rather than the centroid is what makes the cut spacing-independent:
 *    the kept triangles lie wholly inside water the gate accepts, so their union can never bridge a
 *    shallow bar however coarse the local refinement is.
 * 6. **The 30 m channel floor.** A triangle is kept only when its centroid is at least half of
 *    [Tuning.minChannelWidthM] away from land — a point is traversable only when a disc that wide
 *    fits in the water entirely — so a passage narrower than the floor loses every triangle inside
 *    it and its two banks become two stretches instead of one. The floor is held on the triangle
 *    rather than on the node, which is what lets the mesh still reach the shore where the water is
 *    genuinely wide.
 * 7. **Connectivity.** The surviving triangles give an undirected graph; a union-find labels the
 *    connected stretches, which is the label the search reads to keep both ends of a route in one.
 *
 * Geometry is handled in a local equirectangular frame about the box centre, because JTS buffers
 * and distances mean metres; the mesh is written back in WGS84 by the inverse of the same frame.
 * That is a bake-time approximation, exact to well under a metre over a corridor of this size, and
 * it is what lets a 300 m buffer mean 300 m.
 */
class RouteMeshBuilder(
    private val regionId: String,
    private val geometry: RouteGeometry,
    private val tuning: Tuning = Tuning()
) {

    /**
     * The bake's knobs, each one a number the plan fixes: the two spacings bound the refinement, the
     * span grades it, [minDepthM] is the depth gate, [minChannelWidthM] the connectivity floor.
     */
    data class Tuning(
        /** Refinement target (m) at a constraint — the coastline, a zone ring, the band's boundary. */
        val coastalSpacingM: Double = 60.0,
        /** Refinement target (m) far from every constraint — the open sea. */
        val offshoreSpacingM: Double = 500.0,
        /** Distance (m) over which the target grades from coastal to offshore. */
        val refineSpanM: Double = 1_200.0,
        /** The coastal band's half-width (m), used to generate its boundary at bake time. */
        val bandM: Double = 300.0,
        /** The closing depth (m): water shallower than this is not meshed at all. */
        val minDepthM: Double = 2.5,
        /** The minimum channel width (m) the connectivity pass holds. */
        val minChannelWidthM: Double = 30.0,
        /**
         * The berth kept around a regulated zone **where the water has the room** (m) — the hybrid's
         * own distance, and the app-side twin of `route.zoneBerthM` in
         * `app/src/main/assets/maro.properties`, which is the number a *priced* berth is costed with.
         *
         * A zone's outer ring extruded outward by this is its **strip**, and a hard zone's strip is
         * cut out of the kept water, so no triangle survives inside that berth and the search has
         * nothing there to price. The cut is only taken where it **severs nothing** — see
         * [berthReport] — because a strip carrying water that routes is water a boat was using: the
         * zone keeps today's arrangement and the search's own price does the work instead.
         *
         * The two numbers are one fact in two places, the bake's and the search's, and
         * `RouteZoneBerthPropertiesTest` is what keeps them from drifting apart.
         */
        val zoneBerthM: Double = 25.0,
        /** Hard cap on inserted nodes, so a degenerate input cannot exhaust the bake machine. */
        val nodeBudget: Int = 250_000,
        /** How far inside a face a Steiner point is kept (m), so none lands on a constraint. */
        val boundaryInsetM: Double = 0.5,
        /**
         * The berth kept around every regulated zone (m): the zones' own areas are pushed outward by
         * this before the overlay, and no face inside the pushed ring survives, so the mesh's kept
         * water begins this far outside every zone. Regulated zones only — the 300 m band and the
         * coastline are deliberately not buffered (D1, 2026-09-20).
         *
         * **Shipped at zero, on a measurement, 2026-09-20.** At 25 m `nice-menton` loses a third of
         * the water its soundings cover — 46,621 nodes to 10,421, 3.8 MB to 749 KB, 21 stretches to
         * 6 — and two of the four routes the trajectory probe measures collapse from tens of
         * kilometres to 150 m, because the berth does not merely keep a boat off a zone's edge: it
         * takes every zone's *area* out of the water, so a zone can no longer be crossed at its limit
         * and nothing inside one can be reached. That is a different rule from the one requested, and
         * it is the user's to make; the mechanism is here, one number from on.
         */
        val zoneClearanceM: Double = 0.0,
        /**
         * Constraint simplification (m). The coastline arrives at a few metres per vertex, which
         * would put a mesh node every few metres and dwarf the deliberately graded refinement; this
         * moves the land/water boundary by less than the erosion floor and nothing else. The depth
         * gate and the 30 m floor are what keep a node off ground a boat cannot use.
         */
        val simplifyToleranceM: Double = 15.0
    )

    /** One water face of the overlay: its polygon and the mark that its edges carry. */
    private class Face(val polygon: JtsPolygon, val inBand: Boolean)

    /** One triangulated triangle in the local metre frame, tagged with its face's band mark. */
    private class Tri(val a: Coordinate, val b: Coordinate, val c: Coordinate, val inBand: Boolean)

    /** What one zone's strip did under the hybrid berth: the rule's verdict and the water it cost. */
    data class ZoneBerthVerdict(
        /** The ring's own index in the source zone list. */
        val zone: Int,
        /** True when the strip was cut out of the kept water; false when the zone keeps today's arrangement. */
        val hard: Boolean,
        /** The strip's own area (m²): the ring extruded outward, the zone's own area excluded. */
        val stripAreaM2: Double,
        /** The kept water standing in the strip (m²) — cut when hard, left in place and priced when not. */
        val stripWaterM2: Double,
        /** The rule's verdict in words, naming the severance when there was one. */
        val reason: String
    )

    /** How much of the mesh a triangle set makes, read the way [assemble] reads it. */
    data class Tally(val nodes: Int, val edges: Int, val triangles: Int)

    /** The hybrid berth's own reading: the verdicts, the water either side, and the tripwire. */
    data class BerthReport(
        val berthM: Double,
        val zones: List<ZoneBerthVerdict>,
        val waterBeforeM2: Double,
        val waterAfterM2: Double,
        val before: Tally,
        val after: Tally,
        val stretchesBefore: Int,
        val stretchesAfter: Int
    ) {
        val hardZones: Int get() = zones.count { it.hard }
        val pricedZones: Int get() = zones.size - hardZones

        /** The zones whose cut actually found water — a hard zone with nothing to take cost nothing. */
        val cutZones: Int get() = zones.count { it.hard && it.stripWaterM2 > 0.0 }

        /** The water the hard cuts removed (m²), and its share of the water the mesh carried. */
        val removedM2: Double get() = waterBeforeM2 - waterAfterM2
        val removedShare: Double get() = if (waterBeforeM2 <= 0.0) 0.0 else removedM2 / waterBeforeM2

        /**
         * The tripwire the hybrid ships under: past [TRIPWIRE_SHARE] of the mesh's water the hard cuts
         * are doing what the hard-everywhere berth did — the shape the user declined — so that is a
         * finding to report, never a number to accept quietly.
         */
        val tripwireFired: Boolean get() = removedShare > TRIPWIRE_SHARE

        companion object {
            /** The share of the mesh's water past which the hybrid is no longer a hybrid. */
            const val TRIPWIRE_SHARE = 0.01

            /** What a build that never reached the berth carries. */
            val NONE = BerthReport(0.0, emptyList(), 0.0, 0.0, Tally(0, 0, 0), Tally(0, 0, 0), 0, 0)
        }
    }

    /** A node's identity: the metre position rounded to a centimetre, so faces share its vertices. */
    private data class NodeKey(val xi: Long, val yi: Long)

    private val box = geometry.box
    private val gf = GeometryFactory()

    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val mPerDegLon = mPerDegLat * cos(box.centerLat * PI / 180.0)

    private val boxX0 = x(box.lonWest)
    private val boxX1 = x(box.lonEast)
    private val boxY0 = y(box.latSouth)
    private val boxY1 = y(box.latNorth)

    private fun x(lon: Double): Double = (lon - box.centerLon) * mPerDegLon
    private fun y(lat: Double): Double = (lat - box.centerLat) * mPerDegLat
    private fun lon(xM: Double): Double = box.centerLon + xM / mPerDegLon
    private fun lat(yM: Double): Double = box.centerLat + yM / mPerDegLat

    /**
     * Builds the mesh.
     *
     * @return the object-shaped mesh the serializer writes; empty (no node, no edge) when the
     *         geometry carries no water at all, which the bake reports rather than hides.
     */
    fun build(): RouteMesh {
        val boxPolygon = gf.createPolygon(rectRing())
        val coastLines = snapToBox(clip(constraintLines(geometry.coastlineLines()), boxPolygon))
        val zoneRings = constraintRings(geometry.zoneRings())
        val coast = simplify(union(coastLines))

        // **The zone berth.** Every regulated zone's own area, pushed outward by
        // [Tuning.zoneClearanceM] before the overlay: its boundary becomes the constraint the
        // triangulation respects and its inside is dropped, so the mesh's kept water begins that far
        // outside every zone and no leg can hug a zone's edge. The area a ring encloses comes from
        // the polygonizer rather than from `buffer(0)` — which is empty for a closed *line* however
        // well formed the ring is — and that reading is winding-agnostic, which a source ring is not
        // obliged to respect.
        //
        // It is read per ring, in the source rings' own order, because the hybrid berth below weighs
        // each zone's strip on its own and has to name the ring it weighed.
        val zoneAreaOf = geometry.zoneRings().map { raw -> areasOf(toRing(raw)) }
        val zoneAreas = zoneAreaOf.flatten()
        val berth = if (zoneAreas.isEmpty() || tuning.zoneClearanceM <= 0.0) {
            null
        } else {
            union(zoneAreas.map { area -> area.buffer(tuning.zoneClearanceM) })
        }
        // The berth's boundary is the constraint; the rings themselves are not, since the water
        // inside them is no longer water as far as routing is concerned.
        val zoneLines = snapToBox(
            clip(if (berth == null) zoneRings else extractLines(berth.boundary), boxPolygon)
        )

        // The 300 m band, generated here from the analytic predicate rather than from any zone data:
        // the seaward 300 m of the coastline, whose boundary is then a constraint like any other.
        val bandRegion = if (coast.isEmpty) gf.createPolygon() else coast.buffer(tuning.bandM)
        val bandBoundary = simplify(union(snapToBox(extractLines(bandRegion.boundary))))
        val network = union(
            listOf(gf.createLineString(rectRing()), coast, union(zoneLines), bandBoundary)
        )

        val polygonizer = Polygonizer().apply { add(network) }
        val bandPrepared = PreparedGeometryFactory.prepare(bandRegion)
        val berthPrepared = berth?.let { PreparedGeometryFactory.prepare(it) }
        val faces = ArrayList<Face>()
        for (candidate in polygonizer.polygons) {
            val face = candidate as? JtsPolygon ?: continue
            val probe = face.interiorPoint
            if (!geometry.isWater(lat(probe.y), lon(probe.x))) continue
            // The berth, held exactly: because the pushed ring is itself a constraint, the
            // polygonizer never hands over a face that straddles it, so a face's interior point
            // settles the whole face's side.
            if (berthPrepared != null && berthPrepared.covers(probe)) continue
            faces.add(Face(face, bandPrepared.covers(probe)))
        }
        if (faces.isEmpty()) return emptyMesh()

        // An empty constraint set is a real case (a box with no coastline in it at all), and JTS's
        // indexed distance throws on it, so a distance is carried as "nothing to be near". The land
        // distance is separate from the refinement constraints: the channel floor is measured to
        // land, never to a zone or band boundary.
        val constraints = union(listOf(coast, bandBoundary, union(zoneLines)))
        val facetDistance = if (constraints.isEmpty) null else IndexedFacetDistance(constraints)
        val landDistance = if (coast.isEmpty) null else IndexedFacetDistance(coast)

        // The two gate verdicts, kept apart so the bridging pass can see what the strict gate alone
        // would have left: what it must not do is keep an unsounded triangle on its own account.
        val strict = ArrayList<Tri>()
        val unsounded = ArrayList<Tri>()
        for (face in faces) {
            for (tri in triangulate(face, facetDistance, landDistance)) {
                when (depthVerdict(tri)) {
                    Gate.KEEP -> strict.add(tri)
                    Gate.UNKNOWN -> unsounded.add(tri)
                    Gate.SHALLOW -> {}
                }
            }
        }

        val kept = strict + bridgesBetweenStretches(strict, unsounded, facetDistance)
        return assemble(applyZoneBerths(kept, zoneAreaOf.map { union(it) }))
    }

    /**
     * What the hybrid berth decided, zone by zone, with the water it cost — filled by [build].
     *
     * It is the bake's own reading rather than a log: nothing here is printed by the builder, so the
     * bake, a test or a probe can state the rule's verdicts without the build depending on a console.
     */
    var berthReport: BerthReport = BerthReport.NONE
        private set

    // ── Face triangulation ────────────────────────────────────────────────────

    private fun triangulate(
        face: Face,
        facetDistance: IndexedFacetDistance?,
        landDistance: IndexedFacetDistance?
    ): List<Tri> {
        val outer = ringCoordinates(face.polygon.exteriorRing)
        if (outer.size < 3) return emptyList()

        val polygon = P2tPolygon(outer.map { PolygonPoint(it.x, it.y) })
        for (i in 0 until face.polygon.numInteriorRing) {
            val hole = ringCoordinates(face.polygon.getInteriorRingN(i))
            if (hole.size >= 3) polygon.addHole(P2tPolygon(hole.map { PolygonPoint(it.x, it.y) }))
        }
        for (steiner in steinerPoints(face.polygon, facetDistance)) {
            polygon.addSteinerPoint(TPoint(steiner.x, steiner.y))
        }
        Poly2Tri.triangulate(polygon)

        val kept = ArrayList<Tri>()
        for (triangle in polygon.triangles) {
            val a = triangle.points[0]
            val b = triangle.points[1]
            val c = triangle.points[2]
            val tri = Tri(Coordinate(a.x, a.y), Coordinate(b.x, b.y), Coordinate(c.x, c.y), face.inBand)
            if (clearsTheFloor(tri, landDistance)) kept.add(tri)
        }
        return kept
    }

    /**
     * The 30 m channel floor, the gate a triangle passes before any depth question is asked.
     *
     * It is held on the triangle's centroid — a point is traversable only when its whole
     * [Tuning.minChannelWidthM]-wide disc fits in the water, which is exactly "no point within half
     * the floor of land". A passage narrower than the floor therefore loses every triangle inside it
     * and its two banks become two stretches, and an unsounded triangle inside such a passage is
     * refused here too, so the bridging pass can never open a channel the floor has sealed.
     */
    private fun clearsTheFloor(tri: Tri, landDistance: IndexedFacetDistance?): Boolean {
        val floor = tuning.minChannelWidthM / 2.0
        if (floor <= 0.0 || landDistance == null) return true
        val cx = (tri.a.x + tri.b.x + tri.c.x) / 3.0
        val cy = (tri.a.y + tri.b.y + tri.c.y) / 3.0
        return landDistance.distance(gf.createPoint(Coordinate(cx, cy))) >= floor
    }

    /** How a triangle's three vertices fare at the depth gate — see [depthVerdict]. */
    private enum class Gate { KEEP, UNKNOWN, SHALLOW }

    /**
     * The depth gate, read on a triangle's own three vertices.
     *
     * A triangle every vertex of which is sounded deep enough is [Gate.KEEP]; one holding a vertex the
     * soundings call shallower is [Gate.SHALLOW] and is dropped, never priced. A triangle with an
     * unsounded vertex and no shallow one is [Gate.UNKNOWN] — not water the gate vouches for, and not
     * water it refuses — and only [bridgesBetweenStretches] may decide its fate.
     */
    private fun depthVerdict(tri: Tri): Gate {
        var unsounded = false
        for (vertex in arrayOf(tri.a, tri.b, tri.c)) {
            val depth = geometry.depthM(lat(vertex.y), lon(vertex.x))
            if (depth.isFinite()) {
                if (depth < tuning.minDepthM) return Gate.SHALLOW
            } else {
                unsounded = true
            }
        }
        return if (unsounded) Gate.UNKNOWN else Gate.KEEP
    }

    /**
     * **The bridging pass — D4 narrowed to the corridors that merge (2026-09-20).**
     *
     * Reading every missing sounding as *unknown* and keeping its triangle is the version first
     * measured, and it is far too large: it took `nice-menton` from 46,621 nodes, 21 stretches and
     * 3.84 MB to **111,497 nodes, 51 stretches and 9.55 MB**, exhausted the 250,000-node refinement
     * budget and left a 71 km two-node edge spanning the corridor, because the unsounded sea outside
     * the depth grid is most of the box. The reported bay is a pocket, so the rule that admits it has
     * to be one — and what follows is the study's own sentence as a pass: *a missing sounding is kept
     * where it reconnects stretches the gate would otherwise leave separate, and nowhere else.*
     *
     * **The pass floods, and keeps only the corridors the flood used.** Every candidate that touches a
     * stretch of the strictly gated mesh is a source, labelled with that stretch's identity; the flood
     * walks candidate to candidate through shared vertices, carrying its label. When two floods of
     * **different** provenance meet, the two chains of triangles back to their own sources are kept —
     * a bridge — and nothing else is. A candidate whose region touches one stretch only (or none) is
     * never kept on its own account, however large that region is. What it keeps is a *line* through
     * the unknown water, never its area.
     *
     * **And that line is itself bounded.** "Touches one stretch only" is not "contributes nothing":
     * the flood spreads through *every* candidate region that faces a stretch, so the unsounded sea
     * outside the depth grid becomes a source the moment two stretches face it across one, and the
     * first meeting the flood reaches is then a chain of any length — the region's own diameter,
     * tens of kilometres on this coast. So a corridor is kept only when its span, walked centre to
     * centre, is under [CORRIDOR_SPAN_FACTOR] times the local spacing the refinement declares along
     * it: a genuine reconnect across a narrow unsounded slot is a handful of steps, while a chain
     * crossing an unsounded sea is hundreds of them and is refused. The strict mesh's own detour is
     * **not** the second bound it might look like — the two ends are stretches precisely because the
     * strict gate found nothing joining them, so there is no detour to measure against.
     *
     * Three properties are worth stating because they are what the pass must not do. It never *merges*
     * anything by itself — it only adds triangles, and a stretch the strict gate found apart stays
     * apart unless a corridor of unknown water genuinely joins it. It holds the floor: candidates are
     * the triangles that already cleared the 30 m channel floor, so no corridor can open a passage the
     * first gate had sealed. And every corridor is **anchored in vetted water**: a kept triangle must
     * share a vertex with a strict one at each of its two ends, so a corridor can only run between
     * water the gate proved, never strike out into the unsounded sea on a chain of its own.
     *
     * The candidates are ordered canonically, by their own coordinates rather than the polygonizer's,
     * so the corridors a flood finds cannot depend on the order the faces came out.
     *
     * @return the unsounded triangles to add to [strict]; never a replacement for any of them.
     */
    private fun bridgesBetweenStretches(
        strict: List<Tri>,
        candidates: List<Tri>,
        facetDistance: IndexedFacetDistance?
    ): List<Tri> {
        if (strict.isEmpty() || candidates.isEmpty()) return emptyList()
        val ordered = candidates.sortedWith(
            compareBy({ it.a.x }, { it.a.y }, { it.b.x }, { it.b.y }, { it.c.x }, { it.c.y })
        )

        val index = HashMap<NodeKey, Int>()
        val parent = ArrayList<Int>()
        // Filled once the strict mesh has been registered: the roots that stand for a real stretch.
        val stretches = HashSet<Int>()

        fun rootOf(id: Int): Int {
            var root = id
            while (parent[root] != root) root = parent[root]
            var walk = id
            while (parent[walk] != walk) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }

        fun idOf(vertex: Coordinate): Int {
            val key = nodeKey(vertex)
            val known = index[key]
            if (known != null) return known
            val id = parent.size
            parent.add(id)
            index[key] = id
            return id
        }

        /** Joins two vertices, keeping a root that is already a stretch, so a component stays one. */
        fun join(a: Int, b: Int) {
            val rootA = rootOf(a)
            val rootB = rootOf(b)
            if (rootA == rootB) return
            when {
                rootA in stretches -> parent[rootB] = rootA
                rootB in stretches -> parent[rootA] = rootB
                else -> parent[rootB] = rootA
            }
        }

        fun register(tri: Tri) {
            val a = idOf(tri.a)
            val b = idOf(tri.b)
            val c = idOf(tri.c)
            join(a, b)
            join(b, c)
        }

        for (tri in strict) register(tri)
        for (id in parent.indices) stretches.add(rootOf(id))

        // The candidates as a graph: each triangle's three vertex ids, each vertex's own triangles, and
        // the stretches each triangle already touches — read once, since nothing below merges anything.
        val vertices = Array(ordered.size) { IntArray(3) }
        val touching = Array(ordered.size) { IntArray(0) }
        val trianglesOfVertex = HashMap<Int, MutableList<Int>>()
        for (i in ordered.indices) {
            val ids = intArrayOf(idOf(ordered[i].a), idOf(ordered[i].b), idOf(ordered[i].c))
            vertices[i] = ids
            val roots = LinkedHashSet<Int>(3)
            for (id in ids) {
                trianglesOfVertex.getOrPut(id) { ArrayList() }.add(i)
                val root = rootOf(id)
                if (root in stretches) roots.add(root)
            }
            touching[i] = roots.toIntArray()
        }

        val label = IntArray(ordered.size) { UNLABELLED }
        val cameFrom = IntArray(ordered.size) { -1 }
        val kept = BooleanArray(ordered.size)
        // **One corridor per pair of stretches**, which is what keeps the pass a corridor and not a
        // second sea: two stretches that face each other across unknown water meet along their whole
        // facing front, so keeping every meeting would keep the water between them — the failure the
        // first version of this rule made. The first meeting a flood reaches is the shortest path
        // between the pair, and that is the one kept.
        val bridgedPairs = HashSet<Long>()
        val queue = ArrayDeque<Int>()
        for (i in ordered.indices) {
            when {
                // Already a bridge on its own: its vertices stand in two stretches.
                touching[i].size >= 2 -> {
                    kept[i] = true
                    label[i] = touching[i][0]
                    bridgedPairs.add(pairKey(touching[i][0], touching[i][1]))
                    queue.addLast(i)
                }
                touching[i].size == 1 -> {
                    label[i] = touching[i][0]
                    queue.addLast(i)
                }
            }
        }

        /** The chain from [from] back to its own source, meeting triangle first. */
        fun chainToSource(from: Int): List<Int> {
            val chain = ArrayList<Int>()
            var walk = from
            while (walk >= 0 && !kept[walk]) {
                chain.add(walk)
                walk = cameFrom[walk]
            }
            return chain
        }

        /**
         * The corridor bound: the **two** chains' walked span, meeting point to each source, against
         * [CORRIDOR_SPAN_FACTOR] times the local spacing the refinement declares along them.
         *
         * Both halves are measured because the corridor is both of them: bounding each chain alone
         * would licence a corridor twice as long as the bound, one half approaching from each side.
         *
         * The spacing is the bake's own target ([targetSpacing]) at each triangle the corridor
         * crosses, never a measured triangle size: a triangle can carry one edge many kilometres long
         * — the mesh's own longest edge is 11.95 km — so a chain of slivers would measure as short
         * however far it ran. The target is bounded (60 m coastal … 500 m offshore), which makes the
         * bound a real one whatever the triangulation looks like: **no corridor over 4 km can be kept,
         * and none over 480 m beside a constraint**.
         *
         * A meeting where one triangle already stands in both stretches is a hairline, not a chain,
         * and has no span to bound.
         */
        fun withinCorridorBound(a: List<Int>, b: List<Int>): Boolean {
            if (a.size + b.size <= 1) return true
            var span = 0.0
            var spacing = 0.0
            for (chain in arrayOf(a, b)) {
                for ((k, i) in chain.withIndex()) {
                    val local = targetSpacing(gf.createPoint(centreOf(ordered[i])), facetDistance)
                    if (local > spacing) spacing = local
                    if (k > 0) span += centreDistanceM(ordered[chain[k - 1]], ordered[i])
                }
            }
            if (a.isNotEmpty() && b.isNotEmpty()) {
                span += centreDistanceM(ordered[a[0]], ordered[b[0]])
            }
            return span <= CORRIDOR_SPAN_FACTOR * spacing
        }

        /**
         * Keeps the corridor between the two meeting triangles — **if it is short enough to be one**.
         *
         * The bound is applied before anything is marked, so a refused corridor leaves the mesh as the
         * strict gate left it; the pair it belonged to is already recorded as bridged, so the same sea
         * is never walked again looking for a shorter meeting.
         */
        fun keepCorridor(meetingA: Int, meetingB: Int) {
            val a = chainToSource(meetingA)
            val b = chainToSource(meetingB)
            if (!withinCorridorBound(a, b)) return
            for (i in a) kept[i] = true
            for (i in b) kept[i] = true
        }

        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val root = label[i]
            if (root == UNLABELLED) continue
            for (id in vertices[i]) {
                for (j in trianglesOfVertex[id] ?: continue) {
                    if (j == i) continue
                    if (label[j] == UNLABELLED) {
                        label[j] = root
                        cameFrom[j] = i
                        queue.addLast(j)
                    } else if (label[j] != root) {
                        // Two floods of different provenance meet: both corridors are the bridge,
                        // unless this pair of stretches already has one.
                        if (!bridgedPairs.add(pairKey(root, label[j]))) continue
                        keepCorridor(i, j)
                    }
                }
            }
        }

        val bridged = ArrayList<Tri>()
        for (i in ordered.indices) if (kept[i]) bridged.add(ordered[i])
        return bridged
    }

    /** The key two stretch roots make as an unordered pair, so a corridor is kept once per pair. */
    private fun pairKey(a: Int, b: Int): Long =
        (min(a, b).toLong() shl 32) or (max(a, b).toLong() and 0xFFFFFFFFL)

    // ── How long a corridor may be ────────────────────────────────────────────

    /** The distance between two triangles' centres (m), which is what a chain's span walks. */
    private fun centreDistanceM(a: Tri, b: Tri): Double = distanceM(centreOf(a), centreOf(b))

    private fun centreOf(tri: Tri): Coordinate = Coordinate(
        (tri.a.x + tri.b.x + tri.c.x) / 3.0,
        (tri.a.y + tri.b.y + tri.c.y) / 3.0
    )

    /** The distance between two points of the local metre frame (m). */
    private fun distanceM(a: Coordinate, b: Coordinate): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    // ── Bounded adaptive refinement ───────────────────────────────────────────

    /**
     * Steiners for one face, laid on a graded grid.
     *
     * The face's envelope is walked at the offshore spacing; a cell whose centre is not comfortably
     * inside the traversable water is dropped, a cell already at or below its local target contributes
     * its centre, and a coarser one is quartered. Level count, both spacings and [Tuning.nodeBudget]
     * bound the recursion, so the refinement cannot run away on a degenerate face.
     */
    private fun steinerPoints(
        face: JtsPolygon,
        facetDistance: IndexedFacetDistance?
    ): List<Coordinate> {
        val inset = face.buffer(-tuning.boundaryInsetM)
        if (inset.isEmpty) return emptyList()
        val region = PreparedGeometryFactory.prepare(inset)
        val envelope = inset.envelopeInternal
        val step = tuning.offshoreSpacingM
        val out = ArrayList<Coordinate>()
        var yStart = envelope.minY
        while (yStart < envelope.maxY && out.size < tuning.nodeBudget) {
            var xStart = envelope.minX
            while (xStart < envelope.maxX && out.size < tuning.nodeBudget) {
                subdivide(xStart, yStart, step, region, facetDistance, out)
                xStart += step
            }
            yStart += step
        }
        return out
    }

    private fun subdivide(
        xStart: Double,
        yStart: Double,
        size: Double,
        region: PreparedGeometry,
        facetDistance: IndexedFacetDistance?,
        out: MutableList<Coordinate>
    ) {
        if (out.size >= tuning.nodeBudget) return
        val centre = Coordinate(xStart + size / 2.0, yStart + size / 2.0)
        val probe = gf.createPoint(centre)
        if (!region.covers(probe)) return
        val target = targetSpacing(probe, facetDistance)
        if (size <= target || size <= tuning.coastalSpacingM) {
            out.add(centre)
            return
        }
        val half = size / 2.0
        subdivide(xStart, yStart, half, region, facetDistance, out)
        subdivide(xStart + half, yStart, half, region, facetDistance, out)
        subdivide(xStart, yStart + half, half, region, facetDistance, out)
        subdivide(xStart + half, yStart + half, half, region, facetDistance, out)
    }

    /** The local target spacing (m): coastal at a constraint, grading to offshore over the span. */
    private fun targetSpacing(probe: Point, facetDistance: IndexedFacetDistance?): Double {
        val distance = facetDistance?.distance(probe) ?: Double.MAX_VALUE
        val grade = (distance / tuning.refineSpanM).coerceIn(0.0, 1.0)
        return tuning.coastalSpacingM + grade * (tuning.offshoreSpacingM - tuning.coastalSpacingM)
    }

    // ── The hybrid berth ──────────────────────────────────────────────────────

    /**
     * **The hybrid berth — hard only where the cut severs nothing (2026-09-20).**
     *
     * A zone's own outer ring extruded outward by [Tuning.zoneBerthM] is its **strip**. A hard zone's
     * strip is subtracted from the kept water, so no triangle survives inside that berth and the
     * search has no water there to price — exact, and free at search time. It is taken only where it
     * costs nothing that routes, which is **tested rather than assumed**: the same triangles are laid
     * out with the strip removed and their stretch structure is compared with the mesh as it stands,
     * the rule being *the same number of stretches, and no surviving node in a different one*. A cut
     * that fails either reading carried water a boat was using, so the zone reverts to **priced** —
     * today's arrangement, its ring a plain constraint and the search's own berth price doing the
     * work — and the severance is reported with the water the strip held.
     *
     * **The verdicts are cumulative and in the source zones' own order.** Every accepted strip is
     * subtracted before the next zone is weighed, so a zone is judged against the water it will
     * actually be searched on, never against a mesh no route will ever see.
     *
     * Cutting only ever *removes* triangles, so a stretch can only split, never merge; and the water
     * inside a zone is kept — the strip is the ring extruded outward, not the zone's own area — which
     * is what makes this a different rule from [Tuning.zoneClearanceM]'s buffer, the one that takes
     * every zone's interior out of the water. A zone whose own water survives but is only reachable
     * through its strip is therefore caught here as a severance, exactly as it should be.
     */
    private fun applyZoneBerths(triangles: List<Tri>, zoneAreaOf: List<Geometry>): List<Tri> {
        val berthM = tuning.zoneBerthM
        val waterBeforeM2 = triangles.sumOf { areaM2(it) }
        val before = tally(triangles)
        if (berthM <= 0.0 || triangles.isEmpty()) {
            berthReport = BerthReport(berthM, emptyList(), waterBeforeM2, waterBeforeM2, before, before, 1, 1)
            return triangles
        }

        // One id per node, assigned once over the water as it stands: every later reading of the
        // structure quotes the same ids, which is the only thing that makes two of them comparable.
        val ids = HashMap<NodeKey, Int>()
        for (tri in triangles) {
            for (vertex in arrayOf(tri.a, tri.b, tri.c)) {
                if (!ids.containsKey(nodeKey(vertex))) ids[nodeKey(vertex)] = ids.size
            }
        }
        val triIds = Array(triangles.size) { i ->
            val tri = triangles[i]
            intArrayOf(ids[nodeKey(tri.a)]!!, ids[nodeKey(tri.b)]!!, ids[nodeKey(tri.c)]!!)
        }

        /** The stretch each node id stands in under [liveSet], or [ABSENT] when nothing carries it. */
        fun stretchesOf(liveSet: BooleanArray): IntArray {
            val parent = IntArray(ids.size) { it }
            fun find(a: Int): Int {
                var root = a
                while (parent[root] != root) root = parent[root]
                var walk = a
                while (parent[walk] != walk) {
                    val next = parent[walk]
                    parent[walk] = root
                    walk = next
                }
                return root
            }
            fun join(a: Int, b: Int) {
                val rootA = find(a)
                val rootB = find(b)
                if (rootA != rootB) parent[rootB] = rootA
            }
            for (i in triangles.indices) {
                if (!liveSet[i]) continue
                join(triIds[i][0], triIds[i][1])
                join(triIds[i][1], triIds[i][2])
            }
            val out = IntArray(ids.size) { ABSENT }
            for (i in triangles.indices) {
                if (!liveSet[i]) continue
                for (id in triIds[i]) out[id] = find(id)
            }
            return out
        }

        /** The stretches a structure holds, and the nodes they hold. */
        fun stretchCount(roots: IntArray): Int = roots.filter { it != ABSENT }.distinct().size

        /**
         * The rule's own test, as a reason when it fails: **the same number of stretches, and no
         * surviving node in a different one**.
         *
         * Because a cut can only split, two readings compare by their stretch count alone — once a
         * stretch that lost every node is counted: it is a stretch gone, not a stretch kept, and the
         * count would hide it behind a coincidental split.
         */
        fun severanceReason(beforeRoot: IntArray, afterRoot: IntArray): String? {
            val beforeStretches = HashSet<Int>()
            for (id in beforeRoot.indices) {
                if (beforeRoot[id] != ABSENT) beforeStretches.add(beforeRoot[id])
            }
            val survives = BooleanArray(ids.size)
            val afterStretches = HashSet<Int>()
            for (id in afterRoot.indices) {
                val after = afterRoot[id]
                if (after == ABSENT) continue
                afterStretches.add(after)
                survives[beforeRoot[id]] = true
            }
            val gone = beforeStretches.count { !survives[it] }
            return when {
                gone > 0 ->
                    "cut severs: $gone of ${beforeStretches.size} stretch(es) lose every node"
                afterStretches.size > beforeStretches.size ->
                    "cut severs: ${beforeStretches.size} stretch(es) become ${afterStretches.size} — " +
                        "the strip was the only way through"
                else -> null
            }
        }

        // Each strip is found by walking the water with a bounding-box test first. A strip is 25 m of
        // ring around one zone, so the boxes throw almost everything away, and the triangles still
        // standing are the few the exact test is spent on.
        val envelopes = Array(triangles.size) { envelopeOf(triangles[it]) }

        val live = BooleanArray(triangles.size) { true }
        val verdicts = ArrayList<ZoneBerthVerdict>()
        for ((zone, area) in zoneAreaOf.withIndex()) {
            if (area.isEmpty) {
                verdicts.add(ZoneBerthVerdict(zone, false, 0.0, 0.0, "the ring encloses no area"))
                continue
            }
            val strip = area.buffer(berthM).difference(area)
            val stripAreaM2 = strip.area
            val cut = BooleanArray(triangles.size)
            var cutCount = 0
            var cutAreaM2 = 0.0
            val stripEnvelope = strip.envelopeInternal
            for (i in triangles.indices) {
                if (!live[i] || cut[i]) continue
                if (!envelopes[i].intersects(stripEnvelope)) continue
                if (!polygonOf(triangles[i]).intersects(strip)) continue
                cut[i] = true
                cutCount++
                cutAreaM2 += areaM2(triangles[i])
            }
            if (cutCount == 0) {
                verdicts.add(
                    ZoneBerthVerdict(
                        zone, true, stripAreaM2, 0.0,
                        "the strip holds no kept water — the cut takes nothing"
                    )
                )
                continue
            }
            val beforeRoot = stretchesOf(live)
            val afterLive = BooleanArray(live.size) { live[it] && !cut[it] }
            val afterRoot = stretchesOf(afterLive)
            val reason = severanceReason(beforeRoot, afterRoot)
            if (reason == null) {
                for (i in live.indices) live[i] = afterLive[i]
                verdicts.add(
                    ZoneBerthVerdict(
                        zone, true, stripAreaM2, cutAreaM2,
                        "cut severs nothing: $cutCount triangle(s) gone, " +
                            "${stretchCount(afterRoot)} stretch(es) as before"
                    )
                )
            } else {
                verdicts.add(ZoneBerthVerdict(zone, false, stripAreaM2, cutAreaM2, reason))
            }
        }

        val kept = ArrayList<Tri>(triangles.size)
        for (i in triangles.indices) if (live[i]) kept.add(triangles[i])
        berthReport = BerthReport(
            berthM = berthM,
            zones = verdicts,
            waterBeforeM2 = waterBeforeM2,
            waterAfterM2 = waterBeforeM2 - verdicts.filter { it.hard }.sumOf { it.stripWaterM2 },
            before = before,
            after = tally(kept),
            stretchesBefore = stretchCount(stretchesOf(BooleanArray(triangles.size) { true })),
            stretchesAfter = stretchCount(stretchesOf(live))
        )
        return kept
    }

    /** One triangle's area (m²), in the local metre frame it was built in. */
    private fun areaM2(tri: Tri): Double =
        abs((tri.b.x - tri.a.x) * (tri.c.y - tri.a.y) - (tri.b.y - tri.a.y) * (tri.c.x - tri.a.x)) / 2.0

    /** A triangle's bounding box, for the strip query's own prefilter. */
    private fun envelopeOf(tri: Tri): Envelope = Envelope(
        min(tri.a.x, min(tri.b.x, tri.c.x)),
        max(tri.a.x, max(tri.b.x, tri.c.x)),
        min(tri.a.y, min(tri.b.y, tri.c.y)),
        max(tri.a.y, max(tri.b.y, tri.c.y))
    )

    /** A triangle as JTS geometry, so a strip and a triangle can be asked whether they meet. */
    private fun polygonOf(tri: Tri): JtsPolygon =
        gf.createPolygon(gf.createLinearRing(arrayOf(tri.a, tri.b, tri.c, tri.a)))

    /** How many nodes, edges and triangles a set makes — the counts [assemble] would produce. */
    private fun tally(triangles: List<Tri>): Tally {
        val nodeIds = HashMap<NodeKey, Int>()
        val edges = HashSet<Long>()
        for (tri in triangles) {
            val a = nodeIds.getOrPut(nodeKey(tri.a)) { nodeIds.size }
            val b = nodeIds.getOrPut(nodeKey(tri.b)) { nodeIds.size }
            val c = nodeIds.getOrPut(nodeKey(tri.c)) { nodeIds.size }
            if (a != b) edges.add(pairKey(a, b))
            if (b != c) edges.add(pairKey(b, c))
            if (c != a) edges.add(pairKey(c, a))
        }
        return Tally(nodeIds.size, edges.size, triangles.size)
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    private fun assemble(triangles: List<Tri>): RouteMesh {
        val nodeIndex = HashMap<NodeKey, Int>()
        val nodeLat = ArrayList<Double>()
        val nodeLon = ArrayList<Double>()
        val nodeEdges = ArrayList<MutableList<Int>>()
        val edgeFrom = ArrayList<Int>()
        val edgeTo = ArrayList<Int>()
        val edgeLength = ArrayList<Double>()
        val edgeInBand = ArrayList<Boolean>()
        val edgeIndex = HashMap<Long, Int>()
        // The kept triangles, taken as node-index triples rather than thrown away: they are the
        // mesh's own water interior, and shipping them is what turns "is this point inside the mesh"
        // into an exact test instead of an inference from the edges.
        val builtTriangles = ArrayList<RouteTriangle>()

        fun nodeOf(c: Coordinate): Int {
            val key = nodeKey(c)
            val known = nodeIndex[key]
            if (known != null) return known
            val id = nodeLat.size
            nodeLat.add(lat(c.y))
            nodeLon.add(lon(c.x))
            nodeEdges.add(mutableListOf())
            nodeIndex[key] = id
            return id
        }

        fun edgeOf(a: Int, b: Int, inBand: Boolean): Int {
            if (a == b) return -1
            val low = min(a, b)
            val high = max(a, b)
            val key = (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
            val known = edgeIndex[key]
            if (known != null) {
                // An edge on a band boundary is shared by a face inside the band and one outside it:
                // mark it in band so the crossing is priced at the band's own limit.
                if (inBand) edgeInBand[known] = true
                return known
            }
            val id = edgeFrom.size
            edgeFrom.add(low)
            edgeTo.add(high)
            edgeLength.add(metresBetween(nodeLat[low], nodeLon[low], nodeLat[high], nodeLon[high]))
            edgeInBand.add(inBand)
            nodeEdges[low].add(id)
            nodeEdges[high].add(id)
            edgeIndex[key] = id
            return id
        }

        for (tri in triangles) {
            val a = nodeOf(tri.a)
            val b = nodeOf(tri.b)
            val c = nodeOf(tri.c)
            edgeOf(a, b, tri.inBand)
            edgeOf(b, c, tri.inBand)
            edgeOf(c, a, tri.inBand)
            builtTriangles.add(RouteTriangle(a, b, c))
        }

        val components = labelComponents(nodeLat.size, edgeFrom, edgeTo)
        val points = ArrayList<RoutePoint>(nodeLat.size)
        val nodes = ArrayList<RouteNode>(nodeLat.size)
        for (n in nodeLat.indices) {
            val point = RoutePoint(nodeLat[n], nodeLon[n])
            points.add(point)
            nodes.add(RouteNode(point, nodeEdges[n].toList()))
        }
        val edges = ArrayList<RouteEdge>(edgeFrom.size)
        for (e in edgeFrom.indices) {
            edges.add(RouteEdge(edgeFrom[e], edgeTo[e], edgeLength[e], edgeInBand[e]))
        }

        return RouteMesh(
            regionId = regionId,
            box = box,
            points = points,
            components = components.toList(),
            nodes = nodes,
            edges = edges,
            triangles = builtTriangles.toList()
        )
    }

    /** Union-find over the edges: the connected-water label each node carries. */
    private fun labelComponents(nodeCount: Int, edgeFrom: List<Int>, edgeTo: List<Int>): IntArray {
        val parent = IntArray(nodeCount) { it }
        fun find(a: Int): Int {
            var root = a
            while (parent[root] != root) root = parent[root]
            var walk = a
            while (parent[walk] != walk) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }
        for (e in edgeFrom.indices) {
            val rootA = find(edgeFrom[e])
            val rootB = find(edgeTo[e])
            if (rootA != rootB) parent[rootB] = rootA
        }
        val labels = HashMap<Int, Int>()
        val out = IntArray(nodeCount)
        for (n in 0 until nodeCount) {
            val root = find(n)
            out[n] = labels.getOrPut(root) { labels.size }
        }
        return out
    }

    private fun emptyMesh(): RouteMesh = RouteMesh(
        regionId = regionId,
        box = box,
        points = emptyList(),
        components = emptyList(),
        nodes = emptyList(),
        edges = emptyList()
    )

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * A vertex's identity, shared by every pass that has to recognise the same point twice: the metre
     * position rounded to a centimetre, which is finer than any spacing the refinement lays down.
     */
    private fun nodeKey(c: Coordinate): NodeKey = NodeKey(Math.round(c.x * 100.0), Math.round(c.y * 100.0))

    /**
     * The box ring, noded along each edge at the offshore spacing.
     *
     * Four corners would leave each edge of the box a single segment tens of kilometres long, and a
     * constrained triangulation of that is free to lay a sliver along it — a triangle whose three
     * vertices sit far apart and which can therefore bridge a channel or a shallow bar the two coasts
     * would otherwise keep apart. Noding the ring removes the sliver's whole reason to exist, and it
     * costs a few hundred vertices on a corridor this size.
     */
    private fun rectRing(): Array<Coordinate> {
        val step = tuning.offshoreSpacingM
        val out = ArrayList<Coordinate>()
        edgeNodes(boxX0, boxY0, boxX1, boxY0, step, out)
        edgeNodes(boxX1, boxY0, boxX1, boxY1, step, out)
        edgeNodes(boxX1, boxY1, boxX0, boxY1, step, out)
        edgeNodes(boxX0, boxY1, boxX0, boxY0, step, out)
        out.add(Coordinate(boxX0, boxY0))
        return out.toTypedArray()
    }

    /** Appends [from] and the points stepping towards [to] by about [step], excluding [to] itself. */
    private fun edgeNodes(
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
        step: Double,
        out: MutableList<Coordinate>
    ) {
        val span = max(abs(toX - fromX), abs(toY - fromY))
        val parts = max(1, Math.ceil(span / step).toInt())
        for (i in 0 until parts) {
            val t = i.toDouble() / parts
            out.add(Coordinate(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t))
        }
    }

    /**
     * Clamps every vertex onto the box when it is within [SNAP_TO_BOX_M] of it.
     *
     * The overlay's lines have to *meet* the box, not stop a floating-point hair short of it: a leg
     * that ends a nanometre inside leaves the polygonizer a dangling edge, and the face that leg
     * enclosed is silently lost — a wall across the water would simply vanish from the mesh. The
     * clamp is a no-op for every vertex genuinely inside, and moving one by under a millimetre is
     * orders of magnitude below the simplification tolerance and the erosion floor.
     */
    private fun snapToBox(lines: List<LineString>): List<LineString> = lines.map { line ->
        val coordinates = Array(line.numPoints) { i ->
            val c = line.getCoordinateN(i)
            Coordinate(
                c.x.coerceIn(boxX0 - SNAP_TO_BOX_M, boxX1 + SNAP_TO_BOX_M).coerceIn(boxX0, boxX1),
                c.y.coerceIn(boxY0 - SNAP_TO_BOX_M, boxY1 + SNAP_TO_BOX_M).coerceIn(boxY0, boxY1)
            )
        }
        gf.createLineString(coordinates)
    }

    /**
     * @return the polyline as a metre-frame [LineString], or null when it has no usable geometry.
     *
     * A coastline that arrives closed — an island, a hazard ring — must leave this method closed
     * too: keeping the explicit closing vertex *is* what closes it. Collapsing the repeated vertex
     * instead would turn the ring into an open line with a side missing, and a wall or an island
     * would silently vanish from the overlay rather than bound anything.
     */
    private fun toLineString(points: List<RoutePoint>): LineString? {
        val coordinates = dedupeConsecutive(points)
        if (coordinates.size < 2) return null
        return gf.createLineString(coordinates.toTypedArray())
    }

    /** @return the ring as a closed metre-frame [LineString], closing it when it arrives open. */
    private fun toRing(points: List<RoutePoint>): LineString? {
        val coordinates = dedupeConsecutive(points)
        if (coordinates.size < 3) return null
        if (coordinates.first() == coordinates.last()) {
            return if (coordinates.size >= 4) gf.createLineString(coordinates.toTypedArray()) else null
        }
        val closed = ArrayList<Coordinate>(coordinates.size + 1)
        closed.addAll(coordinates)
        closed.add(coordinates.first())
        return gf.createLineString(closed.toTypedArray())
    }

    /** The metre-frame vertices, with consecutive repeats dropped and closure left intact. */
    private fun dedupeConsecutive(points: List<RoutePoint>): List<Coordinate> {
        val out = ArrayList<Coordinate>(points.size)
        for (point in points) {
            val c = Coordinate(x(point.longitude), y(point.latitude))
            if (!c.x.isFinite() || !c.y.isFinite()) continue
            if (out.isEmpty() || out.last() != c) out.add(c)
        }
        return out
    }

    private fun constraintLines(lines: List<List<RoutePoint>>): List<LineString> =
        lines.mapNotNull { toLineString(it) }

    /** @return the rings as closed lines, so the network the polygonizer reads is complete. */
    private fun constraintRings(rings: List<List<RoutePoint>>): List<LineString> =
        rings.mapNotNull { toRing(it) }

    /**
     * @return the areas one closed ring encloses — none when the ring is not a usable one at all.
     *
     * The polygonizer rather than `buffer(0)`, which is empty for a closed *line* however well formed
     * the ring is, and winding-agnostic, which a source ring is not obliged to respect. Read per ring
     * so the hybrid berth can weigh each zone's own strip.
     */
    private fun areasOf(ring: LineString?): List<JtsPolygon> =
        if (ring == null) emptyList()
        else Polygonizer().apply { add(ring) }.polygons.filterIsInstance<JtsPolygon>()

    private fun clip(lines: List<LineString>, bounds: JtsPolygon): List<LineString> =
        lines.flatMap { extractLines(it.intersection(bounds)) }

    private fun extractLines(geometry: Geometry): List<LineString> {
        val out = ArrayList<LineString>()
        for (i in 0 until geometry.numGeometries) {
            when (val part = geometry.getGeometryN(i)) {
                is LineString -> if (part.numPoints >= 2) out.add(part)
                else -> out.addAll(extractLines(part))
            }
        }
        return out
    }

    private fun simplify(geometry: Geometry): Geometry =
        if (geometry.isEmpty) geometry
        else DouglasPeuckerSimplifier.simplify(geometry, tuning.simplifyToleranceM)

    private fun union(parts: Collection<Geometry>): Geometry {
        val present = parts.filter { !it.isEmpty }
        return when (present.size) {
            0 -> gf.createGeometryCollection()
            1 -> present.first()
            else -> UnaryUnionOp.union(present)
        }
    }

    private fun ringCoordinates(ring: LineString): List<Coordinate> {
        val out = ArrayList<Coordinate>(ring.numPoints)
        for (i in 0 until ring.numPoints) {
            val c = ring.getCoordinateN(i)
            if (out.isEmpty() || out.last() != c) out.add(c)
        }
        // Rings are implicitly closed: drop the repeated last vertex poly2tri would choke on.
        if (out.size > 1 && out.first() == out.last()) out.removeAt(out.size - 1)
        return out
    }

    private companion object {
        /** How far off the box a vertex may be and still be clamped onto it (m). */
        const val SNAP_TO_BOX_M = 1.0

        /**
         * How many triangle steps a corridor may span and still count as one: a reconnect across a
         * narrow unsounded slot is a handful of triangles, a chain across an unsounded sea is hundreds
         * of times its own step. Stated rather than derived because it is the policy — how far from a
         * known stretch the mesh is willing to guess — and it is deliberately generous with the
         * spacing, since the refinement's own step is what it is multiplied by.
         */
        const val CORRIDOR_SPAN_FACTOR = 8.0

        /** A bridging candidate the flood has not reached yet — no stretch has claimed it. */
        const val UNLABELLED = -1

        /** A node no surviving triangle carries — the berth's structure reading of "not in the water". */
        const val ABSENT = -1
    }

    /** Geodesic metres between two WGS84 positions, so an edge's length is the distance it spans. */
    private fun metresBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
