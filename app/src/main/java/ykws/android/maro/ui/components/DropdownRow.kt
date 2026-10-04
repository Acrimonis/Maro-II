package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ykws.android.maro.config.AppConfig

/**
 * **A dropdown field: an optional label and description over one box, and the list that box opens.**
 *
 * The box itself is [`DropdownBox`], which owns its surface, its arrow, its metrics and the style its value is
 * drawn in — this control owns everything around it: the optional heading, the anchor, the popup and its wheel.
 * Nothing here re-spells a padding, an arrow or a style, and nothing here holds a number the box already knows.
 *
 * **What its environment sets is the behaviour, [sizing]** — the width the caller gives ([DropdownSizing.Fill],
 * the default) or the width this field's longest entry needs ([DropdownSizing.Content]), which is asked of the
 * box through [`dropdownBoxWidth`] and applied here. No caller hands this control a width of its own.
 *
 * A passed `label`/`description` stacks above the box; every call site passes none today, a section header or
 * the drawer's own comment being what names the control. **A required `accessibleName` is the one string each
 * call site hands it**, carried by the box as its node's `contentDescription` together with `Role.DropdownList`,
 * because a label-less box would otherwise announce nothing at all.
 *
 * **The list is a §2.10 popup the box itself positions, and its body is a wheel** (2026-09-29): a [Popup] at
 * the box's bottom-left, its width the box's own measured width, its height bounded by `popupMaxHeightDp()`,
 * carrying a [PopupSurface] with `scrollable = false` — the wheel owns the only scroll — whose content is a
 * [DropdownWheel]. Material 3's own menu was retired for it: `ExposedDropdownMenu` sized itself from the
 * anchor and then shifted to stay inside the window, which showed as a horizontal offset against the box in
 * landscape — placement we could neither see nor override.
 *
 * The generic [T] is the option value the caller persists (the `CustomSortField` shape): the row never holds
 * user-facing text, the caller hands in already-resolved labels for each option and the selected value.
 *
 * **The selection is resolved once, and both surfaces read that one answer** (2026-09-29): the box's word and
 * the wheel's entry come from the same resolved index, so a value the options do not carry shows the entry the
 * popup bands rather than a blank box beside a banded first row. §2.12 carries the rule.
 */
@Composable
internal fun <T> DropdownRow(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    accessibleName: String,
    description: String? = null,
    sizing: DropdownSizing = DropdownSizing.Fill,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    // One resolution, read by both surfaces: an entry the options do not carry leaves this at the first
    // option, and the box paints that same option's word — never a blank box against a banded entry 0.
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val selectedLabel = options.getOrNull(selectedIndex)?.second.orEmpty()
    val menuMaxHeight = popupMaxHeightDp(LocalConfiguration.current.screenHeightDp).dp
    val measuredWidth = with(LocalDensity.current) { anchorSize.width.toDp() }
    val menuWidth = if (anchorSize.width > 0) measuredWidth else POPUP_WIDTH_DP.dp

    Column(
        modifier = when (sizing) {
            // A field fixed to its longest entry hugs that box; a filling one takes the row it was given.
            DropdownSizing.Content -> modifier
            DropdownSizing.Fill -> modifier.fillMaxWidth()
        }
    ) {
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
            DropdownBox(
                value = selectedLabel,
                accessibleName = accessibleName,
                onClick = { expanded = !expanded },
                // The behaviour turned into the width it means: the box's own answer for the longest entry, or
                // the row the caller gave. Either way the number is the box's business, never the caller's.
                modifier = when (sizing) {
                    DropdownSizing.Content -> Modifier.width(dropdownBoxWidth(options.map { it.second }))
                    DropdownSizing.Fill -> Modifier.fillMaxWidth()
                },
                onMeasured = { anchorSize = it }
            )
            // Flush under the box's own left edge, as wide as the box, so the two align exactly in either
            // orientation and the list can never reach past the space the box already fits.
            if (expanded) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x = 0, y = anchorSize.height),
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false)
                ) {
                    // The wheel owns the popup's scroll, so the surface does not add one of its own.
                    PopupSurface(maxHeight = menuMaxHeight, width = menuWidth, scrollable = false) {
                        DropdownWheel(
                            labels = options.map { it.second },
                            selectedIndex = selectedIndex,
                            onChoose = { index ->
                                options.getOrNull(index)?.let { onSelect(it.first) }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
