package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.map.MarkerType
import ykws.android.maro.ui.map.MarkersViewModel

/**
 * Position step — instruction text, on one card of the settings family. The map stays interactive
 * behind, and the position tracking itself is done by the `LaunchedEffect` in `MapScreen`.
 *
 * The step measures its card rather than filling the body, so the drawer's wrap-content frame shows.
 */
@Composable
internal fun PositionStep(viewModel: MarkersViewModel, isCorridorP1: Boolean) {
    val form by viewModel.createForm.collectAsState()
    val typeLabel = stringResource(
        when (form.type) {
            MarkerType.PIN -> R.string.marker_target_pin
            MarkerType.CIRCLE -> R.string.marker_target_zone_center
            MarkerType.CORRIDOR ->
                if (isCorridorP1) R.string.marker_target_corridor_start else R.string.marker_target_corridor_end
        }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        CardArea {
            Text(
                text = stringResource(R.string.wizard_position_instruction_fmt, typeLabel),
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.wizard_position_ready),
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )

            // Current position coordinates for reference
            form.position?.let { pos ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "%.4f, %.4f".format(pos.latitude, pos.longitude),
                    color = ComposeColor(AppConfig.uiTextMuted),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
