# Maro II — Android Icon Files

## Source Images

| Source file | Path | Purpose | Background |
|-------------|------|---------|------------|
| `Maro_II_app.png` | `c:\Users\Alexandra\Desktop\nICo StuFF\.src\Maro II\Maro_II_app.png` | App launcher icon | ✅ Has background |
| `Maro_II.png` | `c:\Users\Alexandra\Desktop\nICo StuFF\.src\Maro II\Maro_II.png` | Adaptive foreground + Marker | ❌ Transparent bg |

## Android Icon Specifications

- **Format**: PNG only (Android does not support `.ico`)
- **App icon reference**: `@mipmap/ic_launcher` (set via `AndroidManifest.xml`)
- **Adaptive icon API**: 26+ (our `minSdk = 26` ✅)
  - Foreground: central 72×72dp safe zone on 108×108dp canvas
  - Background: solid color/drawable — currently `@drawable/ic_launcher_background`

## Destination Files (11 total)

### A. App Icon — replace existing `ic_launcher.png` (5 files)

Generate from **`Maro_II_app.png`** (has background).

| # | Exact full path | Required size |
|---|-----------------|---------------|
| 1 | `app/src/main/res/mipmap-mdpi/ic_launcher.png` | **48×48 px** |
| 2 | `app/src/main/res/mipmap-hdpi/ic_launcher.png` | **72×72 px** |
| 3 | `app/src/main/res/mipmap-xhdpi/ic_launcher.png` | **96×96 px** |
| 4 | `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` | **144×144 px** |
| 5 | `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | **192×192 px** |

### B. Adaptive Foreground — replace existing `ic_launcher_foreground.png` (5 files)

Generate from **`Maro_II.png`** (transparent background). Same pixel sizes as above.

| # | Exact full path | Required size |
|---|-----------------|---------------|
| 6 | `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png` | **48×48 px** |
| 7 | `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png` | **72×72 px** |
| 8 | `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png` | **96×96 px** |
| 9 | `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png` | **144×144 px** |
| 10 | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` | **192×192 px** |

### C. Center Marker — new file (1 file)

Generate from **`Maro_II.png`** (transparent background). Single file, no density variants needed — size is set in Compose code.

| # | Exact full path | Suggested size |
|---|-----------------|----------------|
| 11 | `app/src/main/res/drawable/maro_marker.png` | **172×172 px** (or any) |

## Adaptive Icon XML (already correct — no changes needed)

These files reference the PNGs above and don't need modification:

- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

## What Happens After Files Are Placed

1. Add `android:icon="@mipmap/ic_launcher"` to `AndroidManifest.xml`
2. Add `maro_marker.png` center marker overlay in `MapScreen.kt`
3. Wire map camera listener → dynamic `isWater` recomputation
4. Remove old `_waterTestPoint` logic
