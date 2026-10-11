package ykws.android.maro.data.track

import kotlin.math.max

/**
 * One marker as the boat met it at a single moment — the identity the dwell is credited to, the
 * label a name would show, and the matcher's own score at that position, lower meaning closer or
 * more specific.
 *
 * The track layer keeps no second ordering: the caller reads the score from the matcher and hands
 * it in, so `MarkerMatcher`'s rule stays the one home of "which marker is closer, which is more
 * specific".
 */
data class MarkerContact(val markerId: String, val label: String, val score: Double)

/**
 * One marker's whole share of a trip: the timed score it earned over every interval it matched,
 * the sum of `seconds × score`, **lower winning** (settled 2026-10-10). Ascending order is the
 * order the markers are used to name the trip.
 */
data class TimedMarker(val markerId: String, val label: String, val timedScore: Double)

/** One end of a trip, already resolved to the label a name would show. */
data class TripEnd(val markerId: String?, val label: String)

/**
 * The label a marker contributes to a name: its manually-set icon in front of its own name, one
 * space between them, or the bare name where no icon was set (settled 2026-10-10).
 */
fun markerLabel(name: String, icon: String?): String =
    if (icon.isNullOrBlank()) name else "$icon $name"

/**
 * A name's segments before they are joined: the origin, the ranked body and the closing
 * destination, each absent when nothing names it.
 */
data class NameShape(val origin: String?, val body: List<String>, val destination: String?)

/**
 * The pure half of a trip's name: the time-weighted ranking its markers are chosen by, the shape
 * the name composes, and the trimming that keeps it within the cap. No Android, no I/O — the
 * ranking, the shape and the trimming are unit tests rather than device runs.
 */
object TrackNaming {

    /** How many markers a name's body may carry (settled 2026-10-10). */
    const val MAX_BODY = 2

    /** What marks a token the length cap cut. */
    const val ELLIPSIS = "\u2026"

    /** The separator between body markers. */
    private const val SEPARATOR = ", "

    /** The fragment marking the boat's own home port in a label (settled 2026-10-11). */
    private const val HOME_PORT_LABEL = "salis"

    /**
     * True when an end must not name a route or a trip: the boat's own home port, told either by
     * the place — [lat]/[lon] inside the Port Salis geofence — or by the label itself naming Salis
     * (settled 2026-10-11).
     */
    fun namesHomePort(
        lat: Double,
        lon: Double,
        label: String,
        homeLat: Double,
        homeLon: Double,
        homeRadiusM: Double
    ): Boolean = label.contains(HOME_PORT_LABEL, ignoreCase = true) ||
        TrackGeofenceChecker.isInsideGeofence(lat, lon, homeLat, homeLon, homeRadiusM)

    /**
     * The timed score of every marker the trip met: for each marker, the sum over the trip's
     * intervals of the interval's seconds times the score that marker carried then, counted only
     * over the intervals in which it matched. **Lower wins**, so the deepest or closest match leads
     * however briefly it was held (settled 2026-10-10).
     *
     * A GAP interval — either end of the pair carrying [PointType.GAP] — contributes nothing, the
     * water between those two fixes being unknown, and a zero or negative interval likewise.
     *
     * @param points    the trip's points in time order, each carrying its own time offset.
     * @param contactAt the markers one point met, with the matcher's score for each.
     */
    fun timedScores(
        points: List<TrackPoint>,
        contactAt: (TrackPoint) -> List<MarkerContact>
    ): List<TimedMarker> {
        val score = LinkedHashMap<String, Double>()
        val label = LinkedHashMap<String, String>()
        for (i in 0 until max(0, points.size - 1)) {
            val from = points[i]
            val to = points[i + 1]
            if (from.type == PointType.GAP || to.type == PointType.GAP) continue
            val seconds = (to.timeOffsetMs - from.timeOffsetMs) / 1_000.0
            if (seconds <= 0.0) continue
            for (contact in contactAt(from)) {
                score[contact.markerId] = (score[contact.markerId] ?: 0.0) + seconds * contact.score
                label[contact.markerId] = contact.label
            }
        }
        return score.entries
            .map { TimedMarker(it.key, label[it.key].orEmpty(), it.value) }
            .sortedBy { it.timedScore }
    }

    /**
     * The origin, body and destination a name will show, chosen from the ranked markers and the
     * trip's two resolved ends (settled 2026-10-11).
     *
     * A [leading] marker — a registered stop's own — takes the first slot by rule rather than by
     * score. Both ends are then dropped from the body, since the origin now leads the name and a
     * distinct destination closes it. The body is capped at [MAX_BODY].
     *
     * @param destination `null` when the destination is the same marked place as the origin, which
     *                    is how a loop drops its shared endpoint.
     */
    fun select(
        ranked: List<TimedMarker>,
        origin: TripEnd,
        destination: TripEnd?,
        leading: TimedMarker? = null
    ): NameShape {
        val excluded = setOfNotNull(origin.markerId, destination?.markerId)
        val body = (listOfNotNull(leading) + ranked)
            .filter { it.markerId !in excluded }
            .distinctBy { it.markerId }
            .take(MAX_BODY)
            .map { it.label }
        return NameShape(
            origin = origin.label.takeIf { it.isNotBlank() },
            body = body,
            destination = destination?.label?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * The name the shape composes, as a chain of segments — the origin, the ranked body and the
     * destination, each dropped when empty and the survivors joined by [connector] (settled
     * 2026-10-11): `<origin> to <body#1>, <body#2> to <destination>`, so a bodyless pair reads
     * `<origin> to <destination>`, a pair with no origin reads `<body> to <destination>`, and a
     * lone segment stands alone.
     *
     * When the whole exceeds [maxLength] every part is cut in proportion to its own length, an
     * ellipsis marking each cut; nothing left means an empty result and the caller falls back to
     * the timestamp.
     *
     * @param connector the language's own word — `to` or `à` — supplied by the caller: a track's
     *                  name is data, and this is the one place it carries literal UI text.
     * @param maxLength the cap the list card and the exported file name both have to live within.
     */
    fun compose(
        origin: String?,
        body: List<String>,
        destination: String?,
        connector: String,
        maxLength: Int
    ): String {
        val head = origin?.trim()?.takeIf { it.isNotEmpty() }
        val tokens = body.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_BODY)
        val tail = destination?.trim()?.takeIf { it.isNotEmpty() }
        val bodyText = tokens.joinToString(SEPARATOR)
        // The segments as they will read; an empty one contributes nothing, so the name is the
        // survivors' chain.
        val segments = listOfNotNull(head, bodyText.takeIf { it.isNotEmpty() }, tail)
        if (segments.isEmpty()) return ""

        // Each segment is one part; a join carries the connector, a body token the separator.
        val parts = listOfNotNull(head) + tokens + listOfNotNull(tail)
        val overhead = (segments.size - 1) * (connector.length + 2) +
            (tokens.size - 1).coerceAtLeast(0) * SEPARATOR.length
        val trimmed = trimProportionally(parts, maxLength - overhead)
        var i = 0
        val trimmedHead = head?.let { trimmed[i++] }
        val trimmedBody = tokens.map { trimmed[i++] }.joinToString(SEPARATOR)
        val trimmedTail = tail?.let { trimmed[i++] }
        return listOfNotNull(trimmedHead, trimmedBody.takeIf { it.isNotEmpty() }, trimmedTail)
            .joinToString(" $connector ")
    }

    /**
     * Cuts the parts to fit [budget] in proportion to their own length — the longer the part, the
     * more it gives up — appending [ELLIPSIS] to each part that was cut and leaving the short ones
     * whole. Public for the tests' sake; the caller only ever reaches it through [compose].
     */
    internal fun trimProportionally(parts: List<String>, budget: Int): List<String> {
        if (parts.isEmpty()) return parts
        if (budget <= 0) return parts.map { ELLIPSIS }
        val total = parts.sumOf { it.length }
        if (total <= budget) return parts
        return parts.map { part ->
            val share = (budget.toDouble() * part.length / total).toInt().coerceAtLeast(1)
            if (part.length <= share) part
            else part.take((share - ELLIPSIS.length).coerceAtLeast(1)).trimEnd() + ELLIPSIS
        }
    }
}
