package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
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
    lambda: Double,
    /**
     * **The marker price's strength** — the Routing row's own factor, threaded as a live value beside
     * [pace] so a slider move reaches the next arm. The marker law is `base × step × strength`.
     */
    markerStrength: Double = 1.0
): RouteCostField {
    val sources = ArrayList<RouteCostSource>(3)
    sources.add(RouteCostSource.Hard(distanceAt = { p -> world.distanceToCoastM(p.latitude, p.longitude) }))
    if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
        sources.add(
            depthGateSource(AppConfig.routeAvoidDepthGateMinM) { p -> depthMAt(world, p) }
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
        // The zones with their boxes settled once per field, so each per-point read rejects by box first.
        val scopedZones = ZoneSet(zones)
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
                            scopedZones.priceClearanceM(p.latitude, p.longitude, zoneOutsideMarginM)
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
                            val interiorLimit = scopedZones.strictestLimitKnAt(p.latitude, p.longitude)
                            val collarLimit = scopedZones.collarLimitKnAt(p.latitude, p.longitude, zoneOutsideMarginM)
                            zonePriceAtLimits(
                                cellM, pace, interiorLimit ?: 0.0, collarLimit ?: 0.0, lambda, zoneFraction
                            )
                        } else {
                            0.0
                        }
                    max(bandPrice, zonePrice)
                },
                // The guard reads a price, never a cell's tag, so one arm's tag stands for both.
                tag = MultipassCellState.ZONE,
                // **The arm a report names.** The source's own price is the two arms' `max`, so it
                // answers the **winner** alone: reporting both would make the arms sum past the price
                // they decompose. The tie goes to the band, exactly as the price's own `max` does.
                armAt = { p ->
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
                            val interiorLimit = scopedZones.strictestLimitKnAt(p.latitude, p.longitude)
                            val collarLimit = scopedZones.collarLimitKnAt(p.latitude, p.longitude, zoneOutsideMarginM)
                            zonePriceAtLimits(
                                cellM, pace, interiorLimit ?: 0.0, collarLimit ?: 0.0, lambda, zoneFraction
                            )
                        } else {
                            0.0
                        }
                    if (bandPrice >= zonePrice) PriceArm.BAND to bandPrice else PriceArm.ZONE to zonePrice
                }
            )
        )
    }
    // **The costed markers** — a λ-free price for 1–9 and a wall for 10. The price is gated by
    // `route.marker.enabled`; the wall never reads that switch (a wall is law and stands whatever it
    // says). A marker-free world adds nothing, so today's fast path (`hasSoft`/`hasBlocking` false) holds.
    sources.addAll(
        markerSources(
            world.routeMarkers(),
            baseCostSec(cellM, pace),
            markerStrength,
            AppConfig.routeMarkerEnabled
        )
    )
    return RouteCostField(sources, cellM)
}

/**
 * **The depth the router reads at a point** — the world's own sample, `NaN` where nothing is known. One
 * home, so the gate's wall, its traced edge and the berth can never disagree about where a sounding is: an
 * excluded or missing node reads the gate depth in the world's blend, an unsurveyed point is `NaN`, and
 * neither is ever shallow.
 */
internal fun depthMAt(world: MultipassWorld, p: LatLng): Double =
    depthMOf(world.depthAt(p.latitude, p.longitude))

/**
 * **The point a fraction [t] of the way from [a] to [b]**, read linearly in latitude and longitude — the one
 * home for the straight every pass draws between two points, at the scale a leg spans.
 */
internal fun chordPointAt(a: LatLng, b: LatLng, t: Double): LatLng = LatLng(
    a.latitude + (b.latitude - a.latitude) * t,
    a.longitude + (b.longitude - a.longitude) * t
)

/** **One sample as the router reads it** — its depth in metres, `NaN` where it holds nothing. */
internal fun depthMOf(sample: DepthSample): Double =
    if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN

/**
 * **The berth's law** — one rate (s per metre of water, before λ) at every distance off the measured
 * 3 m edge, read by the search, the pull's guard and the arm report alike ([rateSecPerM]).
 *
 * - From [floorM] to [marginM] — **the inner ramp**: [innerSecPerM] at the floor, falling straight to
 *   [secPerM] at the margin. It is the price of a narrow passage: the hard wall stands at the floor, and
 *   the water up to the margin is usable but dear, cheapest in a passage's middle.
 * - From [marginM] to [outerM] — **the outer ramp**: [secPerM] at the margin, falling straight to nothing
 *   at the outer edge, the *move out if there is room* the 2026-10-17 order asked for.
 * - Beyond [outerM]: nothing.
 *
 * The rate is continuous at the margin and never rises with the distance, so in a symmetric passage the
 * cheapest water is its middle. A law whose [floorM] stands at its [marginM] has no inner ramp and prices
 * the water inside the margin at the full [secPerM], exactly as the berth always did. It rides the plan
 * because the berth is *this* algorithm's own water: `avoid` prices none, `evolutive` answers the
 * `route.evolutive.berth.*` keys behind the gate's own switch.
 */
data class BerthLaw(
    val marginM: Double,
    val outerM: Double,
    val secPerM: Double,
    val floorM: Double = marginM,
    val innerSecPerM: Double = secPerM
) {
    /** Whether the inner ramp exists — a floor under the margin. */
    val hasInnerRamp: Boolean get() = floorM < marginM

    /** Whether the law prices anything at all. */
    val pricesAnything: Boolean
        get() = (secPerM > 0.0 && outerM > marginM) || (hasInnerRamp && innerSecPerM > 0.0)

    /** The rate (s/m, before λ) at [distanceM] off the edge. */
    fun rateSecPerM(distanceM: Double): Double {
        if (distanceM >= marginM) {
            if (outerM <= marginM || distanceM >= outerM) return 0.0
            return secPerM * (outerM - distanceM) / (outerM - marginM)
        }
        if (!hasInnerRamp) return if (outerM > marginM) secPerM else 0.0
        if (distanceM <= floorM) return innerSecPerM
        return secPerM + (innerSecPerM - secPerM) * (marginM - distanceM) / (marginM - floorM)
    }

    /** A cell's own price (s, before λ) at [distanceM] off the edge — the rate times the cell. */
    fun priceSecAt(distanceM: Double, cellM: Double): Double =
        if (cellM <= 0.0) 0.0 else rateSecPerM(distanceM) * cellM

    /**
     * **The distance to the nearest place the rate's slope changes**, which the pull's price walk reads as
     * its group proof. Inside the inner ramp it is `0.0`: the ramp is steep, so a stretch there is read
     * mark by mark rather than from one midpoint. Elsewhere it is the distance to the margin or the outer
     * edge, as the berth always declared.
     */
    fun clearanceM(distanceM: Double): Double {
        if (hasInnerRamp && distanceM < marginM) return 0.0
        return min(abs(distanceM - marginM), abs(distanceM - outerM))
    }
}

/** The plan's berth law, or `null` where it prices none (or the gate is off). */
internal fun berthLawOf(plan: RouteGridPlan, depthGateActive: Boolean): BerthLaw? {
    if (!plan.pricesBerth || !depthGateActive) return null
    val marginM = AppConfig.routeAvoidDepthGateMarginM
    val secPerM = AppConfig.routeEvolutiveBerthSecPerM
    return BerthLaw(
        marginM = marginM,
        outerM = AppConfig.routeEvolutiveBerthOuterM,
        secPerM = secPerM,
        floorM = AppConfig.routeEvolutiveBerthFloorM.coerceAtMost(marginM),
        innerSecPerM = AppConfig.routeEvolutiveBerthInnerSecPerM.coerceAtLeast(secPerM)
    )
}

/**
 * **The gate's rule for one arm**, or `null` where the gate is out of force: the world's measured edge,
 * the wall at the berth's floor where the plan prices one (`evolutive`) and at the gate's own margin
 * where it does not (`avoid`), the margin as the preferred standoff, and the margin again as the disc
 * around each end inside which the gate waives itself.
 */
internal fun gateRuleOf(
    world: MultipassWorld,
    plan: RouteGridPlan,
    depthGateActive: Boolean,
    minDepthM: Double
): GateRule? {
    if (!depthGateActive) return null
    val preferredM = AppConfig.routeAvoidDepthGateMarginM
    val berth = berthLawOf(plan, depthGateActive)
    return GateRule(
        world.shallowEdges(minDepthM),
        hardM = berth?.floorM ?: preferredM,
        preferredM = preferredM,
        waiverM = preferredM,
        berthOuterM = berth?.outerM ?: 0.0
    )
}

/**
 * **The fine tile's own field — the depth gate's wall and the priced markers, at an explicit base.**
 *
 * A fine tile is rasterised at a **unit base** (its grid opens at `1.0`), so its field's marker price is
 * `1.0 × factor` and the raster's own source cost *is* the pace-free multiplier the tile stores. The
 * walls a fine tile needs are the [costField] grid arm's own — the depth gate's rastered wall and (inert
 * for the fill) the coastline's materialised one — because the tile's fine-mask keeps a gate-blocked
 * cell's own water unless the wall paints it land. The band and the zones are deliberately absent: a
 * fine tile carries none of them.
 *
 * It is [costField]'s grid arm with the base lifted out, so the marker law stays one home
 * ([markerFactorAt]) and a tile's factor and the field's price cannot disagree.
 */
internal fun tileCostField(
    world: MultipassWorld,
    baseCostSec: Double,
    markerStrength: Double
): RouteCostField {
    val sources = ArrayList<RouteCostSource>(3)
    sources.add(RouteCostSource.Hard(distanceAt = { p -> world.distanceToCoastM(p.latitude, p.longitude) }))
    if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
        sources.add(
            depthGateSource(AppConfig.routeAvoidDepthGateMinM) { p -> depthMAt(world, p) }
        )
    }
    sources.addAll(
        markerSources(
            world.routeMarkers(),
            baseCostSec,
            markerStrength,
            AppConfig.routeMarkerEnabled
        )
    )
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
 *
 * **The zone read is the displayed limit, never the routed price** ([MultipassWorld.zoneDisplayLimitKnAt]).
 * A zone may carry a routing-only `speedRoute` override that lowers what the search pays while the water
 * stays signed at the limit the user is shown; a clock reading the price would report a speed the chart
 * does not permit. The **price law lives elsewhere** — the grid's stored `ZoneRing` limits and the guard's
 * own [`strictestLimitKnAt`] — so this lambda's zone arm can move without the search moving with it.
 */
internal fun limitAtFor(world: MultipassWorld): (LatLng) -> Double? {
    val bandM = world.bandWidthM
    val bandLimitKn = AppConfig.routeAvoidZone300LimitKn
    return { p ->
        val zoneLimit = world.zoneDisplayLimitKnAt(p.latitude, p.longitude)
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
 * [limitAtFor] over an arm's **own** zones — the corridor's harvested set, excluded ids already dropped —
 * rather than the world's whole region: a point inside the corridor can stand in no zone whose box misses
 * the corridor, so the answer is the world read's own, while each sample of the clock no longer asks the
 * provider for the whole list and walks every ring of the region. The band's read is unchanged.
 */
internal fun limitAtFor(world: MultipassWorld, zones: ZoneSet): (LatLng) -> Double? {
    val bandM = world.bandWidthM
    val bandLimitKn = AppConfig.routeAvoidZone300LimitKn
    return { p ->
        val zoneLimit = zones.strictestDisplayLimitKnAt(p.latitude, p.longitude)
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
 * **A post-search stage run in the pair's canonical direction.** The pull's corner snap, the fine pass,
 * the sweep, the straightener, the medial nudge and the corner pass each read the line from one end — a
 * predecessor already settled, a successor not yet — so handed the same line reversed they can settle a
 * different point. Run on the line turned so its first point [sortsFirst] before its last, over the setup
 * mirrored to match (the two ends' discs are symmetric, so nothing but the direction moves), and turned
 * back, every such stage answers one line for a pair armed from either end — the rule
 * `261020_FEAT_PLN_Route_direction-symmetry.md` took for the search, extended to the drawing.
 */
internal inline fun inCanonicalDirection(
    line: List<LatLng>,
    setup: PullSetup,
    stage: (line: List<LatLng>, setup: PullSetup) -> List<LatLng>
): List<LatLng> {
    if (line.size < 2 || sortsFirst(line.first(), line.last())) return stage(line, setup)
    return stage(line.asReversed(), setup.reversed()).reversed()
}

/**
 * Moves a bend onto its nearest tangent corner — the nearest corner whose own set radius contains it,
 * across all sets — only when both legs stay clear and their travel time plus penalties does not
 * increase; open water without a corner keeps the bend.
 *
 * It asks two clearances and four priced segments per candidate, so it is a clearance site in its own
 * right rather than a reader of the field it is handed: it takes the walk's own steps from [setup] —
 * [PullSetup.coarseStepM] for its legs and [PullSetup.priceStepM] for its priced segments — so a corner
 * is never moved on a reading the pull would not have made and the two sites keep one partition.
 *
 * The [sets] stay explicit: they are geometry, not water. The walk runs from a context folded from
 * [setup] with no tallies, since a corner snap counts and caches nothing of its own.
 */
internal fun snapToCorners(
    setup: PullSetup,
    sets: List<CornerSet>,
    path: List<LatLng>
): List<LatLng> {
    if (sets.all { it.points.isEmpty() }) return path
    val ctx = setup.context()
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
        if (moveVerdict(out[i - 1], path[i], path[i + 1], corner, ctx) == MoveVerdict.MOVED) out[i] = corner
    }
    return out
}

/** The three answers a proposed move of a bend can get — the one shape the snap and the relocation share. */
internal enum class MoveVerdict { MOVED, REFUSED_CLEAR, REFUSED_PRICE }

/**
 * **The bend move's two proofs, in one home** — whether the bend at [b], between the settled [prev] and the
 * unsettled [next], may stand at [corner] instead: both new legs clear ([MultipassPull.legClear]), read
 * first and short-circuiting so a refused leg pays no price read, then the two new legs' travel plus
 * penalties no dearer than the two they replace ([MultipassPull.totalSec]). The snap and the relocation
 * both decide on this, so a corner neither can take is refused for the same reason by either.
 */
private fun moveVerdict(prev: LatLng, b: LatLng, next: LatLng, corner: LatLng, ctx: PullContext): MoveVerdict {
    val hardClear = MultipassPull.legClear(prev, corner, ctx) && MultipassPull.legClear(corner, next, ctx)
    if (!hardClear) return MoveVerdict.REFUSED_CLEAR
    val replacedSec = MultipassPull.totalSec(prev, b, ctx) + MultipassPull.totalSec(b, next, ctx)
    val movedSec = MultipassPull.totalSec(prev, corner, ctx) + MultipassPull.totalSec(corner, next, ctx)
    return if (movedSec <= replacedSec) MoveVerdict.MOVED else MoveVerdict.REFUSED_PRICE
}

/**
 * **Slides a bend the sweep kept onto the edge of the obstacle that kept it.** The sweep's forward-only
 * greedy leaves a kept vertex where its skipping chord broke, which can be kilometres from the band, ring
 * or body the chord met — a bend in open water that no snap can reach, since every set's radius is read
 * about the bend. This pass reads the reach about the **obstacle** instead: for each [KeptBend] on [path],
 * the candidate is the corner, across all [sets], nearest to the bend's own worst point and **nearer to it
 * than the bend is**, so the reach is the bend's own distance to what kept it. The candidate is then
 * proved by [moveVerdict] against the **current** neighbours — the predecessor as already relocated, the
 * successor as not yet — and a corner equal to either neighbour is refused, so no zero-length leg is
 * written. Where the candidate fails the bend stays where the sweep left it.
 *
 * It is a forward greedy like the snap — a predecessor settled, a successor not — so its caller runs it in
 * the pair's canonical direction ([inCanonicalDirection]); the keeps are matched to the line by their
 * point, which a turned line still holds. It runs once, left to right: the sweep that follows it drops the
 * neighbour a moved bend freed.
 *
 * It reports itself through [trace]: one `RELOCATE idx=… from=… to=… arm=…` per moved bend, one
 * `RELOCATE keep idx=… why=…` per refused or candidate-less one, and a summary. Its counts are its own;
 * the pull's refusal tally is not touched.
 */
internal fun relocateKeptBends(
    setup: PullSetup,
    sets: List<CornerSet>,
    path: List<LatLng>,
    keeps: List<KeptBend>,
    trace: (() -> String) -> Unit = {},
    checkCancelled: () -> Unit = {}
): List<LatLng> {
    if (keeps.isEmpty() || path.size < 3 || sets.all { it.points.isEmpty() }) return path
    val byAt = HashMap<LatLng, KeptBend>(keeps.size * 2)
    for (k in keeps) byAt[k.at] = k
    val ctx = setup.context()
    val out = path.toMutableList()
    var moved = 0
    var refusedClear = 0
    var refusedPrice = 0
    var none = 0
    for (i in 1 until path.size - 1) {
        if (i % RELOCATE_CANCEL_CADENCE == 0) checkCancelled()
        val keep = byAt[path[i]] ?: continue
        val b = path[i]
        val w = keep.worstAt
        val reachM = SpatialOperations.haversine(b, w)
        var nearest: LatLng? = null
        var nearestDist = reachM
        for (set in sets) {
            for (c in set.points) {
                val d = SpatialOperations.haversine(w, c)
                if (d < nearestDist && c != out[i - 1] && c != path[i + 1]) {
                    nearest = c
                    nearestDist = d
                }
            }
        }
        val corner = nearest
        if (corner == null) {
            none++
            trace { "RELOCATE keep idx=$i at=${pointText(b)} why=NONE reachM=${fmt(reachM)}" }
            continue
        }
        when (moveVerdict(out[i - 1], b, path[i + 1], corner, ctx)) {
            MoveVerdict.MOVED -> {
                out[i] = corner
                moved++
                trace {
                    "RELOCATE idx=$i from=${pointText(b)} to=${pointText(corner)} " +
                        "arm=${keep.arm?.name ?: keep.cause.name} worstAt=${pointText(w)}"
                }
            }
            MoveVerdict.REFUSED_CLEAR -> {
                refusedClear++
                trace { "RELOCATE keep idx=$i at=${pointText(b)} why=LAND to=${pointText(corner)}" }
            }
            MoveVerdict.REFUSED_PRICE -> {
                refusedPrice++
                trace { "RELOCATE keep idx=$i at=${pointText(b)} why=PRICE to=${pointText(corner)}" }
            }
        }
    }
    trace {
        "RELOCATE in=${path.size} keeps=${keeps.size} moved=$moved " +
            "refusedClear=$refusedClear refusedPrice=$refusedPrice none=$none"
    }
    return out
}

/** The relocation's cancel cadence — read once every this many vertices, as the sweep reads its own. */
private const val RELOCATE_CANCEL_CADENCE = 64

/** The `lat,lon` pair a relocation line writes for [p], spelt as the `CORNER` and `SWEEP` lines spell it. */
private fun pointText(p: LatLng): String = "${fmt(p.latitude, 5)},${fmt(p.longitude, 5)}"

/**
 * The metres of a line whose own middle stands inside a priced zone's **interior** — the ranking's
 * second figure, the zone share's own objective read in metres rather than in seconds. The test is the
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
internal fun inZone(zones: List<SpeedZone>): (LatLng) -> Boolean {
    val set = ZoneSet(zones)
    return { p -> set.containsAny(p.latitude, p.longitude) }
}

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

/**
 * **A polyline's own length (m)** — the haversine sum, one home for the runner's provisional distance and
 * the engine's own line figure alike, so the two can never drift apart.
 */
internal fun polylineLengthM(points: List<LatLng>): Double {
    var total = 0.0
    for (i in 0 until points.size - 1) {
        total += SpatialOperations.haversine(points[i], points[i + 1])
    }
    return total
}

/**
 * **A pair's canonical direction, one definition** — whether [first] sorts at or before [second] by
 * latitude, then longitude. The two-sided pull turns its candidates to it before pricing them, and the pass
 * runs its search from the end that sorts first, so both read the same rule and a pair armed from either end
 * is the same pair to each.
 */
internal fun sortsFirst(first: LatLng, second: LatLng): Boolean =
    first.latitude < second.latitude || (first.latitude == second.latitude && first.longitude <= second.longitude)
