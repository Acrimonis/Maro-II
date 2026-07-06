
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

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

// ── Colour palette — runtime bridge to colors.properties ──────────────────────

private object DashboardColors {
    val background get() = Color(AppConfig.uiDashboardBackground)
    val cardBg get() = Color(AppConfig.uiDashboardCardBackground)
    val textPrimary get() = Color(AppConfig.uiDashboardTextPrimary)
    val textMuted get() = Color(AppConfig.uiDashboardTextMuted)
    val success get() = Color(AppConfig.uiDashboardStatusSuccess)
    val warning get() = Color(AppConfig.uiDashboardStatusWarning)
    val error get() = Color(AppConfig.uiDashboardStatusError)
    val neutral get() = Color(AppConfig.uiDashboardStatusNeutral)
    val absent get() = Color(AppConfig.uiDashboardStatusAbsent)

    // Semantic aliases — zone and speed status colours point to status tokens
    val zoneDanger get() = Color(AppConfig.uiDashboardStatusError)       // alias to error
    val zoneNormal get() = Color(AppConfig.uiDashboardStatusAbsent)      // alias to absent
    val zoneCompliant get() = Color(AppConfig.uiDashboardStatusSuccess)  // alias to success
    val speedSafe get() = Color(AppConfig.uiDashboardStatusSuccess)      // alias to success
    val speedCaution get() = Color(AppConfig.uiDashboardStatusWarning)   // alias to warning
    val speedDanger get() = Color(AppConfig.uiDashboardStatusError)      // alias to error
    val validationOk get() = Color(AppConfig.uiDashboardStatusSuccess)   // alias to success
    val validationWarn get() = Color(AppConfig.uiDashboardStatusWarning) // alias to warning
    val zoneEntry get() = Color(AppConfig.uiDashboardStatusWarning)      // alias to warning
    val zoneExit get() = Color(AppConfig.uiDashboardStatusSuccess)       // alias to success

    val dullAlpha get() = AppConfig.uiDashboardDullAlpha
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
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
        minOf(byHeight, byWidth).coerceAtLeast(14f)
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

    // ── Find next zone boundary ahead on heading cone ──────────────────
    val currentZone = zoneSituation?.currentZone
    val nearestAhead = zoneSituation?.zonesAround?.firstOrNull()

    // Fix 1: Inside 300m band heading towards shore — exit ray-march fails
    // (heading landward, coastDist never exceeds ZONE_DISTANCE_M).
    // Treat as LAND exclusion before zonesAround can hijack the boundary.
    if (currentZone == null && isWater && distanceToShore != null && distanceToShore <= 300.0) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // Compute exit's effective next-limit (what's beyond the exit boundary)
    val exitNextLimit: Double? = if (currentZone != null) {
        when (currentZone.beyondType) {
            BeyondType.OPEN_SEA -> Double.MAX_VALUE
            BeyondType.ZONE -> {
                zoneSituation?.zonesAround
                    ?.firstOrNull { it.zoneName == currentZone.beyondName }
                    ?.speedLimitKn ?: currentZone.speedLimitKn
            }
            BeyondType.LAND -> null  // excluded below
        }
    } else null

    // Fix 2: Only prefer ahead zone when exit is to open water (no zone beyond).
    // If beyondType is already ZONE or LAND, the exit boundary is meaningful.
    // If beyondType is OPEN_SEA, there might be a zone ahead (e.g. 300m band)
    // that the exit does not account for -- show that instead.
    val boundary: ZoneBoundaryInfo? = when {
        currentZone != null && nearestAhead != null && exitNextLimit != null -> {
            if (currentZone.beyondType == BeyondType.OPEN_SEA) nearestAhead else currentZone
        }
        currentZone != null -> currentZone
        nearestAhead != null -> nearestAhead
        else -> null
    }

    // No zone boundary ahead → coastline
    if (boundary == null) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // ── Gate: within alert thresholds? ─────────────────────────────────
    val withinDistance = boundary.distanceM <= autoRevealDistanceM
    val withinTime = boundary.etaSeconds != null && boundary.etaSeconds <= autoRevealTimeS
    if (!withinDistance && !withinTime) {
        // Beyond thresholds → coastline
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // ── Exclusion: exit with LAND ahead → coastline ────────────────────
    if (boundary == currentZone && currentZone != null && currentZone.beyondType == BeyondType.LAND) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // ── Compare speed limits ───────────────────────────────────────────
    val currentLimit = currentZone?.speedLimitKn ?: Double.MAX_VALUE
    val nextLimit = if (boundary == currentZone) {
        exitNextLimit!!  // exit boundary: next is what's beyond
    } else {
        boundary.speedLimitKn  // entry boundary: next is the zone's limit
    }

    // ── Same limit → coastline (no alert) ──────────────────────────────
    if (nextLimit == currentLimit) {
        DashboardCard(
            title = stringResource(R.string.dash_distance_title),
            value = distanceText(distanceToShore),
            subtitle = stringResource(R.string.dash_distance_from_shore),
            cardColor = DashboardColors.cardBg,
            modifier = modifier
        )
        return
    }

    // ── Render alert ───────────────────────────────────────────────────
    val isMoreRestrictive = nextLimit < currentLimit
    val displayDist = abs(boundary.distanceM)
    val displayText = "\u2191 ${distanceText(displayDist)}"
    val label = if (boundary == currentZone) {
        // Exiting: show what's beyond
        when (currentZone!!.beyondType) {
            BeyondType.OPEN_SEA -> "open water"
            BeyondType.ZONE -> currentZone.beyondName ?: currentZone.zoneName
            BeyondType.LAND -> "land" // unreachable due to exclusion above
        }
    } else {
        // Entering zone ahead
        boundary.zoneName
    }
    val labelWithEta = if (boundary.etaSeconds != null) {
        val etaStr = formatEta(boundary.etaSeconds)
        if (etaStr != null) "$label - $etaStr" else label
    } else label

    val cardColor = if (isMoreRestrictive) DashboardColors.zoneEntry   // amber
                    else DashboardColors.zoneExit                      // green

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
    // Smart speed format: integer when ≥ 10 kn (shorter = bigger font), decimal when < 10
    val speedText = if (speedKnots >= 10f)
        stringResource(R.string.dash_value_kn_int, speedKnots.toInt())
    else
        stringResource(R.string.dash_value_kn, speedKnots)
    DashboardCard(
        title = stringResource(R.string.dash_speed_title),
        value = speedText,
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
    DepthSource.NONE -> stringResource(R.string.dash_empty)
}

/** Readout tint by band: collision (≤5 m) → status error, shallow (≤10 m) → status warning, profiling → status neutral. */
fun depthReadoutColor(depthM: Float): Long = when {
    depthM <= DepthConstants.COLLISION_MAX_DEPTH_M.toFloat() -> AppConfig.uiDashboardStatusError.toLong()
    depthM <= DepthConstants.SHALLOW_TIER_MAX_M.toFloat() -> AppConfig.uiDashboardStatusWarning.toLong()
    else -> AppConfig.uiDashboardStatusNeutral.toLong()
}

