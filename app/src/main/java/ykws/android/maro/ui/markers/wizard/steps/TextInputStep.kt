package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.CardArea

/**
 * Text input step for Title and Description.
 *
 * The field renders as the track card's and the marker card's inline editors do — a transparent
 * `TextField` that reads as the text it holds, no container and no indicators, the name 15sp SemiBold
 * `uiTextPrimary`, `ImeAction.Done` (`TrackHistoryOverlay.kt:625`, `MarkerManagementOverlay.kt:370`).
 * The wizard keeps the label line the editors do not need, since a step has no card around it to name
 * the field, and a placeholder for the empty case.
 *
 * The step measures its card rather than filling the body, and it sets no soft-input mode of its own:
 * the platform's pan moves the panel, so the field and the buttons stay clear of the keyboard exactly
 * as they do in the track card (P7a, 2026-09-26).
 *
 * The field selects its whole text whenever it takes focus, so arriving on the step or tapping an
 * unfocused field puts the whole value under the caret, while a tap after typing still places the caret.
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
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var wasFocused by remember { mutableStateOf(false) }

    // Sync external value changes (e.g. edit pre-populate) without re-selecting
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value)
        }
    }

    // Focus on entry — the select-all rides on the focus below, so it happens every time
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxWidth()) {
        CardArea {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))

            TextField(
                value = textFieldValue,
                onValueChange = { tfv ->
                    textFieldValue = tfv
                    onValueChange(tfv.text)
                },
                singleLine = singleLine,
                minLines = 1,
                textStyle = TextStyle(
                    color = ComposeColor(AppConfig.uiTextPrimary),
                    fontSize = 15.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ComposeColor.Transparent,
                    unfocusedContainerColor = ComposeColor.Transparent,
                    focusedTextColor = ComposeColor(AppConfig.uiTextPrimary),
                    unfocusedTextColor = ComposeColor(AppConfig.uiTextPrimary),
                    cursorColor = ComposeColor(AppConfig.uiTextPrimary),
                    focusedIndicatorColor = ComposeColor.Transparent,
                    unfocusedIndicatorColor = ComposeColor.Transparent
                ),
                placeholder = {
                    Text(
                        if (singleLine) stringResource(R.string.marker_name_hint)
                        else stringResource(R.string.marker_description_hint),
                        color = ComposeColor(AppConfig.uiTextMuted).copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.clearFocus() },
                    onDone = { focusManager.clearFocus() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        // Select all whenever the field takes focus; a tap while it holds focus
                        // reaches the field itself, so the caret can still be placed.
                        if (state.isFocused && !wasFocused) {
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(0, textFieldValue.text.length)
                            )
                        }
                        wasFocused = state.isFocused
                    }
            )
        }
    }
}
