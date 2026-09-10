# Plan: Tracks date-range filter — extended + reordered options

**Feature:** Ui_General (owns the lists/filter domain)
**Date:** 2026-09-07
**Status:** Implemented (2026-09-07)

> **Ask review (2026-09-07):** Approved as-is — no corrections. Line refs and
> semantics verified: THIS_YEAR/yearStartMs have no other consumers; Calendar
> import must stay (todayMidnightMs); zero test/string-resource hits; FilterControl
> selects default by isDefault flag (not position), so All-last is safe.

## Objective

Rework the **tracks** "Date Range" filter options: drop "This Year", extend the
range list, and order the options as specified (shortest → longest → All last).

## Confirmed Requirements (from discussion)

New option order and semantics (fixed day-count windows — consistent with the
existing code style):

| Label | Window | Key |
|-------|--------|-----|
| Last week | 7 days | `LAST_7_DAYS` (reuse) |
| Last 2 weeks | 14 days | `LAST_14_DAYS` (new) |
| Last month | 30 days | `LAST_30_DAYS` (reuse) |
| Last 2 month | 60 days | `LAST_2_MONTHS` (new) |
| Last 3 month | 90 days | `LAST_3_MONTHS` (new) |
| Last 6 month | 180 days | `LAST_6_MONTHS` (new) |
| All | — (no filter) | `ALL` (default; moved to last position) |

- "Last 90 days" from the initial draft was replaced by "Last 2 month" (60 days)
  per user instruction — final list above is authoritative.
- `All` remains the default (no filter) but is **listed last** in the dropdown.
- The live/recording track stays exempt from the date filter (existing `isLive`
  rule) — unchanged.
- **Scope lock:** tracks filter only. No marker-filter change (marker axes are
  icon/pinned/origin — no date range there anyway). No prefs migration (stale
  persisted `THIS_YEAR` key hits `else -> true` in `dateInRange` = no-op).

## Changes — single file: `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt`

### 1. `dateInRange()` (~lines 58–64)

Replace the window mapping:

```kotlin
fun dateInRange(startTimeMs: Long, range: String, todayMidnightMs: Long): Boolean = when (range) {
    "LAST_7_DAYS" -> startTimeMs >= todayMidnightMs - 7 * 86_400_000L
    "LAST_14_DAYS" -> startTimeMs >= todayMidnightMs - 14 * 86_400_000L
    "LAST_30_DAYS" -> startTimeMs >= todayMidnightMs - 30 * 86_400_000L
    "LAST_2_MONTHS" -> startTimeMs >= todayMidnightMs - 60 * 86_400_000L
    "LAST_3_MONTHS" -> startTimeMs >= todayMidnightMs - 90 * 86_400_000L
    "LAST_6_MONTHS" -> startTimeMs >= todayMidnightMs - 180 * 86_400_000L
    else -> true // ALL
}
```

- `THIS_YEAR` branch and `yearStartMs()` helper (lines ~50–56) become unused →
  delete `yearStartMs()`. Confirm no other caller (search: only used by
  `dateInRange`). `Calendar` import stays (still used by `todayMidnightMs()`).

### 2. `trackFilterAxes()` dateRange options (~lines 128–133)

Replace the options list with the ordered labels above (`All` last, still
`isDefault = true`):

```kotlin
options = listOf(
    FilterOptionSpec("LAST_7_DAYS", "Last week"),
    FilterOptionSpec("LAST_14_DAYS", "Last 2 weeks"),
    FilterOptionSpec("LAST_30_DAYS", "Last month"),
    FilterOptionSpec("LAST_2_MONTHS", "Last 2 month"),
    FilterOptionSpec("LAST_3_MONTHS", "Last 3 month"),
    FilterOptionSpec("LAST_6_MONTHS", "Last 6 month"),
    FilterOptionSpec("ALL", "All", isDefault = true)
)
```

## Why the change surface is small

- `trackFilterAxes()` is the single spec driving the Track History list
  ([`TrackHistoryOverlay.kt:368`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:368))
  and the menu drawer filter
  ([`OverlayLayer.kt:335`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:335)).
  Both consume it as data; `FilterControl` renders whatever options it receives
  ([`ListOverlayScaffold.kt:259`](app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:259)).
- The map filter uses the same `trackListFilter` + `matchesFilter` → consistent
  automatically.
- No tests reference date-range options/windows (verified: zero hits in
  `app/src/test`). No string resources involved (labels are inline literals in
  the axis spec; verified zero hits in `res/values/strings.xml`).

## Out of Scope

- No change to marker filter, `ListFilter.parse/format`, sort state, prefs /
  settings version, or `FilterControl` component itself.
- No localization resources added (consistent with existing inline labels).
- `All` default behavior unchanged (clears the axis via `isDefault` handling in
  `FilterControl`).

## Verification

- Build via `apk-build.bat` (assembleDebug).
- Manual: open Track History filter → Date Range shows the 7 options in order
  (Last week … All last); selecting each range filters correctly (14/60/90/180-day
  windows especially); no filter = All; live/recording track always visible.

## Outcome

Implemented 2026-09-07 in `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt`
(single file): `dateInRange()` windows 7/14/30/60/90/180 days + `else -> true` (ALL);
`THIS_YEAR` branch and `yearStartMs()` deleted, `Calendar` import kept for
`todayMidnightMs`; `trackFilterAxes()` dateRange options ordered shortest→longest with
`All` last + `isDefault=true`. Marker axes untouched; TrackHistoryOverlay and menu drawer
consume the same spec. Build SUCCESS via `apk-build.bat`; Ask review PASS — matches plan,
no deviations.
