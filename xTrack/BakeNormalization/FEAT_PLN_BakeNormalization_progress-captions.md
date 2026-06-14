# Progress Captions — Discussion

<!-- scope: feature -->

## Current State

The `LoadingOverlay` composable is shared between coastline loading and raster generation. It shows:

```
[spinner]
Loading coastline...        ← hardcoded string (R.string.map_loading_coastline)
Depth colour map            ← phase from GenerationProgress (only if non-empty)
[===========        ] 42%
```

For raster generation, "Loading coastline" is misleading. The per-step captions are already reasonable.

## Proposal

### Option A: Parameterise LoadingOverlay

Add a `title: String` parameter (default = existing "Loading coastline"):

```kotlin
@Composable
private fun LoadingOverlay(
    progress: GenerationProgress,
    title: String = stringResource(R.string.map_loading_coastline),
    modifier: Modifier = Modifier
)
```

Then callers pass:
- Coastline loading: `LoadingOverlay(progress)` (default title)
- Raster generation: `LoadingOverlay(progress, title = "Generating depth layers...")`

### Option B: Phase-driven title

Replace the hardcoded "Loading coastline" with the phase text when it's non-empty. When phase is empty, show a generic "Loading...".

```kotlin
Text(
    text = if (progress.phase.isNotEmpty()) progress.phase 
           else stringResource(R.string.map_loading_coastline),
    ...
)
```

Then set the initial phase in generateRasterLayers to "Generating depth layers..." and each step updates it.

### Per-step captions (both options)

| Step | Current | Proposed |
|---|---|---|
| Initial | (empty) | "Generating depth layers..." |
| Grid load | "Depth grid" | "Loading depth grid..." |
| Isobaths | "Isobath contours" | "Deriving contour lines..." |
| Colour raster | "Depth colour map" | "Rendering depth colours..." |
| Warning raster | "Shallow warning overlay" | "Rendering hazard overlay..." |

Or keep current terse captions — they're fine.

## Recommendation

Option A — minimal change, explicit caller control. Add `title` param to LoadingOverlay with default value for backward compatibility. Pass "Generating depth layers..." from raster generation path.
