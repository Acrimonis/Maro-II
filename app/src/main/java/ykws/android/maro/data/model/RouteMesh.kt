package ykws.android.maro.data.model

/**
 * The navigation mesh in its object shape: what the prebake builder produces and the serializer
 * writes.
 *
 * The type the app loads and the search reads is [RouteMeshArrays], the packed form of exactly
 * these numbers — one representation serves file, memory and search, and nothing converts between
 * the two at runtime.
 *
 * @property regionId   the region the mesh was baked for (`BuildConfig.REGION_ID`).
 * @property box        the mesh's own water envelope, reusing [BoundingBox].
 * @property points     one vertex per node.
 * @property components connected-water label per node — the stretch a route's ends must share.
 * @property nodes      per-node incidence lists.
 * @property edges      every edge, stored once.
 * @property triangles  every kept triangle, stored once — the mesh's own water interior.
 */
data class RouteMesh(
    val regionId: String,
    val box: BoundingBox,
    val points: List<RoutePoint>,
    val components: List<Int>,
    val nodes: List<RouteNode>,
    val edges: List<RouteEdge>,
    val triangles: List<RouteTriangle> = emptyList()
)

/**
 * One kept triangle as its three node indices, wound in no particular order.
 *
 * It exists because an edge set has no interior: the bake knows exactly which triangles its depth
 * gate and channel floor kept, and shipping them turns "is this point on the water the mesh holds"
 * from an inference into an exact test. Nothing in the search reads them.
 */
data class RouteTriangle(val a: Int, val b: Int, val c: Int)

/**
 * The packed, flat form of a [RouteMesh] — the shape the `.bin` holds and the shape the search
 * walks. Parallel primitive arrays only: no per-node object, no per-edge object, no boxing.
 *
 * `nodeEdges` is a CSR payload, grouped by node, with `nodeOffsets[n] until nodeOffsets[n + 1]`
 * selecting node `n`'s incident edges. Each edge is listed under both of its ends, so this single
 * set of numbers is an undirected graph.
 *
 * `triA` / `triB` / `triC` are the kept triangles as node-index triples — the mesh's water interior,
 * which is what [RouteMeshContainment] answers a point query with. They default to empty, and an
 * empty set means "nothing is proved to be inside": a mesh read from a `.bin` baked before the
 * triangles were shipped behaves exactly as it did, with no point claimed to be on its water.
 */
class RouteMeshArrays(
    val regionId: String,
    val box: BoundingBox,
    val pointsLat: DoubleArray,
    val pointsLon: DoubleArray,
    val nodeComponent: IntArray,
    val edgeFrom: IntArray,
    val edgeTo: IntArray,
    val edgeLengthM: FloatArray,
    val edgeInBand: BooleanArray,
    val nodeOffsets: IntArray,
    val nodeEdges: IntArray,
    val triA: IntArray = IntArray(0),
    val triB: IntArray = IntArray(0),
    val triC: IntArray = IntArray(0)
) {
    /** Number of mesh nodes. */
    val nodeCount: Int get() = pointsLat.size

    /** Number of mesh edges. */
    val edgeCount: Int get() = edgeFrom.size

    /** Number of kept triangles. */
    val triangleCount: Int get() = triA.size

    /** @return node [index] as a [RoutePoint]. */
    fun point(index: Int): RoutePoint = RoutePoint(pointsLat[index], pointsLon[index])

    /** @return edge [index] in its object shape — for the serializer and tests, not the search. */
    fun edge(index: Int): RouteEdge =
        RouteEdge(edgeFrom[index], edgeTo[index], edgeLengthM[index].toDouble(), edgeInBand[index])
}
