package ykws.android.maro.data.model

import ykws.android.maro.data.model.markers.MarkerGeometry
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

/** Milliseconds at start of the current year, UTC. */
private fun yearStartMs(todayMidnightMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = todayMidnightMs
    cal.set(Calendar.MONTH, 0)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis
}

fun dateInRange(startTimeMs: Long, range: String, todayMidnightMs: Long): Boolean = when (range) {
    "LAST_7_DAYS" -> startTimeMs >= todayMidnightMs - 7 * 86_400_000L
    "LAST_30_DAYS" -> startTimeMs >= todayMidnightMs - 30 * 86_400_000L
    "THIS_YEAR" -> startTimeMs >= yearStartMs(todayMidnightMs)
    else -> true // ALL
}

// ─────────────────────────────────────────────────────────────────────────────
// Predicates
// ─────────────────────────────────────────────────────────────────────────────

/** Live track exempt from date filter only (always shows regardless of date range). */
fun TrackSummary.matchesFilter(f: ListFilter, todayMidnightMs: Long): Boolean =
    f.axes.all { (key, value) ->
        when (key) {
            "dateRange" -> isLive || dateInRange(startTimeMs, value, todayMidnightMs)
            "pinned" -> value == "ALL" ||
                (value == "PINNED" && this.pinned) ||
                (value == "UNPINNED" && !this.pinned)
            else -> true
        }
    }

fun UserMarker.matchesFilter(f: ListFilter): Boolean =
    f.axes.all { (key, value) ->
        when (key) {
            "pinned" -> value == "ALL" ||
                (value == "PINNED" && this.pinned) ||
                (value == "UNPINNED" && !this.pinned)
            "geometry" -> value == "ALL" || geometryMatches(this.geometry, value)
            "origin" -> f.axes["geometry"] == "ZONES" || value == "ALL" || originMatches(this.origin, value)
            else -> true
        }
    }

fun geometryMatches(geometry: MarkerGeometry, value: String): Boolean = when (value) {
    "PINS" -> geometry is MarkerGeometry.Pin
    "ZONES" -> geometry !is MarkerGeometry.Pin
    else -> true
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
    val label: String,
    val isDefault: Boolean = false   // true = "All" option
)

/** One filter axis (dropdown section). */
data class FilterAxisSpec(
    val key: String,
    val label: String,
    val options: List<FilterOptionSpec>,
    /** Optional key of the axis that gates this axis. If the gating axis has
     *  the given [dependsOnValue], this axis is disabled + grayed out. */
    val dependsOn: String? = null,
    val dependsOnValue: String? = null
)

/** Track filter axes. */
fun trackFilterAxes(): List<FilterAxisSpec> = listOf(
    FilterAxisSpec(
        key = "dateRange",
        label = "Date Range",
        options = listOf(
            FilterOptionSpec("ALL", "All", isDefault = true),
            FilterOptionSpec("THIS_YEAR", "This Year"),
            FilterOptionSpec("LAST_30_DAYS", "Last 30 Days"),
            FilterOptionSpec("LAST_7_DAYS", "Last 7 Days")
        )
    ),
    FilterAxisSpec(
        key = "pinned",
        label = "Pinned",
        options = listOf(
            FilterOptionSpec("ALL", "All", isDefault = true),
            FilterOptionSpec("PINNED", "Pinned"),
            FilterOptionSpec("UNPINNED", "Unpinned")
        )
    )
)

/** Marker filter axes. Origin is disabled when geometry=ZONES. */
fun markerFilterAxes(): List<FilterAxisSpec> = listOf(
    FilterAxisSpec(
        key = "pinned",
        label = "Pinned",
        options = listOf(
            FilterOptionSpec("ALL", "All", isDefault = true),
            FilterOptionSpec("PINNED", "Pinned"),
            FilterOptionSpec("UNPINNED", "Unpinned")
        )
    ),
    FilterAxisSpec(
        key = "geometry",
        label = "Geometry",
        options = listOf(
            FilterOptionSpec("ALL", "All", isDefault = true),
            FilterOptionSpec("PINS", "Pins"),
            FilterOptionSpec("ZONES", "Zones")
        )
    ),
    FilterAxisSpec(
        key = "origin",
        label = "Origin",
        options = listOf(
            FilterOptionSpec("ALL", "All", isDefault = true),
            FilterOptionSpec("MANUAL", "Manual"),
            FilterOptionSpec("AUTO", "Auto")
        ),
        dependsOn = "geometry",
        dependsOnValue = "ZONES"
    )
)
