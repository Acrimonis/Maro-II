package ykws.android.maro.ui.map

import ykws.android.maro.data.model.LatLng

/**
 * The position the dashboard is computed for.
 *
 * GPS mode → the boat. Demo mode, or before the first fix, → the marker. The rule exists because
 * the map centre is pan-writable, so a dragged map would otherwise answer for the dashboard.
 *
 * Design: `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md` §4.7.
 */
internal fun dashboardPositionFor(marker: LatLng, boat: LatLng?, gpsMode: Boolean): LatLng =
    if (gpsMode && boat != null) boat else marker
