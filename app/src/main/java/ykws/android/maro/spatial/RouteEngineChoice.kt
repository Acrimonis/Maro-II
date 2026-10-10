package ykws.android.maro.spatial

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.multipass.MultipassWorld

/**
 * **One algorithm of the route harness: a stable id, a `labelResId` and the factory that builds
 * its engine from a pace provider, a slow-water budget provider and a world provider** — the
 * `CustomSortField` shape the rulebook names, so no engine holds user-facing text and both locales
 * carry the label.
 *
 * The registry lives beside the seam in `spatial/`, so a new algorithm is one row here plus one
 * class that implements [RouteEngine]; nothing else in the feature changes. The factory receives
 * the pace provider — `() -> Double`, answering the pace in force at answer time — the budget
 * provider — `() -> Int`, the slow-water budget in per cent, asked the same way so a slider move
 * reaches the next line — and the world provider — `() -> MultipassWorld`, the map's live coastline
 * world — so an engine that prices at the app's pace and reads the water asks all three fresh, while
 * one that ignores them stays silent. The id is the persisted value ([AppConfig.routeEngineId]'s
 * default), and [resolve] is the one lookup: it answers the shipped default for an id nothing
 * claims, so a stale stored id falls back rather than leaving the app with no engine to arm.
 */
data class RouteEngineChoice(
    val id: String,
    val labelResId: Int,
    val factory: (
        paceKn: () -> Double,
        aversionKn: () -> Double,
        budgetPct: () -> Int,
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
                factory = { _, _, _, _, _ -> RouteDummyEngine() }
            ),
            RouteEngineChoice(
                id = "avoid",
                labelResId = R.string.route_engine_avoid,
                factory = { paceKn, aversionKn, budgetPct, world, markerStrength ->
                    RouteAvoidEngine(paceKn, aversionKn, budgetPct, world, markerStrength)
                }
            ),
            RouteEngineChoice(
                id = "evolutive",
                labelResId = R.string.route_engine_evolutive,
                factory = { paceKn, aversionKn, budgetPct, world, markerStrength ->
                    RouteEvolutiveEngine(paceKn, aversionKn, budgetPct, world, markerStrength)
                }
            ),
            RouteEngineChoice(
                id = "selective",
                labelResId = R.string.route_engine_selective,
                factory = { paceKn, aversionKn, budgetPct, world, markerStrength ->
                    RouteSelectiveEngine(paceKn, aversionKn, budgetPct, world, markerStrength)
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
