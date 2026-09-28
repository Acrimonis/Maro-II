package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Sub-heading (16sp SemiBold `ui.text.primary`) with an optional 13sp `ui.text.secondary` one-line
 * description, for grouping settings in a section.
 *
 * The **titled** sub-section header inside a card or expander, the rung below [SectionHeader] in the
 * hierarchy: headings are white like every other heading, hierarchy coming from weight and spacing
 * rather than a dimmed colour (§2.9). Where a `CardDescription` explains a card's controls from the
 * top, this one names a **group** within it — the shape the Menu drawer's Route section wears when it
 * stands inside the Navigation card.
 */
@Composable
internal fun SubSectionHeader(title: String, description: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = AppConfig.uiFontSubsectionSize.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiTextSecondary),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
    }
}
