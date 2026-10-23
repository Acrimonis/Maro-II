package ykws.android.maro.ui.map

import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import ykws.android.maro.R
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.route.PaceReading
import ykws.android.maro.data.route.PaceSource
import ykws.android.maro.data.route.SpeedHistory
import ykws.android.maro.data.route.SpeedHistorySeeder
import ykws.android.maro.data.route.speedHistoryProfileKn
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.Track
import ykws.android.maro.data.track.TrackSummary
import ykws.android.maro.data.track.TrackViewModel
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs

/**
 * The speed history's and the day's pace's writes, in one home: the trip a track reduces to, the **Calculate**
 * that rebuilds the store and what it tells the user, the fold-in of one finished trip, and the saving of the
 * day's live pace. Calculate and the first-launch seed run the same [seedSpeedHistory]; nothing else rebuilds
 * the store. The screen only wires these to its own flows and settings.
 */

/** How long (ms) the first-launch seed waits for the speed-zone index and the track list before giving up. */
internal const val SPEED_HISTORY_SEED_WAIT_MS = 120_000L

/** The day's live pace is written at most this often (ms): it edges every second, and the settings store is not a log. */
internal const val PACE_DAY_WRITE_EVERY_MS = 10_000L

/** …and only once it has moved this far (kn). */
internal const val PACE_DAY_WRITE_MIN_STEP_KN = 0.1

/** One track's trip for the history, or null when it has no cruising to speak of. */
internal fun speedHistoryTripOf(track: Track, restricted: (Double, Double) -> Boolean): SpeedHistory.Trip? =
    SpeedHistorySeeder.tripOf(
        points = track.trackPoints,
        startTimeMs = track.startTimeMs,
        demo = track.demo,
        haversineM = { a, b, c, d -> SpatialOperations.haversine(LatLng(a, b), LatLng(c, d)) },
        restricted = restricted
    )

/**
 * The trip a just-finished track folds in as, or null when it never enters the history: a route save, or a
 * track its summary reads as not mostly on water. [summaries] is the whole library, never the user's filtered
 * list — the filter shapes what Calculate reads, not whether a finished trip is on water; a track whose summary
 * has not arrived yet reads as water, as an unclassified track does.
 */
internal fun finishedTripForHistory(
    track: Track,
    summaries: List<TrackSummary>,
    restricted: (Double, Double) -> Boolean
): SpeedHistory.Trip? {
    if (track.route) return null
    val onWater = summaries.firstOrNull { it.id == track.id }?.positionIsWater ?: true
    return if (onWater) speedHistoryTripOf(track, restricted) else null
}

/**
 * The summaries a seed reads: the newest [SpeedHistory.PER_KIND] of each kind by **start time** (a rename never
 * moves a trip), leaving out route saves and any track not mostly on water. Never only N of them, so raising
 * N afterwards still finds trips.
 */
internal fun seedCandidates(summaries: List<TrackSummary>): List<TrackSummary> {
    val eligible = summaries.filter { !it.route && it.positionIsWater }.sortedByDescending { it.startTimeMs }
    return eligible.filter { !it.demo }.take(SpeedHistory.PER_KIND) +
        eligible.filter { it.demo }.take(SpeedHistory.PER_KIND)
}

/** Rebuilds the store from the tracks list as it stands filtered, off the main thread, from [seedCandidates]. */
internal suspend fun seedSpeedHistory(
    trackViewModel: TrackViewModel,
    restricted: (Double, Double) -> Boolean
): List<SpeedHistory.Trip> = withContext(Dispatchers.Default) {
    SpeedHistory.seed(
        seedCandidates(trackViewModel.summaries.value).mapNotNull { summary ->
            trackViewModel.loadTrackDetail(summary.id)?.let { speedHistoryTripOf(it, restricted) }
        }
    )
}

/**
 * What Calculate tells the user once it is done: how many trips the store was rebuilt from, or — when no
 * track was eligible — that the history was left as it stood.
 */
internal fun speedHistoryCalculatedMessage(resources: Resources, trips: Int): String =
    if (trips > 0) {
        resources.getQuantityString(R.plurals.settings_speed_history_calculated, trips, trips)
    } else {
        resources.getString(R.string.settings_speed_history_none_eligible)
    }

/** The settings after a Calculate: the store replaced, and the snapshot — its date and the profile it gave — kept apart. */
internal fun AppSettings.withSeededHistory(store: List<SpeedHistory.Trip>, nowMs: Long): AppSettings {
    val seeded = copy(routeSpeedHistoryTrips = store)
    return seeded.copy(
        routeSpeedHistoryComputedAtEpochMs = nowMs,
        routeSpeedHistoryComputedKn = (seeded.speedHistoryProfileKn() ?: 0.0).toFloat()
    )
}

/** The settings after the live pace is saved as the day's, under the date its trip started on. */
internal fun AppSettings.withDayPace(kn: Double, dateIso: String): AppSettings =
    copy(routePaceDayKn = kn.toFloat(), routePaceDayDate = dateIso)

/** The settings after a finished trip folds in: the store grows, and the Calculate snapshot is left exactly as it was. */
internal fun AppSettings.withFoldedTrip(trip: SpeedHistory.Trip): AppSettings =
    copy(routeSpeedHistoryTrips = SpeedHistory.fold(routeSpeedHistoryTrips, trip))

/**
 * Saves the live pace as the day's while its source is **Live**, until cancelled.
 *
 * Throttled with a trailing edge: a value is saved at most every [PACE_DAY_WRITE_EVERY_MS] and only once it
 * has moved [PACE_DAY_WRITE_MIN_STEP_KN], and a value arriving inside the window waits for the window's end
 * rather than being dropped — a newer Live value replaces it, and a reading that is no longer Live never
 * cancels it, so the last Live value is the one saved.
 *
 * @param tripDate the date to save under, read at the moment of saving.
 * @param clockMs  a monotonic clock (ms).
 */
internal suspend fun saveDayPace(
    pace: Flow<PaceReading>,
    tripDate: () -> String,
    clockMs: () -> Long,
    save: (kn: Double, dateIso: String) -> Unit
) {
    var lastSavedAtMs: Long? = null
    var lastSavedKn = Double.NaN
    pace.filter { it.source == PaceSource.LIVE }.collectLatest { reading ->
        if (abs(reading.kn - lastSavedKn) < PACE_DAY_WRITE_MIN_STEP_KN) return@collectLatest
        val waitMs = lastSavedAtMs?.let { PACE_DAY_WRITE_EVERY_MS - (clockMs() - it) } ?: 0L
        if (waitMs > 0L) delay(waitMs)
        lastSavedAtMs = clockMs()
        lastSavedKn = reading.kn
        save(reading.kn, tripDate())
    }
}
