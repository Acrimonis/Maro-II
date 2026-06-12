# Hydration: RegulatedZones

**Last Bake:** 2026-06-12 20:39 (UTC+2)
**State:** Pre-commit snapshot on `feature/reg-zones-next`. Staged changes include filter design plan, icon-rendering overhaul, and regulation filter implementation. Unstaged: MapScreen.kt tweaks, PrebakeTest updates, and `plans/zone-info-text-discussion.md` (new, untracked).

## Summary

Completed subfeatures: `data-lookup [x]`, `display-layer [x]`, `toggle-control-merge [x]`, `preparation-for-icons-layout [x]`.
In-progress: `trouble-shoot-reg-layers [ ]` (1/4 todos done — root cause pinned, fix implemented, remaining: device verify), `reg-zones-filtering [ ]` (0/4 — not started), `add-zone-text [ ]` (0/5 — design phase).

## Target Files

- `app/src/main/java/ykws/android/maro/data/regulation/RegulatedZone.kt` — MODIFIED (staged)
- `app/src/main/java/ykws/android/maro/data/regulation/RegulationFilter.kt` — NEW (staged)
- `app/src/main/java/ykws/android/maro/data/regulation/ShomRegulationClient.kt` — MODIFIED (staged)
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — MODIFIED (staged + unstaged changes)
- `app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt` — NEW (staged)
- `app/src/test/java/ykws/android/maro/data/regulation/RegulatedZonePrebakeTest.kt` — MODIFIED (staged + unstaged)
- `maro.properties` — MODIFIED (staged)
- `plans/zone-info-text-discussion.md` — NEW (untracked)
- `plans/regulated-zones-filter-design.md` — NEW (staged)
- `plans/regulated-zones-icon-warnings-plan.md` — NEW (staged)
- `plans/icon-rendering-overhaul-plan.md` — NEW (staged)

## Next Step

`#commit` then `#push` to upstream.
