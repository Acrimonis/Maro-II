package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Label + optional description + switch row WITHOUT its own box — placed directly on a
 * [CardArea] or inside a nested card, which own the horizontal inset; this row pads vertically
 * only. [leadingIcon] renders before the label column with the standard 8dp gap.
 *
 * [checkedColor] drives both the checked thumb and the checked track (40% alpha). It is read on
 * every recomposition — callers may pass a dynamic value (e.g. a live GPS status colour), so it
 * must NOT be remembered inside this composable.
 */
@Composable
internal fun ToggleRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    checkedColor: ComposeColor = ComposeColor(AppConfig.uiAccent)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppConfig.uiPaddingToggleVertical.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    color = ComposeColor(AppConfig.uiTextMuted),
                    fontSize = AppConfig.uiFontDescSize.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(AppConfig.uiSpacingLabelControl.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedColor,
                checkedTrackColor = checkedColor.copy(alpha = 0.4f),
                uncheckedThumbColor = ComposeColor(AppConfig.uiTextMuted),
                uncheckedTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
            )
        )
    }
}
