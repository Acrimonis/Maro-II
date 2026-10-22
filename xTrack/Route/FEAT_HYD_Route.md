# Context Hydration — Route — 2026-10-22

**Last Bake:** 2026-10-22 16:17 UTC — written by `#bake` on `feature/route-dash`.

**Directive trace:** none of the five covered action classes was met since the last bake — no dependency added, no machine-shaped spatial file opened, the work was ordered, the device was untouched, and every claim came from a file read.

## State

The route search panel's selection-resize jump is fixed. The old height freeze is removed: the panel body animates its height over 250 ms (`animateContentSize(tween(250))`), the map band only grows while the search runs and settles when it ends (`MapScreen.kt`), and while the pager scrolls the body is floored at its rest height (`heightIn(min = restBodyHeight)`) so the panel can grow but never shrink mid-swipe, releasing at settle so the body eases to the settled page; the pager is recreated on the seat (`key(clampedIndex)`) so a row tap lands as one size change, and `SwipePager` again takes a caller-owned `PagerState` for that read.

**Open:** a small bounce on selection change still remains.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the animated body, the swipe floor and the keyed pager recreation
- `app/src/main/java/ykws/android/maro/ui/components/SwipePager.kt` — the caller-owned `PagerState` so the host reads `isScrollInProgress`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the map band only grows while the search runs
- `docs/ui-drawer-guidelines.md` — the resize rule and the open note
- `xTrack/Route/261022_FEAT_PLN_Route_dash-table.md` — the plan, with `## Implemented` and the open note

## Next Step

Deploy and look at the selection and the swipe; the small bounce on selection change is still open.
