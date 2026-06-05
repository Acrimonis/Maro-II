package ykws.android.maro.data.model

/**
 * One depth contour level and its polylines (already simplified). Derived from the
 * [DepthGrid] via scalar marching squares; not serialised (rebuilt from the grid).
 */
data class Isobath(
    val depthM: Float,
    val lines: List<List<LatLng>>
)

/**
 * In-memory render artifacts derived once from a [DepthGrid]. The colour map itself is
 * a Bitmap held by the UI layer; [bitmapReady] flags that it has been built.
 */
data class DepthRenderModel(
    val isobaths: List<Isobath>,
    val bitmapReady: Boolean = false
)
