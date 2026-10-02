<!-- scope: feature -->
# Settings tabs — Navigation/Position swap, Position renamed Routing, Route block moved

**Status:** in design · nothing implemented
**Branch:** `feature/more-settings` (from `origin/develop` `9b21ea1`)

## Request

- Swap the position of two Settings tabs: **Navigation** and **Position**.
- Rename **Position** to **Routing** (FR **Routage** — the Route block itself stays **Route**).
- Move the block *navigation/Route* into the **Routing** tab.
- Code health, added by the user: the tab's position is handled **one way and one way only**.
- Code health, added by the user: locale keys are renamed so each matches what it really represents — **including the keys that name a group**.

## The reading taken for the third item

The **Route** section that leads the **Navigation** tab — header `route_trip_title` ("Route" / "Route"), the free-water pace slider and the slow-water budget slider — moves into the Routing tab.

The alternative — the **Route algorithm** section in the **System** tab — is a different block and is *not* in scope (**D1**).

## Current state

- Tab labels: [`settingsTabLabels`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4405) — `Layers · Navigation · Position · System`.
- The strip draws one cell per label ([`MapScreenSettingsOverlay.kt:142`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:142)); the pager's `pageCount` derives from the same list ([`:108`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:108)).
- `PositionSettings` has an empty body ([`:1498`–`:1512`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1498)); the Route block runs [`1122`–`1162`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1122) inside `NavigationSettings`, whose remaining blocks are Stop detection ([`:1164`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1164)) and Orientation aids ([`:1220`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1220)).
- Four scroll states are created in [`MapScreen.kt:842`–`845`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:842) and handed over at [`MapScreen.kt:3152`–`3158`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3152).
- The drawer shares three header keys with the overlay — `settings_section_position` ([`MenuDrawerOverlay.kt:130`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:130)), `settings_section_tracks` ([`:168`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:168)) and `settings_section_markers` ([`:264`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:264)).
- One test guards locale keys: [`TrackRenderStringsTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt:88) pins a list that must exist in both locales, and asserts a `retired` list is absent ([`:61`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt:61)).

### Where the tab's position is known today — six places, all by index

1. The order of the label list ([`MapScreen.kt:4405`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4405)).
2. The numeric arms of `when (page)` ([`MapScreenSettingsOverlay.kt:180`–`:184`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:180)).
3. Which scroll state each arm passes on.
4. The four positional fields of `SettingsOverlayData` ([`OverlayLayerParams.kt:70`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:70)) built in [`buildSettingsOverlayData`](../../app/src/main/java/ykws/android/maro/ui/map/MapOverlayData.kt:44) and passed at [`MapScreen.kt:3152`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3152).
5. `MapScreenChrome.selectedTab: Int = 0` ([`MapScreenChrome.kt:17`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt:17)), whose Saver serialises the raw index ([`:39`–`:42`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenChrome.kt:39)).
6. `selectedTab: Int` threaded through [`SettingsOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:98) and [`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:832).

### Group-header keys — the same class of mismatch

Every one of these is read by a `SectionHeader` **and** by a row, or by neither; verified by name-search over `app/src`.

| Key | Value | Readers | Reality |
|---|---|---|---|
| `settings_coastline_label` | Coastline | `SectionHeader` ([`:925`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:925)) **and** the toggle row ([`:929`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:929)) | one key, a header and a row |
| `settings_land_water_icon_label` | Show Land/Water Icon | `SectionHeader` ([`:994`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:994)) **and** the toggle row ([`:998`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:998)) | same |
| `settings_regulated_zones_label` | Regulated zones | header only ([`:748`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:748)) | a header named `_label` |
| `settings_zone300_label` | 300 m band | header only ([`:853`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:853)) | same |
| `settings_danger_zones_label` | Danger zones | header only ([`:1008`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1008)) | same |
| `settings_depth_label` | Depth | header only ([`:1084`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1084)) | same |
| `settings_idle_section_label` | Stop detection | header only ([`:1165`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1165)) | says *idle*; the whole block says *stop* |
| `settings_redisplay_label` | Auto-show Speed Zones | header only ([`:1375`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1375)) | a header named `_label` |
| `settings_map_offset_label` | Automatic map offset | header only ([`:1458`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1458)) | same |
| `settings_regenerate_layers` | Regenerate Layers | header only ([`:1727`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1727)) | no role suffix at all |
| `settings_emodnet_section_label` | Shallow cutoff (EMODnet) | an **Expander** inside Depth ([`:1088`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1088)) | says *section*, is a row |
| `settings_section_position` | Navigation | drawer section ([`MenuDrawerOverlay.kt:130`](../../app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:130)) **and** the System block ([`:1566`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1566)) | name says position, value says Navigation, two unlike surfaces |
| `settings_section_layers` | Layers | **none** | dead |
| `settings_section_navigation` | Navigation | **none** | dead |
| `settings_section_advanced` | Advanced | **none** | dead |
| `settings_emodnet_section_desc` | (a description) | **none** | dead |
| `settings_idle_section_desc` | (a description) | **none** | dead |
| `settings_tab_position` | Position | the tab **and** the filter axis ([`ListFilter.kt:174`](../../app/src/main/java/ykws/android/maro/data/model/ListFilter.kt:174)) | two surfaces, one key |
| `displayScrollState` | — | the Layers tab's scroll state | names a tab that no longer exists under that name |

`settings_section_tracks` and `settings_section_markers` are the honest exemplars — a topic, `settings_section_*`, shared by the drawer and the overlay because both surfaces really carry one group of that name.

## Target

`Layers · Routing · Navigation · System`, with the order living in one declaration, and one convention for header keys — `settings_section_<topic>` for the group, `settings_<topic>_label` / `_desc` for the row inside it.

## Steps

### S1 — One home for the tab set

Add an ordered enum owning identity and label, and delete the parallel list:

```kotlin
internal enum class SettingsTab(@StringRes val labelRes: Int) {
    LAYERS(R.string.settings_tab_layers),
    ROUTING(R.string.settings_tab_routing),
    NAVIGATION(R.string.settings_tab_navigation),
    SYSTEM(R.string.settings_tab_system),
}
```

The declaration order **is** the strip order, the pager order and the index order.

### S2 — Key every site to the tab, never to the number

- `pageCount = SettingsTab.entries.size`; the strip iterates `SettingsTab.entries` comparing the entry itself, not an index.
- The pager body resolves once — `val tab = SettingsTab.entries[page]` — and switches on `tab`.
- The scroll state resolves through one `when (tab)` inside the overlay, so no argument position carries meaning.
- `selectedTab` and `onTabChange` take `SettingsTab` through [`SettingsOverlay`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:98), [`SettingsOverlayData`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt:70) and [`OverlayLayer`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:832).
- `MapScreenChrome.selectedTab` becomes `SettingsTab` defaulting to `LAYERS`, its Saver storing the **name** and restoring by name with a fallback to the first entry.

### S3 — Move the Route block

Cut lines 1122–1160 out of `NavigationSettings` into the renamed routing composable, drop the block's trailing `Spacer` if nothing follows it there, and drop the `Spacer` before Stop detection so Stop detection opens the Navigation tab with no leading gap.

### S4 — Rename to reality (code)

`PositionSettings` → `RoutingSettings` with its banner comment; `positionScrollState` → `routingScrollState`; `displayScrollState` → `layersScrollState`, at declaration, field and argument sites.

### S5 — Rename to reality (tab and axis keys)

- `settings_tab_position` → **retired**; the tab reads **`settings_tab_routing`** (EN "Routing", FR "Routage").
- The filter's position axis gains **`filter_axis_position`** (EN "Position", FR "Position"), joining the family; the axis's code key stays `"position"`, being persisted filter state.

### S6 — Align the group-header keys

Split where one key serves two surfaces, then move every group header onto the one convention:

| Today | Becomes | Note |
|---|---|---|
| `settings_coastline_label` | header `settings_section_coastline`; the row keeps `settings_coastline_label` | one key, two readers — split, not moved |
| `settings_land_water_icon_label` | header `settings_section_land_water_icon`; the row keeps its key | same |
| `settings_regulated_zones_label` | `settings_section_regulated_zones` | header only |
| `settings_zone300_label` | `settings_section_zone300` | header only |
| `settings_danger_zones_label` | `settings_section_danger_zones` | header only |
| `settings_depth_label` | `settings_section_depth` | header only |
| `settings_idle_section_label` | `settings_section_stop_detection` | the block's own word |
| `settings_redisplay_label` | `settings_section_redisplay` | header only |
| `settings_map_offset_label` | `settings_section_map_offset` | header only |
| `settings_regenerate_layers` | `settings_section_regenerate_layers` | header only |
| `settings_emodnet_section_label` | `settings_depth_cutoff_expander` | an expander row, not a section |
| `settings_section_position` | `settings_section_position_source` (drawer) + `settings_section_gps_tuning` (settings) | values per **D6** |
| `settings_section_layers`, `settings_section_navigation`, `settings_section_advanced`, `settings_emodnet_section_desc`, `settings_idle_section_desc` | **deleted** from both locales | no reader |
| `settings_section_tracks`, `settings_section_markers`, `settings_section_language`, `settings_section_route_algorithm`, `settings_section_orientation`, `settings_section_screen` | unchanged | already the convention |

Both locales move together for every row, and each retired key joins the `retired` list in [`TrackRenderStringsTest.kt`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt:61) so a surviving reader fails instead of relabelling a surface silently; the renamed headers join the file's `expected` list ([`:88`](../../app/src/test/java/ykws/android/maro/ui/map/TrackRenderStringsTest.kt:88)) so neither locale can lose one.

### S7 — Docs

[`docs/ui-component-guidelines.md:129`](../../docs/ui-component-guidelines.md:129) names the recenter row as "(Position)" while it lives in the System tab — corrected to the tab it really sits in, alongside the Routing wording; §2.11's row ([`:408`](../../docs/ui-component-guidelines.md:408)) states the `settingsTabLabels` invariant and moves onto the enum. The feature file's `## Implemented` entry and its `## Docs` pointer are written at the session's bake.

## Decisions

- **D1 — which block moves.** Taken: the Navigation tab's Route section. If the System tab's **Route algorithm** was meant instead, only S3's source changes.
- **D2 — the French pair**, answered: the Routing tab reads **Routage**, the moved block keeps **Route**.
- **D3 — the shared key** is settled by the code-health directive: the tab and the filter axis each get their own key.
- **D4 — the order** is Layers · Routing · Navigation · System.
- **D5 —** the tab still named "Navigation" then holds Stop detection and Orientation aids only; renaming it is outside this request.
- **D6 — two shown words** sit on misnamed keys: the drawer's section is commented POSITION SOURCE yet displays "Navigation", and the settings GPS-tuning block displays "Navigation" too. The key split is mechanical and happens either way; whether either word should change is the user's call, and both stay until answered.

## Risks

- The enum change touches `SettingsOverlayData`, `buildSettingsOverlayData`, `OverlayLayer` and `MapScreenChrome` — wide but mechanical, its only behavioural surface the Saver's session-lived format, so nothing migrates.
- Splitting a two-reader key (coastline, land/water icon, `settings_section_position`) must add the header key rather than repoint the existing one, or the row loses its wording.
- Dropping a spacer wrongly shows a leading gap in the Navigation tab or a trailing one in the Routing tab; the shipped rhythm is one 14 dp boundary between blocks.
- The dead-key list was established by name-search over `app/src`, which cannot see a key read through a computed name; S6 re-checks each before deleting.

## Verification

- `gradlew :app:assembleDebug :app:testDebugUnitTest`, then the scoped `ui.map` + `config` run, with the retired-key guard and the `expected` list as the test-side checks.
- Nothing after the Order. The four tabs opening in order, the Routing tab holding the Route block, and the filter axis still reading "Position" are a device check and stay owed.

## Outcome

Shipped on `feature/more-settings`; `apk-build.bat` SUCCESSFUL and `TrackRenderStringsTest` green, nothing device-validated.

- **S1/S2 as planned, with one forced widening.** The enum ships, but it is **public**, not internal: `SettingsOverlayData` is public (the public `OverlayLayer` exposes it, C12) and now carries the type, so an internal enum could not sit in its field — the alternative was leaving an `Int` in the bundle, which is exactly what the change removes. Only `selectedTab.ordinal` and `SettingsTab.entries[page]` remain at the Compose boundary, both derived from the declaration.
- **S3/S4 as planned**, and the unused `settingsVm` the old empty `PositionSettings` held went with the move.
- **S5 as planned**, both values preserved. **D6 is answered by the repo rather than left open**: `xTrack/Ui_General/261001_FEAT_PLN_Ui_General_position-system-rehome.md:50` records that the moved GPS-tuning group keeps its "Navigation" title, shared with the menu's Navigation card, so keeping both words is the decided state.
- **S6 as planned, with one name driven by the value**: `settings_depth_label` displays "Depth Map", so its header key is `settings_section_depth_map`, not `settings_section_depth`.
- **S7 as planned** in `docs/ui-component-guidelines.md` §2.2 and §2.11, and the feature file's `## Implemented` and `## Docs` were written in the same hop.
- **Guard placement.** The rename/retirement guards went into a new test, `theTabSweepCarriesItsNewKeysAndRetiresTheOldOnesInBothLocales`, rather than into the existing retired-list test, whose name and subject are the render triples and the arrows ids.
- **Open after the review.** One should-fix stands: the overlay's `// ── X tab ──` banners no longer follow the strip order — the Navigation section sits above the Routing one — while the file documents tab order. Editorial only.
- **Verification.** `apk-build.bat` SUCCESSFUL with no new warning naming any touched file, and `gradlew :app:testDebugUnitTest --tests "*TrackRenderStringsTest*"` green. The scoped `ui.map` + `config` suite and the device pass stay owed: neither the Ask nor the Architect hop may execute a build, so the full unit run belongs to the next Code pass.
