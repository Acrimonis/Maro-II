package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.markers.BBox
import java.util.Locale

/** A number in the machine's own spelling: the log's figures never localise. */
internal fun fmt(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

/** A box in the degrees it is written in. */
internal fun boxText(box: BBox): String =
    "(${fmt(box.latSouth, 5)}..${fmt(box.latNorth, 5)},${fmt(box.lonWest, 5)}..${fmt(box.lonEast, 5)})"
