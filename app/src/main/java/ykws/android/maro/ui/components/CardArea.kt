package ykws.android.maro.ui.components

import androidx.compose.foundation.background
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
 * Main card surface — 20% white `uiCardBackground`, 12dp radius. Holds a section's description,
 * expanders, and/or standalone controls. The container owns the horizontal inset
 * (`ui.padding.card.horizontal`), so child rows pad vertically only.
 *
 * Named `CardArea` (not `Card`) to avoid shadowing Material3's `Card`.
 */
@Composable
internal fun CardArea(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(
                horizontal = AppConfig.uiPaddingCardHorizontal.dp,
                vertical = AppConfig.uiPaddingCardVertical.dp
            )
    ) {
        content()
    }
}
