package ykws.android.maro.spatial

/**
 * How one coastline polyline reads as land or water, classified by
 * [CoastlineSpatialIndex.landRingOrientation]: an **open coast** (not a geometric ring), a
 * **CCW ring** whose interior is land (ordinary islands and hazard rings alike), or a **CW basin**
 * whose interior stays water (marina breakwaters).
 *
 * It lives here, beside the index that classifies it, so the `data/coastline` repository can name
 * the orientation without importing the avoid engine's vocabulary — the engine's adapter translates
 * it into its own edge type when it reads land edges.
 */
enum class LandRingOrientation {
    OPEN_COAST,
    CCW_RING,
    CW_BASIN
}
