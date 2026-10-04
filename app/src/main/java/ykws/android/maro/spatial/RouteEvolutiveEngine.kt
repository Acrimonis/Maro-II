package ykws.android.maro.spatial

import kotlinx.coroutines.flow.Flow
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.RouteGridPlan
import ykws.android.maro.spatial.avoid.UniformGridPlan

/**
 * **The second routing algorithm's engine: the adaptive grid.**
 *
 * It exists so the resolution-aware walk lands **beside** `avoid` rather than inside it: `avoid` is
 * settled and shipped, its ladder, its keys and its drawn line are what the app runs today, and the
 * adaptive grid changes the walk itself — its resolution two-layer, its second pass a corridor, its clock's
 * sampling tied to the finest cell. Two shipped behaviours behind one name is exactly what a second row is
 * for, and [`RouteEngineChoice`] was built to take one: *one row here plus one class that implements
 * `RouteEngine`*.
 *
 * **As it stands this engine is a composition and says so**: it holds a private
 * [`RouteAvoidEngine`] and forwards the seam's three calls to it, so the row is selectable, armable and
 * identical to `avoid` from its first commit — nothing about the water can differ while the delegate is
 * the whole of it. The adaptive grid's own grid, second pass and readings replace that field as they land,
 * which is why the delegation is a private field and never an inheritance: what a caller reads is the seam,
 * and no caller can tell which side of it the algorithm sits on.
 *
 * Coroutines and `Flow` only, like the seam's other three implementations.
 */
class RouteEvolutiveEngine(
    paceKn: () -> Double,
    aversionKn: () -> Double,
    budgetPct: () -> Int,
    world: () -> AvoidWorld,
    /**
     * **Where this algorithm's own walk differs**, and the only thing it will not share: the cell it
     * rasterizes at and the region its second pass may look at. It ships the uniform plan while the
     * adaptive grid is built, so the row is behaviourally `avoid` today and becomes itself the day its
     * plan lands — the pipeline, the clock and the readings never change with it.
     */
    plan: RouteGridPlan = UniformGridPlan
) : RouteEngine {

    /** The algorithm this engine ships while the adaptive grid is built — replaced, not wrapped, later. */
    private val delegate = RouteAvoidEngine(paceKn, aversionKn, budgetPct, world, plan)

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations =
        delegate.routesToCompute(origin, destination)

    override fun startLookup(computationId: RouteId): RouteId = delegate.startLookup(computationId)

    override fun cancelLookup(id: RouteId) = delegate.cancelLookup(id)

    override val updates: Flow<RouteUpdate> get() = delegate.updates
}
