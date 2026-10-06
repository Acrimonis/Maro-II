package ykws.android.maro.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

/**
 * The list card's **selection check**: a 24 dp `uiAccent` disc bearing the white 16 dp `Check` — the
 * face the scaffold's old corner check carried before the selection work.
 *
 * It **leads the card's first line**, ahead of the date/time on the track card and the coordinates on
 * the marker card, and is drawn **only while the card is selected**, so an unselected card reserves no
 * slot: the header's text keeps its place until selection arrives and then shifts right by this disc
 * plus the caller's 6 dp gap.
 *
 * **One home for the disc.** This is the only drawing of this face; the former `ListTypeGlyph`, whose
 * selected branch held it, is gone with the type glyph itself.
 */
@Composable
fun ListSelectionCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(AppConfig.uiAccent)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.cd_selected),
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
