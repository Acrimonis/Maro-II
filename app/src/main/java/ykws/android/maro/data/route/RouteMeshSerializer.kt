package ykws.android.maro.data.route

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteMesh
import ykws.android.maro.data.model.RouteMeshArrays
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Serialises the navigation mesh to and from Protocol Buffers.
 *
 * The schema lives beside `coastline.proto` and `depth.proto` in `app/src/main/proto/route.proto`
 * and follows them exactly: packed primitive arrays with CSR offsets, built by hand rather than as
 * per-element messages, so one set of numbers is both the file and what the search reads.
 *
 * Two directions, two shapes: [serialize] takes the object-shaped [RouteMesh] the prebake builder
 * produces, and [deserialize] answers with the packed [RouteMeshArrays] the app keeps — the wire
 * form is the runtime form, with nothing to convert between them at load time.
 */
object RouteMeshSerializer {

    /** @return [mesh] as the bytes the bake writes to `data/app-assets/route/<region>.bin`. */
    fun serialize(mesh: RouteMesh): ByteArray {
        val csr = buildCsr(mesh)
        val builder = RouteProtos.NavigationMesh.newBuilder()
            .setRegionId(mesh.regionId)
            .setLonWest(mesh.box.lonWest)
            .setLonEast(mesh.box.lonEast)
            .setLatSouth(mesh.box.latSouth)
            .setLatNorth(mesh.box.latNorth)
            .addAllPointsLat(mesh.points.map { it.latitude })
            .addAllPointsLon(mesh.points.map { it.longitude })
            .addAllNodeComponent(mesh.components)
            .addAllEdgeFrom(mesh.edges.map { it.from })
            .addAllEdgeTo(mesh.edges.map { it.to })
            .addAllEdgeLengthM(mesh.edges.map { it.lengthM.toFloat() })
            .addAllEdgeInBand(mesh.edges.map { it.inBand })
            .addAllNodeOffsets(csr.offsets.toList())
            .addAllNodeEdges(csr.edges.toList())
            .addAllTriA(mesh.triangles.map { it.a })
            .addAllTriB(mesh.triangles.map { it.b })
            .addAllTriC(mesh.triangles.map { it.c })
        return builder.build().toByteArray()
    }

    /** @return the packed mesh read from the bytes of a baked `.bin`. */
    fun deserialize(bytes: ByteArray): RouteMeshArrays = deserialize(ByteArrayInputStream(bytes))

    /**
     * @return the packed mesh, parsed straight off [input] as the depth bake parses its own grid —
     *         the asset is decoded on the way in, never buffered whole first.
     */
    fun deserialize(input: InputStream): RouteMeshArrays {
        val proto = RouteProtos.NavigationMesh.parseFrom(input)
        return RouteMeshArrays(
            regionId = proto.regionId,
            box = BoundingBox(
                latSouth = proto.latSouth,
                latNorth = proto.latNorth,
                lonWest = proto.lonWest,
                lonEast = proto.lonEast
            ),
            pointsLat = proto.pointsLatList.toDoubleArray(),
            pointsLon = proto.pointsLonList.toDoubleArray(),
            nodeComponent = proto.nodeComponentList.toIntArray(),
            edgeFrom = proto.edgeFromList.toIntArray(),
            edgeTo = proto.edgeToList.toIntArray(),
            edgeLengthM = proto.edgeLengthMList.toFloatArray(),
            edgeInBand = proto.edgeInBandList.toBooleanArray(),
            nodeOffsets = proto.nodeOffsetsList.toIntArray(),
            nodeEdges = proto.nodeEdgesList.toIntArray(),
            triA = proto.triAList.toIntArray(),
            triB = proto.triBList.toIntArray(),
            triC = proto.triCList.toIntArray()
        )
    }

    // ── CSR assembly ──────────────────────────────────────────────────────────

    private class Csr(val offsets: IntArray, val edges: IntArray)

    /**
     * Groups the edge ids by node: `offsets[n] until offsets[n + 1]` selects node `n`'s incident
     * edges out of [Csr.edges]. Each edge is written under both of its ends, which is what makes
     * the single stored edge an undirected one.
     */
    private fun buildCsr(mesh: RouteMesh): Csr {
        val nodeCount = mesh.points.size
        val offsets = IntArray(nodeCount + 1)
        for (edge in mesh.edges) {
            offsets[edge.from + 1]++
            offsets[edge.to + 1]++
        }
        for (n in 1..nodeCount) offsets[n] += offsets[n - 1]

        val cursor = offsets.copyOf()
        val payload = IntArray(offsets[nodeCount])
        for (e in mesh.edges.indices) {
            val edge = mesh.edges[e]
            payload[cursor[edge.from]++] = e
            payload[cursor[edge.to]++] = e
        }
        return Csr(offsets, payload)
    }
}
