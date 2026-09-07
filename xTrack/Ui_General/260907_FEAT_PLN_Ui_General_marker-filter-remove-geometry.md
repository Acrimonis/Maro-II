# Plan: Remove "Geometry" axis from the Markers filter

**Feature:** Ui_General (owns the lists/filter domain)
**Date:** 2026-09-07
**Status:** Implemented (2026-09-07)

> **Ask review (2026-09-07):** Approved. One correction folded in — the §3 note
> about the `MarkerGeometry` import was wrong: `originMatches()` uses
> `MarkerOrigin`, so the `MarkerGeometry` import (`ListFilter.kt:3`) becomes
> unused once the geometry branch + `geometryMatches()` are removed and MUST be
> deleted. All other refs/semantics verified.
> Implementation review (2026-09-07): PASS — matches plan exactly; only deviation
> is a benign KDoc cleanup on `markerFilterAxes()`; build SUCCESS.

## Objective

Remove the "Geometry" filter axis (Pins / Circles / Corridors) from the markers
filter. It has no added value — the icon, pinned, and origin axes already cover
the useful filtering dimensions. The markers **list** filter is the target; no
data-model, map-rendering, or track-filter change.

## Confirmed Requirements (from discussion)

- Remove the `geometry` axis from the markers filter **spec** (UI dropdown).
- Keep the remaining marker axes: `icon`, `pinned`, `origin`.
- **No prefs migration** — stale persisted `geometry=*` entries become harmless
  no-ops and are cleared on any filter reset. (Explicit user decision.)
- **Scope lock:** do NOT touch the track filter, marker data model, map overlay
  filtering logic, or string resources (labels are inline in the axis spec).

## Changes

### 1. `ListFilter.kt` — `markerFilterAxes()`

File: `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt`

- Delete the `geometry` `FilterAxisSpec` block (currently lines ~177–186).
- The `origin` axis currently carries `dependsOn = "geometry"` +
  `dependsOnValues = listOf("CIRCLES", "CORRIDORS")` (lines ~195–196). With the
  gating axis removed this is a dangling reference → remove both fields so
  Origin (Manual / Auto) always applies.

### 2. `ListFilter.kt` — `UserMarker.matchesFilter()`

- Remove the `"geometry"` branch (line ~91).
- Simplify the `"origin"` branch: drop the special case that reads
  `f.axes["geometry"] in setOf("ZONES", "CIRCLES", "CORRIDORS")` (line ~92) —
  with no geometry axis it can never fire. Keep plain `originMatches`:
  ```kotlin
  "origin" -> value == "ALL" || originMatches(this.origin, value)
  ```

### 3. `ListFilter.kt` — dead code + unused import

- Delete `geometryMatches()` (lines ~98–104) — no remaining callers.
- Delete the now-unused `import ykws.android.maro.data.model.markers.MarkerGeometry`
  (line 3) — `originMatches()` uses `MarkerOrigin`, not `MarkerGeometry`, so this
  import becomes dead once the geometry branch and `geometryMatches()` are gone.
  (Ask review correction.)

## Out of Scope

- No change to `trackFilterAxes()`, `ListFilter.parse/format`, or serialization.
- No change to `MarkerFilterMigrationTest.kt` (its v7 assertions only verify the
  icon migration preserves an unrelated geometry key; they still pass unchanged).
- No prefs migration / settings version bump.
- No string resources (axis labels are inline literals).

## Verification

- Build via `apk-build.bat` (assembleDebug).
- Manual: open Marker management list filter → "Geometry" dropdown is gone;
  "Icon" / "Pinned" / "Origin" still present and functional; filtering by
  Origin Manual/Auto still works; no crash when a stale persisted filter with a
  `geometry=*` key is present (it simply does not filter; reset clears it).

## Implemented

- `ListFilter.kt` — geometry `FilterAxisSpec` removed from `markerFilterAxes()`
  (now only icon/pinned/origin); origin axis `dependsOn`/`dependsOnValues`
  dangling reference removed → Origin always applies.
- `ListFilter.kt` — `UserMarker.matchesFilter()`: geometry branch removed; origin
  branch simplified to `value == "ALL" || originMatches(this.origin, value)`.
- `ListFilter.kt` — dead `geometryMatches()` + unused `MarkerGeometry` import
  deleted. KDoc on `markerFilterAxes()` corrected (doc-only).
- Build: SUCCESS (`apk-build.bat`, assembleDebug). Ask review PASS.
- Manual verification pending on-device (geometry dropdown gone; icon/pinned/
  origin functional).
