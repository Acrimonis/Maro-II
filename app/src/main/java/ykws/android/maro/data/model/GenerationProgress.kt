package ykws.android.maro.data.model

/**
 * UI-facing progress state during coastline generation.
 *
 * @property phase Human-readable phase name (e.g. "Téléchargement OSM").
 * @property progress 0–100 integer indicating completion within the current phase.
 */
data class GenerationProgress(
    val phase: String,
    val progress: Int
)
