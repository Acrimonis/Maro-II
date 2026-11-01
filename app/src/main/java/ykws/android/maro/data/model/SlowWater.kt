package ykws.android.maro.data.model

/**
 * **The water a limit governs at one point** — a regulated zone with its limit, or the 300 m band with
 * the band's own limit. Open water has no value: [of] answers `null` there.
 *
 * One home for the precedence both slow-water readings share — a route's attribution
 * (`slowTimeByLimit`) and a recorded track's walk (`TrackSlowWater`): a point in the band **and** a
 * zone counts once, **the zone over the band**, so neither reading can double-count the water the
 * other charges.
 */
sealed interface SlowWater {
    /** The limit the water carries (kn) — the zone's own, or the band's when [Band]. */
    val limitKn: Double

    /** Water inside a regulated speed zone, keyed by its strictest limit. */
    data class Zone(override val limitKn: Double) : SlowWater

    /** Water inside the 300 m band and no zone. */
    data class Band(override val limitKn: Double) : SlowWater

    companion object {
        /**
         * The water at one point: [zoneLimitKn] when a zone holds it, the band when [inBand] does, open
         * water (`null`) otherwise. [inBand] is read only when no zone holds the point — a coast distance
         * the zone already decided is never paid for.
         */
        fun of(zoneLimitKn: Double?, inBand: () -> Boolean, bandLimitKn: Double): SlowWater? = when {
            zoneLimitKn != null -> Zone(zoneLimitKn)
            inBand() -> Band(bandLimitKn)
            else -> null
        }
    }
}
