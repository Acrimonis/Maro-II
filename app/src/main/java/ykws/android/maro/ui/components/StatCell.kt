package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * One cell of a readings grid: label (33 %, right-aligned, 11 sp `uiTextMuted`) + value (66 %,
 * left-aligned, 12 sp `uiTextPrimary` Medium), each held to one line.
 *
 * **The app's one rendering of a reading.** The track and route cards' stats grids and the route
 * panel's own row all read this cell, so a figure the app shows twice looks the same twice; the grid
 * the cell sits in — three columns on a card, one row of as many readings as the surface carries on
 * the route panel — belongs to the caller, and `docs/ui-drawer-guidelines.md` §9 is where the card's
 * grid is specified.
 *
 * [modifier] is what lets a caller place it: the cell itself is always `fillMaxWidth` inside the box
 * the caller weights.
 */
@Composable
internal fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            color = Color(AppConfig.uiTextMuted),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(0.33f)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            color = Color(AppConfig.uiTextPrimary),
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(0.66f)
        )
    }
}
