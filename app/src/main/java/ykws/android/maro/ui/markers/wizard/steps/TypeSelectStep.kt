package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.map.MarkerType
import ykws.android.maro.ui.map.MarkersViewModel

/**
 * Type selection step — segmented selector in the style of the language
 * buttons in settings: three equally-weighted segments with Material Icons,
 * accent background on the active choice.
 */
@Composable
internal fun TypeSelectStep(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()
    val accent = ComposeColor(AppConfig.uiSettingsAccent)
    val divider = ComposeColor(AppConfig.uiSettingsDivider)
    val primaryText = ComposeColor(AppConfig.uiSettingsTextPrimary)
    val mutedText = ComposeColor(AppConfig.uiSettingsTextMuted)
    val cardBg = ComposeColor(AppConfig.uiCardBackground)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val types = listOf(
                Triple(MarkerType.PIN, Icons.Filled.LocationOn, "Pin"),
                Triple(MarkerType.CIRCLE, Icons.Filled.RadioButtonUnchecked, "Zone"),
                Triple(MarkerType.CORRIDOR, Conversion_path, "Corridor")
            )
            types.forEach { (type, icon, label) ->
                val selected = form.type == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accent else divider)
                        .clickable { viewModel.updateForm { it.copy(type = type) } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) primaryText else mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = if (selected) primaryText else mutedText,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
