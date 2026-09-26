package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

/**
 * Nested 5% white container (`0x0DFFFFFF` + `0x40FFFFFF` border) shown when an [Expander] is open.
 * Holds any controls, optionally split into sections by [SectionDivider].
 *
 * This is the surface treatment the [Expander] reveals, never a nesting tier: no card or any other
 * full `uiCardBackground` surface goes inside it (`ui-component-guidelines` §2.4). It owns the
 * horizontal inset, so the rows it holds pad vertically only.
 *
 * Promoted here from `MapScreenSettingsOverlay.kt` on 2026-09-26 so a second surface can follow the
 * settings recipe instead of re-drawing it.
 */
@Composable
internal fun NestedCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .background(ComposeColor(AppConfig.uiNestedCardBg))
            .border(1.dp, ComposeColor(AppConfig.uiNestedCardBorder), RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .padding(horizontal = AppConfig.uiPaddingCardHorizontal.dp, vertical = AppConfig.uiPaddingContentComfortable.dp)
    ) {
        content()
    }
}
