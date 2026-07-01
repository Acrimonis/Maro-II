package ykws.android.maro.ui.markers.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// WizardTopBar — Cancel ← + title + dot progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun WizardTopBar(stepIndex: Int, totalSteps: Int, onCancel: () -> Unit) {
    val accent = ComposeColor(AppConfig.uiSettingsAccent)
    val divider = ComposeColor(AppConfig.uiSettingsDivider)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancel",
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = "Create Marker",
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSteps) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (i <= stepIndex) accent else divider)
                )
            }
        }
    }
}
