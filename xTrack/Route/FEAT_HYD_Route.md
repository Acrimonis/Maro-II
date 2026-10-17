# Context Hydration — Route — 2026-10-17

**Last Bake:** 2026-10-17 15:02 UTC — written by `#bake`; absence means never baked

**Directive trace:** The session opened on the user's `#focus route` and `#new rte-cche`, examined the fine-tile cache's reclaim, settled the home-area protection with the user across a question-and-answer pass, wrote the plan of record, ran `#impl` (Code → Ask → Architect) and closed the Ask hop's two blockers in one fix hop. No dependency was added, no machine-shaped data file was opened, the device was never touched, every claim about the code stands behind a file read, and the only git write was the branch creation the user ordered.

## State

The fine-tile store now protects the **home area** — `route.prewarm.box` — from reclamation. `TileStore.protect(names)` carries the set and its own byte total; `MapRoutePrewarmEffect` derives the seed box once, turns its entry names into that set and installs them **before** the walk starts, handing the same requests to `warmOutward` as ring zero; `FineTileCache` remembers the last set and re-applies it at `install`, so an Activity recreation cannot strip protection.

The reclaim walks one **access-ordered index** (entry name → bytes, primed by the listing that already primes the byte total) instead of listing and sorting the directory on every overflowing save, skips protected names, and returns on an arithmetic test alone when only protected bytes remain — reporting that once per store, the latch on the line and never on the behaviour. `list()` deliberately still reads the directory, because the prewarm treats a name as proof that a file stands.

The ceiling is **96 MiB** and **soft** across its four homes — `route.fineTile.maxBytes`, `AppConfig.routeFineTileMaxBytes`, `DirectoryFineTileStore.DEFAULT_MAX_BYTES` and the renamed ceiling test. New readings ride the device log: `STORE trim evicted=… remaining=… protected=… ms=…`, `STORE trim cannot reach ceiling …` once, and `hitRate=NN%` on `PREWARM walk done`.

`:app:testDebugUnitTest :app:assembleDebug` is green at 1317 tests at the first landing and after the review fixes. Nothing is committed, staged or pushed: `feature/rte-cche` still sits on `origin/develop`'s head `ea5a734` with no upstream, and the work is in the working tree.

## Target Files

- `xTrack/Route/261017_FEAT_PLN_Route_home-area-eviction.md` — the plan of record and its `## Implemented`
- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt` — the index, the protected total, the index-only `trim()`, the directory `list()`
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt` — `protect`, inert in `NoOp`
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt` — the remembered set and its re-apply at `install`
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt` — the seed handed in, ring zero built from it, the hit rate on the end line
- `app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt` — the seed derived and protected before the walk
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties` — the 96 MiB ceiling and its soft-ceiling wording
- `app/src/test/java/ykws/android/maro/config/FineTileStoreCeilingPropertiesTest.kt` · the store and warm suites
- `xTrack/Route/FEAT_DSC_Route.md` · `docs` — the state doc and the engines reference

## Next Step

The device pass is the user's: several routes over cold outer water, then a reload, reading that no `STORE trim` names a home tile and that the walk's `hitRate` rises. The home area's own byte count is still unmeasured, `FineTilePrewarm.kt` no longer carries the stray patch fragment — removed on the user's word — and `#commit` has not been run, so the branch still carries nothing of this session.
