package ykws.android.maro.data.model

/** Sort field for lists of [ListableItem]s. Direction toggled separately. */
enum class ListSortField(val label: String) {
    /** Alphabetical by title. */
    TITLE("Title"),
    /** Creation time. */
    CREATED("Created"),
    /** Last modification time. */
    UPDATED("Updated")
}

/** Persisted sort state: field + direction. */
data class ListSortState(
    val field: ListSortField = ListSortField.UPDATED,
    val descending: Boolean = true,
    /** When true, pinned items group at top regardless of sort field. */
    val pinnedGrouped: Boolean = false
) {
    companion object {
        fun parse(raw: String?): ListSortState {
            if (raw == null) return ListSortState()
            val parts = raw.split(":")
            val field = try { ListSortField.valueOf(parts[0]) } catch (_: Exception) { ListSortField.UPDATED }
            val descending = if (parts.size > 1) parts[1].toBooleanStrictOrNull() ?: true else true
            val pinnedGrouped = if (parts.size > 2) parts[2].toBooleanStrictOrNull() ?: false else false
            return ListSortState(field, descending, pinnedGrouped)
        }
        fun format(state: ListSortState): String = "${state.field.name}:${state.descending}:${state.pinnedGrouped}"
    }
}
