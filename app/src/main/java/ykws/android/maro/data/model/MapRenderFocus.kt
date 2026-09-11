package ykws.android.maro.data.model

/**
 * Ephemeral, in-memory map-render focus. Never persisted — cleared on process death.
 *
 * Two orthogonal pieces of view state:
 *  - [highlightedId] — the track currently opened/viewed (single id);
 *  - [touchedIds]    — ids touched this session by import / record / merge / update.
 *
 * Both force an item into the rendered set even when the map filter excludes it or the render cap
 * would drop it. Neither overrides the master layer toggles (`tracksVisible`, `markerLayerState`) —
 * the shell applies those gates separately, after selection.
 *
 * Bounded: only the [maxTouched] most-recently touched ids are kept (oldest evicted first).
 */
class MapRenderFocus(private val maxTouched: Int = MAX_TOUCHED) {

    /** Currently highlighted (viewed) track id, or null. */
    var highlightedId: String? = null
        private set

    // Insertion order = oldest first; re-touching an id refreshes it to newest.
    private val touched = LinkedHashSet<String>()

    /** Ids touched this session, oldest first. */
    val touchedIds: Set<String> get() = touched

    /** Set (or clear) the highlighted id. */
    fun highlight(id: String?) {
        highlightedId = id
    }

    /** Record a freshly imported / recorded / merged / updated id. Evicts the oldest when full. */
    fun markTouched(id: String) {
        if (!touched.remove(id)) {
            while (touched.size >= maxTouched) {
                val eldest = touched.firstOrNull() ?: break
                touched.remove(eldest)
            }
        }
        touched.add(id)
    }

    /** Drop the whole session boost. [highlightedId] is unaffected. */
    fun clearBoost() {
        touched.clear()
    }

    fun isHighlighted(id: String): Boolean = id == highlightedId

    fun isBoosted(id: String): Boolean = id in touched

    /** True when [id] must be force-included in the selection regardless of filter or cap. */
    fun includes(id: String): Boolean = isHighlighted(id) || isBoosted(id)

    companion object {
        /** Session-boost bound: at most this many recently touched ids are remembered. */
        const val MAX_TOUCHED = 8
    }
}
