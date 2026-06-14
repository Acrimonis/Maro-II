package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
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
    val zoneCompliant = Color(0xFF1B5E20)  // dark green — inside zone, speed-compliant
    val speedSafe = Color(0xFF2E7D32)     // green — compliant (<5 kn) inside the 300 m zone
    val speedCaution = Color(0xFFEF6C00)  // orange — 5–10 kn inside the zone
    val speedDanger = Color(0xFFC62828)   // red — >10 kn inside the zone
    val validationOk = Color(0xFF66BB6A)
    val validationWarn = Color(0xFFFFA726)
    /** Amber for zone-entry distance tile — zone boundary ahead. */
    val zoneEntry = Color(0xFFE65100)
    /** Green for zone-exit distance tile — exiting to open sea. */
    val zoneExit = Color(0xFF2E7D32)
    /** Alpha for all subdued/dulled dashboard states — no-data, on-land, far-from-zone, deep-water placeholder. */
    const val dullAlpha = 0.33f
}

// ── Dashboard panel (public, called from MapScreen) ──────────────────────────

/**
 * Dashboard panel: a 2×2 grid of indicator cards — Distance, Speed Zone, Depth, Speed.
 *
 * The top-right card is [SpeedLimitCard], replacing the old [Zone300Card]: it shows
 * the unified speed zone name (300m band or SHOM speed zone), the most restrictive
 * speed limit, heading-aware distance ahead, and speed compliance.
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
    depthSample: DepthSample?,
    speedKnots: Float?,
    zoneSituation: ZoneSituation? = null,
    autoRevealDistanceM: Float = 100f,
    autoRevealTimeS: Float = 10f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(DashboardColors.background)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 2×2 indicator grid ─────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DistanceCard(
                        distanceToShore = distanceToShore,
                        isWater = isWater,
                        state = state,
                        zoneSituation = zoneSituation,
                        autoRevealDistanceM = autoRevealDistanceM,
                        autoRevealTimeS = autoRevealTimeS,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SpeedLimitCard(
                        state = state,
                        isWater = isWater,
                        speedKnots = speedKnots,
                        zoneSituation = zoneSituation,
                        autoRevealDistanceM = autoRevealDistanceM,
                        autoRevealTimeS = autoRevealTimeS,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        activeSpeedLimitKn = zoneSituation?.currentZone?.speedLimitKn,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
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
    titleColor: Color = DashboardColors.textPrimary,
    valueColor: Color = DashboardColors.textPrimary,
    subtitleColor: Color = DashboardColors.textMuted,
    subtitleWeight: FontWeight = FontWeight.Medium,
    isEmpty: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Secondary: small, subdued label.
            Text(
                text = title.uppercase(),
                color = titleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Primary: the value, as large as the cell allows.
            if (isEmpty) {
                // Empty state: small subdued dash instead of the auto-sized value.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value,
                        color = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AutoSizeValue(
                    text = value,
                    color = valueColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
            // Secondary: small, subdued context (empty string keeps cell baselines aligned).
            Text(
                text = subtitle ?: "",
                color = subtitleColor,
                fontSize = 9.sp,
                fontWeight = subtitleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Renders [text] centered at a fixed font size — sized once via [onSizeChanged] to avoid
 * the per-recomposition subcomposite layout pass of [BoxWithConstraints]. Falls back to
 * a sensible default (32sp) before the first measured size is available.
 */
@Composable
private fun AutoSizeValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DashboardColors.textPrimary
) {
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val fontSize = if (contentSize != IntSize.Zero) {
        val heightDp = with(density) { contentSize.height.toDp().value }
        val widthDp = with(density) { contentSize.width.toDp().value }
        val byHeight = heightDp * 0.70f
        val byWidth = widthDp * 1.5f / text.length.coerceAtLeast(1)
        minOf(byHeight, byWidth).coerceIn(14f, 64f)
    } else 32f

    Box(
        modifier = modifier
            .onSizeChanged { if (it != contentSize) contentSize = it },
        contentAlignment = Alignment.Center
    ) {
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

/** Distance in metres rendered as a localised "x.x km" / "x m" string (decimal separator follows locale).
 *  Drops the decimal when ≥ 10 km: shows "18 km" instead of "18.1 km". */
@Composable
private fun distanceText(distanceM: Double): String {
    val km = distanceM / 1000.0
    return if (distanceM >= 1000.0) {
        if (km >= 10.0) stringResource(R.string.dash_value_km_int, km.toInt())
        else stringResource(R.string.dash_value_km, km)
    } else {
        stringResource(R.string.dash_value_m, distanceM)
    }
}

/** Format ETA seconds as a localised string — either "ETA X s" or "ETA X:XX min". */
@Composable
private fun formatEta(etaSeconds: Double?): String? {
    if (etaSeconds == null) return null
    val sec = etaSeconds.toInt()
    return if (sec < 60) stringResource(R.string.dash_eta_sec, sec)
           else stringResource(R.string.dash_eta_min_sec, sec / 60, sec % 60)
}

// ── Distance card ────────────────────────────────────────────────────────────

@Composable
private fun DistanceCard(
    distanceToShore: Double?,
    isWater: Boolean,
    state: CoastlineState,
    zoneSituation: ZoneSituation? = null,
    autoRevealDistanceM: Float = 100f,
    autoRevealTimeS: Float = 10f,
    modifier: Modifier = Modifier
) {
    // ── No data / loading ──────────────────────────────────────────────
    if (state !is CoastlineState.Ready || distanceToShore == null) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = stringResource(R.string.dash_empty),
            isEmpty = true,
            modifier = modifier
        )
        return
    }

    // ── On land ────────────────────────────────────────────────────────
    if (!isWater) {
        val dull = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha)
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_sea),
            cardColor = DashboardColors.zoneNormal,
            titleColor = dull,
            valueColor = dull,
            subtitleColor = DashboardColors.textMuted.copy(alpha = DashboardColors.dullAlpha),
            modifier = modifier
        )
        return
    }

    // ── Dynamic relevance: show the nearest relevant boundary ──────────
    val currentZone = zoneSituation?.currentZone
    val nearestZone = zoneSituation?.zonesAround?.firstOrNull()

    // Helper: inside zone AND within exit-preview thresholds (distance OR time)
    val isNearExit = currentZone != null && (abs(currentZone.distanceM) <= autoRevealDistanceM ||
        (currentZone.etaSeconds != null && currentZone.etaSeconds <= autoRevealTimeS))

    // Helper: zone entry closer than shore AND within threshold
    val isNearEntry = nearestZone != null && nearestZone.distanceM < distanceToShore &&
        nearestZone.distanceM > 0.0 &&
        (nearestZone.distanceM <= autoRevealDistanceM ||
            (nearestZone.etaSeconds != null && nearestZone.etaSeconds <= autoRevealTimeS))

    // Next zone ahead (beyond current zone's boundary) — from zonesAround with ↑ arrow
    val nextZoneAhead = zoneSituation?.zonesAround?.firstOrNull { it.directionArrow == "\u2191" }

    val (displayDist, displayLabel) = when {
        // Priority 1: Inside zone AND within reveal thresholds → show exit distance + beyond type
        // NOTE: nextZoneAhead is intentionally ignored here — when inside a zone, the distance tile
        // shows where you're heading beyond the CURRENT zone's boundary (beyondType), not the next
        // unrelated zone ahead. nextZoneAhead is for the zone tile (SpeedLimitCard) which shows
        // regulation previews for zones on the heading.
        isNearExit -> {
            val label = when (currentZone!!.beyondType) {
                BeyondType.OPEN_SEA -> "open water"
                BeyondType.ZONE -> "\u2192 ${currentZone.beyondName ?: currentZone.zoneName}"
                BeyondType.LAND -> "land"
            }
            -abs(currentZone.distanceM) to label
        }
        // Priority 2: Zone entry closer than shore → show entry distance (negative)
        isNearEntry ->
            -nearestZone!!.distanceM to "\u2192 ${nearestZone.zoneName}"
        // Priority 3: Inside zone, beyond reveal thresholds → show shore (default)
        currentZone != null ->
            distanceToShore to stringResource(R.string.dash_distance_from_shore)
        // Priority 4: Default → show shore
        else ->
            distanceToShore to stringResource(R.string.dash_distance_from_shore)
    }

    val displayText = distanceText(displayDist)
    val etaSeconds = when {
        isNearExit -> currentZone!!.etaSeconds
        isNearEntry -> nearestZone!!.etaSeconds
        else -> null
    }
    val labelWithEta = if (etaSeconds != null) {
        val etaStr = formatEta(etaSeconds)
        if (etaStr != null) "$displayLabel - $etaStr" else displayLabel
    } else displayLabel

    // Card color by boundary type
    val cardColor = when {
        // Inside zone within reveal thresholds → beyond type determines color
        isNearExit -> when (currentZone!!.beyondType) {
            BeyondType.ZONE -> DashboardColors.zoneEntry  // 🟠 amber — next zone ahead
            BeyondType.LAND -> DashboardColors.cardBg     // 🔵 blue — land boundary, informational
            BeyondType.OPEN_SEA -> DashboardColors.zoneExit // 🟢 green — clear exit
        }
        // Approaching a zone — amber only if directly ahead (↑)
        isNearEntry && nearestZone!!.directionArrow == "\u2191" ->
            DashboardColors.zoneEntry
        // Default → blue
        else -> DashboardColors.cardBg
    }

    DashboardCard(
        title = stringResource(R.string.dash_distance_title),
        value = displayText,
        subtitle = labelWithEta,
        cardColor = cardColor,
        modifier = modifier
    )
}

// ── Speed Limit card (unified, replaces Zone300Card) ─────────────────────────

/**
 * Unified speed limit card driven by [ZoneSituation].
 *
 * Single-branch render:
 * 1. Loading / no data → dull dash
 * 2. On land → "TERRE" / dull
 * 3. Inside zone (currentZone != null) → zone name + speed limit + exit distance + compliance color.
 *    Shows "→ Next zone" in subtitle when zones are ahead on heading.
 * 4. Zones ahead (zonesAround not empty) → zone name + direction arrow + distance + ETA
 * 5. No zones at all → "OPEN WATER"
 */
@Composable
private fun SpeedLimitCard(
    state: CoastlineState,
    isWater: Boolean,
    speedKnots: Float?,
    zoneSituation: ZoneSituation? = null,
    autoRevealDistanceM: Float = 100f,
    autoRevealTimeS: Float = 10f,
    modifier: Modifier = Modifier
) {
    // ── No data / loading ──────────────────────────────────────────────
    if (state !is CoastlineState.Ready) {
        DashboardCard(
            title = stringResource(R.string.dash_zone_title),
            value = stringResource(R.string.dash_empty),
            isEmpty = true,
            modifier = modifier
        )
        return
    }

    // ── On land ────────────────────────────────────────────────────────
    if (!isWater) {
        val dull = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha)
        DashboardCard(
            title = stringResource(R.string.dash_zone_title),
            value = stringResource(R.string.dash_not_at_sea),
            subtitle = stringResource(R.string.dash_out_of_zone),
            cardColor = DashboardColors.zoneNormal,
            titleColor = dull,
            valueColor = dull,
            subtitleColor = DashboardColors.textMuted.copy(alpha = DashboardColors.dullAlpha),
            modifier = modifier
        )
        return
    }

    // ── No zone data → libre ──────────────────────────────────────────
    if (zoneSituation == null) {
        DashboardCard(
            title = stringResource(R.string.dash_libre_title),
            value = stringResource(R.string.dash_libre_value),
            subtitle = stringResource(R.string.dash_no_limit),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // ── Inside a zone (currentZone != null) ────────────────────────────
    val current = zoneSituation.currentZone
    if (current != null) {
        val exitDist = abs(current.distanceM)
        val limitKn = current.speedLimitKn
        val compliant = speedKnots == null || speedKnots < limitKn.toFloat()

        val isNearExit = exitDist <= autoRevealDistanceM ||
            (current.etaSeconds != null && current.etaSeconds <= autoRevealTimeS)

        val subtitle = if (isNearExit) {
            when (current.beyondType) {
                BeyondType.OPEN_SEA -> "open water"
                BeyondType.ZONE -> "\u2192 ${current.beyondName ?: current.zoneName}"
                BeyondType.LAND -> ""  // land exit → no subtitle
            }
        } else {
            ""  // Far from exit → no subtitle
        }

        val limitF = limitKn.toFloat()
        val cardColor = if (speedKnots == null) DashboardColors.zoneCompliant
                        else if (speedKnots <= limitF) DashboardColors.speedSafe
                        else if (speedKnots <= limitF * 1.4f) DashboardColors.speedCaution
                        else DashboardColors.speedDanger

        DashboardCard(
            title = current.zoneName,
            value = stringResource(R.string.dash_value_speed_limit, limitKn),
            subtitle = subtitle.ifEmpty { null },
            cardColor = cardColor,
            modifier = modifier
        )
        return
    }

    // ── Zones ahead on heading (approaching) ──────────────────────────
    val ahead = zoneSituation.zonesAround.firstOrNull()
    if (ahead != null) {
        val zoneDist = abs(ahead.distanceM)
        val zoneText = distanceText(zoneDist)
        val etaStr = formatEta(ahead.etaSeconds)

        // Gate: only reveal when within distance OR time threshold
        val shouldReveal = zoneDist <= autoRevealDistanceM ||
            (ahead.etaSeconds != null && ahead.etaSeconds <= autoRevealTimeS)

        if (shouldReveal) {
            val subtitle = if (etaStr != null) "${ahead.zoneName} - $etaStr"
                           else ahead.zoneName
            // 3-tier speed compliance ramp vs ahead zone's limit
            val limitF = ahead.speedLimitKn.toFloat()
            val speedColor = if (speedKnots == null) DashboardColors.cardBg
                             else if (speedKnots <= limitF) DashboardColors.speedSafe
                             else if (speedKnots <= limitF * 1.4f) DashboardColors.speedCaution
                             else DashboardColors.speedDanger
            DashboardCard(
                title = ahead.zoneName,
                value = stringResource(R.string.dash_value_speed_limit, ahead.speedLimitKn),
                subtitle = subtitle,
                cardColor = speedColor,
                modifier = modifier
            )
            return
        }
        // Fall through to LIBRE if beyond reveal thresholds
    }


    // ── No zone anywhere near → LIBRE ─────────────────────────────────
    DashboardCard(
        title = stringResource(R.string.dash_libre_title),
        value = stringResource(R.string.dash_libre_value),
        subtitle = stringResource(R.string.dash_no_limit),
        cardColor = DashboardColors.cardBg,
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
        val dull = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha)
        DashboardCard(
            title = stringResource(R.string.dash_depth_title),
            value = stringResource(R.string.dash_not_at_sea),
            subtitle = stringResource(R.string.dash_out_of_zone),
            cardColor = DashboardColors.zoneNormal,
            titleColor = dull,
            valueColor = dull,
            subtitleColor = DashboardColors.textMuted.copy(alpha = DashboardColors.dullAlpha),
            modifier = modifier
        )
        return
    }

    if (depthSample == null || !depthSample.hasData) {
        val dull = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha)
        DashboardCard(
            title = stringResource(R.string.dash_depth_title),
            value = stringResource(R.string.dash_empty),
            cardColor = DashboardColors.zoneNormal,
            titleColor = dull,
            valueColor = dull,
            modifier = modifier
        )
        return
    }

    val depthM = depthSample.depthM

    // Deep water gate: ≥ 100 m → show localized "Deep!" / "Fond!" in subdued color instead of numeric value.
    if (depthM >= 100f) {
        val dull = DashboardColors.textPrimary.copy(alpha = DashboardColors.dullAlpha)
        DashboardCard(
            title = stringResource(R.string.dash_depth_title),
            value = stringResource(R.string.dash_depth_deep),
            cardColor = DashboardColors.zoneNormal,
            titleColor = dull,
            valueColor = dull,
            modifier = modifier
        )
        return
    }

    val depthColor = depthRampColor(depthM)
    val sourceLabel = depthSourceLabel(depthSample.source)
    val confidencePct = depthSample.confidence.toInt()
    // Confidence colour: red (low) -> amber -> green (high), via HSV hue 0..120 deg.
    val confColor = Color.hsv(120f * (confidencePct / 100f).coerceIn(0f, 1f), 0.90f, 0.72f)

    DashboardCard(
        title = stringResource(R.string.dash_depth_title),
        value = stringResource(R.string.dash_value_depth_m, depthM),
        subtitle = stringResource(R.string.dash_depth_source_conf, sourceLabel, confidencePct),
        subtitleColor = confColor,
        subtitleWeight = FontWeight.Bold,
        cardColor = depthColor.copy(alpha = 0.25f),
        modifier = modifier
    )
}

// ── Speed card (GPS) ──────────────────────────────────────────────────────────

/**
 * Speed card showing current speed over ground.
 *
 * Colour-codes compliance relative to the active speed zone limit (if any):
 * - No zone: normal blue background
 * - Inside zone, speed ≤ limit: dark green
 * - Inside zone, limit < speed ≤ limit × 1.4: orange
 * - Inside zone, speed > limit × 1.4: red
 */
@Composable
private fun SpeedCard(
    speedKnots: Float?,
    activeSpeedLimitKn: Double? = null,
    modifier: Modifier = Modifier
) {
    // Null = demo mode (or no fix yet) → show a dash, default background.
    if (speedKnots == null) {
        DashboardCard(
            title = stringResource(R.string.dash_speed_title),
            value = stringResource(R.string.dash_empty),
            subtitle = stringResource(R.string.dash_demo_mode),
            isEmpty = true,
            modifier = modifier
        )
        return
    }
    // > 99.9 kn = unrealistic for recreational vessels → also show dash (no subtitle).
    if (speedKnots > 99.9f) {
        DashboardCard(
            title = stringResource(R.string.dash_speed_title),
            value = stringResource(R.string.dash_empty),
            isEmpty = true,
            modifier = modifier
        )
        return
    }
    // Colour-code relative to the active speed zone limit (if any).
    val cardColor = if (activeSpeedLimitKn != null) {
        val limit = activeSpeedLimitKn.toFloat()
        when {
            speedKnots <= limit -> DashboardColors.speedSafe
            speedKnots <= limit * 1.4f -> DashboardColors.speedCaution
            else -> DashboardColors.speedDanger
        }
    } else {
        DashboardColors.cardBg
    }
    val subtitle = if (activeSpeedLimitKn != null)
        stringResource(R.string.dash_speed_zone_limit, activeSpeedLimitKn)
    else null
    DashboardCard(
        title = stringResource(R.string.dash_speed_title),
        value = stringResource(R.string.dash_value_kn, speedKnots),
        subtitle = subtitle,
        cardColor = cardColor,
        modifier = modifier
    )
}

// ── Utility functions ────────────────────────────────────────────────────────

/**
 * Depth colour matching the map's hypsometric colour ramp ([DepthColorRamp]).
 *
 * Delegates to [DepthColorRamp.argb] so the dashboard tile colour is always
 * consistent with the depth colour overlay on the map. Extracts RGB (ignoring
 * the ramp's semi-transparent alpha) and returns a full-opacity [Color] so the
 * card's own alpha [copy(alpha = 0.25f)] is applied uniformly.
 *
 * - 0 m (surface): pale cyan with red-orange collision warning tint
 * - 5 m+: pale cyan → navy gradient
 * - 60 m+: deep navy
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

/** Friendly source label for the depth-at-centre readout. Proper nouns stay verbatim. */
@Composable
fun depthSourceLabel(source: DepthSource): String = when (source) {
    DepthSource.LITTO3D -> "Litto3D"
    DepthSource.SHOM -> "SHOM"
    DepthSource.EMODNET -> "EMODnet"
    DepthSource.SDB -> stringResource(R.string.src_satellite)
    DepthSource.GEBCO -> "GEBCO"
    DepthSource.INTERPOLATED -> stringResource(R.string.src_interpolated)
    DepthSource.NONE -> "—"
}

/** Readout tint by band: collision (≤5 m) red, shallow (≤10 m) amber, profiling cyan. */
fun depthReadoutColor(depthM: Float): Long = when {
    depthM <= DepthConstants.COLLISION_MAX_DEPTH_M.toFloat() -> 0xFFEF5350
    depthM <= DepthConstants.SHALLOW_TIER_MAX_M.toFloat() -> 0xFFFFB74D
    else -> 0xFF4FC3F7
}

