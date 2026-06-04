# Context Hydration — 2026-06-04

**Active Feature:** CodeReview · **Active Subfeature:** frequency

Session resolved subfeature **frequency** under the CodeReview epic. Problem: the distance-to-coast / water-state recompute was firing on every osmdroid scroll event (30–60/s) on the UI thread, causing visible map jank.

Done this session (build-verified via `assembleDebug`, **not committed**):
- **Throttled the recompute** — `CoastlineViewModel` gained an `init` pipeline: `_mapCenter.sample(150ms).mapLatest { … }.flowOn(Dispatchers.Default)` → ~6–7 updates/s, off the main thread. `updateMapCenter` now only records the center cheaply.
- **Removed redundant double query** — added `CoastlineRepository.isOnWater(lat, lon, distToCoastMeters)` overload; pipeline computes `distanceToCoast` once and reuses the distance.

Files modified: `CoastlineViewModel.kt`, `CoastlineRepository.kt`, `FEATURE_SCOPE_CodeReview.md`, `local.properties`. frequency subfeature 2/2 todos done. Parent todos #1–#4 already resolved (FIXED/WONTFIX). CodeReview now all clear.

Environment: `local.properties` repointed to this machine's SDK `D:\Programs nICo\_Dev_\Android_SDK_CLI`. Build: `cd /d D:\.src\Maro_II_b && .\gradlew assembleDebug` (leading `.\` required in this shell).

On-device test (2026-06-04): **PASSED** — smoother pan, correct shore distance/water flag. Next step: commit the working-tree changes (not yet committed).
