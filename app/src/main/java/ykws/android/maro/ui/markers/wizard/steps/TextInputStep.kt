package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Text input step for Title and Description.
 *
 * On focus: select-all existing text, keyboard opens.
 * Landscape: content is positioned in the upper portion (no offset needed).
 */
@Composable
internal fun TextInputStep(
    label: String,
    value: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
    isLandscape: Boolean
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var textFieldValue by remember(value) { mutableStateOf(TextFieldValue(value, selection = androidx.compose.ui.text.TextRange(0, value.length))) }

    // Auto-focus on entry + select all
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isLandscape) {
                    // Landscape: position text in upper portion, above keyboard zone
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        if (isLandscape) {
            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                label,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { tfv ->
                    textFieldValue = tfv
                    onValueChange(tfv.text)
                },
                singleLine = singleLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .then(if (singleLine) Modifier.height(56.dp) else Modifier.height(120.dp)),
                colors = drawerTextFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.clearFocus() },
                    onDone = { focusManager.clearFocus() }
                ),
                placeholder = {
                    Text(
                        if (singleLine) "e.g. My marker" else "Optional notes\u2026",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun drawerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ComposeColor(AppConfig.buttonActionBgColor).copy(alpha = 0.5f),
    unfocusedBorderColor = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f),
    cursorColor = ComposeColor(AppConfig.uiSettingsTextPrimary)
)
