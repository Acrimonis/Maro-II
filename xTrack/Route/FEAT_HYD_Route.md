# Context Hydration — Route — 2026-10-14

**Last Bake:** 2026-10-14 14:17 UTC — written by `#bake`; absence means never baked

**Directive trace:** Since the last bake (2026-10-14 12:31 UTC) the session ran on the user's own `#impl` — the `#implement` pipeline's Code hop, its build, its Ask pass and the Architect report. No dependency was added, no machine-shaped data file was opened, no work was started without an order, the device was never touched by the agent, and every claim about the code follows a file read.

## State

**The store's first reclamation now says itself twice.** `reportFineTileReclaimed()` raises its toast and then `postFineTileReclaimNotification()`: the app's **single** channel — `TrackRecordingService.CHANNEL_ID`, made `internal` so the id keeps one literal — its own id 1002 beside the banner's 1001, silent, auto-cancelling, a tap opening `MainActivity`, and posted only while `areNotificationsEnabled()` so a denial degrades to the toast alone. Both surfaces ride the store's own once-guard, and neither locale gained a string: `app_name` and `startup_fine_tile_reclaimed` carry them.

**One permission ask, at startup.** `requestNotificationPermission` is bound at construction and launched from a `LaunchedEffect(Unit)` at the head of the composition, on API 33+ only and only while the grant is missing, so a granted app is never asked again.

**The ceiling is 48 MiB, three homes in step.** `route.fineTile.maxBytes=50331648` in `maro.properties`, with `AppConfig.routeFineTileMaxBytes` and `DirectoryFineTileStore.DEFAULT_MAX_BYTES` mirrored in the same write and `FineTileStoreCeilingPropertiesTest` holding all three to it; `FineTileMap.DEFAULT_MAX_BYTES`, the in-memory 8 MiB, is untouched. `assembleDebug` and the scoped suites are green, the plan closed with its `## Implemented`, and its `(implemented)` row sits in the feature file's `## Docs`.

## Target Files

- `app/src/main/java/ykws/android/maro/MainActivity.kt` — the alert beside the toast, and the one permission ask
- `app/src/main/java/ykws/android/maro/data/track/TrackRecordingService.kt` — `CHANNEL_ID`, the app's single channel, now readable
- `app/src/main/assets/maro.properties`, `config/AppConfig.kt`, `spatial/DirectoryFineTileStore.kt` — the 48 MiB ceiling, its one home and its two mirrors
- `app/src/test/java/ykws/android/maro/config/FineTileStoreCeilingPropertiesTest.kt` — the pin over all three
- `xTrack/Route/261014_FEAT_PLN_Route_reclaim-alert-cache-ceiling.md` — the plan of record, closed
- `xTrack/Route/FEAT_DSC_Route.md` — the landed record: the store bullet and the `## Docs` pointer

## Next Step

No code task is open on the feature. The alert's device pass (R97) is the user's own — the grant taken on the prompt, the notification read in the shade on a reclaim forced with a temporary small ceiling, and the new bound watched across a pre-warm — and it gates nothing. The feature's own open items are unchanged: the forced-crossing list and the rung ranking still read apart from the clock, the corner pass keeps its one item about the 50 m coast margin, and the fine-tail and `selective` device passes stay owed.
