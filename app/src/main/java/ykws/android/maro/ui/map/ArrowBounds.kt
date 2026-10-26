package ykws.android.maro.ui.map

/** Whole geometry bounds: no clipping or resampling, so the original arrow phase is preserved. */
internal data class ArrowBounds(val south: Double, val north: Double, val west: Double, val east: Double) {
    fun outside(view: ArrowBounds): Boolean {
        // Longitude wrapping and invalid/inverted projections take the original projection path.
        if (!ordinary() || !view.ordinary()) return false
        return north < view.south || south > view.north || east < view.west || west > view.east
    }

    private fun ordinary(): Boolean = south.isFinite() && north.isFinite() && west.isFinite() && east.isFinite() &&
        south >= -mercatorLimit && north <= mercatorLimit && south <= north && west <= east && east - west < 180 && west >= -180 && east <= 180

    companion object {
        private val mercatorLimit = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(Math.PI)))
        fun of(points: List<RenderPoint>): ArrowBounds? {
            if (points.isEmpty()) return null
            var south = Double.POSITIVE_INFINITY
            var north = Double.NEGATIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            for (point in points) {
                south = minOf(south, point.lat); north = maxOf(north, point.lat)
                west = minOf(west, point.lon); east = maxOf(east, point.lon)
            }
            return ArrowBounds(south, north, west, east)
        }
    }
}
