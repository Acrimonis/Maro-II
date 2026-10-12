# Context Hydration — Markers — 2026-10-12

**Last Bake:** 2026-10-12 07:53 UTC — written by `#bake`. Two passes landed today: the marker filter popup grouped its three routing axes under one `Routing` title, and then all 26 of the feature's landed plans were retired into `xxArchive/` with a promotion pass first.

**Directive trace:** All five covered action classes were met and none stopped the session — no dependency was added, no machine-shaped data file was opened, the work ran only on the user's own `#focus`, `#impl`, `#archive`, `#bake`, `#commit` and `#push` rather than without an order, no device was touched by the agent (the device pass stays the user's), and every claim written here about the code carries a file read behind it, save the Code hops' build and test results, which are carried forward as their own reports rather than as reads of this session's.

## State

**The three routing filters now share one title (2026-10-12).** The marker filter popup's three routing axes stand under one `Routing` heading while each keeps its own card. No field joined `FilterAxisSpec`: the three axes carry the one `filter_group_routing` key and `FilterControl` draws a `PopupSectionTitle` only when the key differs from the one drawn last, so a repeated key is one group. The order is role, cost, Only, the role axis' negative option reads `None` / `Aucun`, and `filter_axis_route_cost`, `filter_axis_route_role` and `filter_axis_routing_only` retired from both locales. Presentational only — `matchesFilter`, the persistence, the map filter, the wizard, the card marks and `maro.properties` are untouched, and the track, route and menu popups render as before.

**The archive swept clean (2026-10-12).** All 26 landed plans are retired into `xTrack/Markers/xxArchive/` with one row each in its `INDEX.md`, and none was refused — no plan in the set held an open `## Walk`. The promotion pass ran first, as ordered: 28 rules the shipped code still obeys now live in the feature's new `## Rules` section, and six decision groups with their rationale joined `FEAT_DOC_Markers_decisions.md`. Fourteen Outcomes were written without knowing what shipped and say so in one line; the other seven were written from the plans' own evidence. The three superseded decisions in the decisions doc were reviewed and validated by the user, and the doc's header brought current.

**What is owed.** The device pass is the user's: the three cards under the one `Routing` title in the list overlay and in the menu drawer, in both locales, and whether three cards each leading with an "All" row read clearly with no per-card title. One code item stayed open from the review — nothing pins `markerFilterAxes()`, so a future axis reusing a neighbour's `labelResId` would merge silently. And the sweep exposed one record gap: about eleven live plans still in `xTrack/Markers/` carry no `## Docs` row.

## Target Files

- `app/src/main/java/ykws/android/maro/data/model/ListFilter.kt` — the three routing axes reordered and sharing `filter_group_routing`
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — `FilterControl`'s title dedup, the rule's code-local home
- `app/src/main/res/values/strings.xml` · `app/src/main/res/values-fr/strings.xml` — `filter_group_routing` added, `filter_option_no_route_role` revalued, the three axis keys retired
- `docs/ui-lists-guidelines.md` · `docs/ui-component-guidelines.md` — the six-row marker axes table, and §2.10's shared-title rule pointing at `FilterControl`
- `xTrack/Markers/FEAT_DSC_Markers.md` — the shipped entry, the new `## Rules` section, the pointer drops and the `## Docs` detachments
- `xTrack/Markers/FEAT_DOC_Markers_decisions.md` — the promoted decisions and the validated supersessions
- `xTrack/Markers/xxArchive/` — the 26 retired plans and their `INDEX.md`

## Next Step

The device pass is the user's, over the three cards under the one `Routing` title in both surfaces and both locales. The one open code item is the owed assertion pinning `markerFilterAxes()`, and the one open record item is the live plans in `xTrack/Markers/` that no `## Docs` row points at.
