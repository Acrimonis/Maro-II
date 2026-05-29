# Maro II

A Modern Android Development (MAD) greenfield project — Jetpack Compose, Material3, Kotlin DSL.

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

## SSH Setup (per machine)

Each development machine needs its own SSH key pair added to the
[Acrimonis GitHub account](https://github.com/settings/keys).

```bash
# 1. Generate a dedicated key for this project
ssh-keygen -t ed25519 -f ~/.ssh/id_github_acrimonis -C "acrimonis@gmail.com"

# 2. Print the public key, then add it at https://github.com/settings/ssh/new
type ~/.ssh/id_github_acrimonis.pub

# 3. In the cloned repo, tell Git to use this key
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis"
```

> The **private** key (`id_github_acrimonis`) stays on the machine — **never commit it**.
> The **public** key (`id_github_acrimonis.pub`) is uploaded to GitHub only.

---

## Prerequisites

| Dependency | Check |
|-----------|-------|
| **Java 17+** | `java -version` |
| **Android SDK** | `$ANDROID_SDK` must point to a valid SDK with `platforms;android-34` and `build-tools;34.0.0` installed |

---

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Git Workflow

```
main        ●───────────────────────  (tagged releases only)
             \
develop      ●──●──●────●──●──●──●  (HEAD — daily development)
                   \
feature/xxx         ●──●──●──        (isolated feature work)
```

### Rules

| Branch | Purpose | Source | Merges into |
|--------|---------|--------|-------------|
| `main` | Production releases | — | — |
| `develop` | Integration branch | `main` | `main` (at release) |
| `feature/*` | Isolated feature work | `develop` | `develop` |

### Starting a feature

```bash
git checkout develop
git checkout -b feature/my-feature
# work, commit, optionally push
```

### Completing a feature

```bash
git checkout develop
git merge --no-ff feature/my-feature
git branch -d feature/my-feature
```

> `--no-ff` preserves the feature branch topology in the commit history.

### Releasing

```bash
git checkout main
git merge --no-ff develop
git tag v0.1.0
git push --tags
```

### Notes

- Feature branches may be pushed to GitHub at the developer's discretion.
- `main` is merged from `develop` only when a release is cut.
- All day-to-day work targets `develop`.

---

## Project Structure

```
├── build.gradle.kts              # Root build (plugin aliases, apply false)
├── settings.gradle.kts           # Module includes, repository config
├── gradle.properties             # AndroidX, JVM args
├── local.properties              # SDK path (gitignored)
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/                  # Gradle 8.7 wrapper
├── app/
│   ├── build.gradle.kts          # App module config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── java/com/example/newapp/
│           └── MainActivity.kt
└── README.md
```

---

## Marker Sizing (Dynamic)

The center-position marker (boat / dot) resizes in real time based on two inputs:

| Input | Source | Effect |
|-------|--------|--------|
| **Zoom level** (8.0–18.0) | [`MapListener.onZoom`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:507) → [`CoastlineViewModel.updateZoomLevel()`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:107) | Marker grows as you zoom in (represents constant ground footprint) |
| **Distance to coast** (meters) | [`CoastlineViewModel.updateMapCenter()`](app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt:98) → `_distanceToShore` | Marker shrinks near the coast to avoid visually overlapping land |

### Tuning Constants

All knobs are `private const val` at the top of [`CenterMarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:526). Change them there and rebuild.

#### Zoom → dp Anchors (piecewise-linear interpolation)

| Constant | Value | Description |
|----------|-------|-------------|
| `MIN_ZOOM` | 8.0 | Map min zoom (must match `MapView.minZoomLevel`) |
| `MAX_ZOOM` | 18.0 | Map max zoom (must match `MapView.maxZoomLevel`) |
| `REF_ZOOM` | 11.0 | "Normal" baseline zoom |
| `BOAT_DP_AT_MIN_ZOOM` | 24.0 | Boat size (dp) at zoom 8 |
| `BOAT_DP_AT_REF_ZOOM` | 48.0 | Boat size (dp) at zoom 11 |
| `BOAT_DP_AT_MAX_ZOOM` | 96.0 | Boat size (dp) at zoom 18 |
| `DOT_DP_AT_MIN_ZOOM` | 16.0 | Land dot size (dp) at zoom 8 |
| `DOT_DP_AT_REF_ZOOM` | 32.0 | Land dot size (dp) at zoom 11 |
| `DOT_DP_AT_MAX_ZOOM` | 64.0 | Land dot size (dp) at zoom 18 |

#### Distance-to-Coast Shrink Ramp

```
multiplier = DIST_SHRINK_MIN_MULT + (1.0 - DIST_SHRINK_MIN_MULT) × clamp(dist / DIST_SHRINK_RAMP_M, 0, 1)
```

| Constant | Value | Description |
|----------|-------|-------------|
| `DIST_SHRINK_MIN_MULT` | 0.5 | Multiplier at 0 m (on the coastline) |
| `DIST_SHRINK_RAMP_M` | 2000.0 | Distance (m) at which marker reaches full 1.0× size |

**Example:** At zoom 18, the boat is `96dp × multiplier`. On the coastline → 48dp. At 1000 m → 72dp. At ≥2000 m → 96dp.

### Data Flow Diagram

```
MapListener.onZoom()                    MapListener.onScroll() / onZoom()
  │                                        │
  ├─ onZoomChanged(zoomLevelDouble)        ├─ onCenterChanged(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ▼
  │  CoastlineViewModel                    │  CoastlineViewModel
  │  .updateZoomLevel(zoom)                │  .updateMapCenter(lat, lon)
  │    │                                   │    │
  │    ▼                                   │    ├─ _mapCenter ← LatLng
  │  _zoomLevel ← zoom                     │    ├─ _isWater ← repo.isOnWater()
  │    │                                   │    └─ _distanceToShore ← repo.distanceToCoast()
  │    │                                   │
  └────┼───────────────────────────────────┘
       │
       ▼
  MapScreen.collectAsState()
       │
       ├─ zoomLevel: Double
       └─ distanceToShore: Double?
              │
              ▼
       CenterMarkerOverlay(zoomLevel, distanceToShore)
              │
              ├─ sizeByZoom = lerpDp(zoomLevel, anchors)
              ├─ distMultiplier = ramp(distanceToShore)
              └─ finalSizeDp = sizeByZoom × distMultiplier
```

---

## FAQ

### Why isn't the SSH key stored in the repository?

The **private key** (`id_github_acrimonis`) authenticates you on GitHub. Committing it would let anyone impersonate you — **never do this**.

The **public key** (`id_github_acrimonis.pub`) is safe to share, but it's already registered on your GitHub account. Storing it in the repo is redundant.

### I cloned on a new machine and `git push` asks for a password — why?

The Git config (user name, SSH command, remote URL) lives in `.git/config`, which is **not version-controlled**. On each new machine you need to re-run:

```bash
git config user.name "Acrimonis"
git config user.email "acrimonis@gmail.com"
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis"
```

See the [SSH Setup](#ssh-setup-per-machine) section above.

### Why can't I just use my global GitHub account?

You can — this project uses a dedicated `Acrimonis` account for separation. Your global account (`nbadino-doca`) stays untouched and works as before for all other repositories.

### I use TortoiseGit — will the SSH key work?

Yes. TortoiseGit uses the same SSH client as the command line. Point it to the key at:

```
C:\Users\<you>\.ssh\id_github_acrimonis
```

Or set it globally in your `%USERPROFILE%\.ssh\config`:

```
Host github.com
    IdentityFile ~/.ssh/id_github_acrimonis
```

### The build failed — what should I check?

| Symptom | Likely cause |
|---------|-------------|
| `android.useAndroidX` error | Missing `gradle.properties` with `android.useAndroidX=true` |
| Compose BOM not found | Check the BOM version exists in `gradle/libs.versions.toml` |
| SDK platform missing | Run `sdkmanager "platforms;android-34"` |
| `JAVA_HOME` not set | `set JAVA_HOME=C:\Path\To\JDK` |

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `ANDROID_SDK` | `C:\Users\nbadino\Programs_nICo\_Dev_\Android_SDK_CLI` |
| `JAVA_HOME` | `C:\Users\nbadino\.java\corretto-21.0.7` |
| `GRADDLE_HOME` | `C:\Users\nbadino\Programs_nICo\_Dev_\Graddle` |
