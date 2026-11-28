package ykws.android.maro.ui.map

import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.ui.notice.Notice
import ykws.android.maro.ui.notice.NoticeKeys
import ykws.android.maro.ui.notice.NoticeText

/**
 * What the temperature tile tells the user, as a pill on the notice host: a request that went wrong, or a
 * refresh asked with no connection. A refresh that goes well raises no pill — the tile and the water-source
 * line show its answer. The pill is an error that stays long enough to read, under [NoticeKeys.TEMPERATURE] —
 * one slot, so a new line replaces the one still standing. Its line carries the API's own reason, so the
 * caller resolves it ([text]).
 */
internal fun TemperatureProblem.asNotice(text: (TemperatureProblem) -> String): Notice =
    Notice.error(NoticeKeys.TEMPERATURE, NoticeText.Resolved(text(this)))
