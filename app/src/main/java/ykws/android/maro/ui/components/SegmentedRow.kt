package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Single-choice segmented control — Material 3 shape: one **connected** control, outer ends rounded,
 * a single hairline outline, and the **selected state on a tonal container** (`ui.select.container`)
 * with a check glyph in `ui.value.text` and a **1dp `ui.accent` border on the selected cell**. The
 * border is square on purpose: the parent's rounded clip shapes its outer end, so no second shape is
 * needed. **Surface-free**, so the enclosing [CardArea]/[NestedCard] owns the surface. [captions]
 * renders one line under each segment.
 *
 * **The accent fill is the action's, not a selection's** (§5): a selected segment wears the tonal
 * container and a check glyph rather than the accent fill, so a segmented choice and a primary button
 * never wear one face — which is why this component moved off `uiAccent`. The glyph sits **inside** the
 * weighted cell as one centred icon-then-label group, so the label keeps the cell's centre, and it
 * carries **no `contentDescription`**: the semantics already announce the state, and a second
 * announcement would say it twice.
 *
 * Accessibility: `selectableGroup()` plus a [Role.RadioButton] per segment, so it is announced as
 * "n of m, selected" instead of as unrelated buttons.
 *
 * Its multi-choice counterpart is [MultiSelectRow], which wears the same outline and the same tonal
 * on-face while each half lights on its own; the two live side by side here so a change to the shared
 * shape is seen once.
 */
@Composable
internal fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    captions: List<String>? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
                .border(
                    width = 1.dp,
                    color = ComposeColor(AppConfig.uiDividerColor),
                    shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
                )
                .selectableGroup()
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) ComposeColor(AppConfig.uiSelectContainer) else ComposeColor.Transparent)
                        // The selected cell's own edge, so the state is legible without the parent's
                        // single hairline being asked to mean two things.
                        .then(
                            if (isSelected) Modifier.border(1.dp, ComposeColor(AppConfig.uiAccent))
                            else Modifier
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // One centred group: the glyph rides beside the label rather than over it, so the
                    // label keeps the cell's centre while the state marker is added.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = ComposeColor(AppConfig.uiValueText),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = label,
                            color = if (isSelected) ComposeColor(AppConfig.uiTextPrimary) else ComposeColor(AppConfig.uiTextMuted),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        if (captions != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, _ ->
                    Text(
                        text = captions.getOrElse(index) { "" },
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = AppConfig.uiFontCommentSize.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
