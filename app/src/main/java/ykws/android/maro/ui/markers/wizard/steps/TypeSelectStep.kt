package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.SectionDivider
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.map.ButtonColors
import ykws.android.maro.ui.map.IconPickerDialog
import ykws.android.maro.ui.map.MarkerType
import ykws.android.maro.ui.map.MarkersViewModel

/**
 * Type selection step — the three marker types as one segmented control, plus the icon row.
 *
 * Both are **sections of one card**, the settings pattern: a single [CardArea] with the segments and
 * the icon row as its two sections, `SectionDivider()` between them (`ui-component-guidelines`
 * §2.3/§2.6) — the gap between cards is for two cards, and this step has one. It draws no card shell of
 * its own and measures what it holds rather than filling the body, so the drawer's frame shows.
 *
 * The segments carry an icon each, which the shared `SegmentedRow` has no slot for, so the control
 * stays hand-drawn on the card's own surface — the one place this step departs from the segment
 * pattern's preference (§2.7).
 */
@Composable
internal fun TypeSelectStep(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()
    val accent = ComposeColor(AppConfig.uiAccent)
    val divider = ComposeColor(AppConfig.uiDividerColor)
    val primaryText = ComposeColor(AppConfig.uiTextPrimary)
    val mutedText = ComposeColor(AppConfig.uiTextMuted)

    Column(modifier = Modifier.fillMaxWidth()) {
        CardArea {
            // ── Section 1: the marker type ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val types = listOf(
                    Triple(MarkerType.PIN, Icons.Filled.LocationOn, R.string.marker_type_pin),
                    Triple(MarkerType.CIRCLE, Icons.Filled.RadioButtonUnchecked, R.string.marker_type_zone),
                    Triple(MarkerType.CORRIDOR, Conversion_path, R.string.marker_type_corridor)
                )
                types.forEach { (type, icon, labelResId) ->
                    val label = stringResource(labelResId)
                    val selected = form.type == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accent else divider)
                            .clickable { viewModel.updateForm { it.copy(type = type) } }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) primaryText else mutedText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                color = if (selected) primaryText else mutedText,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            SectionDivider()

            // ── Section 2: the icon (purely decorative — no pin semantics) ──
            var showIconPicker by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showIconPicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (form.icon != null) {
                    Text(form.icon!!, fontSize = 20.sp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.LocationOff,
                        contentDescription = stringResource(R.string.cd_change_icon),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (form.icon != null) stringResource(R.string.cd_change_icon) else stringResource(R.string.cd_set_icon),
                    color = mutedText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (showIconPicker) {
                IconPickerDialog(
                    currentIcon = form.icon,
                    onIconSelected = { icon ->
                        viewModel.updateForm { it.copy(icon = icon) }
                        showIconPicker = false
                    },
                    onDismiss = { showIconPicker = false }
                )
            }
        }
    }
}
