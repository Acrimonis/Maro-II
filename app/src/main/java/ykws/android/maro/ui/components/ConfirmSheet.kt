package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig

/**
 * Canonical confirmation bottom sheet — unified pattern replacing [ConfirmDialog].
 *
 * Uses MD3 [ModalBottomSheet] with app color tokens.
 *
 * @param title         Sheet title.
 * @param message       Body text explaining the action.
 * @param confirmLabel  Label on the confirm button (default: "Delete").
 * @param onConfirm     Called when user taps confirm.
 * @param onDismiss     Called when user dismisses the sheet.
 * @param isDestructive When true, confirm button uses [AppConfig.semanticDanger] red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color(AppConfig.uiSettingsBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color(AppConfig.uiSettingsDivider)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = Color(AppConfig.uiSettingsAccent)
                    )
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) Color(AppConfig.semanticDanger) else Color(AppConfig.uiSettingsAccent)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        confirmLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
