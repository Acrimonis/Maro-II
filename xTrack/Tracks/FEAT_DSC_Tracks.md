---
name: Tracks
status: active
created: 2026-06-15 21:43
modified: 2026-09-14 15:52
---

# Feature: Tracks

**Description:**
Trace the boat's movement (position, speed) during active navigation. One trace = one 'track' (Port Salis → Port Salis). Point capture suspends when stationary via `isStill()` gate, but the recording state stays ON. Tracks persisted as protobuf binary and recallable — their polylines display on the map overlay.

## Sections

### verification

#### Todos
- [ ] Build + deploy to device
- [ ] E2E: Enable tacking -> leave Port Salis -> verify auto-start + real-time map trace
- [ ] E2E: Stop sailing -> verify pause -> sail again -> verify resume
- [ ] E2E: Return to Port Salis + stop -> verify auto-finalize + appears in tack history
- [ ] E2E: Open tack history -> tap tack -> verify trace renders on map
- [ ] E2E: Swipe-to-delete tack -> confirm dialog -> verify removed from list + file system
- [ ] E2E: Manual Start/Stop from tack drawer -> verify state matches auto-detection
- [ ] E2E: Export GPX -> copy to computer -> open in QGIS/Google Earth -> verify track/speed/course
- [ ] E2E: Verify settings persistence of tack fields across app restart
- [ ] E2E live-track-paint-regression: fresh recording paints the active line point-by-point (demo + GPS); resume still paints; GAP seam stays dashed; no duplicate history copy when a Map filter is applied
- [ ] E2E resume-confirm-backup: sheet appears from list + both dashboard cards; checkbox checked by default; backup written only when ticked (new card, hidden on map, unpinned, no marker links); recording continues on the original; Cancel changes nothing

#### Docs

### track-list

#### Todos
- [ ] Review and refine track card layout per design spec
- [ ] Verify swipe-to-delete, inline snackbar, undo animations
- [ ] Verify inline editing (auto-focus, field-switch commit, back-to-revert)
- [ ] Verify human-readable formatting (comma decimal, durations)
- [ ] Verify compact padding and flush-left stats grid
- [ ] E2E: Create test tracks, verify all card fields display correctly

#### Rules
- Track list UI must follow 260618_FEAT_PLN_Tracks_TrackList_Design.md spec
- Styling must match Settings overlay patterns (AppConfig tokens)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_TrackList_Design.md` — track list UI design and 26 requirements

### auto-marker-cleanup

Recorder-owned `AutoMarkerManager` for deterministic 🕐 IDLE_AUTO lifecycle; merged-marker keepability, ghost-pin fix, finalize fallback; startup cleanup scoped to crash orphans only.

#### Todos
- [ ] Deploy + E2E verify cleanup scenarios

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_auto-marker-cleanup.md` — cleanup hardening plan

### marker-track-nav

Deferred — cross-navigation between track detail and marker detail via `>` links, with a minimal `OverlayBackStack`.

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-track-nav.md` — plan

### marker-export-import

Track export hardening (unique names, Windows-safe sanitization) + import modes (single GPX Skip/Update/New, ZIP silent skip) shipped; marker export/import pending.

#### Docs
- `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-export-import.md` — plan

## Rules
- Feature-scoped plans go in `xTrack/[Feature]/YYMMDD_FEAT_PLN_[Feature]_[topic].md`.
- Track points only recorded while speed > 2.5 kn; OFF→ON via geofence exit (10s debounce) or manual Start.
- Internal storage: Protobuf binary (kotlinx-serialization-protobuf), not JSON. Export: GPX 1.1.
- Recording lifecycle: ON state persists through stationary; only point capture suspends via `isStill()`.
- 30s periodic checkpoint save; stats accumulated in-memory, written at finalize.
- Swipe-to-delete on TrackHistoryOverlay with snackbar undo.

## Docs
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_TrackList_Design.md` — track list UI requirements
- `xTrack/Tracks/FEAT_DOC_Tracks_decisions.md` — comprehensive decisions record (7 categories, 40+ decisions)
- `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-line-acquisition.md` — GPS point acquisition
- `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-background.md` — persistent foreground service
- `xTrack/Tracks/260622_FEAT_PLN_Tracks_spike-rejection-v2.md` — spike rejection v2
- `xTrack/Tracks/260618_FEAT_PLN_Tracks_adaptive-isstill.md` — adaptive stillness detection
- `xTrack/Tracks/260622_FEAT_PLN_Tracks_pinned-tracks.md` — pinned tracks
- `xTrack/Tracks/260717_FEAT_PLN_Tracks_tracks-paint-order.md` — tracks paint order
- `xTrack/Tracks/260911_FEAT_PLN_Tracks_live-track-paint-regression.md` — live-track paint regression: diagnosis + fix (evidence, fix options, follow-ups)
- `xTrack/Tracks/260911_FEAT_PLN_Tracks_resume-confirm-backup.md` — resume confirmation sheet + optional backup copy (decisions D1–D7, verified constraints, follow-ups)
- `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md` — selected-track speed heatmap: banded ramp, declared tick scale, persisted eye toggle, the count key removed and the scale foot (sections 16–19)

## Walk
**Level 1 — Date:** 2026-09-14 · **Source:** the 260914 selected-track speed heatmap plan's open points and implementation steps — the feature's older device-E2E todos are excluded, being a verification backlog rather than plan items · **Active:** 14
- [x] 1 · Arrow colour inside the banded line (D4a) — child walk closed: arrows take the local band colour
- [x] 2 · Scope and legend (D5) — child walk closed: scope is the selected track only, the live line untouched; legend carried to item 8
- [x] 3 · Confirm the owning feature is Tracks — child walk closed: a section of Tracks, not a new feature
- [x] 4 · Settings surface — child walk closed: no UI in this pass, the mode and ramp load from `maro.properties`
- [x] 5 · Band mapping as a pure, unit-tested function — child walk closed: `ui/map/TrackSpeedHeatmap.kt` plus its test
- [x] 6 · Wire banded appearances into the selected-track branch — child walk closed: one dispatcher, two self-contained paths
- [x] 7 · Keep arrows when enabled, spacing rules untouched — child walk closed: an optional colour resolver, spacing rules untouched
- [x] 8 · Legend, if item 2 keeps it — child walk closed: a compact map strip, only while heatmap mode has a selected track
- [x] 9 · Fold the review findings R1–R10 into the plan before any code — child walks closed: eye toggle in the detail header, session-wide mode
- [x] 10 · Fold the Ask-review findings A1–A14: queued fixes applied, A7, A9 and A10 decided
- [x] 11 · Fold the second Ask pass B1–B17 — carrier stated, legend anchored bottom-left, validation policy withdrawn
- [x] 12 · Implement the change on feature/track-speed — shipped: twelve tests green, apk-build SUCCESS, no high defect from the Ask hop
- [x] 13 · Remediate the Ask hop's findings — two resolved by removal, five parked by decision, five carried into the next hop (plan §17, §18)
- [ ] 14 · Device E2E on a real track — user-owned; the build is green and the run follows their call
- [x] 15 · Plan Outcome and feature-file pointers — Outcome extended, the Implemented entry added

**Level 2 — Date:** 2026-09-14 · **Parent:** 1 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Local band colour — chosen: each arrow takes the colour of the segment it sits on
- [ ] 2 · Constant contrast colour — dropped with the choice
- [ ] 3 · Under-stroke per arrow — dropped with the choice

- Resolutions: arrows carry the local band colour, reading as part of the speed profile. Dropped: the constant-contrast and per-arrow under-stroke alternatives; the first loses the arrow-as-speed cue, the second adds up to 2000 strokes on the overlay rebuild path.

**Level 2 — Date:** 2026-09-14 · **Parent:** 2 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Global mode over whichever track is selected — confirmed: the mode governs the selected track
- [x] 2 · Live recording line — excluded, no behaviour change there
- [ ] 3 · Legend — carried out to item 8 rather than resolved here

- Resolutions: heatmap mode governs the selected track only, the live recording path untouched. Dropped: extending the mode to the live line, which would need its own incremental banding rather than a reuse of the stored-track branch. Carried: the legend, now item 8.

**Level 2 — Date:** 2026-09-14 · **Parent:** 3 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A new section of Tracks — chosen: one owner for the selected-track render branch
- [ ] 2 · A new tracked feature — dropped with the choice

- Resolutions: the heatmap ships as a section of Tracks, keeping the render branch, its arrows and its settings under one owner. Dropped: a new tracked feature, which would have split one render path across two feature files.

**Level 2 — Date:** 2026-09-14 · **Parent:** 4 · **Active:** 3 · **Closed:** 2026-09-14
- [ ] 1 · One segmented row in the Tracks settings section — dropped: no settings UI in this pass
- [ ] 2 · Segmented row plus sliders for the 5 and 10 kn edges — dropped with the surface
- [x] 3 · No UI at all — chosen: the mode and the ramp are `maro.properties` keys

- Resolutions: no Settings surface in this pass — the selected-track rendering mode and the whole ramp are read from `maro.properties`, so changing them is a file edit plus a rebuild, and a `track.heatmap.mode` key carries the choice. Dropped: the segmented row and the band-edge sliders.

**Level 2 — Date:** 2026-09-14 · **Parent:** 5 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A new file `ui/map/TrackSpeedHeatmap.kt` — chosen, with its own unit test
- [ ] 2 · Beside `computeTrackPolylineAppearance` in `MapScreen.kt` — dropped with the choice

- Resolutions: the band mapping ships as `ui/map/TrackSpeedHeatmap.kt` with `TrackSpeedHeatmapTest`, mirroring `DepthColorRamp` and its own file, so `MapScreen.kt` does not grow. Dropped: placing it in `MapScreen.kt` beside the existing appearance factory.

**Level 2 — Date:** 2026-09-14 · **Parent:** 6 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One dispatcher plus two self-contained paths — chosen: no logic replicated, each path whole
- [ ] 2 · Inline the mode branch at both sites — dropped: replication rejected

- Resolutions: the selected track's appearance list comes from a single dispatcher that hands off to two self-contained path builders — today's gold casing-and-core pair, and the heatmap's casing plus banded core — so the mode is decided once and the branch is not copied to the two call sites. The effect's key list gains the mode and the ramp values so a rebuild with edited properties re-renders. Dropped: inlining the branch at both sites.

**Level 2 — Date:** 2026-09-14 · **Parent:** 7 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A colour resolver on the overlay — chosen: one chevron per anchor in its local band, casing once beneath
- [ ] 2 · One colour for every arrow — dropped: it contradicts the local-colour decision

- Resolutions: the overlay gains an optional resolver — null keeps today's appearance iteration, so every non-selected track draws exactly as now, while heatmap mode paints one chevron per anchor in that anchor's band with the dark casing chevron once beneath. Spacing is untouched: `spacingPxForSpeed` and the UNIFORM/SPEED density rules stay as they are. Dropped: one colour for every arrow.

**Level 2 — Date:** 2026-09-14 · **Parent:** 8 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · A compact map legend strip — chosen: ticks at 5, 10 and the top, only while a track is selected in heatmap mode
- [ ] 2 · No legend in this pass — dropped with the choice

- Resolutions: the legend ships as a compact map strip with ticks at 5, 10 and the window top, drawn only while heatmap mode is on and a track is selected. Dropped: documenting the thresholds in `maro.properties` alone — a ramp whose edges sit at 5 and 10 kn cannot be read without a visible scale.

**Level 2 — Date:** 2026-09-14 · **Parent:** 9 · **Active:** 2 · **Closed:** 2026-09-14
- [x] 1 · Restore one minimal control — chosen, but sited on the selected track's detail view, not in Settings
- [ ] 2 · Re-word the Request line — dropped: the control makes the original wording true again

- Resolutions: R1 closes with an eye toggle in the selected track's header — reviewed since to sit in `OverlayLayer`'s drawer header, left of the trash — moving one session-wide mode that defaults to `maro.properties`; item 4's decision stands untouched because the control is not a Settings surface. Dropped: re-wording the Request line.

**Level 2 — Date:** 2026-09-14 · **Parent:** 9 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · One session-wide mode — chosen: the eye moves the mode itself, the file key being the start-up default
- [ ] 2 · Per-track memory — dropped: one state is simpler and the legend explains a single ramp

**Level 2 — Date:** 2026-09-14 · **Parent:** 13 · **Active:** 1 · **Closed:** 2026-09-14
- [x] 1 · Crowd rule basis — resolved by removal: the label-drop rule goes, so the basis question is moot
- [ ] 2 · A NaN tick position parses through into a canvas offset
- [ ] 3 · The twelve-row cap is untested beside its tested family sibling
- [ ] 4 · The bar's top is free file text, so a bad last row shortens it silently
- [x] 5 · The label-drop priority — void: every row now prints its label, overlaps accepted
- [ ] 6 · The landscape inset diff is still unmeasured
- [ ] 7 · The assumeTrue guard has no IDE fallback and can skip silently
- [ ] 8 · The shipped file against section 16 — the tie, and whether it stays a gate
- [ ] 9 · The mode token: the file writes heatmap, the parser accepts only speed
- [ ] 10 · No marks inside the colour bar — the labels carry position alone
- [ ] 11 · The eye toggle persists, reversing section 5's no-persistence non-goal
- [ ] 12 · Remove the label-drop rule — every table row prints, overlaps accepted

- **Summary — closed 2026-09-14:** 1 and 5 resolved by removal, since §18 deletes the rule they were about; 2, 3, 4, 6 and 7 parked by decision, all of them unreachable without a malformed file and each recorded in §18's own list; 8, 9, 10, 11 and 12 carried into the next hop, recorded in plan §17 and §18.

- Resolutions: the eye toggle moves one session-wide mode, starting from `track.heatmap.mode`, with nothing persisted and no per-track state; the legend remains the readout of that single state. Dropped: per-track memory and per-track persistence. Folded with it: R2–R10 corrected the plan's body, so the band count, the ramp domain, the core alpha, the neutral tint, the carry window, the legend's file, the shared derivation, the z-lift cost, the seam rule and the verification criteria are now stated rather than implied.

## Implemented

- **Data model** — `Track`/`TrackPoint` protobuf, `TrackSummary` index, relative `timeOffsetSec`
- **Recorder** — OFF⇄ON state machine, geofence auto-detect, speed gate, orphan recovery
- **Persistence** — `TrackRepository` protobuf CRUD, 30s checkpoints, GPX 1.1 export
- **Map rendering** — active + history polylines, transparency/color gradient, overlay diff
- **Layer toggle** — `TrackLayerIcon` in FanLayout
- **TrackViewModel** — `StateFlow` bridge, LRU cache, sorted list
- **UI** — `TrackStatusIcon`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `LiveTrackCard`
- **Settings** — unified tracking section, HSV pickers, transparency semantics
- **Stop detection** — `AdaptiveGpsPolicy` position-only → `xTrack/Tracks/260618_FEAT_PLN_Tracks_adaptive-isstill.md`
- **Settings fix (2026-06-20)** — 6 tracking fields persisted, opacity naming fix
- **mtrack-setting-opacity (2026-06-20)** — transparency naming + inverted semantics
- **gps-line-acquisition (2026-06-20)** — removed `PASSIVE_PROVIDER` listener
- **gps-background (2026-06-20)** — foreground service rewrite → `xTrack/Tracks/260620_FEAT_PLN_Tracks_gps-background.md`
- **Demo track visibility (2026-06-21)** — off-by-one fix + `gpsMode` bypass
- **spike-rejection-v2 (2026-06-22)** — four-gate algorithm → `xTrack/Tracks/260622_FEAT_PLN_Tracks_spike-rejection-v2.md`
- **pinned-tracks (2026-06-22)** — pin icon + `pinned` proto field → `xTrack/Tracks/260622_FEAT_PLN_Tracks_pinned-tracks.md`
- **track-list-render-indicator (2026-06-24)** — `computeTrackPolylineAppearance()` shared utility
- **idle-time-tracking (2026-06-28)** — `idleDurationSec` accumulator
- **populate-track-info (2026-07-05)** — auto title/description from `whereAmI()`
- **resume-track (2026-07-12)** — resume finalized track as live recording
- **merge-tracks (2026-07-12)** — `TrackMerger` utility
- **checkmark-bottom-right (2026-07-14)** — badge position fix
- **notif-lifecycle-hardening (2026-07-14)** — tap-to-open, post-kill, recording-aware exit
- **tracks-paint-order (2026-07-17)** — newest-on-top + highlight-to-top → `xTrack/Tracks/260717_FEAT_PLN_Tracks_tracks-paint-order.md`
- **idle-reconciliation (2026-08-15)** — unified compound idle predicate
- **track-direction-arrows (2026-09-02)** — chevron overlay, density settings
- **marker-track-link** — `UserMarker.trackId` single back-reference + backfill + delete cascade → `xTrack/Tracks/260831_FEAT_PLN_Tracks_marker-track-link.md`
- **gps-recording-regression** — service GPS sampling pinned to Main dispatcher (Looper fix)
- **gps-switch-confirm** — confirm before switching position source while recording
- **live-track-paint-regression (2026-09-11)** — live polyline was never created after the C3/C4 seam extraction (creation effect read a frozen parameter through `snapshotFlow`); creation re-keyed on recorder state, append/trailing made self-healing, `isLive` excluded from the map-resolve path → `xTrack/Tracks/260911_FEAT_PLN_Tracks_live-track-paint-regression.md`
- **resume-confirm-backup (2026-09-11)** — resuming a stored track now asks first: `ResumeConfirmSheet` with a default-checked backup box; confirm writes a hidden, unpinned copy (fresh UUID, suffixed name, marker links stay on the original) then resumes the original; wired on the list card (early dismiss dropped) and both dashboard cards, gated by `isRecording` → `xTrack/Tracks/260911_FEAT_PLN_Tracks_resume-confirm-backup.md`
- **selected-track-speed-heatmap (2026-09-14)** — the selected track renders as a banded speed ramp in place of the gold highlight: `track.heatmap.familyN.*` colours on per-family draw steps, `track.heatmap.scaleTicks` positions printed with labels free to differ from them, nothing drawn inside the bar, and a drawer-header eye toggle whose choice persists with the key as the first-run default → `xTrack/Tracks/260914_FEAT_PLN_Tracks_selected-track-speed-heatmap.md`
