<!-- scope: feature -->
# Ui_Menu — Architectural Decisions

## 1. Feature Scope
- **Decision:** Extract menu drawer concerns into a dedicated `Ui_Menu` feature rather than scattering across BoatTrace/Ui_General/UI_Map.
- **Rationale:** `MenuDrawerOverlay` was originally created by BoatTrace, modified by Ui_General (tweak-drawer, filter-everywhere), and hosted by UI_Map (OverlayLayer). Centralizing ownership prevents multi-feature conflicts and gives the drawer a clear home.
- **Source:** `GLOBAL_CONTEXT.md` routing map, `xTrack/Ui_Menu/FEAT_DSC_Ui_Menu.md`

## 2. Marker Zones Toggle — Placement
- **Decision:** Add "Show Zones on Map" Switch inside the MARKERS card, above "Manage Markers", separated by HorizontalDivider.
- **Rationale:** Matches POSITION SOURCE GPS toggle pattern (label + Switch in same card). Keeps related items grouped. Avoids creating a separate card for a single toggle.
- **Alternatives considered:** Separate card (excessive), section header row (wrong role — headers are labels + filters, not toggles).
- **Source:** [`MenuDrawerOverlay.kt:298-326`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:298)

## 3. State Persistence
- **Decision:** Both menu drawer toggle and Settings page "Zone shapes" toggle read/write the same `AppSettings.markerZonesVisible` field via `SettingsManager`.
- **Rationale:** Single source of truth. No sync needed — both bind to the same SharedPreferences key. Already consumed by `MarkerOverlay` via `DisposableEffect` key.
- **Source:** [`SettingsManager.kt:181`](app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt:181), [`MapScreen.kt:1367-1371`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1367)

## 4. Map Redraw After Toggle
- **Decision:** Call `mapView?.invalidate()` explicitly in `onToggleMarkerZones` lambda after settings update.
- **Rationale:** OSMdroid MapView may not redraw synchronously after `DisposableEffect` restart within overlay context. Explicit invalidate follows existing patterns at lines 1378/1386/1396.
- **Source:** [`MapScreen.kt:1371`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1371)

## 5. Switch Colors
- **Decision:** Use `uiSettingsAccent` (checked), `uiSettingsTextMuted` (unchecked thumb), `uiSettingsSwitchTrackInactive` (unchecked track).
- **Rationale:** Matches the Settings page "Zone shapes" Switch at line 3327. Consistent visual language.
- **Source:** [`MenuDrawerOverlay.kt:314-319`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:314)
