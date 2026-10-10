package ykws.android.maro.spatial

import kotlinx.coroutines.flow.Flow
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.SelectiveGridPlan

/**
 * **The third routing algorithm's engine: the selective grid.**
 *
 * It exists so the conservative behaviour lands **beside** `avoid` and `evolutive` rather than inside
 * either: the two shipped engines keep their walk, their keys and their drawn line untouched, and this one
 * spends fine detail only where a decision is made — a union of thin collars (the shoreline, the band's
 * outer boundary, the zone rims, the shallow wall) — with a conservative depth preference beside the depth
 * gate. A third row rather than a change to `evolutive`, so the two behaviours stay available to choose
 * from and neither ships a regression the other carried.
 *
 * **As it stands this engine is a composition and says so**: it holds a private [`RouteAvoidEngine`] and
 * forwards the seam's three calls to it, exactly as [`RouteEvolutiveEngine`] does, so the row is
 * selectable, armable and shares the whole pipeline — the runner, the fine pass, the corner pass, the
 * clock, the seam and the flow. The walk it runs differs by its **plan alone**: [`SelectiveGridPlan`]
 * answers its own coarse and fine cells, its four collars and the depth nudge. The depth price is scoped
 * to this engine — `withDepthBand` is on for this plan and off everywhere else — so `avoid` and `evolutive`
 * never price the shallow wall.
 *
 * Coroutines and `Flow` only, like the seam's other implementations.
 */
class RouteSelectiveEngine(
    paceKn: () -> Double,
    aversionKn: () -> Double,
    budgetPct: () -> Int,
    world: () -> MultipassWorld,
    /** The marker price's live strength, forwarded to the shared engine beside the pace. */
    markerStrength: () -> Double = { 1.0 },
    /**
     * **Where this algorithm's own walk differs**, and the only thing it will not share: the cells it
     * rasterizes at, the collars it keeps fine and the shallow-wall price it adds. [`SelectiveGridPlan`]
     * answers its own `route.selective.*` sizes, so the row runs this engine's resolution from its first
     * commit while the pipeline, the clock's shape and the readings stay shared.
     */
    plan: RouteGridPlan = SelectiveGridPlan
) : RouteEngine {

    /** The pipeline both engines stand on — replaced, not wrapped, as the selective grid lands. */
    private val delegate = RouteAvoidEngine(paceKn, aversionKn, budgetPct, world, markerStrength, plan)

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations =
        delegate.routesToCompute(origin, destination)

    override fun startLookup(computationId: RouteId): RouteId = delegate.startLookup(computationId)

    override fun cancelLookup(id: RouteId) = delegate.cancelLookup(id)

    override val updates: Flow<RouteUpdate> get() = delegate.updates
}
