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
                        speedKnots = speedKnots,
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
    speedKnots: Float?,
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

    val distM = abs(distanceToZone).toFloat()
    val outM = distanceToZone.toFloat()  // positive = outside, negative = inside
    // In demo mode (no GPS) assume worst case → >10 kn, so the card shows dark red
    // when near the zone. In GPS mode the actual speed is used.
    val speedK = speedKnots ?: 11f

    // Zone card colour = f(distanceToZone, speedKnots).
    // Near-zone speed compliance rules:
    //   dist<200m & speed>5kn  → dark red   (very close + speeding)
    //   dist<300m & speed>10kn → dark red   (near + very fast)
    //   dist<300m & 5<speed<10 → orange     (near + moderate)
    //   dist<300m & speed<5    → green      (near + compliant)
    //   otherwise              → muted gray (far from zone / no speed data)
    val zoneColor = when {
        distM < 200f && speedK > 5f  -> DashboardColors.zoneDanger
        distM < 300f && speedK > 10f -> DashboardColors.zoneDanger
        distM < 300f && speedK > 5f  -> DashboardColors.speedCaution  // orange
        distM < 300f                  -> DashboardColors.speedSafe     // green
        else                          -> DashboardColors.zoneNormal
    }

    val borderWidth: Dp
    val borderColor: Color
    val valueText: String
    val subtitleText: String

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
        val exitText = if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "%.0f m".format(distM)
        valueText = "EN ZONE !"
        subtitleText = "5 nœuds max — $exitText"
        borderWidth = 2.dp
        borderColor = DashboardColors.red.copy(alpha = pulseAlpha)
    } else {
        val zoneText = if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "%.0f m".format(distM)
        valueText = zoneText
        subtitleText = "de la zone 300m"
        borderWidth = 0.dp
        borderColor = Color.Transparent
    }

    val gradColor = ZoneConfig.distanceToZoneGradientColor
    val gradText  = ZoneConfig.distanceToZoneGradientText
    val transpPct = ZoneConfig.distanceToZoneGradientTransp
    val outTint   = (100 - transpPct) / 100f  // e.g. 33 % transp → 0.67 blend

    // ── Tile gradient: only fades when going outward from the zone ──────────
    // Outside zone: zoneColor at (100−transpPct) % so the tile is always
    // subtler outside than inside even right at the boundary.
    val tintedZone = if (outM <= 0f) zoneColor
                     else lerpColor(DashboardColors.cardBg, zoneColor, outTint)

    // Outside zone: blends tintedZone → cardBg over 0–gradColor m.
    val tileBlend = if (outM <= 0f) {
        1f
    } else if (outM >= gradColor) {
        0f
    } else {
        1f - outM / gradColor
    }

    // ── Text gradient: same in both directions, range 0–gradText m ─────────
    val textBlend = if (distM >= gradText) 0f else 1f - distM / gradText

    val finalColor = lerpColor(DashboardColors.cardBg, tintedZone, tileBlend)
    val textAlpha = 0.05f + 0.95f * textBlend

    DashboardCard(
        title = "⚠️ Zone 300m",
        value = valueText,
        subtitle = subtitleText,
        cardColor = finalColor,
        valueColor = DashboardColors.textPrimary.copy(alpha = textAlpha),
        subtitleColor = DashboardColors.textMuted.copy(alpha = textAlpha),
        borderColor = borderColor,
        borderWidth = borderWidth,
        modifier = modifier
    )
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
        cardColor = depthColor,
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
 * Depth card colour derived from [DepthColorRamp]'s hue, with custom saturation
 * and lightness curves tuned for a subtle, readable card background.
 *
 * Extracts the hue angle from the ramp's ARGB output (preserving the ramp's
 * colour direction: warm red-orange in the collision band → blue progression
 * at depth), then applies reduced saturation and moderate darkness so the
 * card stays distinguishable without competing with white text.
 *
 * At full depth (~55 m+) the card reverts to [DashboardColors.cardBg], the
 * same plain dark tile used by other uncoloured dashboard cards.
 *
 * - 0 m (collision band):  warm reddish   (hue ~5°)
 * - 5–30 m:                muted blue     (hue ~207–217°)
 * - 55 m+:                 plain tile     (cardBg)
 */
private fun depthRampColor(depthM: Float): Color {
    val argb = DepthColorRamp.argb(depthM)
    if (argb == 0) return DashboardColors.cardBg  // NoData

    val d = depthM.coerceIn(0f, DepthColorRamp.MAX_DEPTH_M)

    // At full depth, match the default uncoloured card background.
    if (d >= DepthColorRamp.MAX_DEPTH_M - 5f) return DashboardColors.cardBg

    // Extract hue from the ramp's RGB.
    val rampR = ((argb shr 16) and 0xFF) / 255f
    val rampG = ((argb shr  8) and 0xFF) / 255f
    val rampB = (argb and 0xFF) / 255f
    val max = maxOf(rampR, rampG, rampB)
    val min = minOf(rampR, rampG, rampB)

    var hue = 0f
    if (max - min > 1e-6f) {
        hue = when (max) {
            rampR -> 60f * (((rampG - rampB) / (max - min)).let { if (it < 0f) it + 6f else it })
            rampG -> 60f * ((rampB - rampR) / (max - min) + 2f)
            else  -> 60f * ((rampR - rampG) / (max - min) + 4f)
        }
    }
    if (hue < 0f) hue += 360f

    val t = d / DepthColorRamp.MAX_DEPTH_M  // 0 shallow .. 1 deep

    // Reduced saturation: 55% at surface → 40% at depth.
    val saturation = 0.55f - 0.15f * t
    // Moderate darkness: 35% at surface → 25% at depth.
    val lightness = 0.35f - 0.10f * t

    return Color.hsl(hue, saturation, lightness)
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

/** Linear interpolation between two opaque [Color] values. */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue  = a.blue + (b.blue - a.blue) * f,
        alpha = 1f
    )
}
