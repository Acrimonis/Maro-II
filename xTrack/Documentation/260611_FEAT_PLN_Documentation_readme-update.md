<!-- scope: feature -->
# README Update — Complete Proposal

## Verification Results

| Check | Result |
|-------|--------|
| Gradle version | **8.7** ✓ (wrapper: `gradle-8.7-bin.zip`) |
| Kotlin version | **2.0.0** ✓ (`libs.versions.toml`) |
| AGP version | **8.4.1** (not in current README — should add) |
| BOM version | **2024.05.00** ✓ |
| Java target | **17** (JVM target in `build.gradle.kts`) |
| `docs/MARKER_SIZING.md` | **Does NOT exist** — remove from index |
| `docs/DepthMappingBake.md` | **Does NOT exist** — remove from README |
| `docs/MARO_ARCHITECTURE.md` | **Exists** — add to index |
| `proto/` | Exists with `coastline.proto` + `depth.proto` |
| `data/app-assets/` | Gitignored, baked data lives here |
| `tools/` | Contains `bake-*.bat`, `fetch_*.ps1`, `gdal_env.bat` |

## Proposed README Content

The current [`README.md`](README.md) needs updates in these sections:

### Section 1: Tech Stack — Add AGP, keep rest accurate

Current is mostly correct. Just add the missing AGP row:

```
| **AGP** | 8.4.1 |
```

### Section 2: Quick Build → Build Pipeline (major rewrite)

Replace the current two-line Quick Build with the three-stage model from BakeNormalization:

**Proposed:**

````markdown
## Build Pipeline

Three-stage pipeline: **bake** (data prep) → **build** (package) → **deploy** (install).

### 1. Bake — Data Preparation  (GDAL required)

```bash
apk-bake.bat              # Interactive menu — pick what to bake
apk-bake.bat all          # Bake everything (coastline + depth + zone300)
apk-bake.bat coastline depth  # Selective bake
```

Granular scripts for headless / CI use (`tools\bake-*.bat`):

| Script | What it bakes | Depends on |
|--------|--------------|------------|
| `tools\bake-coastline.bat` | OSM coastline + 300 m band | — |
| `tools\bake-emodnet.bat` | EMODnet deep bathymetry | — |
| `tools\bake-litto3d.bat` | Litto3D shallow bathymetry | — |
| `tools\bake-depth.bat` | Merge + 6 NM clip | `coastline.bin` |
| `tools\bake-zone300.bat` | 300 m band refresh (no network) | `coastline.bin` |

Missing dependencies auto-resolve (`bake-depth` runs `bake-coastline` if absent);
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
````

### Section 3: Prebaking / GDAL Section (update references)

Replace the "Prebaking map data" section. Keep GDAL install guide but update bake instructions:

**Key changes:**
- Remove reference to `docs/DepthMappingBake.md` (doesn't exist)
- Reference `tools\bake-*.bat` instead
- Mention single region source: `maro.region.lonWest`/`lonEast` in `gradle.properties`

### Section 4: Project Structure (modernize tree)

Replace the current tree with one that reflects the current layout:

```
├── apk-bake.bat                 # Interactive bake selector
├── apk-build.bat                # Build debug APK (package only)
├── apk-deploy.bat               # Build + install with ADB
├── settings.gradle.kts          # Module includes
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
├── tools/                       # Bake scripts, GDAL env helpers
├── xTrack/                      # Feature tracking (xTrack system)
└── docs/                        # Extended documentation
```

### Section 5: Documentation Index (sync)

Remove stale entries, add actual docs:

```
| Doc | Scope | When to read |
|-----|-------|-------------|
| [docs/SETUP.md](docs/SETUP.md) | `onboarding` | New machine: SSH keys, ADB, prerequisites |
| [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md) | `reference` | Branching strategy, feature lifecycle |
| [docs/MARO_ARCHITECTURE.md](docs/MARO_ARCHITECTURE.md) | `reference` | Spatial engine constraints and design |
| [docs/FAQ.md](docs/FAQ.md) | `reference` | Build failures, troubleshooting |
```

---

## Implementation Plan

1. Write the updated README.md with all sections above
2. Add new subfeature todos under `### readme  [ ]` in [`FEAT_DSC_Documentation.md`](xTrack/Documentation/FEAT_DSC_Documentation.md) tracking each change
3. Verify the final rendered markdown looks clean

Ready to implement — switch to **Code** mode?

