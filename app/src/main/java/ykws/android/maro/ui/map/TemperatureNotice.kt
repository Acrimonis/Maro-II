package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.data.temperature.TemperatureRefresh
import ykws.android.maro.ui.notice.Notice
import ykws.android.maro.ui.notice.NoticeKeys
import ykws.android.maro.ui.notice.NoticeText
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** What the temperature tile tells the user, as a pill on the notice host. */
internal sealed interface TemperatureNotice {
    /** A refresh has begun (long press on the tile). */
    data object Refreshing : TemperatureNotice

    /** A refresh has ended: what each source answered. */
    data class Refreshed(val refresh: TemperatureRefresh) : TemperatureNotice

    /** A request went wrong, or a refresh was asked with no connection. */
    data class Problem(val problem: TemperatureProblem) : TemperatureNotice
}

/**
 * The tile's notice as the host takes it, under [NoticeKeys.TEMPERATURE] — one slot, so each line replaces the
 * one still standing: the refresh line is a short confirmation, the refresh's answers a result to read, a
 * problem an error that stays long enough to read. The answers' and a problem's lines carry numbers and the
 * API's own reason, so the caller resolves them ([refreshText], [problemText]).
 */
/**
 * A refresh answer's number: one decimal at most, a whole degree without one ("17", "15.5"), in the device's
 * decimal mark; null stays null for the caller's dash.
 */
internal fun refreshDegrees(celsius: Double?, locale: Locale = Locale.getDefault()): String? {
    celsius ?: return null
    val format = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale))
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(celsius)
}

internal fun TemperatureNotice.asNotice(
    problemText: (TemperatureProblem) -> String,
    refreshText: (TemperatureRefresh) -> String
): Notice = when (this) {
    TemperatureNotice.Refreshing ->
        Notice.confirm(NoticeKeys.TEMPERATURE, NoticeText.Res(R.string.temperature_refresh_toast))
    is TemperatureNotice.Refreshed ->
        Notice.result(NoticeKeys.TEMPERATURE, NoticeText.Resolved(refreshText(refresh)))
    is TemperatureNotice.Problem ->
        Notice.error(NoticeKeys.TEMPERATURE, NoticeText.Resolved(problemText(problem)))
}
