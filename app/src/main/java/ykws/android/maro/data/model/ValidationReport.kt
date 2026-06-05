package ykws.android.maro.data.model

/**
 * Residual statistics for one depth tier (e.g. 0–5 m collision band).
 *
 * @property meanBiasM signed mean(sampled − truth); a near-constant bias flags a datum offset.
 * @property rmseM root-mean-square error.
 * @property maxAbsErrM worst single residual.
 * @property count control points that fell inside this tier and had grid coverage.
 */
data class TierResidual(
    val minDepthM: Double,
    val maxDepthM: Double,
    val meanBiasM: Double,
    val rmseM: Double,
    val maxAbsErrM: Double,
    val count: Int
)

/**
 * Outcome of validating a [DepthGrid] against independent ground-truth control points.
 * Both a dev-time regression gate and a runtime confidence indicator (embedded in the cache).
 */
data class ValidationReport(
    val meanBiasM: Double,
    val rmseM: Double,
    val maxAbsErrM: Double,
    val controlPointCount: Int,
    val uncoveredCount: Int,
    val passed: Boolean,
    val datumMismatchSuspected: Boolean,
    val tiers: List<TierResidual>,
    val validatedAtMs: Long = 0L
)
