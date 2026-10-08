package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.max

/**
 * **The fine layer's water, as the plan answers it — before any lattice snapping.**
 *
 * A plan states two things about its own fine water: the **cut reaches** the builder grows the coast's
 * harvested segments by, and the **membership bands** a cell's own coast distance must fall in to be fine
 * water. The two are deliberately separate, because the cut may over-cover (the rasterizer's mask paints
 * any cell the bands do not keep as land, which costs memory and never an answer) while a missed cell
 * would move one: the cut is the safe superset, the bands are the exact water.
 *
 * Two further collars stand beside the coast ones and are stated in the same geographic terms:
 * [zoneRimM] (the water within that distance of a priced zone's boundary) and [depthCollarM] (the water
 * within that distance of a gate-blocked cell). Both answer their own marking pass; neither moves the
 * coast's.
 */
data class FineWater(
    /** The cut reaches (m): each grows the coast's harvested segments' boxes when the tiles are marked. */
    val coastReachesM: List<Double> = emptyList(),
    /** The coast-distance membership bands (m): a cell whose coast distance lies in one is fine water. */
    val coastBandsM: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /** The zone-rim collar width (m): 0.0 disables the collar. */
    val zoneRimM: Double = 0.0,
    /** The depth-dilation collar width (m): 0.0 disables the collar. */
    val depthCollarM: Double = 0.0
) {

    /** True where this answer asks for **no** fine layer at all — the walk is the coarse grid alone. */
    val isEmpty: Boolean
        get() = coastReachesM.isEmpty() && coastBandsM.isEmpty() && zoneRimM <= 0.0 && depthCollarM <= 0.0
}

/**
 * **What a plan is handed to answer [FineWater]** — the corridor, the harvested coast, the margin and the
 * plan's own base cell. It carries no price and no cursor: the cut and the membership are geography, and
 * the plan's answer may not move with a rung. [box], [edges] and [openCoast] are the corridor's own
 * harvest, kept for a plan that reads the coast beside its widths; the depth probe, the priced zones and
 * the sampling step belong to the **builder's** own marking, read there and never handed on.
 */
data class FineWaterQuery(
    val world: MultipassWorld,
    val box: BBox,
    val edges: List<MultipassEdge>,
    val openCoast: List<List<LatLng>>,
    val marginM: Double,
    val baseCellM: Double
)

/**
 * **The fine layer's membership, as the rasterizer applies it** — the coast bands, the zone rim and the
 * depth collar, in the geometry the window sweep can measure. It is [FineWater]'s mask half: the cut
 * already chose the tiles, and this decides, cell by cell, which of them the fine layer keeps.
 */
internal data class FineMask(
    val coastBandsM: List<ClosedFloatingPointRange<Double>>,
    val zoneRimM: Double = 0.0,
    val depthCollarM: Double = 0.0,
    val depthBlockedAt: ((LatLng) -> Boolean)? = null,
    val depthStepM: Double = 0.0
) {

    /** True where no membership rule stands — the window keeps every passable cell, as it always did. */
    val isTrivial: Boolean
        get() = coastBandsM.isEmpty() && zoneRimM <= 0.0 && depthCollarM <= 0.0
}

/** [FineWater]'s mask, with the depth probe the cut and the mask share. */
internal fun FineWater.toMask(depthBlockedAt: ((LatLng) -> Boolean)?, depthStepM: Double): FineMask =
    FineMask(coastBandsM, zoneRimM, depthCollarM, depthBlockedAt, depthStepM)

/**
 * **The gate's wall test, one home** — `true` where a sample is shallower than [minDepthM]. The
 * rasterizer's depth collar, the pull's guard and the fine pass's crossings all read this one predicate,
 * so they can never disagree about where the shallow wall stands.
 */
internal fun depthBlockedAtOf(world: MultipassWorld, minDepthM: Double): (LatLng) -> Boolean =
    { p -> !depthClearsGate(world.depthAt(p.latitude, p.longitude), true, minDepthM) }

/**
 * **The depth band one home builds** — the law's two distances and the wall test above, assembled from
 * the world and the fine cell, so the rasterizer, the pull's guard and the fine pass's crossing re-solves
 * all read one object rather than re-spelling the band. `null` where the gate is off or the grid is not
 * loaded.
 */
internal fun depthBandOf(world: MultipassWorld, minDepthM: Double, fineCellM: Double): DepthBand? =
    if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
        DepthBand(DepthBandLaw.bandM(), DepthBandLaw.stepM(fineCellM), depthBlockedAtOf(world, minDepthM))
    } else {
        null
    }

/**
 * **The depth price band the rasterizer writes** — its width (m), the radial scan's step (m) and the
 * gate's own wall test. The frame writes a λ-free coefficient per cell from the same [DepthBandLaw] the
 * pull's guard reads, so the search and the guard price one point identically.
 */
data class DepthBand(
    val bandM: Double,
    val stepM: Double,
    val blockedAt: (LatLng) -> Boolean
)

/**
 * **The depth band's law, in one home** — the ramp of the price toward the shallow wall, the distance to
 * that wall, and the seconds a coefficient costs at a cursor `k`.
 *
 * The wall carries **no distance index** the way the coastline does, so the distance is measured by a
 * **radial ring scan** on the gate's own predicate: the nearest sampled blocked point at 5 m steps out to
 * the band's own width. The rasterizer and the pull's guard call the very same functions, which is what
 * keeps the search and the guard pricing one point identically. The coefficient is **λ-free** and
 * dimensionless — 1.0 at the wall, 0.0 at the band's outer edge — and only [priceSec] turns it into
 * seconds, at the pass's own cursor.
 */
internal object DepthBandLaw {

    /** The ring scan's direction count — the 8 compass bearings. */
    private const val DIRECTIONS = 8

    /**
     * The distance (m) from [at] to the nearest gate-blocked sample, or `Double.MAX_VALUE` where none
     * stands within [bandM]. `0.0` where [at] is itself blocked.
     */
    fun wallDistanceM(at: LatLng, blockedAt: (LatLng) -> Boolean, stepM: Double, bandM: Double): Double {
        if (blockedAt(at)) return 0.0
        if (stepM <= 0.0 || bandM <= 0.0) return Double.MAX_VALUE
        var distance = stepM
        while (distance <= bandM + 1e-9) {
            for (direction in 0 until DIRECTIONS) {
                val bearing = direction * (360.0 / DIRECTIONS)
                if (blockedAt(SpatialOperations.pointAlongBearing(at.latitude, at.longitude, bearing, distance))) {
                    return distance
                }
            }
            distance += stepM
        }
        return Double.MAX_VALUE
    }

    /** The λ-free depth-price coefficient at a point, in `0.0..1.0` — the per-metre ramp to the wall. */
    fun coefAt(
        at: LatLng,
        blockedAt: (LatLng) -> Boolean,
        stepM: Double,
        bandM: Double
    ): Double {
        if (bandM <= 0.0) return 0.0
        return coefFor(wallDistanceM(at, blockedAt, stepM, bandM), bandM)
    }

    /**
     * The coefficient a **distance to the wall** answers, in `0.0..1.0` — [coefAt]'s own ramp, one home,
     * so a caller that already holds the distance (the rasterizer's shared scan) writes the very value
     * [coefAt] would, never a re-spelt one. This is the whole of the law's answer at the band's edge: a
     * distance at or beyond [bandM] prices nothing, and inside it the ramp rises to 1.0 at the wall.
     */
    fun coefFor(distance: Double, bandM: Double): Double {
        if (bandM <= 0.0) return 0.0
        if (distance >= bandM) return 0.0
        return ((bandM - distance) / bandM).coerceIn(0.0, 1.0)
    }

    /** The seconds a cell of size [cellM] carrying coefficient [coef] costs at cursor [k]. */
    fun priceSec(cellM: Double, coef: Double, k: Double): Double {
        if (coef <= 0.0 || k <= 0.0) return 0.0
        return max(0.0, coef * k * AppConfig.routeSelectiveDepthPriceSecPerM * cellM)
    }

    /** The band's own width (m) — the gate's margin plus the plan's extra, one home for the law. */
    fun bandM(): Double = AppConfig.routeAvoidDepthGateMarginM + AppConfig.routeSelectiveDepthBandExtraM

    /** The sampling step (m) the radial scan uses — the fine cell, never under a metre. */
    fun stepM(fineCellM: Double): Double = fineCellM.coerceAtLeast(1.0)
}

/**
 * **The selective mask's portable half — the plan's own water law, cached.**
 *
 * The fine lattice's origin is the corridor's each arm, so a box list snapped to one arm's lattice does
 * not tile the next arm's. What is portable is the mask **before** the snap — the plan's [FineWater]
 * answer, the four collars' own geography — and that is what is cached here. **The key names exactly what
 * [SelectiveGridPlan.union] reads**: the band's width and the four `route.selective.*` collar widths. The
 * generation stamps, the two switch states and the depth cutoff were **dropped** — they cannot move this
 * value, because a law is geography rather than a read; they would belong only if the cache held the
 * snapped geometry, which is re-derived per arm and is the cheap half. The excluded-zone set is treated
 * as static on the user's call, so it is not a key either.
 */
internal object SelectiveMaskCache {

    /** The keys the portable answer moves with — the fields [SelectiveGridPlan.union] actually reads. */
    data class Key(
        val bandWidthM: Double,
        val shoreCollarM: Double,
        val bandCollarM: Double,
        val zoneRimM: Double,
        val depthCollarM: Double
    )

    private var key: Key? = null
    private var value: FineWater? = null

    /** The cached answer for [key], computing and storing it on a miss — one entry, newest wins. */
    @Synchronized
    fun getOrCompute(key: Key, compute: () -> FineWater): FineWater {
        val cached = value
        if (cached != null && key == this.key) return cached
        val answer = compute()
        this.key = key
        value = answer
        return answer
    }

    /** Drops the one entry — the test's own door, and the ship's if a stamp ever lies. */
    @Synchronized
    fun clear() {
        key = null
        value = null
    }
}
