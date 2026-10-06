package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.map.ButtonColors

/**
 * The list item's **type glyph and its selection affordance in one 24 dp slot**, leading the title line.
 *
 * Two faces, one slot: unselected it draws the item's own type icon — the fan's layer icon, track / route /
 * marker — and selected it becomes the `uiAccent` circle bearing the white check, which is where the
 * scaffold's old corner check mark moved. The morph is the selection's whole report of itself on the title
 * line; the card's 1 dp `uiAccent` border and its 15 % tonal shift are the scaffold's and stay there.
 *
 * **Inert without a door.** [onSelect] carries the tap that enters multiselect from normal mode; in
 * multiselect the card's own interceptor owns the tap, so the caller passes `null` and the glyph stops
 * taking pointers — it reports state and nothing more. At 24 dp the glyph is under the 48 dp touch minimum
 * by design: the card's long-press is the full-size selection door and this is the quick one.
 */
@Composable
fun ListTypeGlyph(
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(Color(AppConfig.uiAccent)) else Modifier)
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_selected),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                // The type icon is decorative unless the glyph itself is the selection door.
                contentDescription = if (onSelect != null) stringResource(R.string.cd_select) else null,
                tint = ButtonColors.icon,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
