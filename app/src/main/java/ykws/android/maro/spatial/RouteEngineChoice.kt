package ykws.android.maro.spatial

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

/**
 * **One algorithm of the route harness: a stable id, a `labelResId` and the factory that builds
 * its engine from a pace provider** — the `CustomSortField` shape the rulebook names, so no engine
 * holds user-facing text and both locales carry the label.
 *
 * The registry lives beside the seam in `spatial/`, so a new algorithm is one row here plus one class
 * that implements [RouteEngine]; nothing else in the feature changes. The factory receives the pace
 * provider — `() -> Double`, answering the pace in force at answer time — so an engine that prices at
 * the app's pace asks it fresh per answer, while one that ignores it stays silent. The id is the
 * persisted value ([AppConfig.routeEngineId]'s default), and [resolve] is the one lookup: it answers
 * the shipped default for an id nothing claims, so a stale stored id falls back rather than leaving the
 * app with no engine to arm.
 */
data class RouteEngineChoice(
    val id: String,
    val labelResId: Int,
    val factory: (paceKn: () -> Double) -> RouteEngine
) {
    companion object {

        /** Every shipped algorithm, in menu order — the dropdown reads this list as it stands. */
        val all: List<RouteEngineChoice> = listOf(
            RouteEngineChoice(
                id = "dummy",
                labelResId = R.string.route_engine_dummy,
                factory = { _ -> RouteDummyEngine() }
            ),
            RouteEngineChoice(
                id = "avoid",
                labelResId = R.string.route_engine_avoid,
                factory = { paceKn -> RouteAvoidEngine(paceKn) }
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
