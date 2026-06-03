<!-- scope: core -->

# Maro II

Marine navigation app for the Nice-to-Fréjus corridor. Modern Android Development (MAD) greenfield — Jetpack Compose, Material3, Kotlin DSL.

> **Status:** Active development on `develop` branch.

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

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Project Structure

```
├── build.gradle.kts              # Root build (plugin aliases, apply false)
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
