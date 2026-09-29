package ykws.android.maro.ui.components

import kotlin.math.abs

/**
 * **The wheel's arithmetic, stated once and free of Compose.**
 *
 * Every figure it needs is an input — the measured slot height, the height bound, the visible rows' own
 * bounds — so the slot count, the end padding and the row under the band can be unit-tested without a
 * device. The design of record is `xTrack/Ui_General/260929_FEAT_PLN_Ui_General_dropdown-wheel.md`.
 */

/** The most slots a wheel opens with, whatever the list holds. */
internal const val WHEEL_MAX_SLOTS = 5

/** The fewest slots a wheel opens with, unless the height bound cannot carry even these. */
internal const val WHEEL_MIN_SLOTS = 3

/** The slot height at font scale 1.0 — a bar cell's own: `18dp` of glyph plus 10dp of padding twice. */
internal const val WHEEL_ITEM_DP = 38f

/**
 * The slots a wheel opens with for [itemCount] entries, the user's rule: **five for five or more, four at
 * four, three for three or fewer** — the content's own count, held between [WHEEL_MIN_SLOTS] and
 * [WHEEL_MAX_SLOTS]. An even count is allowed because the band is one slot tall and the snap puts an entry
 * at the viewport's centre, so it covers exactly that entry whatever the count; the ends still reach the
 * centre through a half-slot padding, which [wheelEndPadDp] computes in floats.
 */
internal fun wheelSlotCount(itemCount: Int): Int =
    itemCount.coerceIn(WHEEL_MIN_SLOTS, WHEEL_MAX_SLOTS)

/**
 * [itemCount]'s slot count cut down to what [maxHeightDp] can carry: the largest count of [slotDp]-tall
 * slots that fits, floored at one. The bound only ever narrows the rule above — it never widens a short
 * list's own count.
 */
internal fun wheelSlotsFitting(itemCount: Int, slotDp: Float, maxHeightDp: Float): Int {
    if (slotDp <= 0f || maxHeightDp <= 0f) return 1
    val fitting = (maxHeightDp / slotDp).toInt().coerceAtLeast(1)
    return minOf(wheelSlotCount(itemCount), fitting)
}

/**
 * A slot count's own end padding, so the first and last entries can reach the centre: `slot × (slots − 1) /
 * 2`. The division is a **float** one on purpose — at four slots the padding is a half-slot, `1.5`, where
 * integer arithmetic would give a whole one and leave both ends unable to reach the centre.
 */
internal fun wheelEndPadDp(slots: Int, slotDp: Float): Float = slotDp * ((slots - 1) / 2f)

/** One visible row's bounds in content pixels, as `LazyListItemInfo` reports them. */
internal data class WheelItemBounds(val index: Int, val offsetPx: Int, val sizePx: Int)

/**
 * The row the band names: the visible row whose own centre sits nearest the viewport's centre.
 *
 * It reads the rows' own bounds rather than the `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`
 * pair, because under content padding that pair both omits the leading padding — the centred row is not
 * `first + 0` — and cannot tell two different band rows apart. Returns null only when nothing is visible.
 */
internal fun wheelCentredIndex(
    visible: List<WheelItemBounds>,
    viewportStartOffsetPx: Int,
    viewportSizePx: Int
): Int? {
    if (visible.isEmpty()) return null
    val viewportCentre = viewportStartOffsetPx + viewportSizePx / 2
    return visible.minByOrNull { row -> abs(row.offsetPx + row.sizePx / 2 - viewportCentre) }?.index
}

/**
 * The scroll that puts [selectedIndex] under the band, measured **from the popup's own rest position** —
 * `index × slot`, the grid the design states. Relative on purpose (2026-09-29): the popup is composed
 * afresh on every open, so frame 0 bands entry 0, and a scroll *by* this amount never asks which way an
 * offset is counted or which origin it is counted from — the direction is the drag's own, and the end
 * padding cancels out of the difference instead of entering the sum.
 */
internal fun wheelTargetScrollPx(selectedIndex: Int, slotDp: Float): Float = selectedIndex * slotDp

/**
 * The one step that closes the wheel's loop: the slots to scroll so the row the band names is the entry
 * the caller holds, zero when the two already agree.
 *
 * It is a **detector**, not a proof: it corrects the landing and can say nothing about the convention
 * behind [wheelCentredIndex] — were the centre read wrong, the wheel would stay self-consistent and still
 * sit off the band. The guard applies it once per open, and never fights a clamp it cannot win.
 */
internal fun wheelCorrectionSlots(targetIndex: Int, centredIndex: Int): Int = targetIndex - centredIndex
