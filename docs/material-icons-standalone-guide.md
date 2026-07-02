# Material Symbols — Standalone Icon Import

> How to add Google Fonts Material Symbols icons as standalone Compose `ImageVector` files, without the `material-icons-extended` library.

## When to Use

Use standalone `.kt` files when:
- The icon is a newer **Material Symbol** not in the Compose `material-icons-extended` library
- The machine can't handle the full extended library at compile time (OOM on ~25 MB AAR)
- You need only a few specific icons

Each standalone file is ~2 KB source → ~2-3 KB in the APK after compilation.

## Step-by-step

### 1. Find the icon

Go to https://fonts.google.com/icons — search for the icon name (e.g., `stacks`, `activity_zone`).

### 2. Get the Compose .kt URL

1. Click the icon to open its detail page
2. Select the **Android** tab
3. Under the Compose section, find the URL ending in `.kt`:

   ```
   https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/40dp/activity_zone.kt?var=opsz,wght,FILL,GRAD,ROND@40,400,0,0,50
   ```

4. Copy this URL.

### 3. Download the .kt file

```bash
curl -o IconName.kt "https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/40dp/icon_name.kt?var=opsz,wght,FILL,GRAD,ROND@40,400,0,0,50"
```

Replace `icon_name` and `IconName.kt` with the actual icon.

> **Note:** The response may be gzipped. Use `curl --compressed` or decompress manually.

### 4. Fix the package

Open the downloaded file. The generated file has:

```kotlin
package com.example.test
```

Change to:

```kotlin
package ykws.android.maro.ui.icons
```

### 5. Place in the project

Save the file to:

```
app/src/main/java/ykws/android/maro/ui/icons/[IconName].kt
```

Create the `icons/` directory if it doesn't exist.

### 6. Use in Compose code

The generated file exposes a top-level `ImageVector` variable with the icon's snake_case name:

```kotlin
import ykws.android.maro.ui.icons.Stacks
import ykws.android.maro.ui.icons.Activity_zone

Icon(imageVector = Stacks, contentDescription = null, tint = Color.White)
```

> **Note:** The variable name uses **snake_case** matching the Google Fonts icon slug (e.g., `activity_zone`, `conversion_path`), not CamelCase.

## Icon ↔ File Mapping (Current Project)

| Google Fonts | Compose library (BOM 2024.05) | Standalone .kt variable |
|-------------|-------------------------------|------------------------|
| `stacks` | `Layers` (fallback) | `Stacks` |
| `output_circle` | `Circle` (fallback) | `Output_circle` |
| `activity_zone` | `GpsFixed` (fallback) | `Activity_zone` |
| `conversion_path` | `AltRoute` (fallback) | `Conversion_path` |
| `filter_alt` | — (extended only) | `FilterAlt` |
| `filter_list` | — (extended only) | `FilterList` |
| `refresh` | — (extended only) | `Refresh` |
| `where_to_vote` | `WhereToVote` (fallback) | `where_to_vote` |

**Core library icons** (no standalone .kt needed): `ArrowDropUp`, `ArrowDropDown`, `KeyboardArrowUp`, `KeyboardArrowDown`, `ArrowUpward`, `ArrowDownward`, `Sort`, `Add`, `Remove`, `Settings`, `Warning`, `AreaChart`.

If you add the standalone files, update `FanIconComponents.kt` to use the `ImageVector` variables directly instead of the Compose library fallbacks.

## Example: Full Flow for `stacks`

```bash
# 1. Download
curl -o Stacks.kt "https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/40dp/stacks.kt?var=opsz,wght,FILL,GRAD,ROND@40,400,0,0,50"

# 2. Fix package (sed or manual)
#    package com.example.test  →  package ykws.android.maro.ui.icons

# 3. Move to project
move Stacks.kt app\src\main\java\ykws\android\maro\ui\icons\Stacks.kt
```

Then in `FanIconComponents.kt`:

```kotlin
import ykws.android.maro.ui.icons.Stacks

@Composable
fun ThreeStripeLayerIcon(alpha: Float) {
    Icon(
        imageVector = Stacks,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp).alpha(alpha)
    )
}
```
