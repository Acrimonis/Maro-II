package ykws.android.maro.data.model

/**
 * One polyline of a depth contour, tagged with the **data source + confidence** of the cells it
 * crosses so the UI can reflect data precision (colour-by-source, dash low-confidence fill).
 */
data class IsobathLine(
    val points: List<LatLng>,
    val source: DepthSource,
    val confidence: Int
)

/**
 * One depth contour level and its polylines (already simplified). Derived from the
 * [DepthGrid] via scalar marching squares; not serialised (rebuilt from the grid).
 */
data class Isobath(
    val depthM: Float,
    val lines: List<IsobathLine>
)

/**
 * In-memory render artifacts derived once from a [DepthGrid]. The colour map itself is
 * a Bitmap held by the UI layer; [bitmapReady] flags that it has been built.
 */
data class DepthRenderModel(
    val isobaths: List<Isobath>,
    val bitmapReady: Boolean = false
)
