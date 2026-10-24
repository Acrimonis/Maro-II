# Context Hydration — Route — 2026-10-24

**Last Bake:** 2026-10-24 05:47 UTC — written by `#bake` on `feature/route-card-hilight`; the route-tweeks bake of 2026-10-23 14:47 stands merged beside it.

**Directive trace:** no dependency was added, no binary file opened and the device never touched; the session started on the user's `#new route-card-hilight`, then `#impl` ran the Code → Ask → Architect pipeline, every write was code, docs and the xTrack files, each claim about the code had a read behind it, and the one `ask` review hop ran under `#implement`; none stopped.

## State

The routes-list card's leading accent bar is **dashed**, the track card's **solid** — the dash reuses the map's own saved-route rhythm (`AppConfig.trackRouteDashOnDp` / `trackRouteDashOffDp`), so the list card mirrors the map's route-dashed / track-solid language instead of inventing a second code. `ListSelectionRail` gained a `dashed` parameter (default `false`); `TrackCardContent` passes `dashed = summary.route`, leaving the marker card's rail and every track card on the solid strip. `gradlew assembleDebug` is green; the review hop approved scope and code health, its one stale-comment note fixed.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt` — the `dashed` parameter and the `drawLine` + `PathEffect.dashPathEffect` branch
- `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt` — `TrackCardContent` passes `dashed = summary.route`; the door-bar comment updated
- `docs/ui-drawer-guidelines.md` §9, `docs/ui-lists-guidelines.md` §Selection door — the dashed-route / solid-track bar shape recorded
- `xTrack/Route/261024_FEAT_PLN_Route_route-card-left-bar-dashed.md` — the plan
- `xTrack/Route/FEAT_DSC_Route.md`, `xTrack/GLOBAL_CONTEXT.md` — the present-tense bullet and the Route summary updated

## Next Step

The visual pass on the device, the user's own: open the routes list beside the tracks list and read the dashed bar against the solid one.
