package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * **The acquisition panel's lateral page switch** — a single page at a time, its seat owned by the caller.
 *
 * The route acquisition panel's summary table pages through this — a swipe or a row tap moves the seat
 * with one effect. It is **not** shared with the marker wizard: the wizard briefly used it for a swipe,
 * but that was built and withdrawn the same session (2026-10-06), and its step body is its own
 * `AnimatedContent` slide.
 *
 * The two-way sync is settled and jump-only on purpose: the caller's seat follows the pager **only once
 * it has settled** ([PagerState.settledPage]), so a mid-fling page index never moves the seat under the
 * user's finger; and a programmatic move is a jump (`scrollToPage`) taken only while the user is not
 * dragging, never an animation the next seat change could strand between two pages.
 *
 * The pager state is owned by the caller, so the host can read [PagerState.isScrollInProgress] in its own
 * composition and hold its body height while the pages turn — the route panel floors its outgoing height
 * through a swipe and re-settles once the pager does.
 *
 * @param pagerState    the caller's pager state, created once per host.
 * @param currentIndex  the page the caller's seat stands on, clamped into `[0, pageCount)`.
 * @param onSettled     called with the settled page index whenever it differs from the seat.
 * @param modifier      applied to the pager; the default fills the width.
 * @param content       one page, by its index.
 */
@Composable
fun SwipePager(
    pagerState: PagerState,
    currentIndex: Int,
    onSettled: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable (Int) -> Unit
) {
    val safeIndex = currentIndex.coerceIn(0, pagerState.pageCount - 1)
    val currentSeat by rememberUpdatedState(safeIndex)

    // Seat → pager: a jump, and never while the user is dragging, so the pager's own gesture is never fought.
    LaunchedEffect(safeIndex, pagerState.pageCount) {
        if (pagerState.currentPage != safeIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(safeIndex)
        }
    }
    // Pager → seat: a swipe moves the seat, but only once the pager has settled.
    LaunchedEffect(pagerState, pagerState.pageCount) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentSeat) onSettled(page)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 0
    ) { index ->
        content(index)
    }
}
