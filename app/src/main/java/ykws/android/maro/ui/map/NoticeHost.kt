package ykws.android.maro.ui.map

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.notice.NoticeArbiter
import ykws.android.maro.ui.notice.NoticeCenter
import ykws.android.maro.ui.notice.NoticeText

/**
 * The notice lane's face: every standing pill of [center], drawn one above the other in the band, the newest
 * on its edge. It holds no rule — [NoticeArbiter] decides what stands and for how long — and only draws, plus
 * the one timer that tells the center when the earliest dwell has run out.
 *
 * It sits **above the drawer ladder**, so a pill reads over an open menu, list or Settings (decided
 * 2026-11-11, reversing the 2026-10-23 rule for pills; the action rows stay under the ladder). It mirrors
 * `MapContent`'s dashboard padding so the pills share the band the map draws in, and it rides above the
 * action rows' measured [rowsHeightPx] so the two lanes read as one column with the rows on the edge.
 */
@Composable
internal fun NoticeHost(
    center: NoticeCenter,
    tagsDrawn: Boolean,
    isLandscape: Boolean,
    /** The live dashboard band, taken as a [State] so its animation re-runs this host alone. */
    dashboardBandHeight: State<Dp>,
    landscapeDashboardWidth: Dp,
    /** The action rows' column height in px, 0 while none stands; read here so only this host recomposes. */
    rowsHeightPx: () -> Int
) {
    // The first host of the process restarts the dwell of anything posted before it composed.
    LaunchedEffect(center) { center.hostAttached() }
    val board by center.board.collectAsState()
    val expiry = NoticeArbiter.nextExpiry(board)
    LaunchedEffect(expiry) {
        if (expiry != null) {
            delay((expiry - center.now()).coerceAtLeast(0L))
            center.expire()
        }
    }
    // A pill whose dwell ran out while no host was composed is not drawn for the frame before the effect
    // takes it down.
    val now = center.now()
    val standing = board.visible.filter { shown ->
        shown.notice.dwellMs?.let { now - shown.shownAtMs < it } ?: true
    }
    if (standing.isEmpty()) return

    val resources = LocalContext.current.resources
    val rowsHeight = with(LocalDensity.current) { rowsHeightPx().toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                if (isLandscape) PaddingValues(start = landscapeDashboardWidth)
                else PaddingValues(bottom = dashboardBandHeight.value)
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = if (rowsHeight > 0.dp) rowsHeight + AppConfig.uiMapToggleGutter.dp else 0.dp)
        ) {
            standing.forEach { shown ->
                NoticePill(
                    message = shown.notice.text.resolve(resources),
                    tagsDrawn = tagsDrawn,
                    danger = shown.notice.danger
                )
            }
        }
    }
}

/** One pill — the banner family's pill face, as the status pills have always worn it. */
@Composable
private fun NoticePill(message: String, tagsDrawn: Boolean, danger: Boolean) {
    MapBanner(
        borderColor = ComposeColor(if (danger) AppConfig.uiDashboardZoneDanger else AppConfig.uiDashboardBackground),
        tagsDrawn = tagsDrawn,
        modifier = Modifier.padding(bottom = AppConfig.noticeStackGapDp.dp),
        reservesControlColumn = true
    ) {
        MapBannerText(text = message)
    }
}

/** The text against [resources]: a plural by its count, a composed line by its resolved parts. */
internal fun NoticeText.resolve(resources: Resources): String = when (this) {
    is NoticeText.Res -> resources.getString(id, *args.toTypedArray())
    is NoticeText.Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
    is NoticeText.Joined -> resources.getString(format, *parts.map { it.resolve(resources) }.toTypedArray())
    is NoticeText.Resolved -> text
}
