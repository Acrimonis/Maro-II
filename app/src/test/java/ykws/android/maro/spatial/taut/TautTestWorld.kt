package ykws.android.maro.spatial.taut

import kotlin.math.min
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.SpatialOperations

/**
 * **A world small enough to reason about by hand, and shaped like the one the engine reads.**
 *
 * It answers the tracer's questions from polygons rather than from a raster: land is a set of closed
 * rings, shallow water is a second set, the deep water between them is a constant sounding, and the
 * coastline distance is computed exactly. That is what makes the engine's own assertions checkable —
 * a route that bends the wrong way, a leg that clips a wall or a price that disagrees with the drawn
 * clock all read as a failure here rather than as a plausible-looking line.
 */
internal class TautTestWorld(
    /** Land, as closed rings — the wall's first source. */
    val land: List<List<LatLng>> = emptyList(),
    /** Water shallower than the 2 m gate, as closed rings — walled where the sounding is fine. */
    val shallow: List<List<LatLng>> = emptyList(),
    /** The 2 m contour the depth feature would trace, as open polylines or rings. */
    val contours: List<List<LatLng>> = emptyList(),
    val zones: List<TautZoneShape> = emptyList(),
    /** The sounding everywhere it is neither land nor shallow, in metres. */
    val deepM: Double = 30.0,
    override val depthBox: BoundingBox? = null,
    override val coastalBandWidthM: Double = 0.0,
    override val coastalBandSpeedLimitKn: Double = 5.0,
    /** True when the world has no depth grid at all — the engine's own refusal to answer. */
    private val gridLoaded: Boolean = true,
    /**
     * True when the coastline is in. False is the window §17 item 3 closes: land answers as water and the
     * shore offset applies nowhere, so an engine that read one layer alone would draw a line over land.
     */
    private val coastLoaded: Boolean = true,
    /**
     * **The world's own generation, and a `var` here on purpose** (§19.4): a test that keeps a terrain and
     * then moves the world has to be able to move this too, which is the only way the invalidator can be
     * read rather than asserted. The shapes themselves are fixed once the fake is built, so this is the
     * *whole* of what "the world changed" means for this world.
     */
    override var generation: Int = 0
) : TautWorld {

    /**
     * **The seam's one readiness member, answered by this fake too.**
     *
     * The world is ready when both layers it draws its wall from are in — the grid the 2 m gate and the
     * contour come from, and the coastline the land and the shore offset come from — and either absence
     * is named rather than collapsed into a boolean, so the engine's own reason is readable from here.
     */
    override val readiness: TautWorldReadiness
        get() = when {
            !gridLoaded || depthBox == null -> TautWorldReadiness.DEPTH_MISSING
            !coastLoaded -> TautWorldReadiness.COASTLINE_MISSING
            else -> TautWorldReadiness.READY
        }

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        land.none { contains(it, latitude, longitude) }

    override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? {
        for (zone in zones) {
            if (!contains(zone.outerRing, latitude, longitude)) continue
            if (zone.holes.any { contains(it, latitude, longitude) }) continue
            return PricedZoneRef(zone.id, zone.name, zone.limitKn)
        }
        return null
    }

    override fun distanceToZoneM(latitude: Double, longitude: Double): Double {
        var best = Double.POSITIVE_INFINITY
        for (zone in zones) {
            for (ring in listOf(zone.outerRing) + zone.holes) {
                for (i in 0 until ring.size - 1) {
                    val distance = SpatialOperations.pointToSegmentDistance(
                        LatLng(latitude, longitude), ring[i], ring[i + 1]
                    )
                    if (distance < best) best = distance
                }
            }
        }
        return best
    }

    /**
     * **How many times this world was asked for its coastline** — the fake's own instrument for the
     * kept-terrain reading (§19.4).
     *
     * A search that reuses a terrain never asks: the harvest and the band are the two callers, and both
     * live inside `TautTerrain.of`. That makes "the coast work did not run" a **deterministic** reading
     * rather than a millisecond comparison a synthetic corridor would drown in noise — which is what b1 is,
     * work not done, and it is why the counter is here and the flag on the dossier is only half the pair.
     */
    var landReads: Int = 0
        private set

    override fun landPolylinesIn(box: BoundingBox): List<List<LatLng>> {
        landReads++
        return land
    }

    override fun zoneShapesIn(box: BoundingBox): List<TautZoneShape> = zones

    override fun shallowContoursIn(box: BoundingBox): List<List<LatLng>> = contours

    override fun depthSampleAt(latitude: Double, longitude: Double): DepthSample {
        if (!gridLoaded) return DepthSample.NONE
        if (shallow.any { contains(it, latitude, longitude) }) {
            return DepthSample(1.0f, DepthSource.LITTO3D, 90, true)
        }
        return DepthSample(deepM.toFloat(), DepthSource.LITTO3D, 90, true)
    }

    /** Exact, and deliberately so: the shore offset's whole meaning is a distance from the coast. */
    override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
        var best = Double.POSITIVE_INFINITY
        for (ring in land) {
            for (i in 0 until ring.size - 1) {
                val distance = SpatialOperations.pointToSegmentDistance(
                    LatLng(latitude, longitude), ring[i], ring[i + 1]
                )
                if (distance < best) best = distance
            }
        }
        return best
    }

    companion object {

        /** A rectangle as a closed ring, so a test's geometry reads as the shape it means. */
        fun rect(south: Double, west: Double, north: Double, east: Double): List<LatLng> = listOf(
            LatLng(south, west),
            LatLng(south, east),
            LatLng(north, east),
            LatLng(north, west),
            LatLng(south, west)
        )

        fun contains(ring: List<LatLng>, latitude: Double, longitude: Double): Boolean {
            if (ring.size < 3) return false
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val yi = ring[i].latitude
                val xi = ring[i].longitude
                val yj = ring[j].latitude
                val xj = ring[j].longitude
                if (((yi > latitude) != (yj > latitude)) &&
                    (longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi)
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }

        /** The fewest metres from a point to a ring's own segments. */
        fun distanceToRing(ring: List<LatLng>, latitude: Double, longitude: Double): Double {
            var best = Double.POSITIVE_INFINITY
            for (i in 0 until ring.size - 1) {
                val distance = SpatialOperations.pointToSegmentDistance(
                    LatLng(latitude, longitude), ring[i], ring[i + 1]
                )
                best = min(best, distance)
            }
            return best
        }
    }
}
