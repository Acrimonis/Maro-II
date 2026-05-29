package ykws.android.maro.data.model

/**
 * UI-ready state for the coastline loading pipeline.
 *
 * Transitions: Idle → Loading → Ready | Error
 */
sealed interface CoastlineState {
    /** Initial state — generation has not been started yet. */
    data object Idle : CoastlineState

    /** Fetch/processing in progress. */
    data object Loading : CoastlineState

    /** Coastline data ready for rendering. */
    data class Ready(
        val polylines: List<CoastlineSegment>,
        val metadata: CoastlineMetadata
    ) : CoastlineState

    /** Pipeline failed. */
    data class Error(val message: String) : CoastlineState
}
