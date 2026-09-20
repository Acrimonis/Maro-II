package ykws.android.maro.data.route

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.regulation.SpeedZoneBuilder
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.SpeedZoneIndex

/**
 * The route's read-only view of the live spatial layers, and **the only file in the feature that
 * imports them**: `CoastlineSpatialIndex`, `SpeedZoneIndex` and `RegulatedZonesRepository` appear
 * here and nowhere else under `route`/`ui.map` route code, which is what keeps the search and the
 * domain types free of any dependency on the coastline or the regulation model.
 *
 * It is a delegator with no logic of its own beyond one line of mapping: it holds the two indices
 * the app already builds, answers the search's three questions from them, and reads the band's
 * limit from the one home that owns it ([AppConfig.zoneRegulatorySpeedKn]) rather than declaring a
 * value of its own.
 *
 * Nothing here fetches: [loadIfNeeded] reads what the coastline and regulation repositories have
 * already loaded, so the adapter cannot make the app download anything, and a layer that has not
 * loaded yet simply answers "no limit" until it has.
 */
class RouteSpatialAdapter(
    private val coastline: CoastlineRepository,
    private val regulatedZones: RegulatedZonesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : RoutePointQueries {

    @Volatile
    private var coast: CoastlineSpatialIndex? = null

    @Volatile
    private var speedZones: SpeedZoneIndex? = null

    /**
     * Serialises the build, so each index is built **single-flight**: the initial preparation and the
     * readiness retry can overlap, and the search asks on every route, so without this two callers
     * could build the same index at once.
     */
    private val loadMutex = Mutex()

    /**
     * Builds the two indices from the repositories' current state.
     *
     * Idempotent and cheap after the first call: a layer that is still loading is simply picked up
     * on a later call, which is why this is safe to call on every search rather than needing a
     * lifecycle hook of its own. A concurrent second call waits for the first and then finds each
     * index already built — or still absent, because its layer has not landed, which it re-checks
     * for the price of reading two state values.
     */
    suspend fun loadIfNeeded() {
        loadMutex.withLock {
            withContext(dispatcher) {
                if (coast == null) {
                    val segments = (coastline.state.value as? CoastlineState.Ready)?.data?.allSegments
                    if (!segments.isNullOrEmpty()) coast = CoastlineSpatialIndex(segments)
                }
                if (speedZones == null) {
                    val zoneSet = regulatedZones.zoneSet.value
                    if (zoneSet != null) speedZones = SpeedZoneIndex(SpeedZoneBuilder.build(zoneSet))
                }
            }
        }
    }

    /** No coastline loaded yet reads as water, which is the permissive default for a mesh query. */
    override fun isWater(latitude: Double, longitude: Double): Boolean =
        coast?.isWater(latitude, longitude) ?: true

    /**
     * The most restrictive priced zone covering the point, or null — see [RoutePointQueries].
     *
     * The query is the zone index's own `query`, whose inside-zones list is already sorted by limit
     * ascending, so the first entry is the most restrictive one and nothing has to be re-sorted here.
     * No zone layer loaded yet answers null, which is the permissive default for the search: a route
     * planned before the regulation has loaded is priced as open water rather than refused.
     */
    override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? {
        val index = speedZones ?: return null
        val zone = index.query(latitude, longitude).allInsideZones.firstOrNull() ?: return null
        return PricedZoneRef(zone.id, zone.name, zone.speedLimitKn)
    }

    /**
     * The zone layer's own nearest-boundary reading, unsigned: the index answers with a negative
     * distance inside a zone, and the berth costs the same either side of the edge.
     */
    override fun distanceToZoneM(latitude: Double, longitude: Double): Double {
        val index = speedZones ?: return Double.POSITIVE_INFINITY
        val signed = index.query(latitude, longitude).distanceToBoundaryM
            ?: return Double.POSITIVE_INFINITY
        return abs(signed)
    }

    /**
     * The band's own limit, read from the single setting that owns it — the same value the dashboard
     * and the band's auto-reveal already use, so a corrected regulation moves the route's pricing too.
     */
    override val coastalBandSpeedLimitKn: Double get() = AppConfig.zoneRegulatorySpeedKn.toDouble()
}
