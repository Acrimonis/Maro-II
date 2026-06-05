package ykws.android.maro.data.model

/**
 * UI-ready state for the depth loading pipeline.
 *
 * Transitions: Idle → Loading → Ready | Error
 */
sealed interface DepthState {
    /** Initial state — load has not been started yet. */
    data object Idle : DepthState

    /** Fetch/processing in progress. */
    data object Loading : DepthState

    /** Depth grid ready for rendering and queries. */
    data class Ready(val grid: DepthGrid) : DepthState

    /** Pipeline failed. */
    data class Error(val message: String) : DepthState
}
