# Context Hydration — Markers — 2026-10-12

**Last Bake:** 2026-10-12 07:12 UTC — written by `#bake`. This session grouped the marker filter popup's three routing axes under one `Routing` title with each keeping its own card, then folded the review's two documentation findings on the user's word.

**Directive trace:** All five covered action classes were met and none stopped the session — no dependency was added, no machine-shaped data file was opened, the work ran only on the user's own `#focus` and `#impl` rather than without an order, no device was touched by the agent (the device pass stays the user's), and every claim written here about the code carries a file read behind it, save the Code hop's own build and test results, which are carried forward as its report rather than as a read of this session's.

## State

**The three routing filters now share one title (2026-10-12).** The marker filter popup's three routing axes stand under one `Routing` heading while each keeps its own card of options. No field joined `FilterAxisSpec`: the three axes in `markerFilterAxes()` carry the one `filter_group_routing` key and `FilterControl` draws a `PopupSectionTitle` only when the key differs from the one drawn last, so a repeated key is one group. The order became role, cost, Only — the title rides the first of the three — the role axis' negative option was revalued from `No route role` to `None` / `Aucun`, and `filter_axis_route_cost`, `filter_axis_route_role` and `filter_axis_routing_only` were retired from both locales.

**Presentational only.** `UserMarker.matchesFilter`, the `ListFilter` persistence, `SettingsManager`, the map filter, the wizard's Routing cost step, the card marks and `maro.properties` are all untouched, `MarkerFilterMigrationTest` is unchanged, and the track, route and menu popups render exactly as before. Both marker surfaces — the list overlay and the menu drawer's MARKERS section — read the one axis spec, so they took the change together.

**The review.** Verdict ship, no blocker. Its three findings: the shared-title rule had been stated twice under `docs/` and now has one home in `ui-component-guidelines.md` §2.10, whose sentence points at the `FilterControl` that implements it; and a one-assertion test is owed, because nothing pins `markerFilterAxes()` and a future axis reusing a neighbour's key would merge silently — the trade-off the plan accepted and the review pushed back on. The two documentation points were folded in on the user's word; the test is not written.

**The record.** `apk-build.bat` BUILD SUCCESSFUL with the marker suites green, per the Code hop's report. The plan of record is `261012_FEAT_PLN_Markers_routing-filter-group.md`, the feature file carries the shipped entry and its pointer, and this work carries no branch of its own.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the three routing axes reordered and sharing `filter_group_routing`
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `FilterControl`'s title dedup, the rule's code-local home
- `app/src/main/res/values/strings.xml` · `app/src/main/res/values-fr/strings.xml` — `filter_group_routing` added, `filter_option_no_route_role` revalued, the three axis keys retired
- `docs/ui-lists-guidelines.md` — the six-row marker axes table, its shared-title fact trimmed to a pointer
- `docs/ui-component-guidelines.md` — §2.10's shared-title rule, now pointing at `FilterControl`
- `xTrack/Markers/261012_FEAT_PLN_Markers_routing-filter-group.md` — the plan of record and its shipped record
- `xTrack/Markers/FEAT_DSC_Markers.md` — the shipped entry and the plan pointer

## Next Step

The device pass is the user's: the three cards under the one `Routing` title in the list overlay and in the menu drawer, in both locales, and whether three cards each leading with an "All" row read clearly with no per-card title. The one open code item is the owed assertion pinning `markerFilterAxes()`.
