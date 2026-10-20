# Context Hydration — Ui_Menu — 2026-10-20

**Last Bake:** 2026-10-20 19:40 UTC — written by `#bake`; absence means never baked

**Directive trace:** no covered action stopped. The route-list filter removal ran on an explicit order; no dependency was added, no machine-shaped data file was opened, the device was never touched, and every claim rests on a file read. The only git writes are the user's `#commit` and `#push` that close the session.

## State

The routes list no longer carries an inline **All · Pinned · Not pinned** row: `RoutesPinnedFilterRow` and its `quickFilterSlot` wiring left `TrackHistoryOverlay.kt`, and the scaffold's now-callerless `quickFilterSlot` hook and render block left `ListOverlayScaffold.kt`. The `pinned` axis stays in `ListFilter`, so the choice lives only in the filter popup. `:app:compileDebugKotlin` passes and the change is committed `71beb9f7`; the drawer's earlier Go button (2026-10-18) is unchanged.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — the routes list scaffold call
- `app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt` — the removed `quickFilterSlot`

## Next Step

The device pass is the user's: open the routes list and confirm no inline pin filter remains while the funnel still offers All/Pinned/Unpinned.
