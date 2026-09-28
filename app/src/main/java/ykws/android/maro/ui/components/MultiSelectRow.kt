package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Multi-choice counterpart of [SegmentedRow] — **the same connected control, and deliberately so**: one
 * bar is the app's norm for a set of choices, whether they exclude each other or not (2026-09-28, settled
 * by the user's word against today's separate pills). What differs between the two controls is what they
 * **do**: [isOn] reports each half's own state and [onToggle] flips only the half that was tapped, so any
 * combination is a state, including none.
 *
 * **The on-face is the app's taken-choice face**, shared with its sibling's selected segment on purpose —
 * `ui.select.container`, a **1dp `ui.accent` border on the on half** and a **check glyph in
 * `ui.value.text`** — with the label rising to `uiTextPrimary`. Off is the inactive face: no fill, a muted
 * label. One bar, one face.
 *
 * **Surface-free**, as its sibling is, so the enclosing card owns the surface.
 *
 * Accessibility: each half is `toggleable` with a [Role.Checkbox] and there is deliberately **no**
 * `selectableGroup()`, so it is announced as "check box, checked/unchecked" — the honest reading for two
 * axes that do not exclude each other, where the sibling's radio group would announce "n of m, selected".
 */
@Composable
internal fun <T> MultiSelectRow(
    options: List<Pair<T, String>>,
    isOn: (T) -> Boolean,
    onToggle: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .border(
                width = 1.dp,
                color = ComposeColor(AppConfig.uiDividerColor),
                shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
            )
    ) {
        options.forEach { (value, label) ->
            val on = isOn(value)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) ComposeColor(AppConfig.uiSelectContainer) else ComposeColor.Transparent)
                    // The on half's own edge, so the state is legible without the parent's single
                    // hairline being asked to mean two things.
                    .then(
                        if (on) Modifier.border(1.dp, ComposeColor(AppConfig.uiAccent))
                        else Modifier
                    )
                    .toggleable(
                        value = on,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(value) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                // One centred group: the glyph rides beside the label rather than over it, so the label
                // keeps the cell's centre while the state marker is added.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (on) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = ComposeColor(AppConfig.uiValueText),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = label,
                        color = if (on) ComposeColor(AppConfig.uiTextPrimary) else ComposeColor(AppConfig.uiTextMuted),
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
