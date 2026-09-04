---
name: BoatTrace
status: active
created: 2026-06-15 21:43
modified: 2026-09-02 13:10
---

# Feature: BoatTrace

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

#### Docs
- `plans/boat-trace-design-discussion.md` — full design & implementation plan

### track-list

#### Todos
- [ ] Review and refine track card layout per design spec
- [ ] Verify swipe-to-delete, inline snackbar, undo animations
- [ ] Verify inline editing (auto-focus, field-switch commit, back-to-revert)
- [ ] Verify human-readable formatting (comma decimal, durations)
- [ ] Verify compact padding and flush-left stats grid
- [ ] E2E: Create test tracks, verify all card fields display correctly

#### Rules
- Track list UI must follow 260618_FEAT_PLN_BoatTrace_TrackList_Design.md spec
- Styling must match Settings overlay patterns (AppConfig tokens)

#### Key Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

#### Docs
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI design and 26 requirements

### auto-marker-cleanup

Recorder-owned `AutoMarkerManager` for deterministic 🕐 IDLE_AUTO lifecycle; merged-marker keepability, ghost-pin fix, finalize fallback; startup cleanup scoped to crash orphans only.

#### Todos
- [ ] Deploy + E2E verify cleanup scenarios

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_auto-marker-cleanup.md` — cleanup hardening plan

### marker-track-nav (DEFERRED)

Cross-navigation between track detail and marker detail via `>` links, with a minimal `OverlayBackStack`.

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-track-nav.md` — plan

### marker-export-import

Track export hardening (unique names, Windows-safe sanitization) + import modes (single GPX Skip/Update/New, ZIP silent skip) shipped; marker export/import pending.

#### Docs
- `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-export-import.md` — plan

## Rules
- Feature-scoped plans go in `xTrack/[Feature]/FEAT_PLN_[Feature]_[topic].md`, NOT in `plans/`.
- Track points only recorded while speed > 2.5 kn; OFF→ON via geofence exit (10s debounce) or manual Start.
- Internal storage: Protobuf binary (kotlinx-serialization-protobuf), not JSON. Export: GPX 1.1.
- Recording lifecycle: ON state persists through stationary; only point capture suspends via `isStill()`.
- 30s periodic checkpoint save; stats accumulated in-memory, written at finalize.
- Swipe-to-delete on TrackHistoryOverlay with snackbar undo.

## Docs
- `plans/boat-trace-design-discussion.md` — final design and implementation plan
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_TrackList_Design.md` — track list UI requirements
- `xTrack/BoatTrace/FEAT_DOC_BoatTrace_decisions.md` — comprehensive decisions record (7 categories, 40+ decisions)
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-line-acquisition.md` — GPS point acquisition
- `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-background.md` — persistent foreground service
- `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_spike-rejection-v2.md` — spike rejection v2
- `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill.md` — adaptive stillness detection
- `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_pinned-tracks.md` — pinned tracks
- `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md` — tracks paint order

## Implemented

- **Data model** — `Track`/`TrackPoint` protobuf, `TrackSummary` index, relative `timeOffsetSec`
- **Recorder** — OFF⇄ON state machine, geofence auto-detect, speed gate, orphan recovery
- **Persistence** — `TrackRepository` protobuf CRUD, 30s checkpoints, GPX 1.1 export
- **Map rendering** — active + history polylines, transparency/color gradient, overlay diff
- **Layer toggle** — `TrackLayerIcon` in FanLayout
- **TrackViewModel** — `StateFlow` bridge, LRU cache, sorted list
- **UI** — `TrackStatusIcon`, `MenuDrawerOverlay`, `TrackHistoryOverlay`, `LiveTrackCard`
- **Settings** — unified tracking section, HSV pickers, transparency semantics
- **Stop detection** — `AdaptiveGpsPolicy` position-only → `xTrack/BoatTrace/260618_FEAT_PLN_BoatTrace_adaptive-isstill.md`
- **Settings fix (2026-06-20)** — 6 tracking fields persisted, opacity naming fix
- **mtrack-setting-opacity (2026-06-20)** — transparency naming + inverted semantics
- **gps-line-acquisition (2026-06-20)** — removed `PASSIVE_PROVIDER` listener
- **gps-background (2026-06-20)** — foreground service rewrite → `xTrack/BoatTrace/260620_FEAT_PLN_BoatTrace_gps-background.md`
- **Demo track visibility (2026-06-21)** — off-by-one fix + `gpsMode` bypass
- **spike-rejection-v2 (2026-06-22)** — four-gate algorithm → `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_spike-rejection-v2.md`
- **pinned-tracks (2026-06-22)** — pin icon + `pinned` proto field → `xTrack/BoatTrace/260622_FEAT_PLN_BoatTrace_pinned-tracks.md`
- **track-list-render-indicator (2026-06-24)** — `computeTrackPolylineAppearance()` shared utility
- **idle-time-tracking (2026-06-28)** — `idleDurationSec` accumulator
- **populate-track-info (2026-07-05)** — auto title/description from `whereAmI()`
- **resume-track (2026-07-12)** — resume finalized track as live recording
- **merge-tracks (2026-07-12)** — `TrackMerger` utility
- **checkmark-bottom-right (2026-07-14)** — badge position fix
- **notif-lifecycle-hardening (2026-07-14)** — tap-to-open, post-kill, recording-aware exit
- **tracks-paint-order (2026-07-17)** — newest-on-top + highlight-to-top → `xTrack/BoatTrace/260717_FEAT_PLN_BoatTrace_tracks-paint-order.md`
- **idle-reconciliation (2026-08-15)** — unified compound idle predicate
- **track-direction-arrows (2026-09-02)** — chevron overlay, density settings
- **marker-track-link** — `UserMarker.trackId` single back-reference + backfill + delete cascade → `xTrack/BoatTrace/260831_FEAT_PLN_BoatTrace_marker-track-link.md`
- **gps-recording-regression** — service GPS sampling pinned to Main dispatcher (Looper fix)
- **gps-switch-confirm** — confirm before switching position source while recording
