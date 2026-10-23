package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import ykws.android.maro.R
import ykws.android.maro.data.route.PaceSource

/** The word that names where the pace comes from, shared by the search panel's header and the Speed History section. */
@StringRes
internal fun PaceSource.labelRes(): Int = when (this) {
    PaceSource.LIVE -> R.string.route_pace_source_live
    PaceSource.TODAY -> R.string.route_pace_source_today
    PaceSource.HISTORY -> R.string.route_pace_source_history
    PaceSource.DEFAULT -> R.string.route_pace_source_default
}
