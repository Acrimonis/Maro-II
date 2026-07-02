package ykws.android.maro.data.model

import ykws.android.maro.R

/** Sort field for lists of [ListableItem]s. Direction toggled separately. */
enum class ListSortField(val labelResId: Int) {
    /** Alphabetical by title. */
    TITLE(R.string.sort_common_title),
    /** Creation time. */
    CREATED(R.string.sort_common_created)
}

/** Per-type sort field not present on [ListableItem]. Key is used for serialization + ViewModel dispatch. */
data class CustomSortField(val key: String, val labelResId: Int)

/** Persisted sort state: field + direction. */
data class ListSortState(
    val field: ListSortField = ListSortField.CREATED,
    val descending: Boolean = true,
    /** When non-null, a [CustomSortField] is active; [field] is ignored for comparator dispatch. */
    val customFieldKey: String? = null
) {
    /**
     * Applies [state] sort to a list of [ListableItem]s.
     *
     * @param items       The list to sort (not mutated).
     * @param customComparator  Called when [customFieldKey] is non-null; receives the key,
     *                          returns a [Comparator] for the concrete type, or null to fall
     *                          back to `updatedAtEpochMs` descending.
     *                          Callers that need the wall clock for live-track duration
     *                          computation (e.g. `totalTimeSec`) should capture
     *                          `val nowMs = System.currentTimeMillis()` before calling.
     * @return A new sorted list.
     */
    fun <T : ListableItem> applySort(
        items: List<T>,
        customComparator: (String) -> Comparator<T>?
    ): List<T> {
        val comparator: Comparator<T> = when {
            customFieldKey != null -> customComparator(customFieldKey!!)
                ?: compareBy { it.updatedAtEpochMs }  // ascending base — .reversed() below handles direction
            else -> when (field) {
                ListSortField.TITLE -> compareBy { it.title.lowercase() }
                ListSortField.CREATED -> compareBy { it.createdAtEpochMs }
            }
        }
        val directed = if (descending) comparator.reversed() else comparator
        return items.sortedWith(directed)
    }

    companion object {
        fun parse(raw: String?): ListSortState {
            if (raw == null) return ListSortState()
            val parts = raw.split(":")
            val field = try { ListSortField.valueOf(parts[0]) } catch (_: Exception) { ListSortField.CREATED }
            val descending = if (parts.size > 1) parts[1].toBooleanStrictOrNull() ?: true else true
            // parts[2] was pinnedGrouped (removed) — skip
            val customFieldKey = if (parts.size > 3) parts[3].ifBlank { null } else null
            return ListSortState(field, descending, customFieldKey)
        }
        fun format(state: ListSortState): String {
            val base = "${state.field.name}:${state.descending}"
            return if (state.customFieldKey != null) "$base:${state.customFieldKey}" else base
        }
    }
}
