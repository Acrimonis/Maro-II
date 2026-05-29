package ykws.android.maro.data.model

import kotlinx.serialization.Serializable

/**
 * Serializable wrapper for persisting coastline data to local storage.
 *
 * Saved as JSON so it survives app restarts. On next launch, the ViewModel
 * loads this cache and displays the coastline immediately without re-fetching
 * from the Overpass API.
 */
@Serializable
data class CoastlineCache(
    val segments: List<CoastlineSegment>,
    val metadata: CoastlineMetadata
)
