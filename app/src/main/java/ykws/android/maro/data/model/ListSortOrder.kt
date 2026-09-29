package ykws.android.maro.data.model

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

/** Sort field for lists of [ListableItem]s. Direction toggled separately. */
enum class ListSortField(val labelResId: Int) {
    /** Alphabetical by title. */
    TITLE(R.string.sort_common_title),
    /** Creation time. */
    CREATED(R.string.sort_common_created)
}

/** Per-type sort field not present on [ListableItem]. Key is used for serialization + ViewModel dispatch. */
data class CustomSortField(
    val key: String,
    val labelResId: Int,
    /** Direction to apply when this field is first selected, before the user toggles. */
    val descendingDefault: Boolean = true
)

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
     *                          back to `updatedAtEpochMs` ascending, with direction applied
     *                          by the shared `descending` flag.
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
                ListSortField.TITLE -> compareBy(titleOrder) { it.title }
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
            val customFieldKey = when {
                parts.size >= 4 -> parts[3].ifBlank { null }  // legacy 4-part: skip pinnedGrouped at parts[2]
                parts.size == 3 -> parts[2].ifBlank { null }
                else -> null
            }
            return ListSortState(field, descending, customFieldKey)
        }
        fun format(state: ListSortState): String {
            val base = "${state.field.name}:${state.descending}"
            return if (state.customFieldKey != null) "$base:${state.customFieldKey}" else base
        }
    }
}

/**
 * **The app's own alphabetical order for a title** — one rule, read by the lists' `TITLE` field and by the
 * route ends' selector (2026-09-29). This is the **ascending** base: a list reverses it for its direction,
 * the selector takes it as it stands. The ignored words come from the shipped `title.sort.ignoredPrefixes`.
 */
internal val titleOrder: Comparator<String> =
    compareBy { titleSortKey(it, AppConfig.titleSortIgnoredPrefixes) }

/**
 * **The sort key of one title** — what "alphabetical on title" means here.
 *
 * It is the title case-folded and stripped of leading non-letters, as it always was, and since 2026-09-29 it
 * also drops one word from [ignoredPrefixes]: `Le Port` files under P. The user's own condition governs the
 * drop — the title must **start with** a configured word, character for character, and the character right
 * after it must be **whitespace** — so `Léman` (a different second character) and `Leman` (no whitespace after
 * `Le`) keep their L, a title that *is* the word has no next word to sort on and keeps itself, and `«Le» Port`
 * is read as it stands rather than after the dress has been stripped. One word is dropped, never a stack, and
 * the key then begins at the first character of the next word.
 *
 * The set arrives as a parameter rather than read from `AppConfig`, so the rule is testable on its own.
 */
internal fun titleSortKey(title: String, ignoredPrefixes: List<String>): String {
    val dropped = ignoredPrefixes.firstNotNullOfOrNull { prefix -> title.textAfterLeading(prefix) }
    return (dropped ?: title).dropWhile { !it.isLetterOrDigit() }.lowercase()
}

/**
 * The text after [prefix] when this string starts with it — case aside, which `regionMatches` folds without
 * the locale's help — and whitespace follows it with something after that; null otherwise, which is what
 * leaves a word the title merely begins with alone.
 */
private fun String.textAfterLeading(prefix: String): String? {
    if (prefix.isEmpty() || length <= prefix.length) return null
    if (!regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return null
    val rest = substring(prefix.length)
    if (rest.firstOrNull()?.isWhitespace() != true) return null
    return rest.dropWhile { it.isWhitespace() }.takeIf { it.isNotEmpty() }
}
