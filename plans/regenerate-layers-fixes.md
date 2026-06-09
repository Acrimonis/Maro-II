# Regenerate Layers — Settings Fixes

<!-- scope: feature -->

## Gaps Identified

Three issues with the current "Raster Cache" settings section:

1. **Placement** — sits at the very bottom (above footer); should be between "Display" and "Advanced" sections
2. **Naming** — "Raster Cache" / "Regenerate Rasters" → "Regenerate Layers" section with per-layer checkboxes
3. **Per-layer selection** — plan required checkboxes for which raster to regenerate; currently regenerates both unconditionally
4. **Close on click** — clicking regenerate should dismiss settings then launch generation

## Fix Plan

### 1. Change `onRegenerateRasters` callback signature
`() -> Unit` → `(List<RasterCache.Layer>) -> Unit`

### 2. Move section + rename
Move the section block from before footer to between "Display" and "Advanced" (after low-depth warning opacity slider, before language picker). Section header: "Regenerate Layers".

### 3. Add per-layer checkboxes
```kotlin
var regenColour by remember { mutableStateOf(true) }
var regenWarning by remember { mutableStateOf(true) }
```
Two `Row` items with `Switch` + label:
- "Depth colour map"
- "Low-depth warning overlay"

### 4. Regenerate button closes settings
```kotlin
Button(onClick = {
    val selected = buildList {
        if (regenColour) add(RasterCache.Layer.DEPTH_COLOUR)
        if (regenWarning) add(RasterCache.Layer.LOW_DEPTH_WARNING)
    }
    onDismiss()
    onRegenerateRasters(selected)
})
```

### 5. Update call site in MapScreen
```kotlin
onRegenerateRasters = { layers ->
    val waterTest = if (state is CoastlineState.Ready) viewModel::isOnWater else { _: Double, _: Double -> true }
    depthViewModel.generateRasterLayers(context, layers, appSettings, waterTest)
}
```

## Files Modified
- `MapScreen.kt` — SettingsOverlay call + signature + section

## Related
- Feature: `DepthSafety` subfeature `caching`
