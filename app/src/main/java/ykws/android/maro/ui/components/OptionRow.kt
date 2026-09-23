package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * The app's one rendering of a checkbox and its label — the dialogs' option rows and the route panel's
 * pin all read it, so a box and its label read the same distance apart everywhere.
 *
 * **The row states no gap of its own: the checkbox's target inset is the gap**, which is what keeps
 * that distance identical at every call site without inventing a number.
 *
 * The row is the target, not the box: it carries the `toggleable` and the merged descendants while the
 * box is left at `onCheckedChange = null`, so the whole row is one tap and one announcement. The
 * label's size is the caller's, the surrounding surface's own type being the one thing an instance
 * may keep.
 */
@Composable
internal fun OptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelFontSize: TextUnit = 14.sp
) {
    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = ComposeColor(AppConfig.uiAccent)
            )
        )
        Text(
            text = label,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = labelFontSize,
            modifier = Modifier.weight(1f)
        )
    }
}
