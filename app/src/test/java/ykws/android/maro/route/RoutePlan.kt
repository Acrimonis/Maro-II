package ykws.android.maro.route

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * One straight leg of the emitted route, with the speed it is actually driven at — plan §12.4: the
 * speed of a segment is `min(cruise, zone limit, corner-limited)`, so the search and the emitted plan
 * use the same numbers.
 */
data class RouteLeg(
    val from: LatLng,
    val to: LatLng,
    val lengthM: Double,
    val speedMps: Double,
    /** The limits this leg is under, most restrictive first: zones, and the 300 m band when D4 says it counts. */
    val limitsKn: List<Double> = emptyList(),
) {

    val timeS: Double get() = if (speedMps <= 0.0) Double.POSITIVE_INFINITY else lengthM / speedMps

    val speedKn: Double get() = speedMps / RouteConfig.KNOT_TO_MPS
}

/**
 * A rounded corner — the fillet of §11.5 and §12.2 plus the speed its radius allows, and the
 * brake-and-accelerate pair §12.3 says belongs in the cost from the first version.
 *
 * [fitted] is false when no arc cleared the water; the vertex then stays sharp and the speed is the
 * floor the profile could justify — never an over-tight arc (§11.5).
 */
data class RouteArc(
    val centre: LatLng,
    val radiusM: Double,
    /** Heading change, in radians, always the smaller of the two directions. */
    val sweepRad: Double,
    val speedMps: Double,
    val lengthM: Double,
    /** Distance spent slowing down into the corner, and back up out of it (m). */
    val brakeDistM: Double = 0.0,
    val accelDistM: Double = 0.0,
    /** Extra seconds the slowdown costs against driving the same legs at their own speed (§12.3). */
    val speedPenaltyS: Double = 0.0,
    val fitted: Boolean = true,
) {

    val timeS: Double get() = if (speedMps <= 0.0) Double.POSITIVE_INFINITY else lengthM / speedMps

    val speedKn: Double get() = speedMps / RouteConfig.KNOT_TO_MPS
}

/** What one run cost to compute — the measurements plan §13.3 asks the harness to print. */
data class RouteMeasure(
    val vertices: Int,
    val edges: Int,
    val states: Int,
    val nodesExpanded: Int,
    val elapsedMs: Long,
    val corridorMarginM: Double,
    val standoffM: Double,
    val harvestEpsilonM: Double,
    val unfittedCorners: Int = 0,
    val corridorRetries: Int = 0,
)

/**
 * A vertex sequence out of the search, before the profile is built.
 *
 * [timeS] is the search's own cost — the leg times plus, when the search is turn-aware, the price of
 * each corner; [edgeTimeS] is the leg times alone, which is the number the Dijkstra reference checks.
 */
data class RoutePath(
    val vertices: List<Int>,
    val points: List<LatLng>,
    val timeS: Double,
    val edgeTimeS: Double,
    val nodesExpanded: Int,
)

/**
 * The route as a **speed profile**, not a polyline (§12.4): the drawn track, the ETA and anything
 * built on them later all need a speed per segment and the corner arcs.
 */
data class RoutePlan(
    val start: LatLng,
    val end: LatLng,
    /** The emitted track: legs and fillets, in order. */
    val points: List<LatLng>,
    val legs: List<RouteLeg>,
    val arcs: List<RouteArc>,
    val distanceM: Double,
    val timeS: Double,
    /** Metres spent inside each zone, keyed by zone name — the zone measurement of §13.3. */
    val zoneDistanceM: Map<String, Double>,
    val measure: RouteMeasure,
) {

    /** Seconds from start to destination. */
    val etaS: Double get() = timeS

    /** Mean speed over the whole route (kn). */
    val averageSpeedKn: Double get() =
        if (timeS <= 0.0) 0.0 else distanceM / timeS / RouteConfig.KNOT_TO_MPS

    /** Straight-line start → destination (m). */
    val straightLineM: Double get() = SpatialOperations.haversine(start, end)

    /** Route length ÷ straight line — 1.0 is a perfectly straight run. */
    val straightLineRatio: Double get() =
        if (straightLineM <= 0.0) 1.0 else distanceM / straightLineM

    val cornerCount: Int get() = arcs.size

    /** Metres inside every zone taken together. */
    val totalZoneDistanceM: Double get() = zoneDistanceM.values.sum()
}

/** A route, or the stated reason there is none (§6, "no-route cases … each needs a stated answer"). */
sealed class RouteOutcome {
    data class Found(val plan: RoutePlan) : RouteOutcome()
    data class NoRoute(val reason: String) : RouteOutcome()
}
