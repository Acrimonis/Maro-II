package ykws.android.maro.spatial

import kotlinx.coroutines.flow.Flow
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridPlan

/**
 * **The second routing algorithm's engine: the adaptive grid.**
 *
 * It exists so the resolution-aware walk lands **beside** `avoid` rather than inside it: `avoid` is
 * settled and shipped, its ladder, its keys and its drawn line are what the app runs today, and the
 * adaptive grid changes the walk itself — its resolution two-layer, its clock's sampling tied to the
 * finest cell. Two shipped behaviours behind one name is exactly what a second row is for, and
 * [`RouteEngineChoice`] was built to take one: *one row here plus one class that implements `RouteEngine`*.
 *
 * **As it stands this engine is a composition and says so**: it holds a private
 * [`RouteAvoidEngine`] and forwards the seam's three calls to it, so the row is selectable, armable and
 * shares the whole pipeline. The walk it runs differs by its **plan alone** — [`EvolutiveGridPlan`]
 * answers its own coarse cell and its own fine cell — so nothing else about the water can part from
 * `avoid` while the delegate is the rest of it. The two-layer grid and the readings replace that field
 * as they land, which is why the delegation is a private field and never an inheritance: what a caller
 * reads is the seam, and no caller can tell which side of it the algorithm sits on.
 *
 * Coroutines and `Flow` only, like the seam's other three implementations.
 */
class RouteEvolutiveEngine(
    paceKn: () -> Double,
    aversionKn: () -> Double,
    budgetPct: () -> Int,
    world: () -> MultipassWorld,
    /**
     * **Where this algorithm's own walk differs**, and the only thing it will not share: the cell it
     * rasterizes at and the fine cell its clock steps at. [`EvolutiveGridPlan`] answers its own
     * `route.evolutive.*` sizes, so the row runs this engine's resolution from its first commit while the
     * pipeline, the clock's shape and the readings stay shared.
     */
    plan: RouteGridPlan = EvolutiveGridPlan
) : RouteEngine {

    /** The pipeline both engines stand on — replaced, not wrapped, as the adaptive grid lands. */
    private val delegate = RouteAvoidEngine(paceKn, aversionKn, budgetPct, world, plan)

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations =
        delegate.routesToCompute(origin, destination)

    override fun startLookup(computationId: RouteId): RouteId = delegate.startLookup(computationId)

    override fun cancelLookup(id: RouteId) = delegate.cancelLookup(id)

    override val updates: Flow<RouteUpdate> get() = delegate.updates
}
