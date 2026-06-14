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
                SpeedZone(
                    id = zone.sourceRef.ifBlank { zone.name },
                    // Filter SHOM's literal "null" string (unnamed zones) so the
                    // dashboard tile displays a human-readable name instead.
                    // Use "Zone X kn" format when we have a speed limit but no real name.
                    name = zone.name
                        .takeIf { it != "null" && it.isNotBlank() }
                        ?: "Zone ${"%.0f".format(zone.speedLimitKn)} kn",
                    speedLimitKn = zone.speedLimitKn!!,
                    outerRing = zone.outerRing,
                    holes = zone.holes,
                    source = zone.source
                )
            }
    }
}
