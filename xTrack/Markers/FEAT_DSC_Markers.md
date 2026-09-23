---
name: Markers
status: active
created: 2026-06-22 11:52
modified: 2026-09-18 22:00
---

# Feature: Markers

**Description:**
User-defined markers on the map — Pin, Circle, and Corridor geometries. Line-of-sight matching with proximity-zone pre-filter (land-blocking via coastline spatial index + 10m grazing tolerance). On-demand "where am I?" query via boat marker tap. Step-by-step wizard for creation/editing. Viewing drawer (card layout) + match result display. Management page with swipe-to-delete. OSMdroid native overlays. Binary layer toggle (HIDDEN/SHOW_ALL). POI emoji icons on markers; pin is a real persisted flag.

## Sections

### marker-focus-zoom

The camera a selected marker asks for when its dashboard opens: a stated share of the smaller
displayed map dimension — corridor 0.50, circle zone 0.30 — with a pin framing the default zone's
200 m footprint at the zone's share and never below the current zoom.

#### Docs
- `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` — plan (implemented)

### pin-halo-rendering

Static settings-driven halo ring differentiates pinned (white, strong) from unpinned (light-blue, faint) markers; corridor always-on colored line + pinned under-line halo; selected-marker gold driven by `selectedMarkerId`.

#### Docs
- `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md` — plan (implemented)

## Todos
- [ ] fix proximity of date points — rays hit/test all of them

## Walk
**Level 1 — Date:** 2026-09-18 · **Source:** `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md` · **Active:** 11
- [x] 1 · Touch zone — a fixed thumb-sized circle centred on the sprite's visual centre at any zoom, with a short transparent-gold ring flash on an accepted tap
- [x] 2 · Rays cleared when the dashboard closes — segments cleared, the run left to finish, publishing gated on the dashboard still being its own
- [x] 3 · Rays withdrawn when the debug toggle goes off
- [x] 4 · Debugger passed per call, the no-op collector for the recorder and the service
- [x] 5 · One resolve body, the null-index and empty-marker cases kept apart
- [x] 6 · One query per tap, the recording snapshot ordered against the close
- [x] 7 · Debugger interface shrunk to what is called
- [x] 8 · The `marker.debug.rays.enabled` hook — retired 2026-09-18, the setting left as the single carrier
- [x] 9 · Verification — build, scoped tests, and whatever guards `spatial/`
- [x] 10 · Device pass — closed by decision 2026-09-18: the user owns it, and this walk no longer tracks it
- [ ] 11 · Bookkeeping bake across Markers, UI_Map and Ui_Settings
- [ ] 12 · Proximity of date points — rays hit and test all of them (pre-existing, outside the plan above)

## Implemented

- **palette-vibrancy-pass (2026-09-23, `feature/color-picker`)** — the sixteen slots re-hexed for vibrancy, superseding the palette carried by the `palette-vibrancy` entry below: Orange `0xFFF57C00` → `0xFFFF6500`, Gold `0xFFF9A825` → `0xFFFFD700`, Purple `0xFF4A148C` → `0xFFCD00CD` and Red `0xFFF44336` → `0xFFD50000`, with two slots swapped out altogether — the water-close Light Cyan `0xFF00BCD4`, at 1.4 against the pale sea the closest entry to the water colour, gave way to Dark Violet `0xFF4A148C`, and the muddy Dark Brown `0xFF4E342E`, which sat 0.018 of lightness from Maroon `0xFF880E4F`, gave way to the vivid Amber `0xFFFFAB00`, leaving Maroon the single dark warm. The red was deepened rather than kept because the brighter orange at lightness 0.31 would otherwise have sat 0.07 from `0xFFF44336`, the proximity flagged twice in review — the gap is now 0.16 — and the cost of the brightening is stated rather than hidden: orange lands at 1.8 over the pale sea, joining the bright tier that leans on the marker's own halo. Two rules are written down rather than re-derived — no hue near the water, and any pair clearing 40° of hue or 0.10 of lightness — in the palette KDoc and in [`docs/color-scheme.md`](docs/color-scheme.md:17). [`colorName()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:578) reads Dark Violet, Red and Amber for slots 6, 7 and 11, a stored marker keeps its `colorIndex` and changes hex only, and no user-facing string is involved

- **palette-vibrancy (2026-09-23, `feature/color-picker`)** — three of the sixteen slots re-hexed on top of the palette order carried by the `palette-range-revision` entry below, which this entry supersedes on colour and leaves standing on order: slot 4 Orange `0xFFE65100` → `0xFFF57C00` and slot 7 Red `0xFFB71C1C` → `0xFFF44336`, both a vivid, more saturated reading at lightness 0.338 and 0.235 and 26° of hue apart, and the forced slot 14 Lime `0xFF9E9D24` → `0xFF880E4F` Maroon — forced, not chosen, because the brighter orange at 0.338 landed within 0.02 of lightness of the lime at 0.315 and only 35° away in hue, the closeness this palette has been through two revisions to remove, while olive already covers the lime's 65° hue at 0.180 and maroon is a strong water colour at 5.9 that restores a colour-vision-safe hue family. The brightening costs water contrast — red now 2.3 and orange 1.7 over the pale sea, down from the figures the entry below records — the marker's halo and outline carrying both. A stored marker keeps its `colorIndex` and changes hex only, [`colorName()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:578) reading `Maroon` for slot 14, and the palette holds no user-facing strings. `apk-build.bat` SUCCESSFUL; no new dependency, no `strings.xml` change, `MarkerColors.default` and every `of()` consumer untouched

- **palette-range-revision (2026-09-23, `feature/color-picker`)** — the same 16 slots re-hexed once more, superseding the palette order carried by the `palette-contrast-revision` entry below: Black, Gold, Green, Light Violet, Orange, Grey, Light Cyan, Red, Olive, Purple, Light Green, Dark Brown, Pink, Cyan, Lime, White. Five reasons drove it — red and orange now differ by 0.12 of lightness as well as by hue, the blue slot is gone because the depth overlay would swallow it, gold became a saturated amber, two purples collapsed into a single purple, and the green-blue slot moved to cyan-teal so it cannot read as a second green. The list's lightness runs 0.00 to 1.00 with deliberate tier twins for the greens, the cyans, the violets and the yellows, and ten of the sixteen hold at least 2.3 over the pale sea. A stored marker keeps its `colorIndex` and changes hex only, [`colorName()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:578) matching the new order slot for slot, and the contrast figures behind the picks were computed against `#AAD3DF` sea and `#F2EFE9` land, style values of the MAPNIK tiles rather than values this repo holds. The rule is stated once in [`docs/color-scheme.md`](docs/color-scheme.md:12). `apk-build.bat` SUCCESSFUL; no new dependency, no `strings.xml` change, `MarkerColors.default` and every `of()` consumer untouched

- **palette-contrast-revision (2026-09-23, `feature/color-picker`)** — the same 16 slots re-hexed for contrast against the pale sea and land of the MAPNIK tiles, superseding the palette order carried by the `colour-picker-unification` entry below: Red, Orange, Green, Teal, Blue, Purple, Brown, Blue Grey, Black, White, Cyan, Gold, Pink, Olive, Maroon, Deep Purple — one strong orange kept (`0xFFE65100`) and the blue band cut from six entries to four, dark blue left out because the depth overlay's own blues would swallow it while a single true blue (`0xFF1565C0`) stays, and white reserved for dark backgrounds rather than the map. Three of the sixteen slots kept their hex — purple, black, white — so a stored marker keeps its `colorIndex` and only changes hex, and [`colorName()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:578) now reads Gold, Olive, Maroon and Deep Purple for slots 11, 13, 14 and 15. The contrast figures behind the picks were computed against `#AAD3DF` sea and `#F2EFE9` land, style values of the MAPNIK tiles rather than values this repo holds, and the rule is stated once in [`docs/color-scheme.md`](docs/color-scheme.md:12). `apk-build.bat` SUCCESSFUL; no new dependency, no `strings.xml` change, `MarkerColors.default` and every `of()` consumer untouched

- **colour-picker-unification (2026-09-23, `feature/color-picker`)** — one 16-entry palette in [`MarkerColors.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt:7) now drives both pickers, each keeping its own contract: the marker dialog stays index-based (`items(colors.size)`, `onColorSelected(index)`) while the settings [`ColorPickerDialog`](app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt:1895) stays ARGB in/out, its inline `presets` deleted and the grid reading `MarkerColors.all`; the order is one entry per hue family — Red, Orange, Green, Teal, Blue, Purple, Brown, Blue Grey, Black, White, Cyan, Amber, Pink, Lime, Light Blue, Indigo — each hex commented by its Material name, [`of()`](app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt:30) and [`randomIndex()`](app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt:37) taking their bound from `palette.indices` / `palette.size` so the list is the single home for the length, and [`colorName()`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:578) re-ordered to match. Two consequences are held on purpose rather than smoothed: a stored marker keeps its `colorIndex` and only changes hex, and settings colours already stored as raw ARGB — `#FF1565C0` out of `colors.properties` — are no longer palette members, so those rows show no selected swatch until a new colour is picked. `apk-build.bat` SUCCESSFUL; no new dependency, no `strings.xml` change, `MarkerColors.default` and every `of()` consumer untouched

- **marker-focus-zoom (2026-09-18)** — a marker's dashboard open now frames it to a stated share of the smaller displayed map dimension, replacing the one uniform 64 px border fit that made every zone type land at the same size: corridor 0.50, circle 0.30, and a pin through a nominal 200 m footprint — the default zone's diameter, the wizard's own `radiusM = 100.0` — at the zone's share and never below the current zoom; the three values live as `marker.focus.*` in `maro.properties` behind `AppConfig`, the fit is a pure `MarkerFocus` helper with eight JVM tests, the 8–18 zoom bounds gained one home and `UserMarker.bboxOf` replaced the private metres-to-degrees conversion the pin's footprint reuses; the navigate flow keeps its single camera ownership and every other select — map tap, dashboard Prev/Next — is framed by one effect guarded by `lastFramedMarkerId` and the inspect hand-off, `apk-build.bat` SUCCESS with `MarkerFocusTest` green → `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md`
- **whereami-card-close-rule (2026-09-18)** — the Where-Am-I card dismisses on a one-finger map pan by its own rule, the gate held in `ui/map/MapPanDetector.kt` with a pinch latched out of it, while a zoom, a double-tap and the zoom buttons leave it standing; the recorder's idle exit dismisses it as before, and the return-to-boat timer is held while a drawer is open and restarted on close
- **whereami-tap-zone-and-flash (2026-09-18)** — the boat's touch box replaced by a fixed radial zone on the sprite's visual centre, its diameter, the pulse's, the beat and its peak all read from the `map.marker.tap.*` keys (sprite and its `centerOffsetYDp` unmoved, so the bow stays on the GPS point), consuming only inside the circle and reaching assistive tech as the same accepted action; an accepted tap beats one gold disc beneath the sprite — `map.marker.tap.flash.color` at `map.marker.tap.flash.alpha`, twice the zone's diameter — never while inspect is armed and never for a drag. Shipped with the ray-clear items through the `#implement` pipeline → `xTrack/Markers/260918_FEAT_PLN_Markers_whereami-tap-zone-and-ray-clear.md`
- **pin-halo-rendering (2026-09-05)** — static halo ring (pinned white / unpinned light-blue, absolute size 18-60px, opacity pairs); pin dimming removed (search dimming kept); corridor always-on colored line + pinned under-line halo; selected-marker gold driven by `selectedMarkerId` (forces zones, folds `navigationZonesVisible`, removes `highlightedMarkerId`); corridor/circle focus zoom-to-fit; icon centered on halo; code split into `MarkerAppearance`/`MarkerHalo` → `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md`
- **icon-pin-decoupling (2026-09-04)** — icon fully decoupled from pin (pure POI emoji, no pin semantics); pin re-implemented as real persisted `UserMarker.pinned` mirroring tracks (repo/VM/card/drawer/multi-select/filter/rendering); removed obsolete `migratePinnedToIcon`; settings v7 migration → `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md`
- **multi-select-merge (2026-07-18)** — multi-select delete/pin/merge on marker list → `xTrack/Markers/260718_FEAT_PLN_Markers_multi-select-and-merge.md`
- **marker-card (2026-07-03)** — normalized list/viewing/match cards, removed `markerFormatText()`
- **wizard-cleanup-race (2026-07-02)** — moved wizard-state cleanup into save/update coroutines
- **auto-marker-proximity (2026-07-02)** — 🕐 auto markers use `boatMarkerAutoMarkerProximityM` (300m) → `xTrack/Markers/260702_FEAT_PLN_Markers_auto-marker-proximity-fix.md`
- **Data model & persistence** — `UserMarker` + sealed `MarkerGeometry`, JSON repo, 16-color palette → `xTrack/Markers/260622_FEAT_PLN_Markers_user-markers-design.md`
- **Match resolution — two-gate model** — zone check + proximity pre-filter + line-of-sight → `xTrack/Markers/FEAT_DOC_Markers_decisions.md`
- **Boundary point sampling** — `pointAlongBearing` direct boundary sampling
- **Overlay rendering** — Pin/Circle/Corridor overlays, tri-state `MarkerLayerState`
- **Creation & editing** — `WizardDrawer` step wizard + `WizardButtonRow` → `xTrack/Markers/260623_FEAT_PLN_Markers_create-zones-flow.md`
- **Viewing & management** — `MarkerDrawer` + `MarkerManagementOverlay`
- **Color system** — `COLOR_CONFIRMED`=blue, `COLOR_UNCONFIRMED`=amber
- **setting-markers** — grouped Appearance card, binary HIDDEN/SHOW_ALL layer toggle, settings migration v5
- **Known issues** — see `xTrack/Markers/FEAT_HYD_Markers.md`

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerHalo.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/markers/wizard/WizardButtonRow.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerColors.kt`

## Docs
- `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` — focus zoom framing: the share rule, the value keys, the pure helper and the camera wiring
- `xTrack/Markers/260625_FEAT_PLN_Markers_wizard-drawerslot-separation.md` — wizard step extraction + DrawerSlot abstraction design
- `xTrack/Markers/260905_FEAT_PLN_Markers_pin-halo-rendering.md` — pin halo rendering plan (implemented)
- `xTrack/Markers/260904_FEAT_PLN_Markers_icon-pin-decoupling.md` — icon/pin decoupling + pin re-implementation plan (implemented)
- `xTrack/Markers/FEAT_DOC_Markers_decisions.md` — architectural decisions with rationale and source references
- `xTrack/Markers/FEAT_HYD_Markers.md` — hydration snapshot + known issues (single source of truth)
- `xTrack/Markers/260622_FEAT_PLN_Markers_user-markers-design.md` — original design plan (all phases ✅)
- `xTrack/Markers/260623_FEAT_PLN_Markers_create-zones-flow.md` — wizard creation flow design
- `xTrack/Markers/260625_FEAT_PLN_Markers_whereami-rework.md` — whereAmI rework design (implemented)
- `xTrack/Markers/260625_FEAT_PLN_Markers_debug-wia.md` — debug instrumentation design (implemented)
- `xTrack/Markers/260630_FEAT_PLN_Markers_whereami-gaps.md` — current angular shadow over-blocking analysis
- `xTrack/Markers/260626_FEAT_PLN_Markers_icon.md` — POI icon system design (implemented)
- `xTrack/Markers/260628_FEAT_PLN_Markers_icon-fixes.md` — icon bug-fix plan (partial: management icon picker ✅, duplicate imports ❌)
- `xTrack/Markers/260626_FEAT_PLN_Markers_marker-pin.md` — pin toggle design (implemented)
- `xTrack/Markers/260626_FEAT_PLN_Markers_marker-pin-tri-state.md` — tri-state layer toggle design (implemented)
- `xTrack/Markers/260624_FEAT_PLN_Markers_area-tap-and-wizard-buttons.md` — area tap + wizard buttons + corridor caps + color settings (area tap ✅, buttons ✅, caps ✅, color settings ❌)
- `xTrack/Markers/260625_FEAT_PLN_Markers_next-session-ui-polish.md` — UI polish plan (format ❌, color picker ✅, edit icon ✅, viewing drawer ✅)
- `xTrack/Markers/260702_FEAT_PLN_Markers_auto-marker-proximity-fix.md` — Auto marker proximity fix
- `xTrack/Markers/260711_FEAT_PLN_Markers_marker-hilite-dual-outline-plan.md` — Marker highlight dual outline plan
- `xTrack/Markers/260623_FEAT_PLN_Markers_marker-proximity-wizard-normalization.md` — Marker proximity wizard normalization
- `xTrack/Markers/260624_FEAT_PLN_Markers_markers-section-normalization.md` — Markers section normalization
- `xTrack/Markers/260701_FEAT_PLN_Markers_remove-proximity-range-gates.md` — Remove proximity range gates
- `xTrack/Markers/260711_FEAT_PLN_Markers_track-hilite-dual-outline-plan.md` — Track highlight dual outline plan
- `xTrack/Markers/260701_FEAT_PLN_Markers_whereami-flow.md` — WhereAmI flow
