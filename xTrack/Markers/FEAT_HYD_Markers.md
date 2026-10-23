# Context Hydration — Markers — 2026-10-23

**Last Bake:** 2026-10-23 16:37 UTC — written by `#bake`. One session on `feature/marker-color`: the colour picker's choice was made sticky end to end, then every marker's name was put behind one pure auto-naming function.

**Directive trace:** All five covered action classes were met and none stopped the session — no dependency was added, no machine-shaped data file was opened, the work ran only on the user's own `#new`, the bug reports and `#impl` rather than without an order, no device was touched by the agent (the device pass stays the user's), and every claim written here about the code carries a file read behind it, save the build result, which is carried forward as its own report.

## State

**The picked colour persists (2026-10-23).** `updateMarker`'s copy gained the `colorIndex` the wizard sets — an edit's colour change had died in the form — and the map preview now carries the form's colour instead of nothing. The preview still drew caution amber, because `MarkerAppearance.of` returns the caution colour for every unconfirmed marker; a new preview-only `isPreview` flag makes the wizard's synthetic marker draw its own palette colour, passed by identity in `MarkerOverlay`, while the stored 🕐 idle pin keeps amber and no unconfirmed marker draws a halo.

**One auto-name (2026-10-23).** `data/markers/MarkerAutoName.kt` is the single home: `markerAutoName(createdAtEpochMs, glyph, geometry, matchesAt)` probes the marker's own geometry — a corridor's midpoint first, its two endpoints pooled only when the midpoint names nothing — through `topNamingMatch`, and falls back to the tracks' `trackAutoName`; the caller-supplied glyph leads the string, so the data layer never imports the UI's `MarkerType`.

**The wizard stops naming a colour (2026-10-23).** `startWizard` opens the title as `📍 yyyy-MM-dd HH:mm`; `CreateFormState` gained `nameIsAuto` (false default) and `createdAtEpochMs`, `formGeometry` is the one construction the create and edit saves read, shape-step exits and Finish re-name while the name is still the machine's, and `updateFormName` freezes it on the first differing keystroke. An edit never re-names: the edit entry forces `nameIsAuto = false`, and a create save clears the form so a later edit cannot inherit a stale flag — the review hop's one blocker, fixed.

**What is owed.** `MarkerAutoNameTest` (the plan's §8) was not shipped; the 🕐 auto-marker stays unaligned with the one function (decision 1); the device pass over the new default title and the persisted colour is the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/data/markers/MarkerAutoName.kt` — the one naming function, the two-tier probes
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `CreateFormState.nameIsAuto`/`createdAtEpochMs`, `formGeometry`, `autoNameOf`, `updateFormName`, `recomputeAutoNameIfAuto`, the rewritten saves
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the preview's name and colour
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt` — the Title step writes through `updateFormName`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerAppearance.kt` · `MarkerOverlay.kt` — `isPreview` and its two call sites
- `xTrack/Markers/261023_FEAT_PLN_Markers_auto-naming.md` — the plan of record

## Next Step

`MarkerAutoNameTest` and the 🕐 alignment are the two code items owed; the device pass is the user's.
