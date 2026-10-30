package ykws.android.maro.spatial

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.UniformGridPlan

/**
 * **One algorithm of the route harness: a stable id, a `labelResId` and the factory that builds
 * its engine from a pace provider, the verdict's weight provider and a world provider** — the
 * `CustomSortField` shape the rulebook names, so no engine holds user-facing text and both locales
 * carry the label.
 *
 * The registry lives beside the seam in `spatial/`, so a new algorithm is one row here plus one
 * class that implements [RouteEngine]; nothing else in the feature changes. The factory receives
 * the pace provider — `() -> Double`, answering the pace in force at answer time — the verdict's
 * weight provider — `() -> Double?`, the dial's own stop, `null` on its *Ignore time* stop, asked
 * the same way so a dial move reaches the next rung — and the world provider — `() -> MultipassWorld`,
 * the map's live coastline world — so an engine that prices at the app's pace and reads the water
 * asks all three fresh, while one that ignores them stays silent. The id is the persisted value
 * ([AppConfig.routeEngineId]'s default), and [resolve] is the one lookup: it answers the shipped
 * default for an id nothing claims, so a stale stored id falls back rather than leaving the app
 * with no engine to arm.
 */
data class RouteEngineChoice(
    val id: String,
    val labelResId: Int,
    /**
     * **The plan this row's engine walks** — the one thing that separates one algorithm from another. It is
     * stated here so a reader of the row (the pre-warm's own host) can name the walk the row runs without
     * constructing an engine; the engine's own default is the same plan, so the row and the engine agree.
     */
    val plan: RouteGridPlan,
    val factory: (
        paceKn: () -> Double,
        verdictWeight: () -> Double?,
        world: () -> MultipassWorld,
        markerStrength: () -> Double
    ) -> RouteEngine
) {
    companion object {

        /** Every shipped algorithm, in menu order — the dropdown reads this list as it stands. */
        val all: List<RouteEngineChoice> = listOf(
            RouteEngineChoice(
                id = "dummy",
                labelResId = R.string.route_engine_dummy,
                plan = UniformGridPlan,
                factory = { _, _, _, _ -> RouteDummyEngine() }
            ),
            RouteEngineChoice(
                id = "avoid",
                labelResId = R.string.route_engine_avoid,
                plan = UniformGridPlan,
                factory = { paceKn, verdictWeight, world, markerStrength ->
                    RouteAvoidEngine(paceKn, verdictWeight, world, markerStrength, UniformGridPlan)
                }
            ),
            RouteEngineChoice(
                id = "evolutive",
                labelResId = R.string.route_engine_evolutive,
                plan = EvolutiveGridPlan,
                factory = { paceKn, verdictWeight, world, markerStrength ->
                    RouteEvolutiveEngine(paceKn, verdictWeight, world, markerStrength, EvolutiveGridPlan)
                }
            ),
            // The evolutive walk with the any-angle search: taut lines from the search itself, the staircase's
            // bends never drawn — the user's pick, never a default; its cost is read in the any-angle plan's §17.
            RouteEngineChoice(
                id = "evolutive_plus",
                labelResId = R.string.route_engine_evolutive_plus,
                plan = EvolutiveGridPlan,
                factory = { paceKn, verdictWeight, world, markerStrength ->
                    RouteEvolutiveEngine(paceKn, verdictWeight, world, markerStrength, EvolutiveGridPlan, anyAngle = true)
                }
            )
        )

        /**
         * The row for [id], or the shipped default's row when no row claims it — the one lookup every
         * reader goes through, so a stale id can never leave the app without an engine to arm.
         */
        fun resolve(id: String): RouteEngineChoice =
            all.firstOrNull { it.id == id }
                ?: (all.firstOrNull { it.id == AppConfig.routeEngineId } ?: all.first())
    }
}
