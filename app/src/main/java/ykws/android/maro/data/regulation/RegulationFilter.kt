package ykws.android.maro.data.regulation

/**
 * Bake-time filter for regulated zones — runs after [RegulationAggregator.aggregate]
 * to remove zones that are irrelevant to the user's vessel or preferred categories.
 *
 * Three gates:
 * 1. **Vessel size** — removes zones whose [RegulatedZone.appliesTo] returns `false`
 *    for the configured vessel length.
 * 2. **Zone type** — removes zones whose [RegulatedZoneType] is in the filtered-out set.
 *    Types in [HARD_FILTERED_TYPES] are removed unconditionally; other filtered types
 *    may survive if their description matches a keep-keyword.
 * 3. **Negative description** — removes zones whose description is purely boilerplate
 *    (e.g. "See French sailing directions" with no other actionable info).
 *
 * All gates are inclusive: a zone passes only if it passes ALL applicable gates.
 */
object RegulationFilter {

    /**
     * Description substrings (lowercased) that indicate a zone is relevant
     * even if its [RegulatedZoneType] would otherwise be filtered out.
     */
    private val KEEP_KEYWORDS = setOf(
        // Speed
        "speed", "vitesse", "knot", "noeud",
        // Anchoring
        "anchor", "mouillage", "mooring", "amarrage",
        // Diving
        "diving", "plongée",
        // Seaplanes
        "seaplane", "hydravion",
        // Hazards & navigation aids
        "obstruction", "buoyed",
        // Aviation
        "airport", "heliport",
        // Navigation
        "navigation", "circulation",
        // Fishing facilities
        "fishing facility", "fishing facilities", "trawling", "dragging", "hauling",
        // Moorings
        "small craft mooring",
    )

    /**
     * Zone types that are **hard-filtered** — removed unconditionally with no
     * keep-keyword exception. These types are considered completely irrelevant
     * to the user's navigation regardless of description content.
     */
    private val HARD_FILTERED_TYPES = setOf(
        RegulatedZoneType.FISHING_PROHIBITED,
    )

    /**
     * Description substrings (lowercased) that indicate a zone is **not** useful
     * — purely boilerplate, no actionable info.
     */
    private val EXCLUDE_KEYWORDS = setOf(
        "see french sailing directions",
        "voir les instructions nautiques",
    )

    /**
     * Filter a [RegulatedZoneSet] in place, returning a new set with only the
     * zones that pass all gates. [RegulationMetadata] counts are updated to
     * reflect the filtered set.
     *
     * @param zoneSet        The aggregated zone set to filter.
     * @param vesselLengthM  User's vessel length in metres. Zones where
     *                       `appliesTo(vesselLengthM) == false` are removed.
     *                       Pass `null` to skip the vessel-size gate.
     * @param filteredTypes  Zone types to exclude. Zones with these types are
     *                       removed **unless** their description matches a
     *                       keep-keyword (except for [HARD_FILTERED_TYPES]
     *                       which are removed unconditionally). Pass an empty
     *                       set to skip.
     * @return A new [RegulatedZoneSet] with only the zones that pass all gates,
     *         or the original [zoneSet] if no filtering occurred.
     */
    fun filter(
        zoneSet: RegulatedZoneSet,
        vesselLengthM: Double? = null,
        filteredTypes: Set<RegulatedZoneType> = emptySet()
    ): RegulatedZoneSet {
        val original = zoneSet.zones
        if (original.isEmpty()) return zoneSet

        val filtered = original.filter { zone ->
            // Gate 1: Vessel size
            if (vesselLengthM != null && !zone.appliesTo(vesselLengthM)) {
                return@filter false
            }

            val desc = zone.description.lowercase()

            // Gate 2: Negative description (boilerplate-only)
            if (desc.isNotBlank() && EXCLUDE_KEYWORDS.any { desc.startsWith(it) }) {
                // Only exclude if the description is NOTHING BUT boilerplate
                val trimmed = desc.removePrefix(EXCLUDE_KEYWORDS.first { desc.startsWith(it) }).trim()
                if (trimmed.isEmpty() || trimmed.startsWith(".") || trimmed.startsWith("(")) {
                    return@filter false
                }
            }

            // Gate 3: Zone type — filter types that are in the blocklist
            if (zone.zoneType in filteredTypes) {
                // Hard-filtered types are removed unconditionally
                if (zone.zoneType in HARD_FILTERED_TYPES) {
                    return@filter false
                }
                // Soft-filtered types: allow exception only if description matches a keep-keyword
                return@filter desc.isNotBlank() && KEEP_KEYWORDS.any { it in desc }
            }

            true
        }

        if (filtered.size == original.size) return zoneSet // no change

        return RegulatedZoneSet(
            zones = filtered,
            metadata = zoneSet.metadata.copy(totalZones = filtered.size)
        )
    }
}
