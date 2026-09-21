package ykws.android.maro.route

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A corner as the geometry and the comfort ceiling decide it — plan §12.2: the fillet is the **widest
 * arc that fits** the water and the standoff, up to `r_min(cruise)`, so speed is preserved wherever
 * the water allows and a slower corner is never chosen to shorten a path.
 *
 * [points] is the sampled arc, and the emitted track *is* these points (§11.5) — the geometry the map
 * draws is the geometry that was validated.
 */
data class RouteCorner(
    val centre: LatLng,
    val radiusM: Double,
    val sweepRad: Double,
    val speedMps: Double,
    val arcLengthM: Double,
    val arcTimeS: Double,
    val brakeDistM: Double,
    val accelDistM: Double,
    val penaltyS: Double,
    /** Tangent distance from the corner along each leg — the leg is trimmed by it. */
    val tangentM: Double,
    /** Where the arc meets the incoming and the outgoing leg. */
    val startTangent: LatLng,
    val endTangent: LatLng,
    val points: List<LatLng>,
    /** False when no arc cleared the water: the vertex stays sharp and the speed is the stated floor. */
    val fitted: Boolean,
) {

    /** What this corner adds to the run: the arc, plus the brake-and-accelerate pair of §12.3. */
    val totalTimeS: Double get() = arcTimeS + penaltyS
}

/** A local metric frame around a corner, so the fillet geometry is plain 2D (the projection SpatialOperations uses too). */
private class LocalFrame(private val origin: LatLng) {

    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
    private val mPerDegLon = mPerDegLat * cos(Math.toRadians(origin.latitude))

    fun east(a: LatLng): Double = (a.longitude - origin.longitude) * mPerDegLon

    fun north(a: LatLng): Double = (a.latitude - origin.latitude) * mPerDegLat

    fun toLatLng(eastM: Double, northM: Double): LatLng = LatLng(
        latitude = origin.latitude + northM / mPerDegLat,
        longitude = origin.longitude + eastM / mPerDegLon,
    )

    /** Bearing of a→b in the frame, radians clockwise from north. */
    fun bearing(a: LatLng, b: LatLng): Double = atan2(east(b) - east(a), north(b) - north(a))
}

/**
 * The corner geometry of §11.5 and §12 — one home for it, because the search prices a heading change
 * with the same number the profile later drives (§12.1: "the price of a heading change becomes the
 * time that corner actually costs").
 *
 * A fillet is a circle of radius `r` tangent to both legs. With a signed turn `Δ` (positive = to the
 * right) its centre sits on the interior bisector at `r ÷ cos(Δ÷2)`, the tangent distance from the
 * corner is `T = r·tan(Δ÷2)`, and the arc runs `|Δ|` radians. Everything below follows from those
 * three facts, so there is nothing here to tune.
 */
object RouteFillet {

    private const val STRAIGHT_RAD = 1e-4

    /**
     * Plans the corner at [corner], entered along [prev]→[corner] and left along [corner]→[next].
     *
     * @param prevSpeedMps speed the incoming leg is driven at — the brake pair starts from it.
     * @param nextSpeedMps speed the outgoing leg is driven at.
     * @param prevLegM length of the incoming leg, which caps the fillet's tangent distance.
     * @param nextLegM length of the outgoing leg.
     */
    fun plan(
        prev: LatLng,
        corner: LatLng,
        next: LatLng,
        prevSpeedMps: Double,
        nextSpeedMps: Double,
        prevLegM: Double,
        nextLegM: Double,
        config: RouteConfig,
        context: RouteContext,
        obstacles: RouteObstacles,
    ): RouteCorner {
        val frame = LocalFrame(corner)
        val u1 = frame.bearing(prev, corner)
        val u2 = frame.bearing(corner, next)
        val delta = wrapPi(u2 - u1)
        val sweep = abs(delta)

        if (sweep < STRAIGHT_RAD) {
            return sharp(corner, 0.0, min(prevSpeedMps, nextSpeedMps), prevSpeedMps, nextSpeedMps, prevLegM, nextLegM, config, fitted = true)
        }

        val tanHalf = tan(sweep / 2.0)
        // The tangent distance may not run past the middle of either leg, so neighbouring fillets never meet.
        val radiusLimitByLegs = min(prevLegM, nextLegM) / (2.0 * tanHalf)
        val widest = min(config.cruiseCornerRadiusM, radiusLimitByLegs)

        if (widest > config.minFilletRadiusM) {
            var radius = widest
            repeat(24) {
                if (radius >= config.minFilletRadiusM) {
                    val candidate = geometry(corner, frame, u1, delta, radius, prevSpeedMps, nextSpeedMps, prevLegM, nextLegM, config)
                    if (candidate != null && fits(candidate, prev, next, config, context, obstacles)) return candidate
                }
                radius *= 0.8
            }
        }

        // No arc cleared the water: the vertex stays sharp and the speed is the floor the profile can
        // justify — §12.2's "nothing is unnavigable because of curvature", made explicit rather than
        // silently over-tight.
        val floorSpeed = min(min(prevSpeedMps, nextSpeedMps), config.cornerSpeedMps(config.minFilletRadiusM))
        return sharp(corner, sweep, floorSpeed, prevSpeedMps, nextSpeedMps, prevLegM, nextLegM, config, fitted = false)
    }

    private fun sharp(
        corner: LatLng,
        sweepRad: Double,
        speedMps: Double,
        prevSpeedMps: Double,
        nextSpeedMps: Double,
        prevLegM: Double,
        nextLegM: Double,
        config: RouteConfig,
        fitted: Boolean,
    ): RouteCorner {
        val (brakeDist, accelDist, penalty) =
            slowdown(prevSpeedMps, speedMps, nextSpeedMps, prevLegM, nextLegM, config)
        return RouteCorner(
            centre = corner,
            radiusM = 0.0,
            sweepRad = sweepRad,
            speedMps = speedMps,
            arcLengthM = 0.0,
            arcTimeS = 0.0,
            brakeDistM = brakeDist,
            accelDistM = accelDist,
            penaltyS = penalty,
            tangentM = 0.0,
            startTangent = corner,
            endTangent = corner,
            points = emptyList(),
            fitted = fitted,
        )
    }

    /** The arc itself for a given radius, or null when the geometry is degenerate. */
    private fun geometry(
        corner: LatLng,
        frame: LocalFrame,
        u1: Double,
        delta: Double,
        radius: Double,
        prevSpeedMps: Double,
        nextSpeedMps: Double,
        prevLegM: Double,
        nextLegM: Double,
        config: RouteConfig,
    ): RouteCorner? {
        val sweep = abs(delta)
        val side = if (delta > 0) 1.0 else -1.0
        // The inward normal of each leg; their sum points along the interior bisector.
        val n1 = u1 + side * Math.PI / 2.0
        val n2 = u1 + delta + side * Math.PI / 2.0

        val bisectorEast = sin(n1) + sin(n2)
        val bisectorNorth = cos(n1) + cos(n2)
        val bisectorLen = sqrt(bisectorEast * bisectorEast + bisectorNorth * bisectorNorth)
        if (bisectorLen < 1e-9) return null // legs doubled back: no finite fillet exists

        val cosHalf = cos(sweep / 2.0)
        if (cosHalf <= 1e-9) return null

        val distance = radius / cosHalf
        val centre = frame.toLatLng(
            bisectorEast / bisectorLen * distance,
            bisectorNorth / bisectorLen * distance,
        )
        val tangent = radius * tan(sweep / 2.0)

        // (start − centre) points opposite the incoming normal, so the sweep runs from there by Δ.
        val startAngle = atan2(sin(n1), cos(n1)) + Math.PI
        val steps = max(4, ceil(radius * sweep / config.validateStepM).toInt())
        val points = ArrayList<LatLng>(steps + 1)
        for (i in 0..steps) {
            val angle = startAngle + delta * (i.toDouble() / steps)
            points.add(
                frame.toLatLng(
                    frame.east(centre) + radius * sin(angle),
                    frame.north(centre) + radius * cos(angle),
                )
            )
        }

        val vArc = min(min(prevSpeedMps, nextSpeedMps), config.cornerSpeedMps(radius))
        val (brakeDist, accelDist, penalty) =
            slowdown(prevSpeedMps, vArc, nextSpeedMps, prevLegM, nextLegM, config)

        return RouteCorner(
            centre = centre,
            radiusM = radius,
            sweepRad = sweep,
            speedMps = vArc,
            arcLengthM = radius * sweep,
            arcTimeS = config.arcTimeS(radius, sweep),
            brakeDistM = brakeDist,
            accelDistM = accelDist,
            penaltyS = penalty,
            tangentM = tangent,
            startTangent = points.first(),
            endTangent = points.last(),
            points = points,
            fitted = true,
        )
    }

    /**
     * The arc must stay in water, clear of the standoff and clear of every boundary (§11.5) — and so
     * must the two **trimmed legs** it leaves behind. A fillet shortens the legs, which moves every
     * sample the validator will later take along them, so a candidate that only checks its own arc can
     * still hand the validator a leg it refuses.
     */
    private fun fits(
        corner: RouteCorner,
        prev: LatLng,
        next: LatLng,
        config: RouteConfig,
        context: RouteContext,
        obstacles: RouteObstacles,
    ): Boolean {
        if (!segmentClear(prev, corner.startTangent, config, context)) return false
        if (!segmentClear(corner.endTangent, next, config, context)) return false
        for (p in corner.points) {
            if (context.isForbidden(p.latitude, p.longitude)) return false
        }
        for (i in 0 until corner.points.size - 1) {
            if (obstacles.index.blocked(corner.points[i], corner.points[i + 1])) return false
        }
        return true
    }

    /** The clearance sampling rule the router uses, so neither side can accept what the other refuses. */
    private fun segmentClear(a: LatLng, b: LatLng, config: RouteConfig, context: RouteContext): Boolean {
        val length = SpatialOperations.haversine(a, b)
        if (length <= 0.0) return !context.isForbidden(a.latitude, a.longitude)
        val steps = max(1, ceil(length / config.clearanceStepM).toInt())
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t,
            )
            if (context.isForbidden(p.latitude, p.longitude)) return false
        }
        return true
    }

    /**
     * The brake-and-accelerate pair of §12.3, charged against the legs' own speeds and capped at half
     * a leg — the one term that has to sit in the search's cost, or the cheapest route prefers the
     * shorter, slower corner and inverts the stated priority.
     */
    private fun slowdown(
        prevSpeedMps: Double,
        vArc: Double,
        nextSpeedMps: Double,
        prevLegM: Double,
        nextLegM: Double,
        config: RouteConfig,
    ): Triple<Double, Double, Double> {
        val a = config.longitudinalMps2
        if (a <= 0.0) return Triple(0.0, 0.0, 0.0)

        val brakeWanted = if (prevSpeedMps > vArc) (prevSpeedMps * prevSpeedMps - vArc * vArc) / (2.0 * a) else 0.0
        val accelWanted = if (nextSpeedMps > vArc) (nextSpeedMps * nextSpeedMps - vArc * vArc) / (2.0 * a) else 0.0
        val brakeDist = min(brakeWanted, prevLegM / 2.0)
        val accelDist = min(accelWanted, nextLegM / 2.0)

        // Under constant acceleration the time over a distance is the distance ÷ the mean speed.
        var penalty = 0.0
        if (brakeDist > 0.0 && prevSpeedMps > 0.0) {
            penalty += brakeDist * 2.0 / (prevSpeedMps + vArc) - brakeDist / prevSpeedMps
        }
        if (accelDist > 0.0 && nextSpeedMps > 0.0) {
            penalty += accelDist * 2.0 / (vArc + nextSpeedMps) - accelDist / nextSpeedMps
        }
        return Triple(brakeDist, accelDist, max(0.0, penalty))
    }

    private fun wrapPi(rad: Double): Double {
        var r = rad
        while (r > Math.PI) r -= 2.0 * Math.PI
        while (r < -Math.PI) r += 2.0 * Math.PI
        return r
    }
}

/**
 * Turns a searched vertex path into the emitted **speed profile** (§12.4): trimmed legs, fillets,
 * the speed each is driven at, the ETA, and the metres spent inside each zone.
 *
 * Each leg is shortened by its corners' tangent distances and each corner contributes its arc, so the
 * geometry on the map is exactly the geometry [`RouteValidator`] clears — §11.5's "the emitted track
 * is then the filleted polyline".
 */
class RouteProfileBuilder(
    private val config: RouteConfig,
    private val cost: RouteCostModel,
    private val context: RouteContext,
    private val obstacles: RouteObstacles,
) {

    fun build(path: RoutePath, graph: RouteGraph, measure: RouteMeasure): RoutePlan {
        val n = path.vertices.size
        val legLengths = ArrayList<Double>(max(0, n - 1))
        val legSpeeds = ArrayList<Double>(max(0, n - 1))
        for (i in 0 until n - 1) {
            val a = path.points[i]
            val b = path.points[i + 1]
            legLengths.add(SpatialOperations.haversine(a, b))
            // The search's own edge speed is reused, so both halves of the pipeline price a leg alike.
            legSpeeds.add(graph.edgeBetween(path.vertices[i], path.vertices[i + 1])?.speedMps ?: legSpeedAt(a, b))
        }

        val corners = ArrayList<RouteCorner?>(n)
        corners.add(null)
        for (i in 1 until n - 1) {
            corners.add(
                RouteFillet.plan(
                    prev = path.points[i - 1],
                    corner = path.points[i],
                    next = path.points[i + 1],
                    prevSpeedMps = legSpeeds[i - 1],
                    nextSpeedMps = legSpeeds[i],
                    prevLegM = legLengths[i - 1],
                    nextLegM = legLengths[i],
                    config = config,
                    context = context,
                    obstacles = obstacles,
                )
            )
        }
        corners.add(null)

        val emitted = ArrayList<LatLng>(path.points.size * 2)
        val legs = ArrayList<RouteLeg>(max(0, n - 1))
        val arcs = ArrayList<RouteArc>(max(0, n - 2))
        var distance = 0.0
        var time = 0.0

        emitted.add(path.points.first())
        for (i in 0 until n - 1) {
            val startPoint = emitted.last()
            val endCorner = corners[i + 1]
            val endPoint = if (endCorner?.fitted == true) endCorner.startTangent else path.points[i + 1]

            val legLength = SpatialOperations.haversine(startPoint, endPoint)
            val speed = legSpeeds[i]
            legs.add(RouteLeg(startPoint, endPoint, legLength, speed, limitsOn(startPoint, endPoint)))
            distance += legLength
            if (speed > 0.0) time += legLength / speed
            emitted.add(endPoint)

            if (endCorner != null) {
                if (endCorner.fitted) {
                    emitted.addAll(endCorner.points.drop(1))
                    distance += endCorner.arcLengthM
                }
                time += endCorner.totalTimeS
                // A vertex the path runs straight through is not a corner, and is not reported as one.
                if (endCorner.sweepRad > 1e-9) {
                    arcs.add(
                        RouteArc(
                            centre = endCorner.centre,
                            radiusM = endCorner.radiusM,
                            sweepRad = endCorner.sweepRad,
                            speedMps = endCorner.speedMps,
                            lengthM = endCorner.arcLengthM,
                            brakeDistM = endCorner.brakeDistM,
                            accelDistM = endCorner.accelDistM,
                            speedPenaltyS = endCorner.penaltyS,
                            fitted = endCorner.fitted,
                        )
                    )
                }
            }
        }

        return RoutePlan(
            start = path.points.first(),
            end = path.points.last(),
            points = emitted,
            legs = legs,
            arcs = arcs,
            distanceM = distance,
            timeS = time,
            zoneDistanceM = cost.pathZoneDistanceM(emitted),
            measure = measure.copy(unfittedCorners = arcs.count { !it.fitted }),
        )
    }

    /** Speed of a leg when the graph dropped it: `min(cruise, zone limit)` at the midpoint (§12.4). */
    private fun legSpeedAt(a: LatLng, b: LatLng): Double {
        val mid = LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)
        return cost.speedMpsAt(mid.latitude, mid.longitude)
    }

    private fun limitsOn(a: LatLng, b: LatLng): List<Double> {
        val mid = LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)
        val limits = context.zoneQuery(mid.latitude, mid.longitude).allInsideZones.map { it.speedLimitKn }
        val inBand = config.band300IsZone &&
            context.isWater(mid.latitude, mid.longitude) &&
            context.distanceToCoastM(mid.latitude, mid.longitude) <= RouteContext.BAND_300_M
        return (if (inBand) limits + config.band300LimitKn else limits).sorted()
    }
}

/** A point [distanceM] along [from]→[to]; [to] when the leg is shorter than that. */
internal fun pointAlong(from: LatLng, to: LatLng, distanceM: Double): LatLng {
    val total = SpatialOperations.haversine(from, to)
    if (total <= 0.0 || distanceM <= 0.0) return from
    if (distanceM >= total) return to
    val t = distanceM / total
    return LatLng(
        latitude = from.latitude + (to.latitude - from.latitude) * t,
        longitude = from.longitude + (to.longitude - from.longitude) * t,
    )
}

/**
 * The validator of §10.6 and §6: the emitted polyline is sampled and asserted against the hard
 * constraints — "a smoothed route that is not re-validated is a defect".
 *
 * It reads both masks the search used: the raw gate of §4 (land, depth, source trust) and the
 * exclusion set the standoff produced, so a fillet that cut a corner into the margin is caught here
 * rather than on the water.
 */
object RouteValidator {

    data class Result(
        val samples: Int,
        val blockedSamples: Int,
        val forbiddenSamples: Int,
        val outsideCorridorSamples: Int,
        val violating: List<LatLng>,
    ) {

        val clear: Boolean
            get() = blockedSamples == 0 && forbiddenSamples == 0 && outsideCorridorSamples == 0

        val summary: String
            get() = if (clear) {
                "$samples samples clear"
            } else {
                "$blockedSamples blocked, $forbiddenSamples inside the standoff, " +
                    "$outsideCorridorSamples outside the corridor, of $samples samples"
            }
    }

    fun validate(
        points: List<LatLng>,
        config: RouteConfig,
        context: RouteContext,
        @Suppress("UNUSED_PARAMETER") obstacles: RouteObstacles,
    ): Result {
        var samples = 0
        var blocked = 0
        var forbidden = 0
        var outside = 0
        val violating = ArrayList<LatLng>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val length = SpatialOperations.haversine(a, b)
            if (length <= 0.0) continue
            val steps = max(1, ceil(length / config.validateStepM).toInt())
            for (k in 0..steps) {
                val p = pointAlong(a, b, length * k / steps)
                samples++
                if (context.isBlocked(p.latitude, p.longitude)) {
                    blocked++
                    if (violating.size < 16) violating.add(p)
                } else if (context.isForbidden(p.latitude, p.longitude)) {
                    forbidden++
                    if (violating.size < 16) violating.add(p)
                }
                if (!context.isInsideCorridor(p.latitude, p.longitude)) {
                    outside++
                    if (violating.size < 16) violating.add(p)
                }
            }
        }
        return Result(samples, blocked, forbidden, outside, violating)
    }
}
