package ykws.android.maro.data.route

/**
 * The learned history: the boat's own past trips, each reduced to one cruising speed, and the profile read off
 * the last N of them.
 *
 * The store keeps up to [PER_KIND] trips of **each kind** — recorded on a GPS fix or on the simulated demo
 * boat — so turning demo trips off never leaves a thin window. Nothing is trimmed to N: N only chooses how
 * many of the stored trips the profile reads, so raising it brings the older trips back. The profile is the
 * **median of those trips weighted by each trip's open-water minutes, capped**, so one abnormal or very short
 * trip cannot move it. There is no wall-clock expiry.
 *
 * Pure Kotlin, no Android dependency.
 */
object SpeedHistory {

    /** How many trips the store keeps of each kind. */
    const val PER_KIND = 15

    /**
     * One finished trip in the store.
     *
     * @property speedKn       the trip's cruising speed.
     * @property openWaterMin  the minutes its eligible readings cover — the weight it votes with.
     * @property startTimeMs   when the trip began, the order "last" is read in (a rename never moves a trip).
     * @property demo          whether it ran on the simulated boat.
     */
    data class Trip(
        val speedKn: Double,
        val openWaterMin: Double,
        val startTimeMs: Long,
        val demo: Boolean = false
    )

    /**
     * The median of [trips]' speeds, each weighted by its open-water minutes capped at [capMin]: the first
     * speed, slowest first, whose cumulative weight reaches half the total. Equal weights when no trip carries
     * any minutes; null for no trips.
     */
    fun weightedMedian(trips: List<Trip>, capMin: Double): Double? {
        if (trips.isEmpty()) return null
        val weighted = trips.map { it.speedKn to it.openWaterMin.coerceIn(0.0, capMin) }
        val total = weighted.sumOf { it.second }
        val ranked = (if (total > 0.0) weighted else weighted.map { it.first to 1.0 }).sortedBy { it.first }
        val half = ranked.sumOf { it.second } / 2.0
        var acc = 0.0
        for ((speed, weight) in ranked) {
            acc += weight
            if (acc >= half) return speed
        }
        return ranked.last().first
    }

    /**
     * The profile: the median of the last [n] trips by start time, GPS trips alone unless [includeDemo], or null
     * when the store holds none of the wanted kind. A store thinner than [n] is read as it stands.
     */
    fun profile(store: List<Trip>, n: Int, includeDemo: Boolean, capMin: Double): Double? =
        weightedMedian(
            store.filter { includeDemo || !it.demo }.sortedBy { it.startTimeMs }.takeLast(n.coerceAtLeast(1)),
            capMin
        )

    /**
     * Adds a finished [trip] to the store: it replaces a stored trip of the same start and kind (a resumed
     * track finalised twice is one trip), and its kind keeps its newest [PER_KIND], the oldest falling off.
     * The result is oldest first.
     */
    fun fold(store: List<Trip>, trip: Trip): List<Trip> =
        (store.filterNot { it.startTimeMs == trip.startTimeMs && it.demo == trip.demo } + trip)
            .keepNewestPerKind()

    /** The store a seed pass builds from [trips] in any order: oldest first, the newest [PER_KIND] of each kind. */
    fun seed(trips: List<Trip>): List<Trip> = trips.keepNewestPerKind()

    private fun List<Trip>.keepNewestPerKind(): List<Trip> {
        val oldestFirst = sortedBy { it.startTimeMs }
        val keepGps = oldestFirst.filterNot { it.demo }.takeLast(PER_KIND).toSet()
        val keepDemo = oldestFirst.filter { it.demo }.takeLast(PER_KIND).toSet()
        return oldestFirst.filter { it in keepGps || it in keepDemo }
    }

    /** The store as one preference string: `speed:minutes:start:demo` per trip, `|` between them. */
    fun encode(store: List<Trip>): String =
        store.joinToString("|") { "${it.speedKn}:${it.openWaterMin}:${it.startTimeMs}:${if (it.demo) 1 else 0}" }

    /** The store back from [encode]'s string; a malformed entry is dropped, never fatal. */
    fun decode(text: String): List<Trip> =
        text.split('|').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 4) return@mapNotNull null
            val speed = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val minutes = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val start = parts[2].toLongOrNull() ?: return@mapNotNull null
            Trip(speed, minutes, start, demo = parts[3] == "1")
        }
}
