package ykws.android.maro.ui.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

/**
 * Shared base composable for ALL map action buttons (right-edge control stack).
 * Renders a 64 dp circle using [ButtonColors.bg].
 *
 * @param onClick  Tap handler.
 * @param modifier Optional modifier (caller may append .zIndex() for stacking).
 * @param icon     The icon content — typically a Canvas drawing (28 dp) or Material Icon.
 */
@Composable
fun MapControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonColors.bg
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        icon()
    }
}
