<!-- scope: feature -->
# Render axes split — Arrows and Colours as two independent choices

**Status:** shipped 2026-09-17 on `feature/twks-props` through the `#implement` pipeline; its pointer is
in `FEAT_DSC_Tracks.md`'s `## Implemented`, so it has left design per `AGENTS.md` §7a. One part of it is
still owed: the device pass noted below.

## 1. Request

The menu's **Tracks rendering** control is one three-way switch that owns both rendering axes. It
becomes two independent choices under a new caption:

```
Display Tracks with:
      [ Arrows ]   [ Colours ]
```

Both chips are on/off by themselves, so four states exist — three of them already shipped under the
tri-state's own names.

## 2. Decision (2026-09-17)

- **Two independent axes.** `Arrows` governs the chevrons, `Colours` governs the banded speed ramp;
  neither implies the other.
- **Four states, all valid — settled 2026-09-17:** neither (today's *Simple*), Arrows only (today's
  *Dir & Speed*), Colours only (the new one), and both (today's *Colours*). No combination is refused
  and no floor keeps a chip lit.
- **`Simple` retires as a label** — the state survives as both chips off, and
  `menu_render_mode_simple` loses its only reader.
- **The drawer eye stays**, and keeps the meaning it was given on 2026-09-15: it flips the *selected*
  track's fill alone, against the `Colours` flag rather than against `mode == HEATMAP`, and never
  touches the arrows.
- **Reverses a recorded reading.** The tri-state was chosen on 2026-09-14 as *"One tri-state owns both
  axes"*, with *"Axes stay separate"* dropped because *"the mode owns the arrows, so the ramp always
  draws them"* — the combination knowingly given up there, a heat map with the arrows switched off, is
  exactly the new third state (`FEAT_DSC_Tracks.md:223`, the 2026-09-14 level's item 1 and its child).
- **Twin box (settled 2026-09-17).** The two chips are a new multi-select sibling of `SegmentedRow` —
  one outline, two halves, each half lit on its own — not two stacked rows and not a second behaviour
  grafted onto the existing control.
- **Home — settled 2026-09-17: the shared folder.** The twin box and `SegmentedRow` both live in
  `ui/components`; the rule is already stated in `docs/ui-component-guidelines.md:122`, and the twin box
  is documented beside `SegmentedRow`'s own section there (`:230`).

## 3. Migration — lossless

The old enum was an encoding of these two bits, so every stored install maps across exactly.

| Stored `track_render_mode` | `arrows` | `colours` | Rendering it already had |
|---|---|---|---|
| `SIMPLE` | false | false | default colours, no chevrons |
| `DIR_SPEED` | true | false | default colours + chevrons |
| `HEATMAP` | true | true | banded ramp + chevrons |

- Read rule: `arrows = mode != SIMPLE`, `colours = mode == HEATMAP`.
- The legacy value is read **once** at cold start, only while the two new keys are absent, and is then
  never consulted again — the same shape the 2026-09-14 pass used when it dropped its two booleans,
  but without that pass's cost: nothing returns to a default. A device holding neither the legacy key
  nor the new ones takes §4's fresh-install default instead.
- `KEY_TRACK_RENDER_MODE` (`track_render_mode`) is deleted with the parse.
- **The retired key is wiped, not left inert — settled 2026-09-17.** The migration removes
  `track_render_mode` in the same write that puts the two flags down, so storage carries the new keys
  alone and no later reader can mistake the old word for live state. The load path commits that removal
  itself (`SettingsManager.kt:463`) rather than deferring it to the next settings write, where a device
  whose owner never opens Settings would carry the stale word for the life of the install.
- **No trace of the old model survives — settled 2026-09-17.** The enum, its key constant, its parse,
  its KDoc, the retired option string and the two test names that echo it are deleted outright; nothing
  is kept behind a compatibility shim.

## 4. Model

- Two non-null booleans in `AppSettings` beside their key constants: `trackArrows` (`track_arrows`)
  and `trackColours` (`track_colours`).
- **Fresh-install default — settled 2026-09-17: `Colours` on, `Arrows` off.** A device with neither the
  new keys nor the legacy one opens on the banded ramp without chevrons, the one state the tri-state
  could not express; every install holding the legacy key keeps its own look, the migration reading it
  first.
- **The default cannot be scoped to "a brand-new install".** The legacy key is written only when a
  setting is saved, so an install predating 2026-09-14 that never changed anything lands on the default
  too. Accepted: telling "never touched" from "new" needs a marker key of its own, and both cases
  plausibly want the ramp on.
- **Accepted consequence:** with `Colours` on, a first run raises the speed scale as soon as one stored
  track is on the map, so a new device shows map chrome it never showed before.
- `TrackRenderMode` retires from the render paths: the two flags are passed as they are, since every
  predicate today is a negation of that one enum (`!= HEATMAP`, `!= SIMPLE`) and each reads one flag
  directly afterwards.
- The store follows: `putBoolean` for both keys, and the `getString(...).name` / `valueOf` pair goes.
- **Writer shape — settled 2026-09-17: per chip.** The menu's single `onRenderModeChange(TrackRenderMode)`
  becomes one callback per axis — `onArrowsChange(Boolean)` and `onColoursChange(Boolean)` — each folded
  into its own `updateSettings { copy(...) }` at the one site that writes a mode today
  (`MapScreen.kt:1733`), so the menu stays the only writer (D3/D5) and a tap announces only the chip
  that moved.

## 5. Touch points

| File | Change |
|---|---|
| `app/src/main/java/ykws/android/maro/config/HeatmapRamp.kt` | `TrackRenderMode` removed with the render-mode enum block |
| `app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt` | two fields + keys, the cold-start migration read, `putBoolean` writes, and `trackSelectionBanded`'s KDoc, which names the mode in three sentences |
| `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` | the row becomes the twin box with its import re-pointed; caption string; the D5 comment re-argued |
| `app/src/main/java/ykws/android/maro/ui/components/` | new home for `SegmentedRow` and the twin box: one outline, per-chip events |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreenSettingsOverlay.kt` | `SegmentedRow` leaves the file with its own KDoc, and its three call sites re-point to the shared import |
| `app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt` | `trackRenderPlan`, `selectionBandedAfterTap`, `legendVisibleFor`, and the rebuild-key conditions at the two `!=` sites |
| `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` | the enum plumbed to the two `bandedOn` call sites |
| `app/src/main/java/ykws/android/maro/ui/map/OverlayLayerParams.kt` | parameter bundles carrying `trackRenderMode` / `renderMode` |
| `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` | the import, the call sites that hand the mode down, the writer at `:1733` and the eye-tap pair at `:1859-1861` |
| `app/src/main/res/values/strings.xml`, `values-fr/strings.xml` | caption + option labels + `settings_colors_desc` |
| `docs/ui-component-guidelines.md` | the twin box documented beside `SegmentedRow`'s section, and the choice tree at `:21` given the multi-choice line it introduces |
| `app/src/test/java/ykws/android/maro/ui/map/TrackRenderModePathTest.kt` | rewritten over the four flag combinations, and renamed off the retired enum |
| `app/src/test/java/ykws/android/maro/ui/map/TrackRenderModeStringsTest.kt` | caption and label assertions follow the new key set; its case name still counts "the three option labels", now two |

Indexed lines as of capture: enum `HeatmapRamp.kt:16`; field `SettingsManager.kt:219`; read
`SettingsManager.kt:463-466`; write `SettingsManager.kt:605`; key `SettingsManager.kt:725`; row
`MenuDrawerOverlay.kt:266-274` with its own parameters at `:81-84`; decisions
`MapTrackOverlayEffects.kt:387`, `:413`, `:437`, rebuild keys `:82` and `:92`; bundle extraction
`OverlayLayer.kt:181`, `:197`, `:374-376` beside the two `bandedOn` sites at `:491` and `:585`; params
`OverlayLayerParams.kt:53` and `:97`; the writer and the eye tap `MapScreen.kt:1733` and `:1859-1861`;
the control the twin box sits beside `MapScreenSettingsOverlay.kt:1313`.

## 6. Semantics to preserve

- **Arrows follow the arrows flag alone.** The 2026-09-15 split stands: the eye does not move the
  arrows, so the arrows' rebuild keys become the flag rather than *"anything but Simple"*.
- **Ramp keys are read where a banded stroke can land** — the `Colours` flag, or the eye banding the
  selection; the same path-derived reading, with the flag in place of the mode.
- **Legend gate — settled 2026-09-17, then narrowed the same day (§11).** The scale keys the ramp's fill
  *and the open track's own fill*: `storedOnMap && (if (selectionOpen) (eyeOverride ?: trackColours) else
  trackColours)`, which retires the shipped "any banded stroke raises it" rule this plan first carried.
- **Eye's first tap** flips against the `Colours` flag (`selectionBandedAfterTap(current, colours)`),
  and still never moves a non-selected track or the other flag.
- **The live recording line** stays outside the axes, as it already does.
- **Settings' arrow expander keeps its 2026-09-14 decision** — label and three controls untouched —
  and its controls now have an owner on screen: they do nothing while the `Arrows` chip is off, which
  the chip states rather than the mode.

## 7. Strings

- Caption `menu_tracks_rendering` — settled 2026-09-17: EN **Display Tracks with:**, FR **Afficher les
  traces avec :**.
- Option keys follow the axes rather than a mode — settled 2026-09-17: `menu_render_arrows`
  (Arrows / Flèches) and `menu_render_colours` (Colours / Couleurs), the `_mode_` infix no longer
  describing what the control is; both locale files and the strings test move with the rename.
- Accepted cost: `Colours` now names the speed ramp while the plain colours are what the same chip
  leaves in place when it is off, so the label carries more than it did as a dial position.
- `menu_render_mode_simple` deleted from both locales.
- `settings_colors_desc` reworded: those colours are the ones drawn when **Colours** is off, today's
  *"the colours Simple and Dir & Speed draw with"* naming two states that no longer exist.

## 8. Settled and outstanding

**Settled 2026-09-17:** the control is a twin box (§2), events report per chip (§4), both controls live
in `ui/components` (§2), a fresh install opens on Colours without Arrows (§4), all four combinations are
valid states with nothing refused (§2), the old model is deleted outright — its key wiped by the
migration, its enum, strings and test names gone, with no shim kept (§3) — Settings' arrow group stays
untouched, the chip being what explains a control that does nothing (§6) — and the legend gate is
unchanged, so an eye-banded selection raises it with Colours off (§6).

**Closed with the plan's recommendation:** the enum and the two test names — `TrackRenderMode` goes
with the old model, so both classes are renamed off it rather than left named after a type that no
longer exists (§3, §5).

**Nothing is left open for arbitration**; what remains is execution, beside the walk level noted below.

**Unrelated but blocking a fold:** the 2026-09-15 walk level in `FEAT_DSC_Tracks.md` still has items 12
and 13 open — reported when Tracks was focused.

## 9. Verification

- Scoped unit run over the rewritten path test (four flag combinations × selected/unselected × the
  eye's three values) and the strings test; the heatmap tests are untouched.
- The twin box checked against what `SegmentedRow` guarantees: each chip's own lit state, both on and
  both off, and an announced check box rather than "n of m", while its four callers — one in the drawer,
  three in the settings overlay — keep the radio behaviour they have and only their import moves.
- A sweep proving the old model left nothing behind: no `TrackRenderMode`, no `track_render_mode`, no
  `menu_render_mode_simple`, and no comment still describing a three-way switch, across code, resources
  and docs.
- `apk-build.bat`, then a device pass on the four states, the legend's gate with the eye, and — for an
  install that held `SIMPLE`, `DIR_SPEED` and `HEATMAP` — the migrated flags **and the vanished key**,
  the wipe being the only reason to read the old value at all.
- Feature bookkeeping per §7a: the plan joins `FEAT_DSC_Tracks.md`'s `## Docs` as it leaves design, and
  on shipping it gains an `## Implemented` one-liner with its pointer, a `#bake` and a `#doctor` pass.

## 10. Ask findings, folded

- **The migration's mapping should be a pure, tested function — carried, and the one thing this change
  leaves unproven.** Its correctness rests on the constructor's argument order (`trackArrows` migrates,
  `trackColours` then reads what it wrote, `SettingsManager.kt:465`): stated in the helper's KDoc, but a
  reorder would break it silently, and nothing tests the three stored values. Lifting
  `legacy mode → (arrows, colours)` into a pure function and pinning the three tokens is the cheap close.
- **The chip callback's stale-flip window — accepted.** `MultiSelectRow` hands back the axis alone, so
  the menu computes `!trackArrows` from the composition's own value; a double tap inside one frame writes
  the same value twice rather than flipping back, and the control already knows the drawn state if
  `onToggle(axis, !on)` ever earns the signature change.
- **A corrupt legacy token — accepted.** `legacy != "SIMPLE"` reads anything unknown as arrows-on where
  the retired parse fell back to the plain default; a corrupt value has no honest reading, and comparing
  against the two known tokens is the repair if one is ever reported.
- **One dead import — carried.** `androidx.compose.foundation.selection.selectableGroup` stays behind in
  `MapScreenSettingsOverlay.kt` after the move.
- **Accepted costs, by decision rather than by oversight:** the twin controls carry their shared geometry
  in two files, and the colours flag still rides two bundles into `OverlayLayer` (pre-existing, B17).

## Outcome

Shipped as planned, with three deviations worth stating:

- `TrackInfoOverlayData.renderMode` collapsed to a single `trackColours` field instead of the two flags:
  the drawer header's eye reads the fill alone and never the chevrons, so a second field would be a
  parameter no caller could use.
- The caption ships as **Display Tracks with:** / **Afficher les traces avec :**, the wording this plan
  recorded, colon and all.
- The scoped run's five reds are **not** this change's. They are the `maro.properties`-versus-`AppConfig`
  drift introduced by the previous commit `f2531e4` — the ramp's new colours, family 5 at 14 kn, family 6
  at step 2.0 and `track.width.selected.casing` at 16 — asserted by `HeatmapRampPropertiesTest` and
  `TrackOutlineTest` and reported at that commit. No `AppConfig` default was touched here, and none of
  the five assertions concerns the render axes.

Owed: the device pass over the four combinations, the legend gate with the eye, and the migrated flags
with the vanished key.

## 11. Second pass (2026-09-17) — the glyph, and what the scale follows

Asked for the same afternoon; both land on the drawer header's toggle.

- **The toggle wears the speedometer.** `Visibility` gave way to the standalone `Speed` vector of
  `ykws.android.maro.ui.icons`, the same family the legend's collapsed face speaks. It is a plain filled
  path, so the accent/inactive tint keeps carrying the control's state where the legend's colour emoji
  could not have; the KDoc above the control stops calling it an eye, and the retired import went with it.
- **The scale follows the open track's fill — the earlier reading reversed.** `legendVisibleFor` became
  `storedOnMap && (if (selectionOpen) (eyeOverride ?: trackColours) else trackColours)`: with a track open
  the key lives and dies with *that* track's ramp, so flipping the selection gold hides it even while the
  other painted tracks stay banded, and a selection the eye has banded still raises it with `Colours` off.
  With nothing selected the fallback stands unchanged — the other tracks carry the ramp or nobody does.
- **Accepted cost:** opening a track and flipping its colours takes the key away from every banded line
  still on the map, which is the opposite of what a legend is usually for. Recorded rather than argued
  again: it is what "visibility of the scale must follow the selection" buys.
- Verified the same way as the pass above — `apk-build.bat` SUCCESS and the scoped `ui.map` + `config` run
  steady at 130 with only the five pre-existing properties-drift reds — with the flipped case pinned in
  `TrackRenderFlagsPathTest`; the device pass already owed now covers the new gate and the new glyph too.
