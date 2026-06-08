package ykws.android.maro.data.coastline

import ykws.android.maro.data.model.BandPolygon
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineMetadata
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data
import kotlin.math.sqrt

/**
 * Serialises [CoastlineData] to/from Protocol Buffers binary format.
 *
 * The Protobuf schema is defined in [app/src/main/proto/coastline.proto].
 * Each coastline point is encoded as 6 packed float32 values:
 *   lat, lon, xM, yM, edgeDxM, edgeDyM  (= 24 bytes per point)
 *
 * Usage:
 *   val bytes = CoastlineSerializer.serialize(data)
 *   val restored = CoastlineSerializer.deserialize(bytes)
 */
object CoastlineSerializer {

    /**
     * Converts [CoastlineData] to a Protobuf byte array for disk caching.
     */
    fun serialize(data: CoastlineData): ByteArray {
        val builder = CoastlineProtos.CoastlineCache.newBuilder()
            .setRegionId(data.regionId)
            .setLonWest(data.boundingBox.lonWest)
            .setLonEast(data.boundingBox.lonEast)
            .setLatSouth(data.boundingBox.latSouth)
            .setLatNorth(data.boundingBox.latNorth)
            .setFetchTimestampMs(data.metadata.fetchTimestampMs)
            .setProjectionRefLat(data.metadata.projectionRefLat)
            .setEpsilonM(data.metadata.epsilonM ?: 0.0)
            .setSource(data.metadata.source)
            .setMainland(segmentToProto(data.mainland))

        for (island in data.islands) {
            builder.addIslands(segmentToProto(island))
        }

        data.zone300?.let { builder.setZone300(zone300ToProto(it)) }

        return builder.build().toByteArray()
    }

    /**
     * Restores [CoastlineData] from a Protobuf byte array read from disk.
     *
     * Computes [CoastlineMetadata.meanSpacingM] and [totalLengthKm] from the
     * edge vectors stored in the packed data rather than storing them redundantly.
     */
    fun deserialize(bytes: ByteArray): CoastlineData {
        val proto = CoastlineProtos.CoastlineCache.parseFrom(bytes)

        val mainland = segmentFromProto(proto.mainland, isMainland = true)
        val islands = proto.islandsList.map { segmentFromProto(it, isMainland = false) }
        val allSegments = listOf(mainland) + islands

        val totalPoints = allSegments.sumOf { it.points.size }
        val totalLength = computeTotalLength(allSegments)
        val meanSpacing = if (totalPoints > allSegments.size) {
            totalLength / (totalPoints - allSegments.size)
        } else 0.0

        return CoastlineData(
            mainland = mainland,
            islands = islands,
            metadata = CoastlineMetadata(
                source = proto.source,
                pointCount = totalPoints,
                meanSpacingM = meanSpacing,
                totalLengthKm = totalLength / 1000.0,
                epsilonM = if (proto.epsilonM != 0.0) proto.epsilonM else null,
                fetchTimestampMs = proto.fetchTimestampMs,
                projectionRefLat = proto.projectionRefLat
            ),
            regionId = proto.regionId,
            boundingBox = BoundingBox(
                latSouth = proto.latSouth,
                latNorth = proto.latNorth,
                lonWest = proto.lonWest,
                lonEast = proto.lonEast
            ),
            zone300 = if (proto.hasZone300()) zone300FromProto(proto.zone300) else null
        )
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Encodes a single [CoastlineSegment] into a Protobuf [Polyline].
     * Each point is serialised as 6 consecutive floats in the packed data array.
     */
    private fun segmentToProto(segment: CoastlineSegment): CoastlineProtos.Polyline {
        val floats = mutableListOf<Float>()
        for (pt in segment.points) {
            floats.add(pt.lat)
            floats.add(pt.lon)
            floats.add(pt.xM)
            floats.add(pt.yM)
            floats.add(pt.edgeDxM)
            floats.add(pt.edgeDyM)
        }
        val builder = CoastlineProtos.Polyline.newBuilder()
            .setOsmWayId(segment.osmWayId)
            .setIsClosed(segment.isClosed)
            .setIsHazard(segment.isHazard)
            .addAllData(floats)
        segment.hazardName?.let { builder.setHazardName(it) }
        return builder.build()
    }

    /**
     * Decodes a Protobuf [Polyline] back into a [CoastlineSegment].
     * The packed data array is consumed in chunks of 6 floats per point.
     */
    private fun segmentFromProto(
        proto: CoastlineProtos.Polyline,
        isMainland: Boolean
    ): CoastlineSegment {
        val data = proto.dataList  // List<Float>
        val chunkSize = 6
        val points = (data.indices step chunkSize).map { i ->
            CoastlinePoint(
                lat = data[i],
                lon = data[i + 1],
                xM = data[i + 2],
                yM = data[i + 3],
                edgeDxM = data[i + 4],
                edgeDyM = data[i + 5]
            )
        }
        return CoastlineSegment(
            osmWayId = proto.osmWayId,
            points = points,
            isMainland = isMainland,
            isClosed = proto.isClosed,
            isHazard = proto.isHazard,
            hazardName = proto.hazardName.takeIf { it.isNotEmpty() }
        )
    }

    // ── Zone300 band ──────────────────────────────────────────────────────────

    private fun zone300ToProto(z: Zone300Data): CoastlineProtos.Zone300 {
        val b = CoastlineProtos.Zone300.newBuilder()
            .setGridCellM(z.gridCellM)
            .setBandM(z.bandM)
        for (poly in z.fillPolygons) {
            val pb = CoastlineProtos.BandPolygon.newBuilder().setOuter(lineToProto(poly.outer))
            for (hole in poly.holes) pb.addHoles(lineToProto(hole))
            b.addFill(pb.build())
        }
        for (line in z.seawardLines) b.addSeaward(lineToProto(line))
        return b.build()
    }

    private fun zone300FromProto(proto: CoastlineProtos.Zone300): Zone300Data =
        Zone300Data(
            fillPolygons = proto.fillList.map { p ->
                BandPolygon(
                    outer = lineFromProto(p.outer),
                    holes = p.holesList.map { lineFromProto(it) }
                )
            },
            seawardLines = proto.seawardList.map { lineFromProto(it) },
            gridCellM = proto.gridCellM,
            bandM = proto.bandM
        )

    /** Encodes a polyline/ring as packed lat/lon float pairs. */
    private fun lineToProto(points: List<LatLng>): CoastlineProtos.LatLngLine {
        val floats = ArrayList<Float>(points.size * 2)
        for (p in points) {
            floats.add(p.latitude.toFloat())
            floats.add(p.longitude.toFloat())
        }
        return CoastlineProtos.LatLngLine.newBuilder().addAllData(floats).build()
    }

    private fun lineFromProto(proto: CoastlineProtos.LatLngLine): List<LatLng> {
        val data = proto.dataList
        return (data.indices step 2).map { i ->
            LatLng(data[i].toDouble(), data[i + 1].toDouble())
        }
    }

    /**
     * Sums all edge vector lengths across all segments.
     * Each point (except the last in a polyline) stores its outgoing edge.
     */
    private fun computeTotalLength(segments: List<CoastlineSegment>): Double {
        var total = 0.0
        for (seg in segments) {
            for (pt in seg.points) {
                total += sqrt(
                    pt.edgeDxM.toDouble() * pt.edgeDxM.toDouble() +
                    pt.edgeDyM.toDouble() * pt.edgeDyM.toDouble()
                )
            }
        }
        return total
    }
}
