package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

/**
 * **The page indicator** — one dot per page, the caller choosing how many are lit.
 *
 * This is the app's single dot row: the marker wizard's step progress and the route acquisition panel's
 * page position are one control, so the two surfaces read alike rather than merely resembling each
 * other. One dot per page at 6 dp, a 4 dp gap, [AppConfig.uiAccent] over the filled set and
 * [AppConfig.uiDividerColor] after — drawn in a drawer header's trailing slot.
 *
 * The two callers differ in what a lit dot means. The marker wizard fills up to and including the
 * current step ([fillUpToCurrent] = true), so the row reads as progress through a sequence; the route
 * acquisition panel fills only the current page ([fillUpToCurrent] = false), because its pages are
 * alternatives rather than stages and no earlier page is a step already taken.
 *
 * The indicator is **decorative**: it names no action and announces nothing, as the wizard's dots never
 * have. The pages it counts stay reachable through their own doors — a swipe or a row tap.
 *
 * @param currentIndex     the page the reader stands on.
 * @param total            the number of pages, one dot each.
 * @param fillUpToCurrent  true fills every dot up to and including [currentIndex] (the wizard's
 *                         progress reading); false fills [currentIndex] alone (the acquisition
 *                         panel's position reading).
 */
@Composable
fun PageDots(
    currentIndex: Int,
    total: Int,
    fillUpToCurrent: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = ComposeColor(AppConfig.uiAccent)
    val divider = ComposeColor(AppConfig.uiDividerColor)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until total) {
            val filled = if (fillUpToCurrent) i <= currentIndex else i == currentIndex
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (filled) accent else divider)
            )
        }
    }
}
