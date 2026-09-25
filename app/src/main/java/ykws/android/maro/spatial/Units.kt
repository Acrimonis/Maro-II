package ykws.android.maro.spatial

/**
 * The app's single home for nautical unit conversions.
 *
 * Before this file the knot and the nautical mile were spelled four different ways across the tree —
 * a `KNOTS_TO_MPS` here, an `MPS_TO_KNOTS` there, a bare `1852.0` in one feature and `1.94384` in
 * another — so a change to either factor meant chasing every copy. Everything now reads its
 * conversion from here, and no feature declares one of its own.
 *
 * The two knot factors are kept at the precision the app's speed maths has always used rather than
 * recomputed as exact reciprocals: the heatmap's ramp boundaries and the direction-arrow density
 * are tuned to these numbers, and a value differing in the sixth decimal moves a boundary test.
 * One home, holding the established values, is the point; re-deriving them is not.
 *
 * Pure Kotlin, no Android dependency, so the prebake JVM tools can use it too.
 */
object Units {

    /** Exact metres in one international nautical mile. */
    const val METRES_PER_NAUTICAL_MILE = 1852.0

    /** Metres per second in one knot, as the app's speed maths has always spelled it. */
    const val MPS_PER_KNOT = 0.514444

    /** Knots in one metre per second, as the app's speed maths has always spelled it. */
    const val KNOTS_PER_MPS = 1.94384

    /** @return the same distance expressed in metres. */
    fun nauticalMilesToMetres(nm: Double): Double = nm * METRES_PER_NAUTICAL_MILE

    /** @return the same distance expressed in nautical miles. */
    fun metresToNauticalMiles(m: Double): Double = m / METRES_PER_NAUTICAL_MILE

    /** @return the same speed expressed in metres per second. */
    fun knotsToMps(kn: Double): Double = kn * MPS_PER_KNOT

    /** @return the same speed expressed in knots. */
    fun mpsToKnots(mps: Double): Double = mps * KNOTS_PER_MPS
}
