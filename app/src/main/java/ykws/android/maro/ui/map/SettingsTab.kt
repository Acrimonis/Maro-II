package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import ykws.android.maro.R

/**
 * The Settings overlay's tabs, in display order — the one home of a tab's identity, its label and
 * its position.
 *
 * The tab strip, the pager, the scroll-state mapping and the persisted selection are all keyed to
 * this enum, so a tab is never addressed by a bare index and a reorder is a change to this
 * declaration alone (docs/ui-component-guidelines.md 2.11).
 */
enum class SettingsTab(@StringRes val labelRes: Int) {
    LAYERS(R.string.settings_tab_layers),
    ROUTING(R.string.settings_tab_routing),
    NAVIGATION(R.string.settings_tab_navigation),
    SYSTEM(R.string.settings_tab_system),
}
