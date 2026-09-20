package ykws.android.maro.data.model

/**
 * One undirected mesh edge.
 *
 * It carries its length and whether it lies inside the 300 m coastal band — nothing else. The speed
 * limit and the time are resolved at search time from the live zone layer, which is what keeps a
 * regulation change or a cruise-speed change from ever requiring a re-bake.
 *
 * @property from    index of the first endpoint node.
 * @property to      index of the second endpoint node.
 * @property lengthM edge length in metres.
 * @property inBand  true when the edge lies inside (or crosses) the 300 m coastal band.
 */
data class RouteEdge(
    val from: Int,
    val to: Int,
    val lengthM: Double,
    val inBand: Boolean = false
)
