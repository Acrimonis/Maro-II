<!-- scope: core -->

# Maro II

Marine navigation app for the Nice-to-Fréjus corridor. Modern Android Development (MAD) greenfield — Jetpack Compose, Material3, Kotlin DSL.

> **Status:** Active development.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.0.0 |
| **UI** | Jetpack Compose + Material3 (BOM 2024.05.00) |
| **Architecture** | ViewModel + StateFlow + Repository |
| **Min / Target SDK** | 26 / 34 |
| **Build** | Gradle 8.7 — Kotlin DSL + Version Catalog |
| **Java** | Corretto 21 (JDK 17 toolchain) |

---

## Quick Build

```bash
./gradlew assembleDebug
```

Or use the convenience scripts:

```bash
apk-build.bat      # Build debug APK
apk-deploy.bat     # Build + install to connected device via ADB
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Prebaking map data (optional — requires GDAL)

Map datasets (coastline, depth) are **prebaked on the computer** and shipped as bundled assets; the
app only loads them. Re-baking is **opt-in** via `apk-build.bat` (answer **Y** at the prompts) and
requires **GDAL ≥ 3.x (which includes PROJ)** on `PATH` — used by `tools\bake_*.bat` to
clip / reproject / convert the raw source downloads. **The app itself never needs GDAL**; it's only
needed when regenerating the data.

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

Verify: `gdalwarp --version` and `projinfo EPSG:2154` (confirms PROJ). Bake steps:
[docs/DepthMappingBake.md](docs/DepthMappingBake.md).

---

## Project Structure

```
├── apk-build.bat                 # Build debug APK
├── apk-deploy.bat                # Build + install to device with ADB
├── settings.gradle.kts           # Module includes, repository config
├── gradle.properties             # AndroidX, JVM args
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/                  # Gradle 8.7 wrapper
├── app/
│   ├── build.gradle.kts          # App module config
│   ├── preloaded/coastlines/     # Pre-generated coastline binary data
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── proto/coastline.proto
│       │   ├── res/              # Drawables, mipmaps, themes, strings
│       │   └── java/ykws/android/maro/
│       │       ├── MainActivity.kt
│       │       ├── data/
│       │       │   ├── coastline/       # CoastlineGenerator, Repository, Serializer
│       │       │   └── model/           # BoundingBox, CoastlineCache, LatLng, etc.
│       │       ├── spatial/             # CoastlineSpatialIndex, SpatialOperations
│       │       └── ui/map/              # CoastlineViewModel, MapScreen
│       └── test/                        # Unit tests
└── docs/                          # Extended documentation
```

---

## Documentation Index

| Doc | Scope | When to read |
|-----|-------|-------------|
| [docs/SETUP.md](docs/SETUP.md) | `onboarding` | New machine: SSH keys, prerequisites, ADB device, env vars |
| [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md) | `reference` | Branching strategy, feature lifecycle, releases |
| [docs/MARKER_SIZING.md](docs/MARKER_SIZING.md) | `feature` | Marker zoom/distance behavior, tuning constants, data flow |
| [docs/FAQ.md](docs/FAQ.md) | `reference` | Build failures, common troubleshooting |

**Scope legend:**
- `core` — always relevant, load by default
- `onboarding` — one-time machine setup
- `feature` — specific to a feature area
- `reference` — on-demand lookup
