---
feature: Route
topic: The route fan drops Save-and-exit and splits acquire from follow
created: 2026-10-17 06:38 UTC
---

# The route fan decouples acquire from follow

**Status:** implemented 2026-10-17 on `feature/route-fan` — build and scoped suites owed

**Feature:** Route (the map's route fan, R75–R80, R91, R92); the owning spec is `FEAT_DSC_Route.md`

## Decisions

- The fan's **Save to track and exit** child (`logout`, `route_exit_save`) is removed — never used. The exit dialog's own Save and Leave door keeps `route_exit_save` (it is the toggle-off and back-key door, R59).
- The dual-purpose `Route` child is split: **Route** arms the acquisition alone, and a new **Follow** child sits directly below it, firing the same selection the acquisition panel's Follow action does.
- The arc reads, top to bottom: **Route auto · Route · Follow · Save · Discard**; the follow child wears a new `navigation` arrow vector.

## Shape of the change

- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the enablement and action lists move to the new five-child order (bottom to top Discard · Save · Follow · Route · Route auto); Follow's cell reads `Choosing && !committed && (line || partial)`, Route's reads `!routeDisplayArmed`, and the close guard now dismisses on follow, acquire and auto.
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — `RouteFanSaveExitIcon` retires, `RouteFanFollowIcon` joins wearing `navigation` with `route_action_select` as its name.
- `app/src/main/java/ykws/android/maro/ui/icons/Navigation.kt` — the new standalone Material Symbols vector.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml` — the fan comment block names `route_action_select` among the reused keys.
- `FEAT_DSC_Route.md` — R91's closers are now follow · acquire · auto; R92 loses the Save-and-exit exception and reads Save/Follow.

## Recorded, not moved

- `app/src/main/java/ykws/android/maro/ui/icons/Logout.kt` has no caller now; deletion needs the user's word.
- The retired archive's R77 still names the 2026-09-29 five children; this plan supersedes that list.

## Outcome

Shipped 2026-10-17: the fan's Save-and-exit child left, `Route` arms the acquisition alone and a new `Follow` child below it takes the settled or partial line, the arc reading Route auto · Route · Follow · Save · Discard. R91 and R92 in `FEAT_DSC_Route.md` carry the closers and the endings. The uncalled `Logout.kt` is promoted to the DSC's removal list.
