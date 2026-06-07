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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val speedSafe = Color(0xFF2E7D32)     // green — compliant (<5 kn) inside the 300 m zone
    val speedCaution = Color(0xFFEF6C00)  // orange — 5–10 kn inside the zone
    val speedDanger = Color(0xFFC62828)   // red — >10 kn inside the zone
    val validationOk = Color(0xFF66BB6A)
    val validationWarn = Color(0xFFFFA726)
}

// ── Dashboard panel (public, called from MapScreen) ──────────────────────────

/**
 * Dashboard panel: a 2×2 grid of indicator cards — Distance, Zone 300 m, Depth, Speed.
 *
 * Each card shows its value as large as the cell allows; the label and context are small and
 * subdued. The validation badge (when present) sits below the grid.
 *
 * Read-only — no action buttons or toggles. See global rule: action controls live in the
 * map overlay area, never in the dashboard.
 */
@Composable
fun DashboardPanel(
    state: CoastlineState,
    isWater: Boolean,
    distanceToShore: Double?,
    inZone300: Boolean,
    distanceToZone: Double?,
    depthSample: DepthSample?,
    validation: ValidationReport?,
    speedKnots: Float?,
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 2×2 indicator grid ─────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DistanceCard(
                        distanceToShore = distanceToShore,
                        isWater = isWater,
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Zone300Card(
                        inZone300 = inZone300,
                        distanceToZone = distanceToZone,
                        state = state,
                        isWater = isWater,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DepthCard(
                        depthSample = depthSample,
                        isWater = isWater,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SpeedCard(
                        speedKnots = speedKnots,
                        inZone300 = inZone300,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // ── Validation badge ───────────────────────────────────────────
            if (validation != null) {
                ValidationBadge(validation = validation)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ── Reusable dashboard card ──────────────────────────────────────────────────

/**
 * A rounded card: a small, subdued title on top, the value as large as the cell allows in the
 * middle, and small subdued context at the bottom. Designed to fill a 2×2 grid cell.
 */
@Composable
private fun DashboardCard(
    title: String,
    value: String,
    subtitle: String? = null,
    cardColor: Color = DashboardColors.cardBg,
    valueColor: Color = DashboardColors.textPrimary,
    subtitleColor: Color = DashboardColors.textMuted,
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
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Secondary: small, subdued label.
            Text(
                text = title,
                color = DashboardColors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Primary: the value, as large as the cell allows.
            AutoSizeValue(
                text = value,
                color = valueColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            // Secondary: small, subdued context (empty string keeps cell baselines aligned).
            Text(
                text = subtitle ?: "",
                color = subtitleColor,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Renders [text] centered at the largest font that fits the cell — sized from the cell height
 * (line height) and capped by width (≈ bold glyph width × length). No external auto-size API.
 */
@Composable
private fun AutoSizeValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DashboardColors.textPrimary
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val byHeight = maxHeight.value * 0.82f
        val byWidth = maxWidth.value * 1.5f / text.length.coerceAtLeast(1)
        val fontSize = minOf(byHeight, byWidth).coerceIn(14f, 64f)
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center
        )
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
    isWater: Boolean,
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

    if (!isWater) {
        val dimAlpha = 0.38f
        DashboardCard(
            title = "⚠️ Zone 300m",
            value = "Not at sea",
            subtitle = "Hors zone",
            cardColor = DashboardColors.zoneNormal,
            valueColor = DashboardColors.textPrimary.copy(alpha = dimAlpha),
            subtitleColor = DashboardColors.textMuted.copy(alpha = dimAlpha),
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
    isWater: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isWater) {
        val dimAlpha = 0.38f
        DashboardCard(
            title = "🌊 Profondeur",
            value = "Not at sea",
            subtitle = "Hors zone",
            cardColor = DashboardColors.zoneNormal,
            valueColor = DashboardColors.textPrimary.copy(alpha = dimAlpha),
            subtitleColor = DashboardColors.textMuted.copy(alpha = dimAlpha),
            modifier = modifier
        )
        return
    }

    if (depthSample == null || !depthSample.hasData) {
        DashboardCard(
            title = "🌊 Profondeur",
            value = "—",
            modifier = modifier
        )
        return
    }

    val depthM = depthSample.depthM
    val depthColor = depthRampColor(depthM)
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

// ── Speed card (GPS) ──────────────────────────────────────────────────────────

@Composable
private fun SpeedCard(
    speedKnots: Float?,
    inZone300: Boolean,
    modifier: Modifier = Modifier
) {
    // Null = demo mode (or no fix yet) → show a dash, default background.
    if (speedKnots == null) {
        DashboardCard(
            title = "🚤 Vitesse",
            value = "—",
            subtitle = "mode démo",
            modifier = modifier
        )
        return
    }
    // The 300 m zone limit is 5 kn — colour-code compliance there; default background elsewhere.
    val cardColor = if (inZone300) {
        when {
            speedKnots < 5f -> DashboardColors.speedSafe
            speedKnots <= 10f -> DashboardColors.speedCaution
            else -> DashboardColors.speedDanger
        }
    } else {
        DashboardColors.cardBg
    }
    DashboardCard(
        title = "🚤 Vitesse",
        value = "%.1f kn".format(speedKnots),
        subtitle = if (inZone300) "5 kn max en zone" else null,
        cardColor = cardColor,
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
 * Depth colour matching the map's hypsometric colour ramp ([DepthColorRamp]).
 *
 * Delegates to [DepthColorRamp.argb] so the dashboard tile colour is always
 * consistent with the depth colour overlay on the map. Extracts RGB (ignoring
 * the ramp's semi-transparent alpha) and returns a full-opacity [Color] so the
 * card's own alpha [copy(alpha = 0.25f)] is applied uniformly.
 *
 * - 0 m (surface): pale cyan with red-orange collision warning tint
 * - 5 m+: pale cyan → navy gradient
 * - 60 m+: deep navy
 */
private fun depthRampColor(depthM: Float): Color {
    val argb = DepthColorRamp.argb(depthM)
    if (argb == 0) return DashboardColors.cardBg  // NoData → fallback to card background
    return Color(
        red   = ((argb shr 16) and 0xFF) / 255f,
        green = ((argb shr  8) and 0xFF) / 255f,
        blue  = (argb and 0xFF) / 255f,
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
