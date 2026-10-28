package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig

/**
 * **A fixture's turn geometry, pinned to the values it was built on.** The corner pass and the approach pass's split
 * floor read `route.turn.lateralAccelMps2` and `route.turn.transitionSec` through [AppConfig], whose code defaults
 * follow `maro.properties`; a fixture whose radii, rooms or spans were measured at other values states them here
 * rather than inheriting the defaults, and the values it found are restored after it, so no other test reads them.
 * The values are set on their own — never through `AppConfig.applyProperties`, which also resets the `path.*` set and
 * recomputes other seeds.
 */
internal object TurnConfigFixture {
    /**
     * The acceleration (m/s²) the older turn fixtures were measured at — the code default until 2026-10-28, when the
     * default came to follow `maro.properties`.
     */
    const val BUILT_AT_MPS2 = 0.33

    /** The spiral roll-in time (s) the older corner fixtures were measured at — the code default until 2026-10-28. */
    const val BUILT_AT_TRANSITION_SEC = 2.0

    /**
     * Runs [block] with the turn's lateral acceleration at [lateralAccelMps2] (m/s²) and its roll-in time at
     * [transitionSec] (s), each defaulting to the value in force, then restores the values in force before.
     */
    fun <T> withTurn(
        lateralAccelMps2: Double = AppConfig.routeTurnLateralAccelMps2,
        transitionSec: Double = AppConfig.routeTurnTransitionSec,
        block: () -> T
    ): T {
        val previousAccel = AppConfig.routeTurnLateralAccelMps2
        val previousTransition = AppConfig.routeTurnTransitionSec
        AppConfig.routeTurnLateralAccelMps2 = lateralAccelMps2
        AppConfig.routeTurnTransitionSec = transitionSec
        try {
            return block()
        } finally {
            AppConfig.routeTurnLateralAccelMps2 = previousAccel
            AppConfig.routeTurnTransitionSec = previousTransition
        }
    }
}
