package ykws.android.maro.ui.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.ValidationReport
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Colour palette ────────────────────────────────────────────────────────────

private object DashboardColors {
    val background = Color(0xFF1A1A2E)
    val cardBg = Color(0xFF16213E)
    val textPrimary = Color(0xFFE0E0E0)
    val textMuted = Color(0xFF90A4AE)
    val green = Color(0xFF4CAF50)
    val yellow = Color(0xFFFFEB3B)
    val red = Color(0xFFF44336)
    val zoneDanger = Color(0xFFB71C1C)
    val zoneNormal = Color(0xFF37474F)
    val validationOk = Color(0xFF66BB6A)
    val validationWarn = Color(0xFFFFA726)
    val btnBlue = Color(0xFF1565C0)
    val btnRed = Color(0xFFC62828)
}

// ── Dashboard panel (public, called from MapScreen) ──────────────────────────

/**
 * Dashboard panel redesigned as visual gauge cards for quick reading of indicators.
 *
 * Landscape: 3 cards stacked vertically, full panel width.
 * Portrait:  3 cards in a horizontal row; collapses to vertical stack below 240dp per card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardPanel(
    state: CoastlineState,
    isWater: Boolean,
    distanceToShore: Double?,
    inZone300: Boolean,
    distanceToZone: Double?,
    depthSample: DepthSample?,
    validation: ValidationReport?,
    onGenerate: () -> Unit,
    onRegenerateBand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(DashboardColors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Indicator cards area ───────────────────────────────────────
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val availableWidth = maxWidth
                // 240dp breakpoint: if each card gets at least 240dp, use row layout
                val wideEnough = availableWidth >= 240.dp * 3

                if (wideEnough) {
                    // Wide layout: 3 cards in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DistanceCard(
                            distanceToShore = distanceToShore,
                            isWater = isWater,
                            state = state,
                            modifier = Modifier.weight(1f)
                        )
                        Zone300Card(
                            inZone300 = inZone300,
                            distanceToZone = distanceToZone,
                            state = state,
                            modifier = Modifier.weight(1f)
                        )
                        DepthCard(
                            depthSample = depthSample,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (availableWidth >= 240.dp) {
                    // Medium layout: 2 per row, third wraps
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DistanceCard(
                            distanceToShore = distanceToShore,
                            isWater = isWater,
                            state = state,
                            modifier = Modifier.weight(1f)
                        )
                        Zone300Card(
                            inZone300 = inZone300,
                            distanceToZone = distanceToZone,
                            state = state,
                            modifier = Modifier.weight(1f)
                        )
                        DepthCard(
                            depthSample = depthSample,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Narrow layout: all cards stacked
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DistanceCard(
                            distanceToShore = distanceToShore,
                            isWater = isWater,
                            state = state,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Zone300Card(
                            inZone300 = inZone300,
                            distanceToZone = distanceToZone,
                            state = state,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DepthCard(
                            depthSample = depthSample,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── Validation badge ───────────────────────────────────────────
            if (validation != null) {
                ValidationBadge(validation = validation)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // ── Action buttons row ─────────────────────────────────────────
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DashboardColors.btnBlue,
                        disabledContainerColor = DashboardColors.btnBlue.copy(alpha = 0.25f),
                        disabledContentColor = DashboardColors.textMuted.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = state !is CoastlineState.Loading
                ) {
                    Text(
                        text = when (state) {
                            is CoastlineState.Loading -> "Côte…"
                            else -> "Côte"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onRegenerateBand,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DashboardColors.btnRed,
                        disabledContainerColor = DashboardColors.btnRed.copy(alpha = 0.25f),
                        disabledContentColor = DashboardColors.textMuted.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = state is CoastlineState.Ready
                ) {
                    Text(
                        text = "Bande",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                EarthWaterIcon(
                    emoji = if (isWater) "\uD83C\uDF0A" else "\uD83C\uDFD4\uFE0F",
                    isActive = true,
                    activeColor = if (isWater) DashboardColors.btnBlue else DashboardColors.green
                )
            }
        }
    }
}

// ── Reusable dashboard card ──────────────────────────────────────────────────

/**
 * A rounded card with a coloured background, a large value, and optional title/subtitle.
 */
@Composable
private fun DashboardCard(
    title: String,
    value: String,
    subtitle: String? = null,
    cardColor: Color = DashboardColors.cardBg,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(cardColor)
            .then(
                if (borderWidth > 0.dp && borderColor != Color.Transparent) {
                    Modifier.border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(10.dp))
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = DashboardColors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = DashboardColors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = DashboardColors.textMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Distance card ────────────────────────────────────────────────────────────

@Composable
private fun DistanceCard(
    distanceToShore: Double?,
    isWater: Boolean,
    state: CoastlineState,
    modifier: Modifier = Modifier
) {
    if (state !is CoastlineState.Ready || distanceToShore == null) {
        DashboardCard(
            title = "📏 Distance",
            value = "—",
            modifier = modifier
        )
        return
    }

    val displayText = formatDistance(distanceToShore)
    val label = if (isWater) "de la côte" else "de la mer"

    DashboardCard(
        title = "📏 Distance",
        value = displayText,
        subtitle = label,
        modifier = modifier
    )
}

// ── Zone 300 card ────────────────────────────────────────────────────────────

@Composable
private fun Zone300Card(
    inZone300: Boolean,
    distanceToZone: Double?,
    state: CoastlineState,
    modifier: Modifier = Modifier
) {
    if (state !is CoastlineState.Ready || distanceToZone == null) {
        DashboardCard(
            title = "⚠️ Zone 300m",
            value = "—",
            modifier = modifier
        )
        return
    }

    if (inZone300) {
        // Pulsing border when inside the danger zone
        val infiniteTransition = rememberInfiniteTransition(label = "zonePulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        val zoneM = abs(distanceToZone)
        val exitText = if (zoneM >= 1000.0) "%.1f km".format(zoneM / 1000.0) else "%.0f m".format(zoneM)

        DashboardCard(
            title = "⚠️ Zone 300m",
            value = "EN ZONE !",
            subtitle = "5 nœuds max — $exitText",
            cardColor = DashboardColors.zoneDanger,
            borderColor = DashboardColors.red.copy(alpha = pulseAlpha),
            borderWidth = 2.dp,
            modifier = modifier
        )
    } else {
        val zoneM = abs(distanceToZone)
        val zoneText = if (zoneM >= 1000.0) "%.1f km".format(zoneM / 1000.0) else "%.0f m".format(zoneM)

        DashboardCard(
            title = "⚠️ Zone 300m",
            value = zoneText,
            subtitle = "de la zone 300m",
            cardColor = DashboardColors.zoneNormal,
            modifier = modifier
        )
    }
}

// ── Depth card ───────────────────────────────────────────────────────────────

@Composable
private fun DepthCard(
    depthSample: DepthSample?,
    modifier: Modifier = Modifier
) {
    if (depthSample == null || !depthSample.hasData) {
        DashboardCard(
            title = "🌊 Profondeur",
            value = "—",
            modifier = modifier
        )
        return
    }

    val depthM = depthSample.depthM
    val depthColor = depthGradientColor(depthM)
    val sourceLabel = depthSourceLabel(depthSample.source)
    val confidencePct = depthSample.confidence.toInt()

    DashboardCard(
        title = "🌊 Profondeur",
        value = "%.1f m".format(depthM),
        subtitle = "$sourceLabel · ${confidencePct}%",
        cardColor = depthColor.copy(alpha = 0.25f),
        modifier = modifier
    )
}

// ── Validation badge ─────────────────────────────────────────────────────────

@Composable
private fun ValidationBadge(
    validation: ValidationReport,
    modifier: Modifier = Modifier
) {
    val (badge, badgeColor) = if (validation.passed) {
        "✓ Données validées (RMSE %.1f m)".format(validation.rmseM) to DashboardColors.validationOk
    } else {
        "⚠ Validation incomplète (RMSE %.1f m)".format(validation.rmseM) to DashboardColors.validationWarn
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge,
            color = badgeColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Earth / Water icon control ───────────────────────────────────────────────

@Composable
fun EarthWaterIcon(
    emoji: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.30f)
                else Color.White.copy(alpha = 0.93f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

// ── Utility functions ────────────────────────────────────────────────────────

/** Format a distance in meters to a human-readable string (m or km). */
private fun formatDistance(distanceM: Double): String {
    return if (distanceM >= 1000.0) {
        "%.1f km".format(distanceM / 1000.0)
    } else {
        "%.0f m".format(distanceM)
    }
}

/**
 * Interpolate depth colour: green (safe, 0m) → yellow (~20m) → red (danger, 60m+).
 * Uses a two-stop gradient: 0–20m green→yellow, 20–60m yellow→red.
 */
private fun depthGradientColor(depthM: Float): Color {
    val t = (depthM / 60f).coerceIn(0f, 1f)
    return if (t <= 1f / 3f) {
        // Green → Yellow (0–20m)
        val tt = t * 3f
        lerpColor(DashboardColors.green, DashboardColors.yellow, tt)
    } else {
        // Yellow → Red (20–60m)
        val tt = (t - 1f / 3f) * 1.5f
        lerpColor(DashboardColors.yellow, DashboardColors.red, tt)
    }
}

/** Linear interpolation between two colours. */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f
    )
}

/** Friendly source label for the depth-at-centre readout. */
fun depthSourceLabel(source: DepthSource): String = when (source) {
    DepthSource.LITTO3D -> "Litto3D"
    DepthSource.SHOM -> "SHOM"
    DepthSource.EMODNET -> "EMODnet"
    DepthSource.SDB -> "Satellite"
    DepthSource.GEBCO -> "GEBCO"
    DepthSource.INTERPOLATED -> "Interpolé"
    DepthSource.NONE -> "—"
}

/** Readout tint by band: collision (≤5 m) red, shallow (≤10 m) amber, profiling cyan. */
fun depthReadoutColor(depthM: Float): Long = when {
    depthM <= DepthConstants.COLLISION_MAX_DEPTH_M.toFloat() -> 0xFFEF5350
    depthM <= DepthConstants.SHALLOW_TIER_MAX_M.toFloat() -> 0xFFFFB74D
    else -> 0xFF4FC3F7
}
