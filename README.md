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

The center-position marker (boat 🚤 on water, blue dot 🔵 on land) changes size in
real time as you move around the map. Two things control its size:

### 1. Zoom Level — "How close am I looking?"

Think of zoom like a camera lens. At **zoom 8** you're looking at the whole French
Riviera from space — cities are specks, the boat marker is tiny (~17 dp) so it
doesn't cover half of Nice. At **zoom 18** you're practically standing on the dock
— you can see individual streets, so the boat grows huge (~543 dp) to match the
level of detail.

| Zoom | What you see on screen | Boat size |
|------|------------------------|-----------|
| **8** | Whole region (Marseille → Italy) | ≈ 17 dp — a small icon |
| **11** | City scale (Cannes → Antibes) | 48 dp — the "normal" size |
| **14** | Neighborhood scale (a few km across) | ≈ 136 dp — clearly visible |
| **16** | Street level (hundreds of meters) | ≈ 271 dp — big and bold |
| **18** | Dock level (a single marina) | ≈ 543 dp — fills the crosshair area |

The boat doesn't grow randomly — it follows the same exponential rule the map
itself uses (every +1 zoom doubles the ground detail). A **mitigating factor of
0.5×** slows it down so it doesn't get out of control.

> **Example:** You open the app at zoom 11 — the boat is a comfortable 48 dp.
> You pinch-zoom into the Port of Cannes at zoom 16 — the boat has grown to
> ~271 dp, proportional to how much more detail you now see on the map.

### 2. Distance to Coast — "Am I about to run aground?"

When you're far out at sea, the boat marker can be big and proud. But as you
approach the coastline, it shrinks — otherwise it would visually overlap the
land and look like you've already crashed into the beach.

| Distance from shore | Boat size multiplier | Feels like… |
|---------------------|---------------------|-------------|
| **0 m** (on the coastline) | 0.5× (half size) | "I'm right at the edge — tiny boat, careful!" |
| **500 m** | 0.625× | "Approaching the bay…" |
| **1000 m** | 0.75× | "A few minutes from shore…" |
| **2000 m+** | 1.0× (full size) | "Open water — full throttle!" |

> **Example:** You're zoomed in at level 14 offshore (~136 dp boat). You pan
> toward the Cannes shoreline. At 500 m the boat is ~85 dp. Right on the
> beach it's ~68 dp — half the offshore size. No pretending you're still at sea.

---

### Under the Hood (for the curious)

The two effects combine: **`finalSize = zoomSize × distanceMultiplier`**.

**Zoom formula** — exponential, matching how the map itself scales:
```
dp = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
```

**Distance ramp** — linear shrink near the coast:
```
multiplier = 0.5 + 0.5 × clamp(distance / 2000, 0, 1)
```

All tuning knobs live as `private const val` at the top of
[`CenterMarkerOverlay`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:526):

| Constant | Value | What it does |
|----------|-------|-------------|
| `REF_ZOOM` | 11.0 | Zoom where the marker is at its "normal" base size |
| `BOAT_BASE_DP` | 48.0 | Boat size at zoom 11 (dp) |
| `DOT_BASE_DP` | 16.0 | Land dot size at zoom 11 (dp) |
| `ZOOM_EXPONENT` | 0.5 | How aggressively zoom changes the size (1.0 = exactly like the map) |
| `DIST_SHRINK_MIN_MULT` | 0.5 | Smallest the marker gets on the coastline |
| `DIST_SHRINK_RAMP_M` | 2000.0 | How far from shore until the marker reaches full size (meters) |

Change a value, rebuild — the marker behavior changes instantly. No hunting
through code.

### Data Flow

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
              ├─ sizeByZoom = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
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
