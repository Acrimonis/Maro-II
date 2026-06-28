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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.icons.Conversion_path
import ykws.android.maro.ui.map.ButtonColors
import ykws.android.maro.ui.map.IconPickerDialog
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) primaryText else mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            color = if (selected) primaryText else mutedText,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pin toggle + icon picker
        var showIconPicker by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg)
                .clickable { showIconPicker = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (form.icon != null) {
                Text(form.icon!!, fontSize = 20.sp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.LocationOff,
                    contentDescription = "Pin this marker",
                    tint = ButtonColors.icon,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (form.icon != null) "Pinned" else "Pin this marker",
                color = mutedText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (showIconPicker) {
            IconPickerDialog(
                currentIcon = form.icon,
                onIconSelected = { icon ->
                    viewModel.updateForm { it.copy(icon = icon) }
                    showIconPicker = false
                },
                onDismiss = { showIconPicker = false }
            )
        }
    }
}
