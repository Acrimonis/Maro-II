<!-- scope: core -->

# Prebake Batch

How to (re)bake the shipped depth grid `nice-frejus.bin` — the cooked asset the app loads.
"Prebake" = merge the GDAL-prepared `.asc` sources into one 25 m grid, clip to the 6 NM
navigable zone, drop land cells, validate, and serialize. Pure Kotlin (no device, no GDAL)
once the `.asc` ingredients already exist.

## Quick re-bake (logic-only change)

Use when only the bake *logic* changed (e.g. `DepthZoneMask`) and the `.asc` sources are
already staged under `data/app-assets/depth/`.

- **Wrapper (own shell):** `.\tools\bake-depth.bat`
- **PowerShell** (needs `--%` so `-D` props aren't split):
  `.\gradlew.bat --% :app:testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true --rerun-tasks`
- **cmd.exe:** `gradlew.bat :app:testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true --rerun-tasks`

`-Dmaro.prebake=true` un-gates `DepthPrebakeTest` (skipped otherwise); `--rerun-tasks` forces it.
Add `-Dmaro.repoDir=<repo-root>` only if the test can't locate `data/app-assets/` (it defaults to
the parent of the module dir).

## Inputs / output

| Role | Path |
|------|------|
| Deep source (EMODnet) | `data/app-assets/depth/emodnet-nice-frejus.asc` (required) |
| Shallow source (Litto3D) | `data/app-assets/depth/litto3d-nice-frejus.asc` (optional) |
| Coastline (zone clip + land mask) | `data/app-assets/coastlines/nice-frejus.bin` |
| **Output (shipped grid)** | `data/app-assets/depth/nice-frejus.bin` |

`data/app-assets/` is wired as an asset source dir, so the regenerated `.bin` ships in the APK.

## Pipeline (`DepthGenerator.generate`)

1. Merge deep sources (EMODnet, best-resolution-wins).
2. Merge shallow (Litto3D, shoalest-wins, ≤ 10 m authoritative).
3. `DepthZoneMask.apply` — null cells **> 6 NM** from the coast **and** on-land cells (`!isWater`).
4. Validate against control points (collision-tier RMSE is the safety gate).
5. Serialize → `.bin`.

## Reading the result

Gradle does **not** echo the test's `println`. Find the report in
`app/build/test-results/testDebugUnitTest/TEST-*DepthPrebakeTest.xml` (`<system-out>`):
- `Prebaked depth grid -> …\nice-frejus.bin (<bytes>)`
- `Coverage: NONE=… LITTO3D=… EMODNET=… /<total>` (NONE = masked: out-of-zone + land)
- `Validation: passed=true rmse=… datumMismatch=false` ← the gate

## Full bake (when `.asc` sources are missing)

`.\tools\apk-bake.bat` (or per-source `tools\bake-emodnet.bat` / `bake-litto3d.bat` /
`bake-coastline.bat`). These need **GDAL** (set `GDAL_HOME`) and download/clip the raw sources —
minutes, one-time; the merge step itself is then pure Kotlin.

## After a re-bake

Repackage so the APK bundles the new grid:

`.\gradlew.bat assembleDebug`

## Notes

- Non-interactive / agent shells: prefix `.\` and use `--%` before any `-D…` gradle prop.
- `local.properties` must point `sdk.dir` at this machine's Android SDK.
