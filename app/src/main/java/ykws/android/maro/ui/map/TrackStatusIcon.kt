package ykws.android.maro.ui.map

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Tracking status icon — one of the row's squares, painted by [MapToggleSquare] on the shared
 * [MapSurface].
 *
 * 44×44 dp rounded square with 🐾 paw-prints emoji. `trackingFace` (`MapToggleFace.kt`) resolves the
 * square's two channels from the recorder's state; both the tile and the data mark are painted by
 * [MapToggleSquare] from the resolved face.
 *
 * - **OFF:** the shared pale face, glyph dimmed, no dot.
 * - **ON + moving (recording):** nominal blue fill, **green** dot.
 * - **ON + still (standing by):** green fill, **green** dot — the one place the fill and the dot share a
 *   hue, for two different reasons (standing by, and the data is complete).
 */
@Composable
fun TrackStatusIcon(
    recorderState: TrackRecorderUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = trackingFace(recorderState)

    MapToggleSquare(face = face.toSurfaceFace(), onClick = onClick, modifier = modifier) {
        Text(
            text = "\uD83D\uDC3E", // 🐾 paw prints
            fontSize = TOP_TOGGLE_ICON_SIZE,
            fontWeight = FontWeight.Bold
        )
    }
}
