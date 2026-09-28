---
name: Ui_General
status: active
created: 2026-06-08 16:43
modified: 2026-09-28 19:45
---

# Feature: Ui_General

**Description:**
App-lifecycle UX for the Maro-II app: back-exit guard, edge-to-edge rendering, WindowInsets management, list normalization, drawer framework, and menu-drawer UX. (Keep-screen-on moved to the Performance feature 2026-09-12.)

## Sections

### track list colors

Track list (TrackHistoryOverlay) color review — ensure track cards/stats/labels/icons use correct `ui.card.background` + `colors.properties` tokens.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

### multi-select

Long-press multiselect mode on list items: scaffold owns selection state + contextual bottom action bar; consumer-injected multi-actions (batch delete/export/pin).

#### Docs
- `xTrack/Ui_General/260712_FEAT_PLN_Ui_General_multiselect-list-plan.md`

### bottom banner

The pills the map shows at the bottom of the band — the exit-press-back banner, the lock toggle's banner and the import/export status banner — and the space they occupy between the bottom-left regulated-zone tag column and the right control column. One control serves every instance and one guideline entry holds its rules; no instance is exempt, the two legacy progress and error cards included.

#### Docs
- `xTrack/Ui_General/260921_FEAT_PLN_Ui_General_bottom-banner-centring.md`

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the exit toast's box, the two banner call sites, the double-back guard
- `app/src/main/java/ykws/android/maro/ui/map/MapControls.kt` — `LockBanner` and `MapStatusBanner` today, one `MapBanner` holding the shared skin after the fix
- `app/src/main/java/ykws/android/maro/ui/map/CoastlineMapView.kt` — `LoadingOverlay` and `ErrorOverlay`, which take that control
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneComponents.kt` — the tag stack, the info text, the pair derivation they share
- `docs/ui-drawer-guidelines.md` §1 — the paint-only right-edge control column rule
- `docs/ui-component-guidelines.md` §5 — the banner family entry, the only home for its rules

### route-dialogs

The route's panel — the dashboard slot's own content — and the one exit dialog, with the shared row and cell they read. The anatomy and the naming are settled and shipped; what is open is the device pass and the diagnostic logs the last fix left in place.

#### Todos
- [ ] Take the device pass over the panel, the exit dialog and the aim ring: the line drawn from the boat with the ring under it, the destination dot surviving the marker pass, and the panel reading title · status · table · rule · pin with its actions at the foot.
- [ ] Remove the three diagnostic log lines from `RouteHost` once that pass has answered.

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the panel's anatomy and the action roles
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the map objects, the ring overlay and the three log lines to remove
- `app/src/main/java/ykws/android/maro/ui/components/OptionRow.kt` · `StatCell.kt` — the shared checkbox row and the shared reading cell

#### Docs
- `xTrack/Ui_General/260923_FEAT_PLN_Ui_General_route-dialog-alignment.md` — the panel, the doctrine and its revisions
- `xTrack/Ui_General/260923_FEAT_PLN_Ui_General_dialog-option-row.md` — the option row and its gap
- `docs/ui-component-guidelines.md` §5.6 · §5.8 — the two homes both rules live in

## Walk
**Level 1 — Date:** 2026-09-28 · **Source:** `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_edit-return-and-advance.md` · **Closed:** 2026-09-28 — all five items resolved by the user's word; nothing parked, nothing dropped
- [x] 1 · A card that cannot find its marker — settled 2026-09-28: it closes instead of showing an empty card, and the card and the editor both resolve in the world the card walks, so a legitimate open is never blind to its own marker
- [x] 2 · Stepping to the next marker — settled 2026-09-28: yes, a step frames its target the way opening it does, zoom included
- [x] 3 · Who decides when an item leaves the list — settled 2026-09-28: whoever has the hand, so the write that changes the item checks, through one shared ordering rule the delete path also uses
- [x] 4 · The card's neighbours after a removal — settled 2026-09-28: the list is rebuilt from the current screen, minus the departed marker, with the cursor on its neighbour
- [x] 5 · Two bits of bookkeeping — settled 2026-09-28: the moved plan is archived with an index row, and the next bake writes the front-matter date and the pointer list

## Implemented

- **map-cards-and-the-filter (2026-09-28)** — the map draws its filter's set and nothing else, and a click on the map stands. Three escapes deleted: the marker reveal-on-select, the highlighted track's eligibility override in `TrackSelectionPolicy` (its dead cap rescue with it, the ranking that puts the highlighted first kept), and the pinned carve-out's repeated OR in `storedTrackSelection` (its `highlightedTrackId` parameter gone) — so no pinned, highlighted or opened item rides past the map filter. R2's map-world close left the map-opened card: `scopeClosed`'s `MAP` arm is `false` while `LIST` and `INSPECT` keep theirs, so a filter write leaves a map tap standing and still closes the spy card. The map-opened card now reads the map's own source of truth (`cardWalkWorld`'s fourth collection, the unfiltered markers), so one item holds through its own write and closes only on a genuine deletion. The dead `DrawerSource.WHERE_AM_I` went at every site — the match panel staying and walking nothing — a plain map tap opens `listOf(sel)` on the `MAP` source, and the menu chevron's first item now reads the menu's own referential (the map filter) rather than the list world, the marker chevron's opener handed that world and source at last. Its Prev/Next now grey both ends on every door of the item's-list kind — the menu chevron's card reading its ends exactly as the panel's does through the pure `cardStepEnds`. `gradlew :app:assembleDebug` SUCCESS; the full unit suite green at 767 tests → `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md`

- **edit-return-and-advance (2026-09-28)** — the editor hands its card back, and an item's departure advances. One pure rule, `advanceAfterDeparture(departedId, world, excluded)` in [`CardWalkPolicy.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CardWalkPolicy.kt) — next, else previous, else none — serves both cards, and the card and the editor resolve in the world the card walks rather than in the list world, so a map-tapped marker renders its own card; the close is issued from the state layer's `reconcileOpenCard`, one call per write, and that is what let the drawer's `marker_not_found` branch and its string in both locale files be deleted — no live card could reach them, and a momentarily null marker now returns early and draws nothing. `startWizard(markerId, door)` gained its required door: a card-entered edit restores `Viewing` on a save and on a cancel, a list-entered one keeps its close, and the management delete advances the deleted item's own card rather than closing every open card. `gradlew :app:assembleDebug` SUCCESS; the scoped `Marker`/`Track`/`Dashboard`/`Inspect` suites green at 249 tests with the new `AdvanceAfterDepartureTest` and `CardWalkDecisionsTest` → `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_edit-return-and-advance.md`

- **dialog-option-row (2026-09-23)** — the app's checkbox-and-label row became one control: `ui/components/OptionRow.kt` carries the `toggleable(Role.Checkbox)` and the merged semantics, tints the box `uiAccent` and gives the label a weight, and **states no gap of its own** — the checkbox's target inset is the gap, so the distance between a box and its label is one fact wherever it is drawn. The three dialog rows (the route exit's session scope, the resume backup, the GPX import's keep-originals) and the route panel's pin all read it; the pin's own `Spacer(4.dp)` went with them. The rule lives in `docs/ui-component-guidelines.md` §5.6, with §5.8's pin row pointing at it → `xTrack/Ui_General/260923_FEAT_PLN_Ui_General_dialog-option-row.md`

- **route-dialog-alignment (2026-09-23)** — the route's two surfaces now read like the recording exit dialog. The doctrine is one bullet in `docs/ui-component-guidelines.md` §5.6: a button's colour states its role — the **accent** is the surface's own outcome, the **red** is the action that withholds the work, the **outline** is everything that neither writes nor loses — and a stacked surface reads affirmative → neutral → destructive, that order scoped to a stack so a requirement's own row order (R17's `Freeze`/`Resume` beside `Save track`) stands. The panel ([`RouteConfirmPanel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt:1)) was rebuilt on the list card's lines and then folded: **one header row** — the phase's title left, the two ends right-aligned at 11 sp `uiTextMuted`, the coordinates never truncated and the title what yields — the 0.5 dp rule drawn only where a plan stands, the panel's state line under it, then `Dist · ETA` on the shared `StatCell`, the crossing line, the pin and the actions; the choosing phase with no plan keeps the title, the sentence and the single `Exit`. Its outcomes were re-roled and renamed — `Route` accent · `Save track and Route` · `Save track and End` · `Exit`, the generic `Cancel` specialized to the mode's own door, `Save track` taking the following panel's accent — and the exit dialog re-ordered to `Save track and End` · `Continue` · `Discard route`, its accent reading the draft's own save key so one act carries one key. `StatCell` left `TrackHistoryOverlay.kt` for `ui/components` as one cell for both readers, the ends print through `routeCoordinate` / `routeDestinationText` pinned by `RouteEndsTextTest`, the panel's anatomy has its own §5.8, and R16 · R17 · R23 with the epic's five repeats moved onto the new words. `apk-build.bat` SUCCESS, the `ui.map` suite green at 28 classes with no failure, and the Ask hop's five should-fixes closed → `xTrack/Ui_General/260923_FEAT_PLN_Ui_General_route-dialog-alignment.md`

- **filter-popup-scroll (2026-09-22)** — both list popups bound their own height and scroll past it, so the track filter's thirteen rows stay reachable in landscape instead of being clipped off the screen edge; one `popupMaxHeightDp` derivation — the window's height less a 96dp reserve, floored for a degenerate window — feeds the filter and the sort popup, pinned by `ListPopupHeightTest`, with the rule written into `docs/ui-component-guidelines.md` §2.10 and the stale filter tables in `docs/ui-lists-guidelines.md` corrected with it → `xTrack/Ui_General/260922_FEAT_PLN_Ui_General_filter-popup-scroll.md`

- **bottom-banner (2026-09-21)** — the map's bottom band now has one banner control and one clearance rule. The exit-press-back banner sat at `W/2 − 79` because its box centred inside the left overlay column — already `W − 82dp` wide — and then reserved that same 82dp right control column a second time, with `TextAlign.Start` left-aligning the recording message it wraps. `MapBanner(borderColor, tagsDrawn, reservesControlColumn, modifier)` in `MapControls.kt` now owns the skin, the border colour and the clearance, and all five instances take it: the exit toast, `LockBanner`, `MapStatusBanner` and the two cards `LoadingOverlay` and `ErrorOverlay`, which keep their own interiors, full width and roles. The pill's line is one definition — `MapBannerText`, 16sp Medium, centred, uncapped wrap grown upward — the adaptive start inset is the pure tested `bannerStartInset(tagsDrawn)`, the tag Boolean is computed once at `MapScreen.kt:1306` and passed down as `bandTagsDrawn`, and the `(category, speedKn)` derivation collapsed into one `regulatedZoneTags` read by the strip, the info text and the inset. The rules live once in `docs/ui-component-guidelines.md` §5.7, with `docs/ui-drawer-guidelines.md` §1 pointing at it. `apk-build.bat` SUCCESS with no new warning; the scoped `ui.map` run green at 23 classes and 227 tests, the previously recorded reds not reproducing. The centring itself and the recording string's two-to-three-line wrap stay unproven until the device pass → `xTrack/Ui_General/260921_FEAT_PLN_Ui_General_bottom-banner-centring.md`
- **string-extraction (2026-09-19)** — the standing rule that no user-facing text is a literal, applied in one pass: `FilterOptionSpec` / `FilterAxisSpec` gained `labelResId` (the `CustomSortField` shape) with all nineteen filter labels read through `stringResource`, the sort-group default followed as an id, and every other label, title, section caption, wizard line, notification line, compass letter and depth phase moved into both locale files — 84 new keys with the French written for each surface, 15 existing keys reused rather than duplicated, and the notification resolved through the service's own context; the rule itself now sits in `AGENTS.md` §1, and the six Previous/Next literals in `OverlayLayer` closed with it; `apk-build.bat` SUCCESS with the scoped run at 380 tests and only the known reds → `xTrack/Ui_General/260919_FEAT_PLN_Ui_General_string-extraction.md`

- **dashboard-close-conditions** — the selected-item dashboard (marker detail, track detail) closes on exactly two conditions: a surface wanting its slot (the wizard, the other selected-item dashboard) or a change of the world its Prev/Next walks. The menu, settings and both lists keep the selection — the four detail slots stand down while a panel is open — the one-item guard lives inside the openers, and the ten referential callbacks close through two named helpers → `xTrack/Ui_General/260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md`

- **track-filter-date-range** — Track Date Range filter extended+reordered: 7 options shortest→longest (Last week → Last 6 month) with All last + default; THIS_YEAR dropped → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_track-filter-date-range.md`
- **marker-filter-remove-geometry** — markers filter no longer exposes a Geometry (Pins/Circles/Corridors) axis; icon/pinned/origin remain, origin ungated → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_marker-filter-remove-geometry.md`
- **list-count-display** — filtered item counts: Track History title "· N" + menu Tracks/Markers row counts left of chevron, live track excluded → `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_list-count-display.md`
- **compact-list-cards** — tighter list cards (14sp desc, reduced padding, no header→title divider)
- **landscape-drawer-settings-sizing** — landscape panels open at portrait widths (menu 75% / settings full short edge), shared scrim → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_landscape-drawer-settings-sizing.md`
- **scrim-strengths-and-dashboard-close** — unified 0.50 scrim on menu/settings/lists only; the fan keeps the selected-item dashboard open. The menu arm is superseded 2026-09-17 (see `## Rules`) → `xTrack/Ui_General/260904_FEAT_PLN_Ui_General_scrim-strengths-and-dashboard-close.md`
- **drawer-dynamic-height** — bottom-anchored drawers with card-height probe + animated height
- **drawer-vertical-rhythm** — uniform 12dp card padding / header vpad / footer rhythm
- **touch-lock** — 📵 splash guard blocking accidental touches (LockScrim + unlock toggle + zoom gated; `status.lock.*` tokens). Renamed from "screen-lock" 2026-09-12 so `screen lock` is free for the device-timeout feature owned by Performance → `xTrack/Ui_General/260827_FEAT_PLN_Ui_General_touch-input-lock.md`
- **menu-drawer-rows** — "Tracks"/"Markers" rows; chevron opens first filtered/sorted item → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_menu-drawer-rows.md`
- **delete-advance-next** — drawer delete advances to adjacent item + snackbar undo stack → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_delete-advance-next.md`
- **top-left-icons** — GPS→tracking→land/water order; GPS click-to-toggle; 🐾 icon; red idle dot → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_top-left-icons-reorder.md`
- **landscape-menu-drawer** — scroll-when-overflow, overscroll suppressed when fits → `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md`
- **notification-lifecycle** — foreground notification follows recording state; recorder + GPS moved into service → `xTrack/Ui_General/260815_FEAT_PLN_Ui_General_notification-lifecycle.md`
- **filter** — extensible `ListFilter` (tracks=date+pinned, markers=pinned+geometry+origin), sort UX normalized → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter.md`
- **filter everywhere** — map mirrors filtered list; fan binary ON/OFF → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_filter-everywhere.md`. *Superseded in part 2026-09-28:* the map draws its own filter's set with no reveal-on-select and no highlighted or pinned escape, and R2's map-world close left the map-opened card — `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md`
- **BackToExitConfirm** — double-back-to-exit guard
- **KeepScreenOn** — keep-screen-on setting (ownership moved to Performance 2026-09-12: the policy and keeper now live in `data/power/` and the setting is documented in `xTrack/Performance/FEAT_DSC_Performance.md`)
- **page layout** — `enableEdgeToEdge()` + status-bar immersion + WindowInsets
- **immersive ui rework** — targeted insets only on overlays; map fills full screen
- **tweak drawer** — `DrawerScaffold`/`DrawerHeader` shared components → `xTrack/Ui_General/260703_FEAT_PLN_Ui_General_tweak-drawer.md`
- **list sort** — `ListOverlayScaffold` generic scaffold (sort/filter/swipe/undo) + `ListAction` contract
- **list extra sort** — `CustomSortField` per-type sort fields → `xTrack/Ui_General/260702_FEAT_PLN_Ui_General_list-extra-sort.md`
- **reg speed zone** — regulated-zone auto-show on approach (distance/time threshold) → `xTrack/ZoneTile/260617_FEAT_PLN_ZoneTile_speed-enforcement-zone-auto-show-plan.md`
- **fan tweak** — scrim removed, MapView touch listener dismisses fan
- **toast & progress dialog** — bottom overlays full-width (padding 6dp, LoadingOverlay full)
- **overlay styling** — toast/Loading/Error unified navy card (blue/red borders)
- **click-N-move** — marker tap closes list, centers map, opens Viewing drawer → `xTrack/Ui_General/260705_FEAT_PLN_Ui_General_click-n-move.md`
- **translation** — 84 FR strings; 14 files localized → `xTrack/Ui_General/260706_FEAT_PLN_Ui_General_translation-survey.md`
- **menu** — drawer menu items wrapped in card backgrounds

## Rules
- The selected-item dashboard — the marker detail drawer and the track detail drawer — closes when a surface wants its own slot (the marker/track wizard, the other selected-item dashboard) or when the scope of the world its walk reads changes — a filter edit, a sort and a reset included, as R2 has always said. **The map-opened card is the one exception (2026-09-28):** a click on the map seats a single item whose standing is not the filter's business, so a filter write leaves it standing, where a list card closes on the list's write and a spy (inspect) card closes on the map's. A deletion, and an edit that leaves the item out of its world, are the other case: those advance to the adjacent item, the previous when the lost one was last, and the dashboard closes only when the world is left empty. No third reason closes it, and it never points at nothing.
- An edit entered from the marker card returns to that card on a save and on a cancel alike, with the camera flying back to the marker — the design of record is `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_edit-return-and-advance.md`, which carries the walk's two framings and the inspect card's own return as well.
- The menu, settings, track history and marker management are panels over the map: they never close it, and the selection returns when they close. Every other action — the layer fan, displays, zoom, lock, gestures — leaves it open.
- Action table, code sites and the open checks: `xTrack/Ui_General/260917_FEAT_PLN_Ui_General_dashboard-close-conditions.md`.

## Docs
- `docs/ui-lists-guidelines.md` — ListOverlayScaffold API, filter system, swipe-to-delete
- `docs/ui-component-guidelines.md` — canonical UI component patterns
- `docs/ui-drawer-guidelines.md` — DrawerScaffold API
- `xTrack/Ui_General/260701_FEAT_PLN_Ui_General_listable-item-interface.md` — ListableItem migration plan (marker + track lists under one interface)
- `docs/material-icons-standalone-guide.md` — standalone icon registry
- `docs/color-scheme.md` — canonical colour tokens
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-rationalization.md` — 2-column Row layout refactor
- `xTrack/UI_Map/260616_FEAT_PLN_UI_Map_map-overlay-layout-inventory.md` — overlay inventory
