<!-- scope: feature -->
# Rename: `ZoneConfig` → `AppConfig` + Move to `config/` package

## Why
- `ZoneConfig` now loads ALL app configuration (colours, UI settings, navigation, depth, zones), not just zone data
- Placed in `ui.map` package but it's a configuration concern, not UI
- `AppConfig` is clear, Spring-idiomatic naming

## Steps

### 1. Create new file

`app/src/main/java/ykws/android/maro/config/AppConfig.kt`

Copy the entire content of `ZoneConfig.kt` into this new file. Change:

- `package ykws.android.maro.ui.map` → `package ykws.android.maro.config`
- `object ZoneConfig` → `object AppConfig`
- Update KDoc references from `zone.properties` / `maro.properties` to mention `colors.properties` as primary

### 2. Update ALL import + reference lines

Every file that currently references `ZoneConfig` needs:

- Import change: `import ykws.android.maro.ui.map.ZoneConfig` → `import ykws.android.maro.config.AppConfig`
- Reference change: `ZoneConfig.` → `AppConfig.`

Files affected (18 total):

| File | References |
|---|---|
| `MainActivity.kt` | `ZoneConfig.init(this)` |
| `MapScreen.kt` | `ZoneConfig.*` (~40 refs) |
| `DashboardPanel.kt` | `ZoneConfig.*` (~17 refs via DashboardColors) |
| `FanIconComponents.kt` | `ZoneConfig.*` (4 refs in ButtonColors) |
| `ArcLayoutToggle.kt` | `ZoneConfig.*` (4 refs) |
| `LowDepthWarningBitmap.kt` | `ZoneConfig.overlayLowDepthColor` |
| `DepthBitmap.kt` | `ZoneConfig.*` (default param) |
| `DepthViewModel.kt` | `ZoneConfig.*` (2 refs) |
| `DepthColorRamp.kt` | `ZoneConfig.*` (~10 refs) |
| `RegulatedZoneIconProvider.kt` | `ZoneConfig.*` (~11 refs) |
| `CoastlineViewModel.kt` | `ZoneConfig.overlayLowDepthMinOpacity` |
| `RasterCache.kt` | `ZoneConfig.rasterColorsHash` (used in DepthViewModel and MapScreen, not directly) |
| `ZoneConfig.kt` itself | (deleted after move) |

### 3. Delete old file

`app/src/main/java/ykws/android/maro/ui/map/ZoneConfig.kt`

### 4. Build

`gradlew assembleDebug` — fix any compilation errors from missed references.

## Risk

Low. This is a pure rename + package move — no logic changes. All tests should pass identically.
