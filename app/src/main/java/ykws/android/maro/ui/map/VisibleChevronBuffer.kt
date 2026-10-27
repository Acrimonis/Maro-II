package ykws.android.maro.ui.map

/** Reused scratch point; projectors fill it without allocating one result per endpoint. */
internal class MutableScreenPoint(var x: Float = 0f, var y: Float = 0f)

/** Per-overlay, per-draw projection results. Capacity cannot exceed that overlay's sampled anchors. */
internal class VisibleChevronBuffer {
    private var indices = IntArray(0)
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    private val screen = MutableScreenPoint()
    var count: Int = 0
        private set
    val capacity: Int get() = indices.size
    fun anchorIndex(index: Int): Int = indices[index]
    fun x(index: Int): Float = xs[index]
    fun y(index: Int): Float = ys[index]

    fun prepare(points: List<RenderPoint>, anchors: List<ArrowAnchor>, width: Float, height: Float,
                margin: Float, project: (RenderPoint, MutableScreenPoint) -> Unit) {
        if (anchors.size > indices.size) {
            indices = IntArray(anchors.size)
            xs = FloatArray(anchors.size)
            ys = FloatArray(anchors.size)
        }
        count = 0
        anchors.forEachIndexed { index, anchor ->
            project(points[anchor.segmentIndex], screen)
            val ax = screen.x; val ay = screen.y
            project(points[anchor.segmentIndex + 1], screen)
            val x = ax + (screen.x - ax) * anchor.t
            val y = ay + (screen.y - ay) * anchor.t
            if (x < -margin || x > width + margin || y < -margin || y > height + margin) return@forEachIndexed
            indices[count] = index
            xs[count] = x
            ys[count] = y
            count++
        }
    }
}
