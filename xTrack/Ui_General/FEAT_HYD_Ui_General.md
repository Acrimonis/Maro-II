# Hydration: Ui_General

**Session:** Filters-link — List/Map filter referential decoupling. IMPLEMENTED and committed on
`feature/filters-link` (Ask reviewed, builds SUCCESS).

## State
- **Filters-link decoupling — Implemented.** Two filter referentials per type (List and Map), linked by
  default, with a Link/LinkOff toggle in the menu. The menu filter drives the Map referential; the list
  headers drive the List referential; linked edits write both, unlinked writes only its own, re-link from
  the menu converges list to map. Map rendering applies the Map filter over the unfiltered track set;
  marker map overlay uses the map-filtered world; reveal-on-select force-draws a from-list item while its
  panel is open; map-tapped markers open from the map world (`DrawerSource.MAP`, clamp nav); menu counters
  reflect map items to be rendered; whereAmI overrides the map filter.
- Settings use Option B: prefs versioning removed; `trackMapFilter`/`markerMapFilter`/
  `trackFilterLinked`/`markerFilterLinked` keys default (Map = All, link = ON); defunct legacy keys
  cleaned idempotently.
- MapScreen code health step 1 done under Ui_Settings: Settings overlay subtree extracted to
  `MapScreenSettingsOverlay.kt` (MapScreen 5841 → 3417 lines).

## Commits (feature/filters-link)
b4693df foundation · 123b8b2 bake · b71a230 settings extraction · c79ddff bake · de2f013 wiring ·
48bb560 marker map-tap MAP world.

## Plans
- `xTrack/Ui_General/260909_FEAT_PLN_Ui_General_filters-link-decoupling.md` (status: Implemented)

## Next
Optional residuals none; queued: MapScreen orchestration-monolith refactor (code health step 2, after this
feature lands via PR).

**Last Bake:** 2026-09-09 12:18 UTC
