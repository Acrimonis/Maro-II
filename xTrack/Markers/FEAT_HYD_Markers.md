# Markers — Hydration Snapshot (2026-07-01 10:45 UTC)

## State
- **Active subfeature:** 2-gate-simplification
- **Status:** implemented — build ✅
- **Branch:** feature/markers-zones
- **Build:** assembleDebug ✅
- **15/15 subfeatures complete**

## Implemented (this session)

### 2-gate-simplification — proximity gate removed, pre-filter restored
- `ProximityMatch` → `LineOfSightMatch` rename (MarkerMatcher, MarkerOverlay, MarkerDrawer)
- `proximityRange()` now reads `proximityOverrideM` directly — no formula defaults in matching
- Proximity gate removed for circles/corridors: line-of-sight is sole pass/fail
- Range pre-filter restored: `minBoundaryDist > proximityOverrideM` → skip (optimization only)
- `ProximityConfig` removed (dead code)

### Tangent coverage fix
- `sampleBoundaryPoints()` generates points from zone center via `pointAlongBearing` — never ray-intersection
- Circle: 16 points on visible arc (`90°−asin(r/d)` half-arc); tangents guaranteed at samples 0/15
- Corridor: 16 points per end-cap (full 360°); edge sampling unchanged
- `circlePointAtBearing` removed (ray-intersection dropped tangent samples due to `disc < 0`)

### Docs updated
- `#doc update` — FEAT_DSC_Markers.md and FEAT_DOC_Markers_decisions.md updated
- Flow diagram at plans/whereami-flow.md

### sort-scoring
- Composite `sortScore()`: `typeWeight × (zoneSize + distance × W)` replacing `sizeOf()`
- Pin=0.15, Circle=1.0, Corridor=2.0, W=3.0 — configurable in `maro.properties`
- Children-before-parents preserved (depth-first)

### Files changed
| File | Change |
|------|--------|
| `maro.properties` | 4 sort keys |
| `AppConfig.kt` | 4 properties + loading |
| `MarkerMatcher.kt` | direct-line fast path, Pin land, NPE fix, bearing, debug cleanup, edge sampling, sortScore() |
| `MarkersViewModel.kt` | coroutine cancellation |

## Implemented (this session)

6 fixes applied across 2 files — see [`FEAT_PLN_Markers_whereami-fixes.md`](xTrack/Markers/FEAT_PLN_Markers_whereami-fixes.md) for full plan:

| Fix | Description | File |
|-----|-------------|------|
| Direct-line fast path | Skip angular analysis when closest boundary point has clear LOS | `MarkerMatcher.kt` |
| Pin land respect | `bestBoundaryPoint` checks unblocked intervals (was: always returns position) | `MarkerMatcher.kt` |
| NPE safety | `proximityOverrideM ?: defaultFormula()` | `MarkerMatcher.kt` |
| Bearing direction | `initialBearing(boat, x)` (was reversed: marker→boat) | `MarkerMatcher.kt` |
| Debug cleanup | Removed hardcoded Sainte Marguerite dump + bearing gate | `MarkerMatcher.kt` |
| Coroutine cancel | `whereAmIJob?.cancel()` on rapid taps | `MarkersViewModel.kt` |
| Edge sampling | 5 bearings per interval instead of midpoint | `MarkerMatcher.kt` |

## Known Issues

### 🟡 Angular shadow fallback may still over-block
The angular algorithm still runs when the closest boundary point IS blocked by land. Fix 4 only addresses the case where closest point is clear. If there's a marker behind an island where ALL boundary points are blocked, this is correct behavior. But if the closest arc is behind a cape but a farther arc is reachable, angular analysis may still over-block.

### 🟡 Pending on-device validation
- Sainte Marguerite corridor: verify 4/4 test clicks now match (was 2/4)
- Bearing display: verify "NW of MarkerName" shows correct direction
- Pin behind island: verify pin no longer matches when land blocks
- Rapid tap: verify no UI flicker

### 🟢 Deferred
- Component cones (Fix 7) — performance optimization, not correctness

### 🟡 StateFlow double-initialization race (low impact)
**Location:** [`MarkersViewModel.kt:123-136`](app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt:123).

`markerLayerState` and `userMarkersVisible` StateFlows read `flow.value` eagerly then mirror via collection. Race if SettingsManager hasn't loaded preferences yet — transient UI flicker at worst.

### 🟢 Duplicate imports in MarkerDrawer.kt (cosmetic)
**Location:** [`MarkerDrawer.kt:42-48`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:42).

`mutableStateOf`, `remember`, `setValue` imported twice. No functional impact — compile warning only.

## Unimplemented Plans (verified against source)

| Plan | Item | Status |
|------|------|--------|
| `icon-fixes.md` §5b/6b | Timestamp display on drawer + management | ❌ Not implemented |
| `icon-fixes.md` §6c | `markerFormatText` format unification between files | ❌ Duplicated in both files |
| `area-tap-and-wizard-buttons.md` §3 | Marker color settings in Settings page | ❌ Colors hardcoded in MarkerOverlay |
| `next-session-ui-polish.md` §8 | List item text format (Pin/Circle/Corridor) | ❌ Current format: `📌 0 200` not `Pin / 200m proximity` |

## Key Files
- `app/src/main/java/ykws/android/maro/data/model/markers/UserMarker.kt`
- `app/src/main/java/ykws/android/maro/data/markers/UserMarkerRepository.kt`
- `app/src/main/java/ykws/android/maro/spatial/MarkerMatcher.kt`
- `app/src/main/java/ykws/android/maro/spatial/CoastlineSpatialIndex.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt`

## Design Docs
- `xTrack/Markers/FEAT_DOC_Markers_decisions.md` — all architectural decisions
- `xTrack/Markers/FEAT_PLN_Markers_user-markers-design.md` — original design (all phases ✅)
- `xTrack/Markers/FEAT_PLN_Markers_create-zones-flow.md` — wizard creation flow
- `xTrack/Markers/FEAT_PLN_Markers_whereami-rework.md` — whereAmI rework
- `xTrack/Markers/FEAT_PLN_Markers_whereami-gaps.md` — current bug analysis
- `xTrack/Markers/FEAT_PLN_Markers_debug-wia.md` — debug instrumentation
- `xTrack/Markers/FEAT_PLN_Markers_icon.md` — POI icon system
- `xTrack/Markers/FEAT_PLN_Markers_icon-fixes.md` — icon bug-fix plan (partial)
- `xTrack/Markers/FEAT_PLN_Markers_marker-pin.md` — pin toggle
- `xTrack/Markers/FEAT_PLN_Markers_marker-pin-tri-state.md` — tri-state layer toggle
- `xTrack/Markers/FEAT_PLN_Markers_area-tap-and-wizard-buttons.md` — area tap + buttons + caps + color settings
- `xTrack/Markers/FEAT_PLN_Markers_next-session-ui-polish.md` — UI polish plan
