# Context Hydration — CodeReview — 2026-06-04

**Active Subfeature:** none

## State
Subfeature **frequency** resolved: throttled the distance-to-coast/water recompute (was firing 30–60/s on the UI thread → jank) to ~6–7 Hz via `_mapCenter.sample(150ms).mapLatest{…}.flowOn(Default)` in `CoastlineViewModel`; removed a redundant double spatial query via a `CoastlineRepository.isOnWater(lat, lon, distMeters)` overload. frequency 2/2 todos done; parent todos #1–#4 resolved (FIXED/WONTFIX). On-device test (2026-06-04) PASSED; CodeReview all clear. Build-verified (`assembleDebug`), not committed.

## Target Files
- `CoastlineViewModel.kt` — throttle pipeline
- `CoastlineRepository.kt` — `isOnWater` distance-reuse overload

## Next Step
Commit the working-tree changes (not yet committed).
