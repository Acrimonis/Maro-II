package ykws.android.maro.data.route

import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.depth.DepthSerializer
import ykws.android.maro.data.depth.DepthZoneMask
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.regulation.RegulatedZoneSerializer
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.SpeedZoneIndex
import java.io.File

/**
 * The bake-time world, and the live answers the search is priced with — read straight off the `.bin`
 * files the app also ships.
 *
 * It lives here, beside the builder and the probe, because **the bake and every reading of the bake
 * must see the same world**: the prebake builds the mesh through this geometry and the trajectory
 * probe measures that mesh through it, so a disagreement between them is impossible by construction
 * rather than by both files being edited together. Nothing in `src/main` implements or imports
 * either class.
 *
 * The caller owns the refusal: a missing input is the bake's to name (`tools\bake-route.bat`, and the
 * prebake test's own `require`), while a reader that only wants a number may skip instead — so
 * [PrebakedInputs.load] reads what it is given and does not decide that question.
 */

/** The *bande des 300 m* limit, as the band layer owns it — the value an in-band edge is priced at. */
internal const val COASTAL_BAND_SPEED_LIMIT_KN = 5.0

/** The three prebaked inputs the bake and the probe both read, each a `.bin` the app ships. */
internal class PrebakedInputs(
    val coast: CoastlineData,
    val zones: RegulatedZoneSet,
    val depth: DepthGrid
) {
    companion object {
        /**
         * The paths the app's own asset tree uses, so a probe and a bake never disagree about which
         * file they read. Every file must already exist — see the class note on who refuses.
         */
        fun load(repoDir: File, region: String): PrebakedInputs = PrebakedInputs(
            CoastlineSerializer.deserialize(
                File(repoDir, "data/app-assets/coastlines/$region.bin").readBytes()
            ),
            RegulatedZoneSerializer.deserialize(
                File(repoDir, "data/app-assets/regulated-zones/$region.bin").readBytes()
            ),
            DepthSerializer.deserialize(
                File(repoDir, "data/app-assets/depth/$region.bin").readBytes()
            )
        )

        /** The same three paths, so a caller can test them for existence before [load] reads them. */
        fun paths(repoDir: File, region: String): List<File> = listOf(
            File(repoDir, "data/app-assets/coastlines/$region.bin"),
            File(repoDir, "data/app-assets/regulated-zones/$region.bin"),
            File(repoDir, "data/app-assets/depth/$region.bin")
        )
    }
}

/** @see PrebakedInputs */
internal class PrebakedRouteGeometry(
    private val coast: CoastlineData,
    private val zones: RegulatedZoneSet,
    private val depth: DepthGrid
) : RouteGeometry {

    private val index = CoastlineSpatialIndex(coast.allSegments)

    override val box: BoundingBox =
        DepthZoneMask.envelopeOf(coast.boundingBox, DepthZoneMask.SIX_NM_M)

    override fun coastlineLines(): List<List<RoutePoint>> = coast.allSegments.map { segment ->
        segment.points.map { RoutePoint(it.lat.toDouble(), it.lon.toDouble()) }
    }

    override fun zoneRings(): List<List<RoutePoint>> = zones.zones.map { zone ->
        zone.outerRing.map { RoutePoint(it.latitude, it.longitude) }
    }

    /** The app's own water test: inside the coast, or out past the 6 NM the licence covers. */
    override fun isWater(latitude: Double, longitude: Double): Boolean {
        if (index.query(latitude, longitude).distanceMeters > DepthZoneMask.SIX_NM_M) return true
        return index.isWater(latitude, longitude)
    }

    /** The depth the gate reads, straight off the grid's own cell — `NaN` outside it. */
    override fun depthM(latitude: Double, longitude: Double): Double {
        val row = ((latitude - depth.boundingBox.latSouth) / depth.cellSizeDegLat).toInt()
        val col = ((longitude - depth.boundingBox.lonWest) / depth.cellSizeDegLon).toInt()
        if (row < 0 || row >= depth.rows || col < 0 || col >= depth.cols) return Double.NaN
        return depth.depths[row * depth.cols + col].toDouble()
    }
}

/** @see PrebakedInputs */
internal class PrebakedQueries(
    private val geometry: RouteGeometry,
    zoneSet: RegulatedZoneSet
) : RoutePointQueries {

    private val zones = SpeedZoneIndex(SpeedZoneBuilder.build(zoneSet))

    override fun isWater(latitude: Double, longitude: Double): Boolean =
        geometry.isWater(latitude, longitude)

    /** The same live zone layer the app prices with, read as the zone rather than only its limit. */
    override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? {
        val zone = zones.query(latitude, longitude).allInsideZones.firstOrNull() ?: return null
        return PricedZoneRef(zone.id, zone.name, zone.speedLimitKn)
    }

    /** The same live zone layer the app prices with, unsigned — see [RoutePointQueries]. */
    override fun distanceToZoneM(latitude: Double, longitude: Double): Double {
        val signed = zones.query(latitude, longitude).distanceToBoundaryM
            ?: return Double.POSITIVE_INFINITY
        return kotlin.math.abs(signed)
    }

    /** The 300 m band's own live limit — the value the band's mark is priced at. */
    override val coastalBandSpeedLimitKn: Double = COASTAL_BAND_SPEED_LIMIT_KN
}
