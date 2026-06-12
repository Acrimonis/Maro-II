package ykws.android.maro.data.regulation

/**
 * Hardcoded seed zones — **no longer used**.
 *
 * Data is now fetched exclusively from SHOM INSPIRE WFS and IGN API Carto Nature
 * at prebake time. No fallback seeds needed since the system gracefully degrades
 * to empty if endpoints are unreachable.
 *
 * @see ShomRegulationClient
 * @see IgnCartoNatureClient
 */
object RegulationSeeds {

    /**
     * Returns an empty list — seeds have been removed.
     * All regulated zone data comes from live WFS/API sources.
     */
    fun getSeeds(): List<RegulatedZone> = emptyList()
}
