---
name: Tracks
status: active
created: 2026-06-15 21:43
modified: 2026-09-11 16:15
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

### marker-track-nav (DEFERRED)

Cross-navigation between track detail and marker detail via `>` links, with a minimal `OverlayBackStack`.

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
