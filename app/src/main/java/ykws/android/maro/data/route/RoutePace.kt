package ykws.android.maro.data.route

/** Where the pace `Now` plans at comes from — the word the panel's header and the Speed History section print. */
enum class PaceSource { LIVE, TODAY, HISTORY, DEFAULT }

/** The pace `Now` plans at, with the source it comes from. */
data class PaceReading(val kn: Double, val source: PaceSource)

/**
 * The pace's pure rules around [PacePlateau]: which pace a trip starts on and which source names the number.
 *
 * The starting pace is the day's saved live pace while its date is the trip's, else the learned history, else
 * the shipped default. The source is **Live** once the trip's eligible readings fill a quarter of the window —
 * the point from which they can move the number — and otherwise the starting pace's own.
 *
 * Pure Kotlin, no Android dependency.
 */
object RoutePace {

    /**
     * The pace a trip starts on.
     *
     * @param dayKn      the saved live pace of the day, null or non-positive when none was saved.
     * @param dayDate    the local date that pace was saved under.
     * @param tripDate   the local date the trip started on — checked once, so a trip past midnight keeps it.
     * @param historyKn  the learned history's value, null when the history is empty.
     * @param defaultKn  the shipped pace, the floor beneath both.
     */
    fun starting(
        dayKn: Double?,
        dayDate: String,
        tripDate: String,
        historyKn: Double?,
        defaultKn: Double
    ): PaceReading = when {
        dayKn != null && dayKn > 0.0 && dayDate == tripDate -> PaceReading(dayKn, PaceSource.TODAY)
        historyKn != null && historyKn > 0.0 -> PaceReading(historyKn, PaceSource.HISTORY)
        else -> PaceReading(defaultKn, PaceSource.DEFAULT)
    }

    /**
     * Whether the trip's own readings fill enough of the window to be named **Live**: a quarter of it, the
     * point from which they can move the number, not the first reading.
     */
    fun isLive(readingMs: Long, windowMs: Long): Boolean = readingMs * 4 >= windowMs

    /**
     * Whether a speed reading is the boat cruising: a real, positive speed at or above [stopKn]. The one rule
     * the live pace and a stored trip's reduction both read; the zone and band exclusion is the caller's.
     */
    fun isCruising(speedKn: Double, stopKn: Double): Boolean =
        speedKn.isFinite() && speedKn > 0.0 && speedKn >= stopKn
}
