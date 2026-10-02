package ykws.android.maro.ui.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * The screen's chrome state — the eight written surface values folded out of [`MapScreen`](MapScreen.kt)'s
 * body (mapscreen-health, Migration Phase 3). Held in one `rememberSaveable`; each field keeps the storage
 * class it had as a local.
 */
@Stable
internal class MapScreenChrome(
    selectedTab: SettingsTab = SettingsTab.LAYERS,
) {
    var showSettings by mutableStateOf(false)
    var showTrackDrawer by mutableStateOf(false)
    var showTrackHistory by mutableStateOf(false)
    var showMarkerManagement by mutableStateOf(false)
    var navigateToTarget by mutableStateOf<NavigateTarget?>(null)

    var selectedTab by mutableStateOf(selectedTab)

    /** Resume confirmation: non-null while the dialog awaits the Resume/Cancel choice. */
    var pendingResume by mutableStateOf<PendingTrackResume?>(null)

    /** Transient track-operation status banner (export/import in progress): null = hidden. */
    var trackOpStatus by mutableStateOf<String?>(null)

    companion object {
        /**
         * Serialises [selectedTab] alone: it is the one `rememberSaveable` member, and the other seven
         * are plain `remember` — saving them would make them survive process death, which they did not
         * before this fold.
         *
         * The tab is stored by **name**, never by index, so reordering the tabs cannot re-interpret a
         * restored selection; an unknown name falls back to the first tab.
         */
        val Saver: Saver<MapScreenChrome, String> = Saver(
            save = { it.selectedTab.name },
            restore = { name ->
                MapScreenChrome(
                    selectedTab = SettingsTab.entries.firstOrNull { tab -> tab.name == name }
                        ?: SettingsTab.LAYERS
                )
            },
        )
    }
}
