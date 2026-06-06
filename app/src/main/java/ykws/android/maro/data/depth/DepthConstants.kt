package ykws.android.maro.data.depth

import ykws.android.maro.data.model.BoundingBox

/**
 * Tunable constants for the depth feature, shared by the generator, repository, render
 * model, and view model. See DepthMappingPlan.md § 9.
 */
object DepthConstants {
    const val REGION_ID = "nice-frejus"

    /** Baked-grid extent — full Cannes→Menton coastal zone (matches the coastline zone). lat S/N, lon W/E. */
    val WATER_BBOX = BoundingBox(latSouth = 43.40, latNorth = 43.75, lonWest = 6.70, lonEast = 7.31)

    const val GRID_RES_M = 25.0
    const val SHALLOW_TIER_MAX_M = 10.0       // Litto3D authoritative ceiling (constraint #4)
    const val COLLISION_MAX_DEPTH_M = 5.0

    /** Height of the IGN69 datum above LAT here (micro-tidal Côte d'Azur); Litto3D depth→LAT shift. */
    const val IGN69_ABOVE_LAT_M = 0.40

    /** Isobath depths (metres). Finer near the surface (collision), coarser deep (profile). */
    val ISOBATH_LEVELS = listOf(2f, 4f, 6f, 8f, 10f, 15f, 20f, 25f, 30f, 40f, 50f, 60f)
    const val ISOBATH_EPSILON_M = 8.0

    const val DEPTH_MAP_MIN_DRAW_ZOOM = 11.0
    const val ISOBATH_MIN_DRAW_ZOOM = 13.0
    const val SHALLOW_ISOBATH_MIN_ZOOM = 15.0
}
