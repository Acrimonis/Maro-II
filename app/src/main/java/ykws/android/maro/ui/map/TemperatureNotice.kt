package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.ui.notice.Notice
import ykws.android.maro.ui.notice.NoticeKeys
import ykws.android.maro.ui.notice.NoticeText

/** What the temperature tile tells the user, as a pill on the notice host. */
internal sealed interface TemperatureNotice {
    /** A refresh has begun (long press on the tile). */
    data object Refreshing : TemperatureNotice

    /** A request went wrong, or a refresh was asked with no connection. */
    data class Problem(val problem: TemperatureProblem) : TemperatureNotice
}

/**
 * The tile's notice as the host takes it, under [NoticeKeys.TEMPERATURE] — one slot, so a problem replaces a
 * refresh line still standing and vice versa: the refresh line is a short confirmation, a problem an error that
 * stays long enough to read. A problem's line carries the API's own reason, so the caller resolves it ([problemText]).
 */
internal fun TemperatureNotice.asNotice(problemText: (TemperatureProblem) -> String): Notice = when (this) {
    TemperatureNotice.Refreshing ->
        Notice.confirm(NoticeKeys.TEMPERATURE, NoticeText.Res(R.string.temperature_refresh_toast))
    is TemperatureNotice.Problem ->
        Notice.error(NoticeKeys.TEMPERATURE, NoticeText.Resolved(problemText(problem)))
}
