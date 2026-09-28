package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

/**
 * Two card sections laid side by side and divided by the vertical rule — `ui-component-guidelines`
 * §2.14. Each side is a `Column` taking the share of the width it is weighted and filling the row's
 * height; the row is as tall as its taller side and the rule stretches to that height, so the two
 * sections read as one card's two halves rather than as two cards.
 *
 * **The sides share one height on purpose.** A side puts its heading block first, then
 * `Spacer(Modifier.weight(1f))` and any minimum gap, then its control — so the two controls sit on the
 * same bottom line even when they differ in height, while the headings stay at the top. The sides are
 * not given the slack by this composable; the spacer is the caller's, which is what keeps a heading and
 * its control free to be drawn by different pieces.
 *
 * The enclosing `CardArea` owns the surface and the horizontal inset (§2.0), and each side pads
 * vertically only, as every row does. The rule carries `${ui.divider.gap}` either side and
 * `${ui.divider.height}` of width, and no inset of its own.
 *
 * @param weightLeft the left section's share of the width; the right side takes the remainder.
 */
@Composable
internal fun SectionRow(
    weightLeft: Float,
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AppConfig.uiDividerGap.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(weightLeft)
                .fillMaxHeight(),
            content = left
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(AppConfig.uiDividerHeight.dp)
                .background(ComposeColor(AppConfig.uiDividerColor))
        )
        Column(
            modifier = Modifier
                .weight(1f - weightLeft)
                .fillMaxHeight(),
            content = right
        )
    }
}
