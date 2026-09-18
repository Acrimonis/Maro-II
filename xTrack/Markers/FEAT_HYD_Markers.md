# Context Hydration — Markers — 2026-09-18

**Last Bake:** 2026-09-18 22:00 UTC — written by `#bake`; absence means never baked

**Directive trace:** No dependency was added, no machine-shaped data file was opened, no work started without an order and the device was untouched on either side of this merge; the one claim written without a read behind it — the framing values reached through `AppSettings` — was corrected in the same session once the compiler rejected it, and this branch's own claims each cited a read or an attributed hop, so all five classes were met and none stopped.

## State

Two sessions meet here, both on 2026-09-18. `marker-focus-zoom` shipped on `feature/mrkrs` (off `origin/develop` at `35c66c0`): opening a marker's dashboard frames that marker to a stated share of the smaller displayed map dimension — corridor 0.50, circle zone 0.30, and a pin through a nominal 200 m footprint at the zone's share, never below the current zoom — through the pure `MarkerFocus.kt` with `MarkerFocusTest` 8/8 green. The Where-Am-I work shipped on `feature/where-are-zone-trans`: one query per tap whose recording note survives a close, the ray lifetime owned by `setDrawerState()`, the debugger passed per call, one `resolveAt` body, the debugger interface cut to what is called, and the build-time rays hook retired; the boat's tap zone is a fixed radial circle whose six `map.marker.tap.*` values carry the zone, the pulse, the beat and its peak, the flash beating once beneath the sprite, and the card's close rule gained a one-finger pan gated by `MapPanDetector` so a pinch cannot trip it, with the recorder's idle exit still dismissing it. `apk-build.bat` SUCCESSFUL on both branches; the scoped runs carry only properties-versus-defaults reds, which now include the tuned `map.marker.tap.*` values (duration 666, ratio 0.20, alpha 0.33). The walk sits at item 11 and holds `#bake`'s fold; the device passes are the user's.

## Target Files

- `app/src/main/java/ykws/android/maro/ui/map/MarkerFocus.kt` — the framing fit, and the only place the share rule is expressed
- `app/src/main/java/ykws/android/maro/ui/map/MapOverlays.kt` — the boat's radial zone and the flash
- `app/src/main/java/ykws/android/maro/ui/map/MarkersViewModel.kt` — `setDrawerState`, `resolveAt`, the per-open id
- `app/src/main/java/ykws/android/maro/ui/map/MapPanDetector.kt` and `MapGpsFollowEffects.kt` — the pan gate and its listener
- `app/src/main/java/ykws/android/maro/ui/map/PanResumeTimer.kt` and `NavigationViewModel.kt` — the return-to-boat hold and restart
- `app/src/main/assets/maro.properties` — the three `marker.focus.*` keys and the six `map.marker.tap.*` ones

## Next Step

Two are owed before the device runs: pin the three `marker.focus.*` keys to their parse with a properties test — the earlier Ask pass's open finding — and rule on the shipped-versus-default values for the tap zone and the flash (666/540, 0.20/0.3333, 0.33/0.50), which keep the properties reds open. Then a marker's dashboard over a corridor, a circle and a pin, watching the share, whether GPS auto-follow steals the frame and whether closing the card leaves the camera alone; and the tap zone, the flash, the pan and the TalkBack path.
