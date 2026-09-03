package ykws.android.maro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measures [content]'s natural height at the available width and reports it via
 * [onMeasured], while laying out nothing (zero size, invisible, non-interactive).
 * Used to probe a card's wrapped height so a drawer slot can be given a fixed,
 * animated height.
 */
@Composable
fun MeasureHeight(
    modifier: Modifier = Modifier,
    onMeasured: (Dp) -> Unit,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) {
            onMeasured(0.dp)
            layout(0, 0) {}
        } else {
            val p = measurables[0].measure(
                Constraints(0, constraints.maxWidth, 0, Constraints.Infinity)
            )
            onMeasured(with(this) { p.height.toDp() })
            layout(0, 0) {}
        }
    }
}
