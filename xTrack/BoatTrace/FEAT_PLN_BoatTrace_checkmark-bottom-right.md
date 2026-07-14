# BoatTrace — Check Mark Bottom-Right

**Feature:** BoatTrace
**Subfeature:** merge-tracks
**Created:** 2026-07-14
**Implemented:** 2026-07-14

## Change

In [`ListOverlayScaffold.kt:359`](../../app/src/main/java/ykws/android/maro/ui/components/ListOverlayScaffold.kt:359), move the selected-item check mark badge from `Alignment.TopEnd` to `Alignment.BottomEnd`.

```
// Before:
.align(Alignment.TopEnd)

// After:
.align(Alignment.BottomEnd)
```

One-line change. No other files affected.
