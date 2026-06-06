# Context Hydration — 2026-06-06

**Active Feature:** Zone300
**Active Subfeature:** drawZone
**Next-session goal:** ship the prebaked 300 m band (`apk-bake.bat` → `apk-build.bat` → `apk-deploy.bat`). The land-mirror is fixed AND on-device validated.

## State — land-mirror FIXED (on-device validated ✅)
Two-layer fix, committed + pushed on `feature/300M-Claude-II`:
1. **Classifier:** open-polyline 6 NM south-ray → **closed-polygon containment** in
   `CoastlineSpatialIndex.isWater` (even-odd **north-ray** + inland cap; winding-independent).
   `CoastlineRepository.isOnWater` keeps the >6 NM short-circuit and delegates.
2. **Band builder (the decisive fix):** `Zone300Builder` final mask now gated by the per-cell
   water bit — `mask = seaConnected && WATER && NEAR` — so a flood bleeding past a barrier gap
   can no longer paint a mirrored land-side band. **User confirmed on device: water-side only.**
Full `testDebugUnitTest` = **105 green** (2 self-skip: baker + EMODnet harness).

## Prebake (build-time band) — TOOLING DONE, just run it
The baked band is a build artifact, **never committed** — baked into gitignored `data/`, then
incorporated into the APK at build:
- `apk-bake.bat` (online) → `Zone300AssetBaker` (`-Dmaro.bake=true`, forwarded by `build.gradle.kts`;
  uses prod's containment `isWater` + cell size) writes **gitignored** `data/app-assets/coastlines/nice-frejus.bin`.
- `build.gradle.kts` adds `data/app-assets` as an **assets srcDir** → APK gets
  `assets/coastlines/nice-frejus.bin` (validated via `mergeDebugAssets`). Absent ⇒ live-fetch fallback.
- `loadCoastline` = disk cache → bundled asset → live fetch (newer-by-timestamp wins).
**To ship:** `apk-bake.bat` → `apk-build.bat` → `apk-deploy.bat`. **Nothing to commit.**
(Couldn't run `apk-bake` here — no Overpass HTTPS in this sandbox.)

## Don't redo / gotchas
- Land-mirror is fixed + on-device validated — do NOT reopen the classifier/builder. Guards:
  `CoastlineSpatialIndexWaterTest` (ground-truth sweep, incl. fragmented coast) + the anti-mirror
  `Zone300BuilderTest` case. `Zone300WaterOracleHarness` (`-Dmaro.validate=true`) = optional EMODnet check.
- `data/` is gitignored (`/data/` anchored — must NOT match the `maro.data` source package).
- Memory: `zone300-land-mirror.md`.
