package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 16 POI icons available for markers — 4×4 grid. */
val ICON_SET = listOf(
    "\u2693",        // ⚓ Anchor
    "\uD83E\uDD3F",  // 🤿 Diver
    "\u26A0\uFE0F",  // ⚠️ Warning
    "\uD83D\uDCCD",  // 📍 Pin
    "\uD83D\uDC1F",  // 🐟 Fish
    "\u26F5",        // ⛵ Sailboat
    "\uD83C\uDFCA",  // 🏊 Swimmer
    "\uD83C\uDFA3",  // 🎣 Fishing
    "\u2B50",        // ⭐ Star
    "\uD83D\uDC80",  // 💀 Danger
    "\uD83C\uDFDD\uFE0F", // 🏝️ Island
    "\uD83D\uDDFA\uFE0F", // 🗺️ Map
    "\uD83D\uDC2C",  // 🐬 Dolphin
    "\uD83D\uDC1A",  // 🐚 Shell
    "\uD83C\uDFD6\uFE0F", // 🏖️ Beach
    "\uD83D\uDD50"   // 🕐 Clock
)

/**
 * Icon picker dialog — 3×4 grid + "None" option.
 * Same pattern as [MarkerColorPickerDialog].
 */
@Composable
fun IconPickerDialog(
    currentIcon: String?,
    onIconSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marker Icon") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 4 rows × 4 columns
                for (row in 0..3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..3) {
                            val idx = row * 4 + col
                            if (idx < ICON_SET.size) {
                                val icon = ICON_SET[idx]
                                val selected = icon == currentIcon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (selected) Modifier.border(
                                                2.dp,
                                                ComposeColor(0xFF1E88E5.toInt()),
                                                RoundedCornerShape(8.dp)
                                            ) else Modifier.background(
                                                ComposeColor(0xFF424242.toInt()),
                                                RoundedCornerShape(8.dp)
                                            )
                                        )
                                        .clickable { onIconSelected(icon) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = icon,
                                        fontSize = 24.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onIconSelected(null) }) {
                Text("None (✕)", color = ComposeColor(0xFFE53935.toInt()))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
