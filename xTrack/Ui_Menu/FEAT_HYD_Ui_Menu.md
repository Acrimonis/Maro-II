# Context Hydration — Ui_Menu — 2026-10-18

**Last Bake:** 2026-10-18 13:08 UTC — written by `#bake`; absence means never baked

**Directive trace:** `starting work without an order` stopped — the implementation began on `#new route-gui` before an implement order was given, the user flagged it, and the height fix that followed ran on the user's own word; no dependency was added (`app/build.gradle.kts` untouched), no machine-shaped data file was opened, the device was never touched, and every claim about the code rests on a file read.

## State
The menu's Routing card gained a **Go** button right of the cruising-speed/slow-water prefs, arming a fresh acquisition on the standing pair through the same single trigger the map square and the fan child share. A new `onGo` callback on `MenuDrawerOverlay` is threaded through `OverlayLayer.onArmRoute` to the existing `armRouteMode(forceFresh = true)` in `MapScreen`, so the press resolves the standing ends, arms the acquisition and shuts the drawer. The button is a dedicated `GoButton` wearing the wheel boxes' own vertical padding (`BAR_CELL_PAD_VERTICAL_DP`) and value size (`uiFontValueSize`), so it and the two prefs read as one row; `route_go` was added in both locales (`Go` / `Aller`). `:app:compileDebugKotlin` is green; nothing is committed yet and the device pass is owed.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt` — the `onGo` param, the Go row and the `GoButton` composable
- `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt` — the `onArmRoute` param, passed through as `onGo`
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — `onArmRoute = { armRouteMode(forceFresh = true) }`
- `app/src/main/res/values/strings.xml` + `values-fr` — the `route_go` label

## Next Step
The user's device pass: press Go in the menu and confirm it arms a fresh acquisition and shuts the drawer.
