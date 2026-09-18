package ykws.android.maro.ui.map

/**
 * The pan gate behind the Where-Am-I card's own close: one finger, past touch slop, once per gesture.
 *
 * A drag moves the map away from the boat, and the card lists the boat's position, so the drag is the
 * card's dismissal and the recorder's idle exit is not — that exit needs a live recording and a drawer
 * the idle threshold asked for, and it never fires for a card the user opened by their own tap.
 *
 * Three shapes are excluded by construction rather than by a later guard:
 * - a pinch never becomes a pan: [pinchStarted] latches the whole gesture as a multi-touch one, and the
 *   latch outlives a finger of it lifting, since the map can carry on panning inside a gesture the user
 *   began with two;
 * - a tap and a double-tap never exceed the slop, so no movement of a tap is ever read as a drag;
 * - the zoom buttons are controls outside the map, so their touches never arrive here at all.
 *
 * Kept free of Android types so the rule can be tested without a map, a gesture or a screen: the
 * listener maps [android.view.MotionEvent] onto the three calls, and the slop is the view's own
 * `ViewConfiguration.scaledTouchSlop`, passed in.
 */
internal class MapPanDetector(private val slopPx: Float) {

    private var downX = 0f
    private var downY = 0f
    private var down = false
    private var fired = false
    private var pinch = false

    /** A finger landed: the new gesture's own zero point, with the previous latch cleared. */
    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        down = true
        fired = false
        pinch = false
    }

    /** A second finger landed: this gesture is a pinch, and a pinch is never a pan. */
    fun pinchStarted() {
        pinch = true
    }

    /** The last finger lifted, or the gesture was cancelled: the gesture is over and nothing is held. */
    fun gestureEnd() {
        down = false
        fired = false
        pinch = false
    }

    /**
     * A move. True on the single move that first carries the finger past [slopPx] — the caller closes
     * the card on that one true — and false on every later move of the same drag, which is what makes
     * the callback fire once per gesture rather than once per frame.
     */
    fun move(pointerCount: Int, x: Float, y: Float): Boolean {
        if (!down || fired || pinch || pointerCount != 1) return false
        val dx = x - downX
        val dy = y - downY
        if (dx * dx + dy * dy <= slopPx * slopPx) return false
        fired = true
        return true
    }
}
