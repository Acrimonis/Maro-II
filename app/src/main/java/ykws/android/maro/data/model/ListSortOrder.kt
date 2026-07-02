package ykws.android.maro.data.model

import ykws.android.maro.R

/** Sort field for lists of [ListableItem]s. Direction toggled separately. */
enum class ListSortField(val labelResId: Int) {
    /** Alphabetical by title. */
    TITLE(R.string.sort_common_title),
    /** Creation time. */
    CREATED(R.string.sort_common_created),
    /** Last modification time. */
    UPDATED(R.string.sort_common_updated)
}

/** Per-type sort field not present on [ListableItem]. Key is used for serialization + ViewModel dispatch. */
data class CustomSortField(val key: String, val labelResId: Int)

/** Persisted sort state: field + direction. */
data class ListSortState(
    val field: ListSortField = ListSortField.UPDATED,
    val descending: Boolean = true,
    /** When true, pinned items group at top regardless of sort field. */
    val pinnedGrouped: Boolean = false,
    /** When non-null, a [CustomSortField] is active; [field] is ignored for comparator dispatch. */
    val customFieldKey: String? = null
) {
    companion object {
        fun parse(raw: String?): ListSortState {
            if (raw == null) return ListSortState()
            val parts = raw.split(":")
            val field = try { ListSortField.valueOf(parts[0]) } catch (_: Exception) { ListSortField.UPDATED }
            val descending = if (parts.size > 1) parts[1].toBooleanStrictOrNull() ?: true else true
            val pinnedGrouped = if (parts.size > 2) parts[2].toBooleanStrictOrNull() ?: false else false
            val customFieldKey = if (parts.size > 3) parts[3].ifBlank { null } else null
            return ListSortState(field, descending, pinnedGrouped, customFieldKey)
        }
        fun format(state: ListSortState): String {
            val base = "${state.field.name}:${state.descending}:${state.pinnedGrouped}"
            return if (state.customFieldKey != null) "$base:${state.customFieldKey}" else base
        }
    }
}
