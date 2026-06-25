package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.map.MarkersViewModel

/**
 * Position step — instruction text. Map stays interactive behind.
 * The actual position tracking is done by the LaunchedEffect in MapScreen.
 */
@Composable
internal fun PositionStep(viewModel: MarkersViewModel, isCorridorP1: Boolean) {
    val form by viewModel.createForm.collectAsState()
    val typeLabel = when (form.type) {
        ykws.android.maro.ui.map.MarkerType.PIN -> "the pin"
        ykws.android.maro.ui.map.MarkerType.CIRCLE -> "the zone center"
        ykws.android.maro.ui.map.MarkerType.CORRIDOR -> if (isCorridorP1) "the corridor start" else "the corridor end"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Move the map to set $typeLabel",
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap Next when ready.",
            color = ComposeColor(AppConfig.uiSettingsTextMuted),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        // Show current position coordinates for reference
        form.position?.let { pos ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "%.4f, %.4f".format(pos.latitude, pos.longitude),
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
