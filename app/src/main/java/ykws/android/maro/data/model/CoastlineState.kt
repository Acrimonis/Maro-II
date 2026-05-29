package ykws.android.maro.data.model

/**
 * UI-ready state for the coastline loading pipeline.
 *
 * Transitions: Loading → Ready | Error
 */
sealed interface CoastlineState {
    /** Fetch/processing in progress. */
    data object Loading : CoastlineState

    /** Coastline data ready for rendering and queries. */
    data class Ready(
        val data: CoastlineData
    ) : CoastlineState {
        /** Convenience access to all segments for rendering. */
        val polylines: List<CoastlineSegment> get() = data.allSegments

        /** Convenience access to metadata. */
        val metadata: CoastlineMetadata get() = data.metadata
    }

    /** Pipeline failed. */
    data class Error(val message: String) : CoastlineState
}
