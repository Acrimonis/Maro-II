package ykws.android.maro.data.regulation

/**
 * Builds a list of [SpeedZone] runtime models from a [RegulatedZoneSet].
 *
 * Filters to zones with [RegulatedZone.speedLimitKn] != null, creating a
 * lightweight runtime model suitable for spatial indexing and the unified
 * speed limit engine. The 300m geometric band is NOT included here — it
 * gets its own path via [distanceToCoast] in the ViewModel merge logic.
 */
object SpeedZoneBuilder {

    /**
     * Build speed zones from a regulated zone set.
     *
     * @param zoneSet The deserialized [RegulatedZoneSet], or null (graceful degradation).
     * @return List of [SpeedZone] for zones with a non-null speed limit. Empty if
     *         [zoneSet] is null or contains no speed zones.
     */
    fun build(zoneSet: RegulatedZoneSet?): List<SpeedZone> {
        if (zoneSet == null) return emptyList()

        return zoneSet.zones
            .filter { it.speedLimitKn != null }
            .map { zone ->
                // Compute effective speed before name — the name fallback
                // uses the speed value, so it must reflect any overrides.
                val effectiveKn = when {
                    // Lérins "outside channel" zone: SHOM says 3 kn,
                    // but treated as 5 kn for consistency with the 300m band.
                    zone.description.contains("outside channel", ignoreCase = true) ||
                        (zone.name == "other" && zone.speedLimitKn == 3.0) -> 5.0
                    else -> zone.speedLimitKn!!
                }
                SpeedZone(
                    id = zone.sourceRef.ifBlank { zone.name },
                    // Filter SHOM's literal "null" string (unnamed zones) so the
                    // dashboard tile displays a human-readable name instead.
                    name = zone.name
                        .takeIf { it != "null" && it.isNotBlank() }
                        ?: "Zone ${"%.0f".format(effectiveKn)} kn",
                    speedLimitKn = effectiveKn,
                    outerRing = zone.outerRing,
                    holes = zone.holes,
                    source = zone.source
                )
            }
    }
}
