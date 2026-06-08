package ykws.android.maro.data.coastline

import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.PointHazard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Converts isolated [PointHazard]s into closed micro-circle [CoastlineSegment]s
 * (island-equivalent rings) so the spatial engine treats them as land/obstruction.
 *
 * Pure & framework-free → directly unit-testable without network or Android.
 *
 * The ring is wound **counter-clockwise** (east → north) to match the island
 * winding the signed-distance band builder assumes, and the first vertex is
 * repeated at the end so the spatial index's `pts[i]→pts[i+1]` edge loop closes —
 * the same closed-ring convention as OSM islands (duplicate head/tail node).
 */
object HazardRings {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Vertices used to approximate each hazard micro-circle. */
    const val DEFAULT_VERTICES = 16

    /** Builds the island-equivalent segment for a single hazard. */
    fun toSegment(
        hazard: PointHazard,
        refLat: Double,
        vertices: Int = DEFAULT_VERTICES
    ): CoastlineSegment = CoastlineSegment(
        osmWayId = 0L,
        points = buildRing(hazard.lat, hazard.lon, hazard.bufferRadiusM, refLat, vertices),
        isMainland = false,
        isClosed = true,
        isHazard = true,
        hazardName = hazard.name.ifBlank { null }
    )

    /**
     * Builds a closed [vertices]-gon ring of radius [radiusM] around a point,
     * returned as enriched [CoastlinePoint]s with projected XY (fixed [refLat])
     * and edge vectors — identical projection conventions to the coastline.
     */
    fun buildRing(
        centerLat: Double,
        centerLon: Double,
        radiusM: Double,
        refLat: Double,
        vertices: Int = DEFAULT_VERTICES
    ): List<CoastlinePoint> {
        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(centerLat))
        val dLat = radiusM / mPerDegLat
        val dLon = radiusM / mPerDegLon

        val ring = ArrayList<LatLng>(vertices + 1)
        for (k in 0 until vertices) {
            val angle = 2.0 * PI * k / vertices
            ring.add(LatLng(centerLat + dLat * sin(angle), centerLon + dLon * cos(angle)))
        }
        ring.add(ring.first())  // close the ring

        return computeEdgeVectors(ring, refLat)
    }

    /**
     * Projects each [LatLng] to local Cartesian (fixed [refLat]) and pre-computes
     * the edge vector to the next vertex. Last point's edge is (0,0).
     * Mirrors `CoastlineGenerator.computeEdgeVectors` for ring geometry.
     */
    private fun computeEdgeVectors(points: List<LatLng>, refLat: Double): List<CoastlinePoint> {
        if (points.isEmpty()) return emptyList()

        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))

        val result = ArrayList<CoastlinePoint>(points.size)
        for (i in points.indices) {
            val lat = points[i].latitude
            val lon = points[i].longitude
            val xM = lon * mPerDegLon
            val yM = lat * mPerDegLat

            if (i < points.size - 1) {
                val edgeMidLat = (lat + points[i + 1].latitude) / 2.0
                val edgeMPLon = mPerDegLat * cos(Math.toRadians(edgeMidLat))
                val dx = (points[i + 1].longitude - lon) * edgeMPLon
                val dy = (points[i + 1].latitude - lat) * mPerDegLat
                result.add(
                    CoastlinePoint(
                        lat = lat.toFloat(), lon = lon.toFloat(),
                        xM = xM.toFloat(), yM = yM.toFloat(),
                        edgeDxM = dx.toFloat(), edgeDyM = dy.toFloat()
                    )
                )
            } else {
                result.add(
                    CoastlinePoint(
                        lat = lat.toFloat(), lon = lon.toFloat(),
                        xM = xM.toFloat(), yM = yM.toFloat(),
                        edgeDxM = 0f, edgeDyM = 0f
                    )
                )
            }
        }
        return result
    }
}
