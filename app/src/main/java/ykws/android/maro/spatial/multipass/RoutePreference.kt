package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig

/**
 * **The acquisition's three rungs — their word and the λ each is solved at.**
 *
 * One home for the ladder's shape: its three stops and their λ, so the engine's own declaration and the
 * panel that names its rows read this, and no surface can name a stop the engine does not seat. The three
 * stops are listed most-averse first, in the ladder's own index order — `around`, `best` and `fast` —
 * which is the order the acquisition's first column lists its rungs in.
 *
 * **Each stop's λ is a key of its own** — `route.avoid.ladder.around|best|fast.lambda` in
 * `maro.properties`, read through [AppConfig] — because the three values are the ladder's one lever and
 * the properties file is where every value lives; this enum is what maps each rung onto its own, and no
 * subclass below holds a number. The word a user reads for a rung is the acquisition's own vocabulary
 * (`route_rung_*`), held by the engine's own declaration and never by this file.
 *
 * **The ladder's stops and the verdict's dial are two scales.** This enum holds the **drawings'** rates
 * alone: the crown the user reads on the card is decided by the verdict's own weight ([VerdictDial],
 * `route.avoid.slowWater.weight`), so a hand-set ladder moves which line each rung name drew and never
 * the weighting the crown reads.
 */
internal enum class RoutePreference {

    /** λ `route.avoid.ladder.around.lambda` — the most aversion: detours to shun slow water where it can. */
    AROUND {
        override val lambda: Double get() = AppConfig.routeAvoidLadderAroundLambda
    },

    /** λ `route.avoid.ladder.best.lambda` — the middle stop, between the two. */
    BEST {
        override val lambda: Double get() = AppConfig.routeAvoidLadderBestLambda
    },

    /** λ `route.avoid.ladder.fast.lambda` — the least aversion: the shortest clock, whatever water it crosses. */
    FAST {
        override val lambda: Double get() = AppConfig.routeAvoidLadderFastLambda
    };

    /**
     * The fixed λ this stop's rung is solved at, read from its own properties key as it is asked for — so
     * the file stays the value's one home and an edit reaches the next arming. No rung is ever corrected:
     * this value is the whole of what separates one acquisition of an arming from its siblings.
     */
    abstract val lambda: Double
}
