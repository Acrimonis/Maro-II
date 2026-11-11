package ykws.android.maro.ui.map

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.data.temperature.TemperatureSource
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * The air/water temperature tile: one square in the zone-icon column, the same square as the land/water
 * reading, wearing the water colour for the water view and its own green for the air view, at its own
 * `temperature.tile.alpha` so the colour stays strong over the map.
 *
 * A tap switches between air and water when the marker is on water. A long press asks for fresh values and
 * says so: a vibration, a toast (raised by the ViewModel, so the settings control gets it too), and the
 * shown value subdued until the new one arrives.
 * The text is subdued while the value is outdated or is the previous one awaiting its replacement.
 */
@Composable
internal fun TemperatureTile(
    viewModel: TemperatureViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val preferred by viewModel.preferredView.collectAsState()
    val view = effectiveTemperatureView(state, preferred)
    val reading = if (view == TemperatureView.WATER) state.water ?: state.air else state.air

    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // One toast at a time: a new notice replaces the one on screen instead of queueing behind it.
    LaunchedEffect(viewModel) {
        var shown: Toast? = null
        viewModel.notices.collect { notice ->
            shown?.cancel()
            shown = when (notice) {
                TemperatureNotice.Refreshing ->
                    Toast.makeText(context, R.string.temperature_refresh_toast, Toast.LENGTH_SHORT)
                is TemperatureNotice.Problem ->
                    Toast.makeText(context, problemMessage(context, notice.problem), Toast.LENGTH_LONG)
            }.also { it.show() }
        }
    }
    val tint = ComposeColor(temperatureTint(view == TemperatureView.WATER))
    val face = mapSurfaceFaceActive(tint).copy(fill = tint.copy(alpha = AppConfig.temperatureTileAlpha))
    val description = stringResource(
        if (view == TemperatureView.WATER) R.string.temperature_tile_water_cd else R.string.temperature_tile_air_cd
    )

    MapToggleSquare(
        face = face,
        modifier = modifier,
        onClick = { viewModel.toggleView() },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.refreshNow()
        },
        contentDescription = description,
        paddingStart = AppConfig.temperatureTilePaddingStartDp.dp,
        paddingEnd = AppConfig.temperatureTilePaddingEndDp.dp
    ) {
        val text = reading.celsius?.let { stringResource(R.string.temperature_tile_value_fmt, it) }
            ?: stringResource(R.string.temperature_tile_none)
        // The square is a fixed size, so the reading is set in dp-sized text, deaf to the system font scale,
        // and a longer value such as "-10°" shrinks to the room of READING_ROOM_CHARS characters.
        val shrink = if (text.length > READING_ROOM_CHARS) READING_ROOM_CHARS.toFloat() / text.length else 1f
        Text(
            text = text,
            fontSize = with(LocalDensity.current) { (AppConfig.temperatureTileFontSp * shrink).dp.toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            color = ComposeColor(if (reading.subdued) AppConfig.uiTextMuted else AppConfig.uiTextPrimary)
        )
    }
}

/** The toast line for a problem: no connection, or the source, the HTTP status and the API's own reason. */
private fun problemMessage(context: Context, problem: TemperatureProblem): String {
    val source = problem.source ?: return context.getString(R.string.temperature_problem_offline_toast)
    val label = context.getString(
        if (source == TemperatureSource.AIR) R.string.temperature_source_air else R.string.temperature_source_water
    )
    val detail = if (problem.answeredWithoutValue) {
        context.getString(R.string.temperature_problem_empty)
    } else {
        listOfNotNull(problem.code?.let { context.getString(R.string.temperature_problem_http, it) }, problem.reason)
            .joinToString(" – ")
            .ifEmpty { context.getString(R.string.temperature_problem_no_answer) }
    }
    return context.getString(R.string.temperature_problem_toast, label, detail)
}

/** How many characters the reading's square has room for at the configured size: "18°", "-5°". */
private const val READING_ROOM_CHARS = 3
