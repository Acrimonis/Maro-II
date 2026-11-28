package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.temperature.SeaSource
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * The info column's temperature source line: where the number on the tile comes from, so it follows the
 * tile's view. The water view lists each sea source's own value, a source with none left out; the air view,
 * the only one on land, gives the air source's value behind the land icon. Muted while the value is
 * outdated, like the tile's. It draws nothing with no source value to show, and it collects the temperature
 * state itself, so a new reading recomposes this line alone.
 */
@Composable
internal fun TemperatureSourcesLine(viewModel: TemperatureViewModel) {
    val state by viewModel.state.collectAsState()
    val preferred by viewModel.preferredView.collectAsState()

    if (effectiveTemperatureView(state, preferred) == TemperatureView.WATER) {
        val parts = seaFigures(state.waterSources).map { (source, number) -> sourcePart(source.partRes, number) }
        SourceLine(R.string.temperature_water_line_fmt, parts, outdated = state.water?.subdued == true)
    } else {
        val parts = listOfNotNull(state.airSourceC)
            .map { sourcePart(R.string.temperature_air_arome_fmt, sourceNumber(it)) }
        SourceLine(R.string.temperature_air_line_fmt, parts, outdated = state.air.subdued)
    }
}

/** One source's part of a line: its name, [nameRes], around its value. */
@Composable
private fun sourcePart(@StringRes nameRes: Int, number: String): String =
    stringResource(nameRes, stringResource(R.string.temperature_degrees_fmt, number))

/** [parts] joined into the line of [lineRes], which carries the view's icon; nothing for no part. */
@Composable
private fun SourceLine(@StringRes lineRes: Int, parts: List<String>, outdated: Boolean) {
    if (parts.isEmpty()) return
    ZoneInfoLine(
        text = stringResource(lineRes, parts.joinToString(" · ")),
        color = ComposeColor(if (outdated) AppConfig.uiTextMuted else AppConfig.uiMapOverlayTextColor)
    )
}

/** One sea source's own part of the line: its name around its value. */
@get:StringRes
private val SeaSource.partRes: Int
    get() = when (this) {
        SeaSource.MARINE -> R.string.temperature_sea_marine_fmt
        SeaSource.ECMWF -> R.string.temperature_sea_ecmwf_fmt
        SeaSource.COPERNICUS -> R.string.temperature_sea_copernicus_fmt
    }

/**
 * The sources that have a value among [valuesC] (in [SeaSource]'s order), each with its number as
 * [sourceNumber] writes it.
 */
internal fun seaFigures(valuesC: List<Double?>, locale: Locale = Locale.getDefault()): List<Pair<SeaSource, String>> =
    SeaSource.entries.mapNotNull { source ->
        valuesC.getOrNull(source.ordinal)?.let { source to sourceNumber(it, locale) }
    }

/**
 * A source's value as the line writes it: one decimal at most, a whole degree without one ("17", "15.5"), in
 * the decimal mark of [locale].
 */
internal fun sourceNumber(valueC: Double, locale: Locale = Locale.getDefault()): String {
    val number = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale))
    number.roundingMode = RoundingMode.HALF_UP
    return number.format(valueC)
}
