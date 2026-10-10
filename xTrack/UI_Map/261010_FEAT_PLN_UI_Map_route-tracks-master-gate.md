# UI_Map — The layer fan as the route-and-tracks master gate

**Status:** implemented 2026-10-10 on `feature/rte-n-trck-list` — build and scoped suites green, device pass owed
**Feature:** UI_Map (the map's layer fan and the stored-tracks render gate); the two header eyes are Ui_Menu's, the flag's home is the settings store, and the marker layer joins through Markers' own gate
**Supersedes:** [`261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md`](../Ui_Menu/261005_FEAT_PLN_Ui_Menu_kind-visibility-toggles.md) **D2** — *the map's layer fan is left alone… the fan is therefore not a routes door* — which the user reversed on 2026-10-10

## Request

- The layer fan's route-and-tracks child is the **primary display gate** for the two kinds; routes must react to it as tracks do.
- Taken with the user: **the fan is the master — what it says goes — and the two header eyes are effective only while it is on.**
- The user's exception: **while a dashboard is open, the item whose info it shows must be drawn**, and it alone. The same behaviour is expected for markers walked with the marker layer off.
- The eyes are drawn **dimmed but actionable** while the master is off.

## Root cause

- The fan has six children — marker · tracks · depth · regulated zones · 300 m band · low-depth warning ([`MapScreen.kt:4994`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:4994)) — and child 1 alone wrote `tracksVisible` ([`MapScreen.kt:5013`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:5013)); nothing of the fan reached routes, exactly as that plan's D2 intended.
- The render gate is per kind already ([`storedTrackSelection()`](../../app/src/main/java/ykws/android/maro/ui/map/MapTrackOverlayEffects.kt:520)), so the fan could not be a master while the tracks header's eye wrote the very flag the fan wrote: one switch would have hidden routes from a *tracks* header.
- Markers had no escape at all: the whole overlay block sits behind `if (markerLayerVisible)` ([`MapScreen.kt:3272`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3272)), so a hidden layer drew nothing even with its card open.
- The old way to make an opened item visible was to **force its kind on** ([`MapScreen.kt:1893`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1893)) — under a master that would have raised the whole family, contradicting "what it says goes".

## Target

```text
The fan (child 1)          routeTracksVisible — the master, persisted like its siblings
The two header eyes        tracksVisible · routesVisible — refinements, dimmed and tappable while the master is off

Map render, tracks/routes  kindLayerOn(master, kind eye):
                             master off → neither kind, save the open dashboard's own item
                             master on  → each kind follows its own eye

Map render, markers        the marker layer's own binary gate, with the same open-card exception
Inspect candidates         unchanged — still gated, so a switched-off kind is never offered
Counts and lists           unchanged — no visibility flag in either
```

## Steps

- **S1 — the master flag.** `AppSettings.routeTracksVisible` with `KEY_ROUTE_TRACKS_VISIBLE`, load, save and default true; the doc sentence that read *the map's layer fan reads `tracksVisible` alone* is replaced by the master's own rule.
- **S2 — the fan's child.** `NavigationViewModel.toggleRouteTracksVisibility()`, and fan child 1 wired to it; the fan's badge and `activeStates` read the master. Child 1 no longer writes `tracksVisible`, which stays the TRACKS header eye's alone.
- **S3 — one rule, one home.** `kindLayerOn(master, kindEye)` in `MapTrackOverlayEffects.kt`, read at the render pass (with `routeTracksVisible` added to `rebuildKeys`), the inspect candidates and the list preview.
- **S4 — the dashboard escape.** `storedTrackSelection` takes `selectedId`; a gated-off half answers `listOfNotNull(selected)` of its own kind, and never the filter — so the 2026-09-28 rule that no opened item rides past the map filter stands. `openSelectedTrack` no longer raises a layer.
- **S5 — the marker half.** The overlay composes when the layer is on **or** the open card's marker is in the map-filtered set, and draws that one marker alone.
- **S6 — the dimmed eyes.** `KindVisibilityToggle(dimmed = !masterVisible)` draws the eye tint at the inactive alpha while staying enabled, so a tap still writes the kind's flag.
- **S7 — the record.** This plan; the owning feature files at the next bake.

## Decisions

- **D1 — the master is a third flag, not a reuse of `tracksVisible`.** Taken with the user (fan is master; eyes refine): reusing it would make the TRACKS header's eye the master, so one header would hide routes. **Objection:** a third switch over one drawing, and a user holding *family off* with both eyes off gets nothing back until they touch an eye — the alternative, the fan flipping both eyes, cannot express *off now, restore later*.
- **D2 — the escape is the open card's item, not the list's.** Taken with the user ("while dashboard is opened; you need to see the item matching the info displayed"), so the trigger is the card and not the panel.
- **D3 — the escape reaches the visibility gates alone.** The filter is not escaped, because the map's own rule since 2026-09-28 is that no opened item rides past a filter; the two rules do not collide because they govern different things.
- **D4 — the inspected candidate set stays gated.** Its recorded rule is that the sweep must not resurrect a kind the user switched off; the exception belongs to the item a card actually describes.
- **D5 — the fan's glyph is left as it is.** Renaming or re-glyphing child 1 for the wider meaning is a design change the user did not ask for; named here so the choice is visible.

## Risks

- The gate is read at several sites; all four went through `kindLayerOn` in this pass, and a later fifth site could read a bare eye.
- The escape bypasses the filter only in the sense that a hidden kind draws its open item even where the map filter would exclude it — the lookup is by id from the unfiltered summaries, so that edge is deliberate and is the price of "the info on screen must have its item on the map".
- A third flag adds one key; an install upgrading defaults it true, i.e. the pre-change behaviour.

## Verification

- `gradlew :app:testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` and `apk-build.bat` — run green on 2026-10-10 (416 tests, 0 failures; APK produced).
- Tests added to `TrackRouteRoleTest`: the master-and-eye rule per kind, the open dashboard's item drawn alone with both gates off, the other kind untouched, and the filter still governing an open item.
- Device pass owed: fan child 1 hiding and showing both kinds; each eye dimmed while the master is off yet still writable; a list-opened route drawn alone with the family off; a hidden marker layer drawing its open card's marker alone.
