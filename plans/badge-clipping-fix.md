# Badge Clipping Fix

## Root Cause

`Button(shape = CircleShape)` clips all content to the circle (radius 32dp from center at 32dp, 32dp). The badge at `Alignment.TopEnd` extends to (64dp, 0) — **45dp from center** — so it gets clipped.

## Fix

Restructure both `ArcAnchorButton` and the dummy anchor in `ArcButtonOverlay` to have the badge OUTSIDE the clipped container.

### ArcAnchorButton (lines 59-85)

Current structure:
```
Button(64dp, CircleShape) ← clips everything
  └── Box(64dp)
       ├── Box(32dp) + Canvas
       └── Box(18dp, TopEnd) badge ← CLIPPED
```

New structure:
```
Box(64dp)  ← no clip, holds the layout
  ├── Box(64dp, CircleShape, clickable)  ← clipped button area
  │   └── Box(32dp) + Canvas (3-stripe icon)
  └── Box(18dp, TopEnd) badge  ← OUTSIDE the clip, fully visible
```

The `onGloballyPositioned` callback stays on the outer Box to report correct anchor center.

### Dummy anchor in ArcButtonOverlay (lines 143-164)

Same restructuring — move badge outside the CircleShape clip.

### MapScreen.kt

No changes needed — `activeLayerCount` is already passed.

## Files to modify
- `app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt` — restructure both anchors
