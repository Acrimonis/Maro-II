# Hydration: Ui_General

**Session:** Track Date Range filter extend+reorder — Ask review PASS, baked for commit.

1. **Track Date Range filter — extend+reorder** — `ListFilter.kt`: `dateInRange()`
   windows now 7/14/30/60/90/180 days (`LAST_7_DAYS`…`LAST_6_MONTHS`) with
   `else -> true` (ALL); the `THIS_YEAR` branch and `yearStartMs()` helper were deleted
   (`Calendar` import kept — still used by `todayMidnightMs`); `trackFilterAxes()`
   dateRange options ordered shortest→longest with `All` last and `isDefault=true`.
   Track History list and the menu drawer consume the same `trackFilterAxes()` spec;
   `FilterControl` selects default by `isDefault` flag, so All-last is safe. Build
   SUCCESS; Ask review PASS — matches plan, no deviations.
2. **Marker filter — Geometry axis removed** — `ListFilter.kt` `markerFilterAxes()`
   returns icon/pinned/origin only, origin ungated; dead code removed (prior batch).
3. **List count display** — Track History title "· N" + menu Tracks/Markers counts
   left of chevron, live excluded; committed as bfd4794 (prior batch).

**Next:** commit + push the Ui_General batch on `feature/list-counts`.

**Target files:**
- `data/model/ListFilter.kt` (date-range windows/options; marker filter axes)

**Plans:**
- `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_track-filter-date-range.md` (status: Implemented)
- `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_marker-filter-remove-geometry.md` (status: Implemented)
- `xTrack/Ui_General/260907_FEAT_PLN_Ui_General_list-count-display.md` (status: Implemented)

**Last Bake:** 2026-09-07 15:37 UTC
