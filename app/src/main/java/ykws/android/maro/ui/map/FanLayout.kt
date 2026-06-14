package ykws.android.maro.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A fan layout that renders a parent button at a fixed position with child buttons
 * fanned out behind it along a circular arc. Children are drawn ON TOP of the parent
 * (Compose draws later-declared children on top by default).
 *
 * Geometry (confirmed as STRONG rules in the feature spec):
 * - θ is the primary parameter (inter-button angle).
 * - Parent at center; children on an arc at radius R.
 * - R derived from θ + buttonSizeDp + edgeGapDp.
 * - All parent→child distances = R (equidistant per relationship).
 * - All adjacent child chords = 2R × sin(θ/2) (equidistant per relationship).
 * - Children centered in the directional arc (offset from base angle).
 *
 * @param config       Fan geometry configuration.
 * @param modifier     Root modifier for the fan's bounding box.
 * @param parent       Composable for the parent button's icon content.
 * @param onParentClick Tap handler for the parent button.
 * @param children     List of composables for each child button's icon content.
 * @param onChildClick  Tap handler for each child button, indexed by position.
 */
@Composable
fun FanLayout(
    config: FanConfig,
    modifier: Modifier = Modifier,
    parent: @Composable () -> Unit,
    onParentClick: () -> Unit,
    children: List<@Composable () -> Unit>,
    onChildClick: ((Int) -> Unit)? = null
) {
    // Compute R in dp (arc radius) from θ + button size + gap.
    // Formula: R = (buttonSizeDp + edgeGapDp) / (2 × sin(θ/2))
    // All dp values, no pixel conversion needed — the ratio is dimensionless.
    val radiusDp: Dp = remember(config.thetaDeg, config.buttonSizeDp, config.edgeGapDp) {
        val halfThetaRad = Math.toRadians((config.thetaDeg / 2f).toDouble())
        val totalDp = config.buttonSizeDp.value + config.edgeGapDp.value
        (totalDp / (2f * sin(halfThetaRad)).toFloat()).dp
    }

    // The box size equals buttonSize so the parent fills it completely.
    // Children are offset from the box center (which is also the parent center).
    Box(modifier = modifier.size(config.buttonSizeDp)) {
        // Parent button — declared first, drawn at lowest z-order.
        // It fills the box entirely so it sits at the expected screen position.
        MapControlButton(onClick = onParentClick, icon = parent)

        // Child buttons — declared after parent, drawn ON TOP.
        // Positioned via offset relative to the box center (parent center).
        if (config.isOpen && config.currentCount > 0) {
            val count = config.currentCount.coerceAtMost(children.size)
            val totalArcSpan = (count - 1) * config.thetaDeg
            val offsetDeg = (180f - totalArcSpan) / 2f // centred in reference semicircle
            val baseAngleRad = Math.toRadians((config.baseAngleDeg + offsetDeg).toDouble())

            children.take(count).forEachIndexed { i, childContent ->
                val angleRad = baseAngleRad + i * Math.toRadians(config.thetaDeg.toDouble())
                // Offset from box center in dp (convert Float × Double to Dp)
                val offsetXDp = (cos(angleRad) * radiusDp.value).toFloat().dp
                val offsetYDp = (sin(angleRad) * radiusDp.value).toFloat().dp

                Box(
                    modifier = Modifier
                        .offset(x = offsetXDp, y = offsetYDp)
                        .size(config.buttonSizeDp)
                ) {
                    MapControlButton(
                        onClick = { onChildClick?.invoke(i) },
                        icon = childContent
                    )
                }
            }
        }
    }
}
