package ykws.android.maro.route

import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stage-0 route configuration — the corner and depth arithmetic of plan §3, §4, §11 and §12.
 *
 * The three user-facing values are `route.standoffM`, `route.cruiseSpeedKn` and `route.maxLateralG`
 * (plan §11.1). They are declared **once, here**. `maro.properties` becomes their home when the
 * Stage-1 runtime reads them; this class then follows that file rather than holding defaults of its
 * own, as `ONE HOME PER FACT` requires. Stage 0 touches no shipped asset — everything in this
 * package lives in `src/test`, so the harness changes no APK (plan §13.1, §13.6).
 *
 * Every quantity the plan leaves open is a constructor parameter, which is what lets Stage 0 answer
 * D3 ([hardStandoff]), D4 ([band300IsZone]) and the curvature floor ([minFilletRadiusM]) by
 * measurement instead of by opinion (plan §13.4).
 */
data class RouteConfig(
    /** Minimum clearance kept from the exclusion geometry (m) — the hard dilation of §11.2. */
    val standoffM: Double = 25.0,
    /** Speed assumed outside any zone (kn) — `route.cruiseSpeedKn`. */
    val cruiseSpeedKn: Double = 25.0,
    /** Lateral acceleration ceiling — the source of every corner radius and speed (§11.3, §12.1). */
    val maxLateralG: Double = 0.2,
    /** Feasibility gate: no leg may sit over water shallower than this (m). */
    val minDepthM: Double = 2.0,
    /** The 2–3 m band is priced, never a second gate (§4): cost rises from here down to [minDepthM]. */
    val preferredDepthM: Double = 3.0,
    /** Metres traded per metre inside the shallow band — objective B's `k_band` (§3). */
    val shallowBandPrice: Double = 2.0,
    /** EMODnet cells reading shallower than this are NoData: the depth feature's own distrust, inherited (§2). */
    val emodnetCutoffM: Float = 2f,
    /** D3 — true: the standoff is a hard dilation of the exclusion geometry; false: a priced band of the same width. */
    val hardStandoff: Boolean = true,
    /** D4 — the 300 m band counts as an implied 5 kn zone. */
    val band300IsZone: Boolean = true,
    /** The band's implied limit when [band300IsZone] (kn) — the French coastal default the band stands for. */
    val band300LimitKn: Double = 5.0,
    /** Price of the standoff band around a speed zone — soft, because a zone stays crossable (§11.2). */
    val zoneStandoffPrice: Double = 2.0,
    /**
     * D1's `k_zone`: extra seconds traded per second inside a zone, sliding objective A towards B
     * (§3). Zero by default, because A is the recommended engine — it already minimises time inside
     * a zone, since time inside the zone is exactly what it prices — and the aversion is the knob
     * that turns it into B. The harness sweeps it rather than assuming it.
     */
    val zoneAversionK: Double = 0.0,
    /** Corridor inflation around the A→B segment (m) — §10.3 step 1, one nautical mile as the starting guess. */
    val corridorMarginM: Double = 1852.0,
    /** Corridor retries on a no-route verdict, each doubling the margin — the stated fallback of §10.5. */
    val corridorRetries: Int = 2,
    /** Simplification tolerance for harvested contour vertices — tied to the standoff (§11.6). */
    val harvestEpsilonM: Double = 25.0 / 4.0,
    /** Vertex budget; above it the harvest re-simplifies with a coarser tolerance. */
    val maxVertices: Int = 900,
    /** Nearest-neighbour edges kept per vertex — the visibility prune. */
    val maxEdgeNeighbours: Int = 10,
    /**
     * Every pair closer than this is kept, so contour and ring chains never break apart. It also
     * bounds the candidate set: a dense contour inside a 400 m ball alone would run to thousands of
     * pairs per vertex, so the default stays a few vertices wide.
     */
    val localEdgeM: Double = 120.0,
    /** Cost integration step along an edge (m) — the time integral only. */
    val costSampleStepM: Double = 25.0,
    /**
     * Step the **hard clearance** is sampled at (m): the router's feasibility test and the fillet
     * fit. It is deliberately half [`validateStepM`], so a leg the router accepts cannot be refused
     * by the validator's own, coarser reading of the same line — one sampling rule, two consumers.
     * A forbidden region narrower than this can still slip between samples; the standoff, one grid
     * cell, is wider than it on both axes.
     */
    val clearanceStepM: Double = 12.5,
    /** Longitudinal ceiling the brake-and-accelerate pair is charged at (§12.3). */
    val longitudinalG: Double = 0.2,
    /** Tightest fillet a forced corner is rounded with; below it the vertex stays sharp (§12.5 open point 2). */
    val minFilletRadiusM: Double = 5.0,
    /** D3's fallback: a lower standoff tried when the first closes the passage; null = fail with a reason (§11.2). */
    val fallbackStandoffM: Double? = null,
    /** Sampling step the emitted polyline is validated at (m). */
    val validateStepM: Double = 25.0,
) {

    /** Cruise speed in metres per second. */
    val cruiseMps: Double get() = cruiseSpeedKn * KNOT_TO_MPS

    /** Lateral acceleration ceiling in m/s². */
    val lateralMps2: Double get() = maxLateralG * G

    /** Longitudinal acceleration ceiling in m/s². */
    val longitudinalMps2: Double get() = longitudinalG * G

    /** `r = v² ÷ a` — the minimum turn radius at a given speed (§11.3). */
    fun minTurnRadiusM(speedMps: Double): Double = speedMps * speedMps / lateralMps2

    /** `v = √(a·r)` — the speed a corner of radius [radiusM] is driven at (§12.1). */
    fun cornerSpeedMps(radiusM: Double): Double = sqrt(lateralMps2 * max(radiusM, 0.0))

    /** Time on an arc of [radiusM] swept through [sweepRad]: `t = Δθ·√(r ÷ a)` (§12.1). */
    fun arcTimeS(radiusM: Double, sweepRad: Double): Double =
        if (radiusM <= 0.0) 0.0 else sweepRad * sqrt(radiusM / lateralMps2)

    /** The radius at which a corner costs no slowdown at all — `r_min(cruise)` (§12.2). */
    val cruiseCornerRadiusM: Double get() = minTurnRadiusM(cruiseMps)

    /**
     * A copy at another standoff, with the harvest tolerance re-tied to it (§11.6) — the fallback
     * tier of §11.2 is a re-run at a stated second value, never a silent relaxation.
     */
    fun withStandoff(m: Double): RouteConfig =
        copy(standoffM = m, harvestEpsilonM = minOf(harvestEpsilonM, m / 4.0))

    companion object {
        /** Knots → metres per second. The epic's own copy lives in `RouteConfig` (§route-search rules). */
        const val KNOT_TO_MPS = 0.514444

        /** Standard gravity (m/s²). */
        const val G = 9.80665

        /** Metres per degree of latitude (mean). */
        const val M_PER_DEG_LAT = 111_320.0

        /** One nautical mile in metres. */
        const val NM_M = 1852.0

        /** Metres per degree of longitude at [lat]. */
        fun mPerDegLon(lat: Double): Double = M_PER_DEG_LAT * cos(Math.toRadians(lat))

        /** Degrees of latitude for [m] metres. */
        fun latDegForM(m: Double): Double = m / M_PER_DEG_LAT

        /** Degrees of longitude for [m] metres at [lat]. */
        fun lonDegForM(m: Double, lat: Double): Double = m / mPerDegLon(lat)
    }
}

/**
 * The corridor of plan §10.3 step 1 and §10.5 — read off the depth grid, which is the only spatial
 * bound Stage 0 has and the one that must be *stated* rather than implied.
 */
object RouteCorridor {

    /** The A→B segment inflated by [marginM] on all four sides. */
    fun bboxOf(a: LatLng, b: LatLng, marginM: Double): BBox {
        val midLat = (a.latitude + b.latitude) / 2.0
        val dLat = RouteConfig.latDegForM(marginM)
        val dLon = RouteConfig.lonDegForM(marginM, midLat)
        return BBox(
            latSouth = min(a.latitude, b.latitude) - dLat,
            latNorth = max(a.latitude, b.latitude) + dLat,
            lonWest = min(a.longitude, b.longitude) - dLon,
            lonEast = max(a.longitude, b.longitude) + dLon,
        )
    }

    /** The depth grid's own extent. */
    fun gridBbox(grid: DepthGrid): BBox = BBox(
        latSouth = grid.boundingBox.latSouth,
        latNorth = grid.boundingBox.latNorth,
        lonWest = grid.boundingBox.lonWest,
        lonEast = grid.boundingBox.lonEast,
    )

    /** [bbox] restricted to [container], or null when the two do not overlap at all. */
    fun clipTo(bbox: BBox, container: BBox): BBox? {
        val out = BBox(
            latSouth = max(bbox.latSouth, container.latSouth),
            latNorth = min(bbox.latNorth, container.latNorth),
            lonWest = max(bbox.lonWest, container.lonWest),
            lonEast = min(bbox.lonEast, container.lonEast),
        )
        return if (out.latSouth >= out.latNorth || out.lonWest >= out.lonEast) null else out
    }

    /** True when [p] falls inside [bbox], optionally expanded by [marginDeg]. */
    fun contains(bbox: BBox, p: LatLng, marginDeg: Double = 0.0): Boolean =
        p.latitude in (bbox.latSouth - marginDeg)..(bbox.latNorth + marginDeg) &&
            p.longitude in (bbox.lonWest - marginDeg)..(bbox.lonEast + marginDeg)

    /**
     * Depth-grid row window covering [bbox], widened by [extraCells] so the standoff band and the
     * dilation kernel are representable inside the raster, and clipped to the grid.
     */
    fun rowsFor(grid: DepthGrid, bbox: BBox, extraCells: Int): IntRange {
        val r0 = floor((bbox.latSouth - grid.boundingBox.latSouth) / grid.cellSizeDegLat).toInt() - extraCells
        val r1 = ceil((bbox.latNorth - grid.boundingBox.latSouth) / grid.cellSizeDegLat).toInt() + extraCells
        return r0.coerceIn(0, grid.rows - 1)..r1.coerceIn(0, grid.rows - 1)
    }

    /** Depth-grid column window covering [bbox], widened by [extraCells] and clipped to the grid. */
    fun colsFor(grid: DepthGrid, bbox: BBox, extraCells: Int): IntRange {
        val c0 = floor((bbox.lonWest - grid.boundingBox.lonWest) / grid.cellSizeDegLon).toInt() - extraCells
        val c1 = ceil((bbox.lonEast - grid.boundingBox.lonWest) / grid.cellSizeDegLon).toInt() + extraCells
        return c0.coerceIn(0, grid.cols - 1)..c1.coerceIn(0, grid.cols - 1)
    }
}
