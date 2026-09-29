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
 * @param enabled  False while this control has **no press at all** — a fan child whose action the
 *                 current phase does not offer. The disabled face is the **caller's own**: Material's
 *                 disabled container and content colours are pinned back to the button's own tokens, so
 *                 the circle keeps its skin and the child's content alone draws what "disabled" looks
 *                 like there. The alternative — a grey child with a live press — would be a defect.
 * @param icon     The icon content — typically a Canvas drawing (28 dp) or Material Icon.
 */
@Composable
fun MapControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonColors.bg,
            disabledContainerColor = ButtonColors.bg,
            disabledContentColor = ButtonColors.icon
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        icon()
    }
}
