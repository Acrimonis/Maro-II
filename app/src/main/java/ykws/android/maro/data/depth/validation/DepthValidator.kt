package ykws.android.maro.data.depth.validation

import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.TierResidual
import ykws.android.maro.data.model.ValidationReport
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Compares an extracted [DepthGrid] against independent ground-truth control points and
 * produces a [ValidationReport] — both a dev-time regression gate and a runtime confidence
 * indicator. See DepthMappingDesign.md § Data validation process.
 *
 * Residuals are computed after **datum alignment** (truth → grid datum). A near-constant
 * residual (low spread, high |mean bias|) is the fingerprint of a vertical-datum offset,
 * not random error — flagged via [ValidationReport.datumMismatchSuspected].
 */
object DepthValidator {

    data class Config(
        val collisionMaxDepthM: Double = 5.0,
        val shallowMaxDepthM: Double = 10.0,
        val diveMaxDepthM: Double = 60.0,
        val collisionRmsePassM: Double = 0.5,   // 0–5 m gate (blocking)
        val collisionMaxErrM: Double = 1.0,
        val diveRmsePassM: Double = 3.0,         // 5–60 m gate (soft)
        val diveMaxErrM: Double = 6.0,
        val datumBiasFlagM: Double = 0.5,
        val minControlPoints: Int = 4
    )

    /** Height of each datum surface above LAT (metres), for the Côte d'Azur micro-tidal coast. */
    private val OFFSET_TO_LAT = mapOf(
        DepthDatum.LAT to 0.0,
        DepthDatum.MSL to 0.15,
        DepthDatum.IGN69 to 0.40,
        DepthDatum.UNKNOWN to 0.0
    )

    private fun offsetToLat(d: DepthDatum): Double = OFFSET_TO_LAT[d] ?: 0.0

    /** Converts [depth] referenced to [from] into a depth referenced to [to]. */
    private fun convertDatum(depth: Double, from: DepthDatum, to: DepthDatum): Double =
        depth + offsetToLat(from) - offsetToLat(to)

    fun validate(
        grid: DepthGrid,
        points: List<ControlPoint>,
        cfg: Config = Config(),
        nowMs: Long = 0L
    ): ValidationReport {
        // (alignedTruthDepth, residual = sampled − truth)
        val data = ArrayList<Pair<Double, Double>>()
        var uncovered = 0
        for (cp in points) {
            val sample = grid.depthAt(cp.lat, cp.lon)
            if (!sample.hasData) { uncovered++; continue }
            val alignedTruth = convertDatum(cp.knownDepthM, cp.datum, grid.datum)
            data.add(alignedTruth to (sample.depthM - alignedTruth))
        }

        val covered = data.size
        if (covered == 0) {
            return ValidationReport(
                meanBiasM = 0.0, rmseM = 0.0, maxAbsErrM = 0.0,
                controlPointCount = 0, uncoveredCount = uncovered,
                passed = false, datumMismatchSuspected = false,
                tiers = emptyList(), validatedAtMs = nowMs
            )
        }

        val res = data.map { it.second }
        val meanBias = res.average()
        val rmse = sqrt(res.sumOf { it * it } / covered)
        val maxAbs = res.maxOf { abs(it) }
        val std = sqrt(res.sumOf { (it - meanBias).pow(2) } / covered)
        val datumMismatch = abs(meanBias) > cfg.datumBiasFlagM && std < cfg.datumBiasFlagM * 0.5

        fun tier(lo: Double, hi: Double, inclusiveHi: Boolean): TierResidual? {
            val sub = data.filter { (d, _) -> d >= lo && (if (inclusiveHi) d <= hi else d < hi) }
            if (sub.isEmpty()) return null
            val r = sub.map { it.second }
            return TierResidual(
                minDepthM = lo, maxDepthM = hi,
                meanBiasM = r.average(),
                rmseM = sqrt(r.sumOf { it * it } / r.size),
                maxAbsErrM = r.maxOf { abs(it) },
                count = r.size
            )
        }

        val collision = tier(0.0, cfg.collisionMaxDepthM, inclusiveHi = false)
        val shallow = tier(cfg.collisionMaxDepthM, cfg.shallowMaxDepthM, inclusiveHi = false)
        val dive = tier(cfg.shallowMaxDepthM, cfg.diveMaxDepthM, inclusiveHi = true)

        val collisionPass = collision == null ||
            (collision.rmseM <= cfg.collisionRmsePassM && collision.maxAbsErrM <= cfg.collisionMaxErrM)

        val passed = collisionPass && covered >= cfg.minControlPoints && !datumMismatch

        return ValidationReport(
            meanBiasM = meanBias,
            rmseM = rmse,
            maxAbsErrM = maxAbs,
            controlPointCount = covered,
            uncoveredCount = uncovered,
            passed = passed,
            datumMismatchSuspected = datumMismatch,
            tiers = listOfNotNull(collision, shallow, dive),
            validatedAtMs = nowMs
        )
    }
}
