package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig

/**
 * **A fixture's turn geometry, pinned to the acceleration it was built on.** The corner pass and the approach pass's
 * split floor read `route.turn.lateralAccelMps2` through [AppConfig], whose code default follows `maro.properties`; a
 * fixture whose radii, rooms or spans were measured at another value states that value here rather than inheriting the
 * default, and the value it found is restored after it, so no other test reads it. The value is set on its own — never
 * through `AppConfig.applyProperties`, which also resets the `path.*` set and recomputes other seeds.
 */
internal object TurnConfigFixture {
    /**
     * The acceleration (m/s²) the older turn fixtures were measured at — the code default until 2026-10-28, when the
     * default came to follow `maro.properties`.
     */
    const val BUILT_AT_MPS2 = 0.33

    /** Runs [block] with the turn's lateral acceleration at [mps2] (m/s²), then restores the value in force before. */
    fun <T> withLateralAccel(mps2: Double, block: () -> T): T {
        val previous = AppConfig.routeTurnLateralAccelMps2
        AppConfig.routeTurnLateralAccelMps2 = mps2
        try {
            return block()
        } finally {
            AppConfig.routeTurnLateralAccelMps2 = previous
        }
    }
}
