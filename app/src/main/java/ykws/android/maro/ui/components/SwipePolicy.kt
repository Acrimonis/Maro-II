package ykws.android.maro.ui.components

/**
 * **The card swipe's arithmetic, stated once and free of Compose.**
 *
 * Every figure it needs is an input — the drag's own offset, the card's measured width and the
 * threshold — so the release's decision and the drag's clamp can be unit-tested without a device.
 * The design of record is `xTrack/Ui_General/261003_FEAT_PLN_Ui_General_right-swipe-pin.md`.
 */

/**
 * What a released card drag resolves. [Delete] keeps the left swipe's existing lifecycle, with a
 * pending set and a snackbar behind it; [TogglePin] commits the pin on release and returns the card
 * to rest; [None] is the snap-back that changes nothing.
 */
internal enum class SwipeOutcome { None, Delete, TogglePin }

/**
 * The outcome a release resolves: a leftward drag past the threshold deletes, a rightward one past
 * it toggles the pin, and a drag inside the threshold — or one resolving exactly at it — changes
 * nothing.
 *
 * A card whose width is not measured yet resolves nothing whatever the offset, so a first-frame
 * jitter can never fire an action.
 */
internal fun swipeOutcome(offsetPx: Float, cardWidthPx: Float, threshold: Float): SwipeOutcome {
    if (cardWidthPx <= 0f) return SwipeOutcome.None
    val reach = cardWidthPx * threshold
    return when {
        offsetPx > reach -> SwipeOutcome.TogglePin
        offsetPx < -reach -> SwipeOutcome.Delete
        else -> SwipeOutcome.None
    }
}

/**
 * The offset a drag is held at: the card travels its own width either way and no further, so what
 * a drag reveals is never wider than the card that owns it.
 */
internal fun swipeClampedOffset(offsetPx: Float, cardWidthPx: Float): Float =
    offsetPx.coerceIn(-cardWidthPx, cardWidthPx)
