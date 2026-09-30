# 260923 · Route — the aim ring joins the map's own layers, so the boat paints over it

**Status:** shipped 2026-09-23, uncommitted — `apk-build.bat` SUCCESS, the full suite green, the Ask hop's five should-fixes closed in a second Code hop. The user's own words: *the boat should draw over the aim target*. **Deviations from §2:** `ROUTE_TARGET_SIZE_DP` was deleted rather than moved, its only reader being the Compose box osmdroid has no analogue for, while the three geometry constants moved intact; `RouteHost` lost its now-unread `modifier` and `mapCenterOffsetDp`; and the ring rides a **`Polyline` subclass**, because `OverlayZOrder.titleOf` recognises a title by its *type* — the honest alternative, one arm in `titleOf`, is named for whenever this plan is reopened. `refusedCrosshairAlpha`'s beat and the title's type arm are pinned by tests; the band link and the repaint cost stay a device judgement.

## 1. What is seen and why

The boat's sprite is an **osmdroid marker**, so it lives in the map's marker band. The aim ring — the target painted at the screen point the route is aimed at, with its refused crosshair — is a **Compose `Canvas`** ([`RouteOverlay.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:314), `RouteTarget`), and every Compose overlay sits **above the whole `MapView`**. So the ring is drawn over the boat by construction, whatever the bands say, and no osmdroid ordering can put the boat on top while the ring is Compose.

That also contradicts the feature's own stated order — *tile → base → tracks → markers* — which the route's line already obeys: [`OverlayZOrder.kt:37`](../../app/src/main/java/ykws/android/maro/ui/map/OverlayZOrder.kt:37) carries the `route_` title prefix inside the **track band**, so every route line is drawn above the tracks and below the markers. The ring is the one piece of the feature's drawing that escaped that rule.

## 2. The change

**Move the aim ring and its refused crosshair out of Compose and into an osmdroid `Overlay` that [`RouteHost.kt`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:107) owns** — the one file that already touches osmdroid for the feature and already receives both inputs the ring needs.

- Its title carries the `route_` prefix (`route_target`), so `OverlayZOrder` places it in the **track band** with no rule added: above the tracks and the route lines, below the pin and **below every marker, the boat included**. `OverlayZOrder` itself does not change.
- It paints at the **same screen point it paints at today** — the canvas' own centre plus the app's own offset, the same `mapCenterOffsetPx` / `mapCenterOffsetDp` inputs `RouteHost` already receives — so the ring and the aim the machine asks about cannot drift apart.
- It reads the same values it reads today: `route.target.color` · `route.target.widthDp` · `route.target.pulseMs`, and the ring's geometry from the constants that move with it ([`ROUTE_TARGET_SIZE_DP`](../../app/src/main/java/ykws/android/maro/ui/map/RouteOverlay.kt:43) and its three siblings).
- **The pulse stays a pulse**: the same `route.target.pulseMs` drives it, read from the clock the overlay already repaints on rather than from a Compose animation, so the ring's tick survives the move.
- **The refused state is the same ring**: the crosshair is drawn by the overlay when the aim is refused, exactly as the Compose target draws it now.

**What leaves:** the `RouteTarget` composable and its call site in the map's Compose content, and the constants it was the only reader of — deleted rather than left beside their new home.

## 3. What does not change

The aim itself (the map centre read with the app's own offset), the ask policy, the refused state's meaning, the ring's size, colour, stroke and pulse period, the destination pin's band (`marker_route_dest`, above the ring as today), and the line's band. No string, no key, no setting, and no other overlay's slot.

## 4. The epic's open item this closes

`FEAT_DSC_Route.md`'s key-files table still leaves the route's own overlay ordering *to be decided* (plan §14 C10). With the line, the ring and the pin all placed by one rule, that sentence can state the slot rather than leaving it open — the line and the ring in the track band, the pin in the marker band, the boat above all three.

## 5. Verification

`apk-build.bat` SUCCESS with no new warning, and `gradlew.bat testDebugUnitTest --tests "ykws.android.maro.ui.map.*"` green. Where the ring lands and that the boat now paints over it are **device judgements** — the plan states the band, and the device answers for the pixels.
