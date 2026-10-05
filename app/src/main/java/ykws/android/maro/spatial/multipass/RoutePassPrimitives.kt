package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The unified cost field for one search, built fresh so a layer that landed since the last answer
 * is read: the coastline's wall — materialized by the rasterizer's geometry sweep, and answering
 * the clearance the pull's margin reads — and the depth gate when it is enabled and the grid is
 * in, which the rasterizer paints cell by cell.
 *
 * The field is **rebuilt per λ pass** while the grid is not: [lambda] reaches the soft arm's own
 * closure, so a corrective pass costs a few lambdas rather than a second raster sweep. A field
 * built for a grid carries **no soft source at all** — the band's and the rings' limits live on the
 * grid and the A\* prices them — so a grid's field is its hard walls alone.
 *
 * [withZones] and [withBand] are the guard's own doors: a candidate that drops one source's price
 * drops it here too, so the dropped price is dropped from the search and the pull alike. The two
 * arms are combined with **max, never summed** — the band's own price and a ring's are one law off
 * one cursor, so a band cell and a ring cell of the same limit cost the same and two sources over
 * one cell charge it once.
 */
internal fun costField(
    world: MultipassWorld,
    cellM: Double,
    pace: Double,
    withZones: Boolean,
    withBand: Boolean,
    zones: List<SpeedZone>,
    lambda: Double
): RouteCostField {
    val sources = ArrayList<RouteCostSource>(3)
    sources.add(RouteCostSource.Hard(distanceAt = { p -> world.distanceToCoastM(p.latitude, p.longitude) }))
    if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
        sources.add(
            depthGateSource(AppConfig.routeAvoidDepthGateMinM) { p ->
                val sample = world.depthAt(p.latitude, p.longitude)
                if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN
            }
        )
    }
    val bandM = world.bandWidthM
    val bandPriced = withBand && AppConfig.routeAvoidZone300Enabled && bandM > 0.0
    val zonesPriced = withZones && AppConfig.routeAvoidSpeedZoneEnabled && zones.isNotEmpty()
    if (bandPriced || zonesPriced) {
        // **One arm, one price law.** The band's limit's time excess and a zone's are the same
        // quantity through the same function, and the two arms are combined with `max`: a 5 kn band
        // cell and a 5 kn ring cell cost the same, the band follows the pass's λ with every other
        // slow source, and two sources over one cell charge it once rather than twice.
        // `zone300.softCostAversion` is retired.
        val bandOutsideMarginM = AppConfig.routeAvoidZone300OutsideMarginM
        val bandFraction = AppConfig.routeAvoidZone300OutsideMarginCostFraction
        val bandSec = zonePriceSec(cellM, pace, AppConfig.routeAvoidZone300LimitKn, lambda)
        val bandReach = bandReachM(bandM, bandOutsideMarginM)
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        val zoneFraction = AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction
        val scopedZones = zones
        sources.add(
            RouteCostSource.Soft(
                // **The declaration.** The arm is `max(bandPrice, zonePrice)`, so it changes where
                // **either** does and the clearance is the two declarations' minimum. The band's own
                // two circles need no new geometry — `min(|d − width|, |d − reach|)` off the coast
                // distance the arm already reads. A ring's arm changes on the outer ring and on **every
                // hole** (the shipped collar read is blind to a hole) and again at the collar edge, which
                // is `speedZonePriceClearanceM`'s own walk. `MAX_VALUE` where the arm is 0 everywhere:
                // a source that names no boundary proves nothing.
                clearanceAt = { p ->
                    var nearest = Double.MAX_VALUE
                    if (bandPriced && bandSec > 0.0) {
                        val d = world.distanceToCoastM(p.latitude, p.longitude)
                        nearest = minOf(nearest, abs(d - bandM), abs(d - bandReach))
                    }
                    if (zonesPriced) {
                        nearest = minOf(
                            nearest,
                            speedZonePriceClearanceM(
                                scopedZones, emptySet(), p.latitude, p.longitude, zoneOutsideMarginM
                            )
                        )
                    }
                    nearest
                },
                priceSec = { p ->
                    val bandPrice =
                        if (bandPriced && bandSec > 0.0) {
                            bandPriceAt(
                                bandM, bandOutsideMarginM, bandSec, bandFraction,
                                world.distanceToCoastM(p.latitude, p.longitude)
                            )
                        } else {
                            0.0
                        }
                    val zonePrice =
                        if (zonesPriced) {
                            val interiorLimit =
                                strictestLimitKnAt(scopedZones, emptySet(), p.latitude, p.longitude)
                            val collarLimit = speedZoneCollarLimitKnAt(
                                scopedZones, emptySet(), p.latitude, p.longitude, zoneOutsideMarginM
                            )
                            zonePriceAtLimits(
                                cellM, pace, interiorLimit ?: 0.0, collarLimit ?: 0.0, lambda, zoneFraction
                            )
                        } else {
                            0.0
                        }
                    max(bandPrice, zonePrice)
                },
                // The guard reads a price, never a cell's tag, so one arm's tag stands for both.
                tag = MultipassCellState.ZONE
            )
        )
    }
    return RouteCostField(sources)
}

/**
 * The band's law for the rasterizer, or `null` where it is not priced: its own width and limit and
 * the outside margin beyond it. `route.avoid.zone300.enabled` gates it — the switch prices the band,
 * so a priced band is a band whose limit the grid stores; the clock's own read is untouched by the
 * switch (`limitAtFor`).
 */
internal fun bandLaw(world: MultipassWorld): BandLaw? =
    if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
        BandLaw(
            widthM = world.bandWidthM,
            limitKn = AppConfig.routeAvoidZone300LimitKn,
            outsideMarginM = AppConfig.routeAvoidZone300OutsideMarginM
        )
    } else {
        null
    }

/**
 * The limit in force at a point — the clock's own read, and **λ-free by construction**: the ETA
 * obeys the limits and never the price, so the reported time cannot move with the loop's λ.
 *
 * It answers the **strictest limit in force**: the 300 m band's own limit inside the band's width,
 * a speed zone's own limit inside its ring, and the lesser of the two where both hold. The band's
 * read is **the same test the band's price makes** — [insideBandWidthM] over the world's own
 * distance read — so the search and the clock can never disagree about which water is the band.
 * Both reads stand whatever their `enabled` switch says: a switch prices the search, it never
 * suspends the limit.
 */
internal fun limitAtFor(world: MultipassWorld): (LatLng) -> Double? {
    val bandM = world.bandWidthM
    val bandLimitKn = AppConfig.routeAvoidZone300LimitKn
    return { p ->
        val zoneLimit = world.zoneLimitKnAt(p.latitude, p.longitude)
        val bandLimit =
            if (bandM > 0.0 &&
                insideBandWidthM(world.distanceToCoastM(p.latitude, p.longitude), bandM)
            ) bandLimitKn else null
        when {
            zoneLimit == null -> bandLimit
            bandLimit == null -> zoneLimit
            else -> min(zoneLimit, bandLimit)
        }
    }
}

/**
 * Moves a bend onto its nearest tangent corner — the nearest corner whose own set radius contains it,
 * across all sets — only when both legs stay clear; open water keeps the bend.
 *
 * It asks two clearances and four priced segments per candidate, so it is a clearance site in its own
 * right rather than a reader of the field it is handed: it takes the walk's own [coarseStepM] for its
 * legs and the walk's [priceStepM] for its priced segments, so a corner is never moved on a reading
 * the pull would not have made and the two sites keep one partition.
 */
internal fun snapToCorners(
    path: List<LatLng>,
    sets: List<CornerSet>,
    marginM: Double,
    coarseStepM: Double,
    priceStepM: Double,
    field: RouteCostField,
    start: LatLng,
    aim: LatLng,
    approaches: EndApproaches
): List<LatLng> {
    if (sets.all { it.points.isEmpty() }) return path
    val out = path.toMutableList()
    for (i in 1 until path.size - 1) {
        var nearest: LatLng? = null
        var nearestDist = Double.MAX_VALUE
        for (set in sets) {
            for (c in set.points) {
                val d = SpatialOperations.haversine(path[i], c)
                if (d < set.radiusM(c) && d < nearestDist) {
                    nearest = c
                    nearestDist = d
                }
            }
        }
        val corner = nearest ?: continue
        val hardClear =
            MultipassPull.legClear(out[i - 1], corner, marginM, coarseStepM, field, start, aim, approaches) &&
                MultipassPull.legClear(corner, path[i + 1], marginM, coarseStepM, field, start, aim, approaches)
        val replacedPrice = MultipassPull.softPriceSec(out[i - 1], path[i], marginM, priceStepM, field) +
            MultipassPull.softPriceSec(path[i], path[i + 1], marginM, priceStepM, field)
        val snappedPrice = MultipassPull.softPriceSec(out[i - 1], corner, marginM, priceStepM, field) +
            MultipassPull.softPriceSec(corner, path[i + 1], marginM, priceStepM, field)
        if (hardClear && snappedPrice <= replacedPrice) {
            out[i] = corner
        }
    }
    return out
}

/**
 * The metres of a line whose own middle stands inside a priced zone's **interior** — the λ loop's
 * second figure, its share's own objective read in metres rather than in seconds. The test is the
 * world's own interior read, so the outside margin is a price and never a metre counted here.
 */
internal fun zoneMetres(zones: List<SpeedZone>, points: List<LatLng>): Double {
    if (zones.isEmpty()) return 0.0
    var total = 0.0
    for (i in 0 until points.size - 1) {
        val mid = LatLng(
            (points[i].latitude + points[i + 1].latitude) / 2.0,
            (points[i].longitude + points[i + 1].longitude) / 2.0
        )
        if (strictestLimitKnAt(zones, emptySet(), mid.latitude, mid.longitude) != null) {
            total += SpatialOperations.haversine(points[i], points[i + 1])
        }
    }
    return total
}

/**
 * **The ring water, as the share split reads it** — a point inside any priced zone's own ring. It is
 * the world's own interior read, so a collar is a price and never a metre charged to a ring.
 */
internal fun inZone(zones: List<SpeedZone>): (LatLng) -> Boolean =
    { p -> strictestLimitKnAt(zones, emptySet(), p.latitude, p.longitude) != null }

/**
 * **The band's law water, as the share split reads it** — a point inside the band's own width. The
 * exact test the clock and the price make ([insideBandWidthM]), so the split never charges the band
 * for water the band's limit does not govern.
 */
internal fun inBand(world: MultipassWorld): (LatLng) -> Boolean =
    { p ->
        world.bandWidthM > 0.0 &&
            insideBandWidthM(world.distanceToCoastM(p.latitude, p.longitude), world.bandWidthM)
    }

/** The overlap of [box] with [limit], or `null` where the two do not meet. */
internal fun clampTo(box: BBox, limit: BBox): BBox? {
    val out = BBox(
        max(box.latSouth, limit.latSouth),
        min(box.latNorth, limit.latNorth),
        max(box.lonWest, limit.lonWest),
        min(box.lonEast, limit.lonEast)
    )
    return if (out.latSouth >= out.latNorth || out.lonWest >= out.lonEast) null else out
}

/** Whether a point stands inside [box] — the splice's own containment test. */
internal fun inBox(box: BBox, p: LatLng): Boolean =
    p.latitude in box.latSouth..box.latNorth && p.longitude in box.lonWest..box.lonEast
