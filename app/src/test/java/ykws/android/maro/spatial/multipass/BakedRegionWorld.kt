package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.depth.DepthSerializer
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.regulation.RegulatedZoneSerializer
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.LandRingOrientation
import java.io.File

/**
 * **The region the app ships, read from the bake's own files and answered the way the live world answers it.**
 *
 * [LiveMultipassWorld] cannot be fed bytes on a JVM — its two repositories load through an Android context —
 * so this restates its reads over the same three deserialisers the app uses: the baked coastline into the
 * app's own [CoastlineSpatialIndex], the baked depth grid read through the chart's EMODnet cutoff exactly as
 * the router reads it, and the baked speed zones through [SpeedZoneBuilder]. No marker is costed unless
 * [withMarkers] hands in stored ones, projected by the live world's own [routeMarkersOf], and nothing is
 * generated or synthesised: a line routed over this world is routed over the water the device holds.
 *
 * It exists for measurements that a hand-built fixture cannot stand for — a real island chain, a real shelf,
 * the real 300 m band and the real zones. The bake is gitignored, so [load] answers `null` where the files
 * are absent and the caller skips rather than fails.
 */
internal class BakedRegionWorld private constructor(
    private val coastline: CoastlineData,
    private val index: CoastlineSpatialIndex,
    private val grid: DepthGrid,
    private val zones: List<SpeedZone>,
    private val markers: List<RouteMarker> = emptyList()
) : MultipassWorld {

    private val bounds = coastline.boundingBox
    private val cutoffM = AppConfig.depthEmodnetShallowCutoffM
    private val gateMinM = AppConfig.routeAvoidDepthGateMinM.toFloat()
    private val edgesByMin = HashMap<Double, ShallowEdgeIndex>()

    override val coastlineReady: Boolean get() = true
    override val depthReady: Boolean get() = true
    override val bandWidthM: Double get() = 300.0
    override val emodnetCutoffM: Float get() = cutoffM
    override val regionBounds: BBox get() = BBox(bounds.latSouth, bounds.latNorth, bounds.lonWest, bounds.lonEast)
    override val latticeAnchor: LatticeAnchor
        get() = LatticeAnchor(grid.boundingBox.latSouth, grid.boundingBox.lonWest, grid.boundingBox.centerLat)

    override fun segmentsIn(box: BBox): List<MultipassEdge> =
        index.segmentsInBbox(box).mapNotNull { seg ->
            val orientation = index.landRingOrientation(seg.polylineIdx)
            if (orientation == LandRingOrientation.OPEN_COAST) null else MultipassEdge(seg.a, seg.b, orientation)
        }

    override fun openCoastIn(box: BBox): List<List<LatLng>> = index.openCoastPolylinesIn(box)

    override fun isWater(latitude: Double, longitude: Double): Boolean = index.isWater(latitude, longitude)

    override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
        index.query(latitude, longitude).distanceMeters

    override fun depthAt(latitude: Double, longitude: Double): DepthSample =
        grid.depthAtFilled(latitude, longitude, cutoffM, gateMinM)

    override fun shallowEdges(minDepthM: Double): ShallowEdgeIndex =
        edgesByMin.getOrPut(minDepthM) { shallowEdgesOver(grid, cutoffM, minDepthM) }

    override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())

    override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
        strictestLimitKnAt(zones, emptySet(), latitude, longitude)

    override fun zoneDisplayLimitKnAt(latitude: Double, longitude: Double): Double? =
        strictestDisplayLimitKnAt(zones, emptySet(), latitude, longitude)

    override fun routeMarkers(): List<RouteMarker> = markers

    /**
     * This world with [stored] markers costed as the live world costs them — the device's own marker file — on
     * the same coastline index. The depth reads take the [AppConfig] values in force when it is called.
     */
    fun withMarkers(stored: List<UserMarker>): BakedRegionWorld =
        BakedRegionWorld(coastline, index, grid, zones, routeMarkersOf(stored))

    companion object {

        /** The world over [region]'s baked files under [repoDir], or `null` where any of the three is absent. */
        fun load(repoDir: File, region: String): BakedRegionWorld? {
            val assets = File(repoDir, "data/app-assets")
            val coastFile = File(assets, "coastlines/$region.bin")
            val depthFile = File(assets, "depth/$region.bin")
            val zonesFile = File(assets, "regulated-zones/$region.bin")
            if (!coastFile.exists() || !depthFile.exists()) return null
            val coast = CoastlineSerializer.deserialize(coastFile.readBytes())
            val depth = depthFile.inputStream().buffered().use { DepthSerializer.deserialize(it) }
            val zones = if (zonesFile.exists()) {
                SpeedZoneBuilder.build(RegulatedZoneSerializer.deserialize(zonesFile.readBytes()))
            } else {
                emptyList()
            }
            return BakedRegionWorld(coast, CoastlineSpatialIndex(coast.allSegments), depth, zones)
        }
    }
}
