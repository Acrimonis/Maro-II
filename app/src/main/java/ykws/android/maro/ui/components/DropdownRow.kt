package ykws.android.maro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ykws.android.maro.config.AppConfig

/** The box's own metrics; its vertical padding is the bar cells' own [`BAR_CELL_PAD_VERTICAL_DP`]. */
private const val FIELD_PAD_HORIZONTAL_DP = 12
private const val FIELD_ARROW_GAP_DP = 8
private const val FIELD_BORDER_DP = 1

/**
 * Optional label + optional description + the dropdown box — placed directly on a [CardArea], which owns
 * the horizontal inset. Pass `label = null` where a section header already supplies the heading.
 *
 * **The box is the control, the anchor and the tap target, built on the bars' own base** (2026-09-29): a
 * `Row` on `uiRadiusCard` behind the bars' own **1dp `uiAccent` edge** with their 10dp vertical padding —
 * the base [MultiSelectRow] and [SegmentedRow] stand on — holding the value in white `uiTextPrimary`, one
 * line ellipsised, and the app's `KeyboardArrowDown` in `uiAccent`. It paints no surface of its own, as the
 * bars paint none. A passed `label`/`description` stacks above the box; no call site passes one today.
 * A **required `accessibleName`** is the one string its call site hands it, set as the node's
 * `contentDescription` together with `Role.DropdownList` — a label-less box would otherwise announce
 * nothing at all.
 *
 * **The list is a §2.10 popup the box itself positions** (2026-09-29): a [Popup] at the box's bottom-left,
 * its width the box's own measured width, its height bounded by `popupMaxHeightDp()`, its content a
 * [PopupSurface] of [PopupRow]s. Material 3's own menu was retired for it: `ExposedDropdownMenu` sized
 * itself from the anchor and then shifted to stay inside the window, which showed as a horizontal offset
 * against the box in landscape — placement we could neither see nor override. Nothing about the placement
 * is now out of our hands, and nothing M3 would colour can reach the value.
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
    accessibleName: String,
    description: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val menuMaxHeight = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp
    val measuredWidth = with(LocalDensity.current) { anchorSize.width.toDp() }
    val menuWidth = if (anchorSize.width > 0) measuredWidth else POPUP_WIDTH_DP.dp

    Column(modifier = Modifier.fillMaxWidth()) {
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
        Box {
            // The box is the anchor, the tap target and the reporter of the list's own width, so §2.12's
            // single-target rule holds and the list is placed from bounds we measured ourselves.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
                    .border(
                        width = FIELD_BORDER_DP.dp,
                        color = ComposeColor(AppConfig.uiAccent),
                        shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
                    )
                    .onSizeChanged { anchorSize = it }
                    .clickable { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibleName
                        role = Role.DropdownList
                    }
                    .padding(
                        horizontal = FIELD_PAD_HORIZONTAL_DP.dp,
                        vertical = BAR_CELL_PAD_VERTICAL_DP.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = ComposeColor(AppConfig.uiTextPrimary),
                    fontSize = AppConfig.uiFontValueSize.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(FIELD_ARROW_GAP_DP.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ComposeColor(AppConfig.uiAccent)
                )
            }
            // Flush under the box's own left edge, as wide as the box, so the two align exactly in either
            // orientation and the list can never reach past the space the box already fits.
            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x = 0, y = anchorSize.height),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false)
                ) {
                    PopupSurface(maxHeight = menuMaxHeight, width = menuWidth) {
                        PopupSectionCard {
                            options.forEach { (value, optionLabel) ->
                                PopupRow(
                                    text = optionLabel,
                                    selected = value == selected,
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
        }
    }
}
