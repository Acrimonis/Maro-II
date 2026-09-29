package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.AddLocationAlt
import ykws.android.maro.ui.map.ButtonColors

/**
 * The create-marker action a markers surface's header opens on: a 40 dp icon-only button, then the
 * vertical rule that divides it from the chrome group.
 *
 * **The order rule lives here, stated once:** a header's controls read
 * **the surface's own action · rule · chrome group**. Creating is the surface's own act and stands
 * beside the section label that names what it creates; the chrome group — link, filter, reset —
 * shapes *how the list is read* and takes the outer edge. The two hosts of this control read the
 * same order because they draw the same composable: the markers list's header row
 * (`ListOverlayScaffold`'s `headerActions` slot, which renders first) and the hamburger menu's
 * MARKERS header (`SectionHeader`'s `trailing` slot, outside the filter-axes gate, so creation
 * never disappears with the filters). The tracks surfaces take no create action.
 *
 * The rule is the app's own vertical-rule idiom — [AppConfig.uiDividerHeight] of width in
 * [AppConfig.uiDividerColor], [AppConfig.uiDividerGap] either side — but at a **fixed length** rather
 * than a `fillMaxHeight()`. Neither header row is intrinsic-sized, so a fill would read the incoming
 * maximum instead of the controls beside it and stretch the row; the length is therefore carried here
 * and reads identically in both hosts. It stays under the 28 dp glyph band, so the 40 dp buttons keep
 * the row's height and the rule never becomes its driver. The glyph is the standalone
 * [AddLocationAlt] vector drawn directly, tinted [ButtonColors.icon] like its neighbours — the accent
 * belongs to a surface's own filled action, which is the empty state's `Create First Marker` button.
 *
 * @param onClick the create action, wired by each host to the same create path.
 */
@Composable
internal fun MarkerCreateAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = AddLocationAlt,
                contentDescription = stringResource(R.string.cd_create_marker),
                tint = ButtonColors.icon,
                modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
            )
        }
        Spacer(Modifier.width(AppConfig.uiDividerGap.dp))
        Box(
            modifier = Modifier
                .height(RULE_LENGTH_DP.dp)
                .width(AppConfig.uiDividerHeight.dp)
                .background(ComposeColor(AppConfig.uiDividerColor))
        )
        Spacer(Modifier.width(AppConfig.uiDividerGap.dp))
    }
}

/**
 * The rule's length in dp. Shorter than the 28 dp glyphs beside it and well under the 40 dp buttons, so
 * it reads as a divider inside the control row and never sets the row's height (see the KDoc above).
 */
private const val RULE_LENGTH_DP = 24
