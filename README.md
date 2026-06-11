<!-- scope: core -->

# Maro II

Marine navigation app for the Nice-to-Fréjus corridor. Modern Android Development (MAD) greenfield — Jetpack Compose, Material3, Kotlin DSL.

> **Status:** Active development.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.0.0 |
| **AGP** | 8.4.1 |
| **UI** | Jetpack Compose + Material3 (BOM 2024.05.00) |
| **Architecture** | ViewModel + StateFlow + Repository |
| **Min / Target SDK** | 26 / 34 |
| **Build** | Gradle 8.7 — Kotlin DSL + Version Catalog |
| **Java** | JDK 17 toolchain |

---

## Build Pipeline

Three-stage pipeline: **bake** (data prep) → **build** (package) → **deploy** (install).

### 1. Bake — Data Preparation (requires GDAL)

```bash
apk-bake.bat                    # Interactive menu — pick what to bake
apk-bake.bat all                # Bake everything (coastline + depth + zone300)
apk-bake.bat coastline depth    # Selective bake
```

Granular scripts for headless / CI use (`tools\bake-*.bat`):

| Script | What it bakes | Depends on |
|--------|--------------|------------|
| `tools\bake-coastline.bat` | OSM coastline + 300 m band | — |
| `tools\bake-emodnet.bat` | EMODnet deep bathymetry | — |
| `tools\bake-litto3d.bat` | Litto3D shallow bathymetry | — |
| `tools\bake-depth.bat` | Merge + 6 NM clip | `coastline.bin` |
| `tools\bake-zone300.bat` | 300 m band refresh (no network) | `coastline.bin` |

Missing dependencies auto-resolve (e.g. `bake-depth` runs `bake-coastline` if absent);
use `--no-auto-deps` to hard-fail.

### 2. Build — Package Only

```bash
apk-build.bat         # runs gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Deploy — Install + Launch

```bash
apk-deploy.bat        # Build + install to connected device via ADB
```

**No baked data in git.** All bake outputs live under the gitignored
`data/app-assets/` tree and are packaged at build time via `assets.srcDir`.
Fresh clone → bake before build (auto-deps covers it).

---

## Data Pipeline (GDAL Setup)

Map datasets (coastline, depth) are **baked on the computer** and shipped as bundled
assets; the app only loads them. **The app itself never needs GDAL** — it's only
required when regenerating the data.

The bake scripts read the **region bounding box** from a single source of truth:
`maro.region.lonWest` / `maro.region.lonEast` in [`gradle.properties`](gradle.properties).
The coastline clips E/W to these points; N/S follows the real coast (OSM fetch uses a
generous window). The depth envelope is **derived** = coastline bbox + 6 NM.

Install GDAL (Windows — pick one):

- **Portable (minimal):** from <https://gisinternals.com/release.php> download the **x64 · MSVC 2022 ·
  latest stable GDAL** "Compiled binaries" zip, extract it, and run `SDKShell.bat` (sets `PATH` +
  `PROJ_LIB` + `GDAL_DATA`); launch the build from that shell.
- **conda:** `conda install -c conda-forge gdal`.

**Simplest — one variable:** set **`GDAL_HOME`** = your extracted GDAL root (e.g. `D:\…\GDal`).
The bake scripts (`tools\gdal_env.bat`) derive `PATH` + `GDAL_DATA` + `PROJ_LIB`/`PROJ_DATA` from it
automatically (GISInternals layout) — nothing else to configure. (GDAL is wired up only *during* the
bake, not on your global PATH.)

Or put GDAL on `PATH` globally (`<GDAL>` = root): PATH += `<GDAL>\bin` and `<GDAL>\bin\gdal\apps`;
`GDAL_DATA` = `<GDAL>\bin\gdal-data`; `PROJ_DATA` = `<GDAL>\bin\proj9\SHARE`.

Verify: `gdalwarp --version` and `projinfo EPSG:2154` (confirms PROJ).

---

## Project Structure

```
├── apk-bake.bat                 # Interactive bake selector
├── apk-build.bat                # Build debug APK (package only)
├── apk-deploy.bat               # Build + install with ADB
├── settings.gradle.kts          # Module includes, repository config
├── gradle.properties            # Region props, AndroidX, JVM args
├── gradle/
│   ├── libs.versions.toml       # Version catalog
│   └── wrapper/                 # Gradle 8.7 wrapper
├── app/
│   ├── build.gradle.kts         # App module config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/              # Checked-in config (maro.properties, zone.properties)
│       ├── proto/               # coastine.proto + depth.proto
│       ├── res/                 # Drawables, mipmaps, themes, strings
│       └── java/ykws/android/maro/
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── coastline/   # CoastlineGenerator, Repository, Serializer
│           │   ├── depth/       # DepthGenerator, Repository, Merge, ZoneMask
│           │   ├── location/    # AdaptiveGpsPolicy, CompassSource
│           │   ├── model/       # BoundingBox, DepthGrid, LatLng, etc.
│           │   └── settings/    # SettingsManager
│           ├── spatial/         # CoastlineSpatialIndex, SpatialOperations
│           └── ui/map/          # ViewModels, DashboardPanel, MapScreen
├── data/app-assets/             # Gitignored — baked data (coastlines/, depth/)
├── tools/                       # Bake scripts (bake-*.bat), GDAL env helpers
├── xTrack/                      # Feature tracking (xTrack system)
└── docs/                        # Extended documentation
```

---

## Documentation Index

| Doc | Scope | When to read |
|-----|-------|-------------|
| [docs/SETUP.md](docs/SETUP.md) | `onboarding` | New machine: SSH keys, ADB device, env vars |
| [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md) | `reference` | Branching strategy, feature lifecycle, releases |
| [docs/MARO_ARCHITECTURE.md](docs/MARO_ARCHITECTURE.md) | `reference` | Spatial engine constraints, memory-mapped I/O, async rendering |
| [docs/FAQ.md](docs/FAQ.md) | `reference` | Build failures, common troubleshooting |

**Scope legend:**
- `core` — always relevant, load by default
- `onboarding` — one-time machine setup
- `feature` — specific to a feature area
- `reference` — on-demand lookup
