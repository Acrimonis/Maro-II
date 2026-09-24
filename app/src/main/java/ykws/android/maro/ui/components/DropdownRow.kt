package ykws.android.maro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Optional label + optional description + dropdown row WITHOUT its own box — placed directly on a
 * [CardArea], which owns the horizontal inset; this row pads vertically only. Pass `label = null` where
 * a section header already supplies the heading. The control shows the selected
 * option's label with a down-arrow and opens a [DropdownMenu] listing [options] on tap.
 *
 * The generic [T] is the option value the caller persists (the `CustomSortField` shape): the row never
 * holds user-facing text, the caller hands in already-resolved labels for each option and the selected
 * value. It is the dropdown the row family gains for a list that may grow past two entries, where the
 * inline [SegmentedRow] would not fit.
 */
@Composable
internal fun <T> DropdownRow(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    description: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppConfig.uiPaddingToggleVertical.dp)
            .clickable { expanded = true },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label != null) {
                Text(
                    text = label,
                    color = ComposeColor(AppConfig.uiTextPrimary),
                    fontSize = AppConfig.uiFontToggleSize.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    color = ComposeColor(AppConfig.uiTextMuted),
                    fontSize = AppConfig.uiFontDescSize.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(AppConfig.uiSpacingLabelControl.dp))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    color = ComposeColor(AppConfig.uiValueText),
                    fontSize = AppConfig.uiFontValueSize.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ComposeColor(AppConfig.uiAccent)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
