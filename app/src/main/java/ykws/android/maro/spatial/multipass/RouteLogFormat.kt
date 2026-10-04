package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.markers.BBox
import java.util.Locale

/** A number in the machine's own spelling: the log's figures never localise. */
internal fun fmt(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

/** Nanoseconds in a millisecond — the readings' one unit, so no seat spells its own. */
internal const val NANOS_PER_MS = 1_000_000.0

/** Milliseconds spent since [startNs] — the instrument's own reading, taken at the call's two ends. */
internal fun msSince(startNs: Long): Double = (System.nanoTime() - startNs) / NANOS_PER_MS

/** A box in the degrees it is written in. */
internal fun boxText(box: BBox): String =
    "(${fmt(box.latSouth, 5)}..${fmt(box.latNorth, 5)},${fmt(box.lonWest, 5)}..${fmt(box.lonEast, 5)})"
