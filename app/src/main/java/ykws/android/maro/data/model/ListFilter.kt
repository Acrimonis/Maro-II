package ykws.android.maro.data.model

import ykws.android.maro.R
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackSummary
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Filter model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Extensible filter state stored as a map of axis key → value.
 * Absent key = default (ALL). Serialized as `key1=value1;key2=value2`.
 */
data class ListFilter(val axes: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = axes.isEmpty()

    companion object {
        fun parse(raw: String?): ListFilter {
            if (raw.isNullOrBlank()) return ListFilter()
            val map = mutableMapOf<String, String>()
            raw.split(";").forEach { entry ->
                val eq = entry.indexOf('=')
                if (eq > 0) map[entry.substring(0, eq)] = entry.substring(eq + 1)
            }
            return ListFilter(map)
        }

        fun format(filter: ListFilter): String =
            filter.axes.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date range helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Midnight today in UTC ms. Stable all day. */
fun todayMidnightMs(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun dateInRange(startTimeMs: Long, range: String, todayMidnightMs: Long): Boolean = when (range) {
    "LAST_7_DAYS" -> startTimeMs >= todayMidnightMs - 7 * 86_400_000L
    "LAST_14_DAYS" -> startTimeMs >= todayMidnightMs - 14 * 86_400_000L
    "LAST_30_DAYS" -> startTimeMs >= todayMidnightMs - 30 * 86_400_000L
    "LAST_2_MONTHS" -> startTimeMs >= todayMidnightMs - 60 * 86_400_000L
    "LAST_3_MONTHS" -> startTimeMs >= todayMidnightMs - 90 * 86_400_000L
    "LAST_6_MONTHS" -> startTimeMs >= todayMidnightMs - 180 * 86_400_000L
    else -> true // ALL
}

// ─────────────────────────────────────────────────────────────────────────────
// Predicates
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live tracks are exempt from the axes whose subject a recording cannot answer for: the date range,
 * whose `isLive` arm is the original case, the position, whose classification would otherwise move
 * under the user's eyes as the track grows, and the kind, because a recording in progress is not a
 * route and must not vanish from the list mid-journey.
 */
fun TrackSummary.matchesFilter(f: ListFilter, todayMidnightMs: Long): Boolean =
    f.axes.all { (key, value) ->
        when (key) {
            "dateRange" -> isLive || dateInRange(startTimeMs, value, todayMidnightMs)
            "pinned" -> value == "ALL" ||
                (value == "PINNED" && this.pinned) ||
                (value == "UNPINNED" && !this.pinned)
            // The rule itself lives on the summary: water wins the tie, an unclassified track counts
            // as water, and the live track passes whatever its points say.
            "position" -> isLive || when (value) {
                "WATER" -> positionIsWater
                "LAND" -> !positionIsWater
                else -> true
            }
            // The field's own two words: a route is a track the app saved, everything else is a
            // recorded track. A live recording passes whatever the flag says (see the note above).
            "route" -> isLive || when (value) {
                "TRACKS" -> !route
                "ROUTES" -> route
                else -> true
            }
            else -> true
        }
    }

fun UserMarker.matchesFilter(f: ListFilter): Boolean =
    f.axes.all { (key, value) ->
        when (key) {
            "icon" -> value == "ALL" ||
                (value == "WITH_ICON" && this.icon != null) ||
                (value == "WITHOUT_ICON" && this.icon == null)
            "pinned" -> value == "ALL" ||
                (value == "PINNED" && this.pinned) ||
                (value == "UNPINNED" && !this.pinned)
            "origin" -> value == "ALL" || originMatches(this.origin, value)
            else -> true
        }
    }

fun originMatches(origin: MarkerOrigin, value: String): Boolean = when (value) {
    "MANUAL" -> origin == MarkerOrigin.USER
    "AUTO" -> origin == MarkerOrigin.IDLE_AUTO
    else -> true
}

// ─────────────────────────────────────────────────────────────────────────────
// UI axis specs
// ─────────────────────────────────────────────────────────────────────────────

/** One option in a filter axis dropdown. */
data class FilterOptionSpec(
    val value: String,
    /** Resource id of the option's label — read with `stringResource` where it is drawn. */
    val labelResId: Int,
    val isDefault: Boolean = false   // true = "All" option
)

/** One filter axis (dropdown section). */
data class FilterAxisSpec(
    val key: String,
    /** Resource id of the axis' label — read with `stringResource` where it is drawn. */
    val labelResId: Int,
    val options: List<FilterOptionSpec>,
    /** Optional key of the axis that gates this axis. If the gating axis value
     *  is in [dependsOnValues], this axis is disabled + grayed out. */
    val dependsOn: String? = null,
    val dependsOnValues: List<String>? = null
)

/** Track filter axes. */
fun trackFilterAxes(): List<FilterAxisSpec> = listOf(
    FilterAxisSpec(
        key = "dateRange",
        labelResId = R.string.filter_axis_date_range,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("LAST_7_DAYS", R.string.filter_option_last_week),
            FilterOptionSpec("LAST_14_DAYS", R.string.filter_option_last_2_weeks),
            FilterOptionSpec("LAST_30_DAYS", R.string.filter_option_last_month),
            FilterOptionSpec("LAST_2_MONTHS", R.string.filter_option_last_2_months),
            FilterOptionSpec("LAST_3_MONTHS", R.string.filter_option_last_3_months),
            FilterOptionSpec("LAST_6_MONTHS", R.string.filter_option_last_6_months)
        )
    ),
    FilterAxisSpec(
        key = "pinned",
        labelResId = R.string.settings_marker_halo_pinned_label,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("PINNED", R.string.settings_marker_halo_pinned_label),
            FilterOptionSpec("UNPINNED", R.string.filter_option_unpinned)
        )
    ),
    FilterAxisSpec(
        key = "position",
        labelResId = R.string.settings_tab_position,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("WATER", R.string.filter_option_on_water),
            FilterOptionSpec("LAND", R.string.dash_not_at_sea)
        )
    ),
    FilterAxisSpec(
        key = "route",
        labelResId = R.string.filter_axis_kind,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("TRACKS", R.string.filter_option_tracks),
            FilterOptionSpec("ROUTES", R.string.filter_option_routes)
        )
    )
)

/** Marker filter axes. */
fun markerFilterAxes(): List<FilterAxisSpec> = listOf(
    FilterAxisSpec(
        key = "icon",
        labelResId = R.string.action_icon,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("WITH_ICON", R.string.filter_option_with_icon),
            FilterOptionSpec("WITHOUT_ICON", R.string.filter_option_without_icon)
        )
    ),
    FilterAxisSpec(
        key = "pinned",
        labelResId = R.string.settings_marker_halo_pinned_label,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("PINNED", R.string.settings_marker_halo_pinned_label),
            FilterOptionSpec("UNPINNED", R.string.filter_option_unpinned)
        )
    ),
    FilterAxisSpec(
        key = "origin",
        labelResId = R.string.sort_custom_origin,
        options = listOf(
            FilterOptionSpec("ALL", R.string.filter_option_all, isDefault = true),
            FilterOptionSpec("MANUAL", R.string.filter_option_manual),
            FilterOptionSpec("AUTO", R.string.filter_option_auto)
        )
    )
)
