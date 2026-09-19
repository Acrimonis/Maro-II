<!-- scope: feature -->
# Hardcoded strings — the pass that moves every user-facing label into both locales

Plan opened 2026-09-19 on the user's standing instruction: never hardcode a user-facing string, always create
an entry in the corresponding locale. The inventory below is what the code actually holds today, and the
mechanics are already in the codebase — the sort fields beside the filter axes read `@StringRes` ids through
`stringResource`, so the filter axes are the same change applied to their own type.

## 1. The criterion

Only text a user can read is in scope. Kept as they are: brand names (`"Maro II"` in the drawer title and the
notification channel name), log tags and messages, `require` messages that never surface, Compose animation
labels (`label = "livePulse"`), and icon vector names (`ImageVector.Builder(name = "AddLocationAlt")`) — none
of those is UI text, and translating them would be noise.

## 2. The inventory

**Filter axes and options — `data/model/ListFilter.kt`**, nineteen literals: the track axes' "Date Range",
the seven date options, "Pinned"/"Unpinned"/"All", the new "Position" with "On water"/"On land"; the marker
axes' "Icon"/"With icon"/"Without icon", "Origin"/"Manual"/"Auto".

**List surfaces — `ui/components/ListOverlayScaffold.kt` and its callers**: `Text(axis.label)` and
`Text(option.label)` (lines 288 and 313), the `customSortLabel: String = "Custom"` default, and the action
labels' `spec.label` path; in `ui/map/TrackHistoryOverlay.kt` the "Merge" batch action, the
`"Track History · N"` title, `"RECORDED TRACKS"` and the `"Tracks"` sort label; in
`ui/map/MarkerManagementOverlay.kt` the `"Markers · N"` title, `"YOUR MARKERS"` and the `"Markers"` sort label.

**Marker wizard — `ui/markers/wizard/`**: `WizardTopBar`'s "Create Marker"; `WizardButtonRow`'s
"Previous"/"Next"/"Finish"; `PositionStep`'s `"Move the map to set $typeLabel"` (a format string) and
"Tap Next when ready.".

**Map chrome and settings — `ui/map/`**: `MarkerDrawer`'s "Previous"/"Next"; `WizardDrawer`'s "Proximity",
"Alert distance around the zone", "Name", "Description"; `MapScreenSettingsOverlay`'s "Active track",
"Past tracks", "Pinned tracks"; `MapScreen`'s "Generating Layers" progress title.

**Notification — `data/track/TrackRecordingService.kt`**: the mode, recording and navigation labels, "On
Water"/"On Land", the composed title, and `CHANNEL_DESC` (the channel *name* stays, being the brand).

**Depth progress — `data/depth/DepthGenerator.kt`**: "Fusion profonde", "Fusion littorale", "Aucune source" —
already French and shown in the loading overlay, so they need English entries as much as French ones.

## 3. The mechanics

- Both spec types gain a resource id instead of a literal: `FilterAxisSpec.labelResId` and
  `FilterOptionSpec.labelResId`, mirroring `CustomSortField.labelResId` in the same package; the two `Text`
  calls in `ListOverlayScaffold` become `stringResource(...)`.
- Every other literal moves to a `@StringRes` id read where it is drawn, and the interpolated ones
  (`"Track History · N"`, `"Move the map to set $typeLabel"`) become format strings.
- The service resolves its notification text through the context it already holds.
- **Existing keys are reused rather than duplicated** — "Previous", "Next", "Name" and the like may already
  exist from the settings and drawer surfaces; a new key is created only where none covers the text.
- Both locales get every key: `res/values/strings.xml` and `res/values-fr/strings.xml`, with the French
  written for the surface rather than transliterated.

## 4. The rule, encoded

`AGENTS.md` §1 gains one bullet: no hardcoded user-facing strings — every label, option, title and
notification line lives in both locale files and is read through a `@StringRes` id, a spec holding an id
rather than a literal.

## 5. Verification

- `apk-build.bat` clean, with the scoped `ui.map` + `config` + `data.*` run showing no new red.
- A sweep of the touched files for remaining quoted literals, showing only the categories §1 keeps.
- Device pass: the filter sheet, both list titles, the wizard and the notification read correctly in both
  locales.

## Outcome

Shipped 2026-09-19 in one Code task, no git writes.

Eighty-four new keys in both locale files, seventeen files touched, and the mechanics exactly as §3 planned:
`FilterOptionSpec.labelResId` and `FilterAxisSpec.labelResId` replaced the two `label: String` fields — the
`CustomSortField` shape — with all nineteen filter literals read through `stringResource` at the two call sites,
and the sort-group default becoming an id. The sweep then reached further than §2's inventory: the wizard step
files' type labels, hints and slider titles, the compass letters (French spells `O`/`SO`/`NO`), the where-am-i
and marker fallbacks, the track and marker placeholders, and the live-card status words — thirty-odd strings the
inventory had not named, all keyed.

Fifteen existing keys were reused rather than duplicated, as §3 required: `settings_marker_halo_pinned_label`
for "Pinned", `action_icon`, `sort_custom_origin`, `settings_tab_position`, `dash_not_at_sea`, the two
`menu_manage_*` captions, `action_merge`, the two icon-row descriptions and three action strings. The
notification text resolves through the context the service already holds, and `CHANNEL_DESC` is gone — the
channel name stays the brand. `DepthGenerator`'s callback carries a `@StringRes` phase id instead of a French
literal.

`apk-build.bat` SUCCESSFUL, and the scoped run came to 380 tests with only the known reds: four
outline/tap-flash, two marker-migration, two heatmap keys that belong to the user's own ramp edit, and
`RegulationAggregatorTest`, which the Global Todos already record as failing at HEAD.

The sweep's own leftovers were then closed inside the same order: `OverlayLayer`'s six "Previous"/"Next"
literals give way to `action_previous` / `action_next` through `stringResource`, and the boat-length label became
`settings_boat_length_label` in both locales. Three `apk-build.bat` runs then failed to delete
`build/intermediates/.../R.jar`, held by another process — an environment lock rather than anything in the change —
and the fourth, once the holder was released, came back BUILD SUCCESSFUL in one minute with the APK written and the
substitution compiled in.

Deviations: none. Open: the device pass over both locales; and one French literal kept by §1's criterion —
`"Aucune source"` in `DepthGenerator.buildLabel` is the depth grid's persisted metadata label, drawn nowhere, so
keying it would mint a resource no surface reads. `DepthViewModel.progressTitle()` returns English with no caller,
being dead code.
