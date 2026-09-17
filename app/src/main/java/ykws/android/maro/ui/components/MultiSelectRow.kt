package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Multi-choice counterpart of [SegmentedRow] — the same one-outline, connected shape, but the halves
 * are independent: [isOn] reports each one's own state and [onToggle] flips only the half that was
 * tapped, so any combination is a state, including none. Off is the inactive face (no fill, muted
 * label), on is the accent face the single-choice control uses for its selected segment.
 *
 * **Surface-free**, as its sibling is, so the enclosing card owns the surface.
 *
 * Accessibility: each half is `toggleable` with a [Role.Checkbox] and no `selectableGroup()`, so it is
 * announced as "check box, checked/unchecked" — the honest reading for two axes that do not exclude
 * each other, where the sibling's radio group would announce "n of m, selected".
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
                    .background(if (on) ComposeColor(AppConfig.uiAccent) else ComposeColor.Transparent)
                    .toggleable(
                        value = on,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(value) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
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
