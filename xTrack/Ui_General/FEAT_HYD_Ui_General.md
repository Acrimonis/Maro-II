# Hydration: Ui_General

**Session:** landscape-menu-drawer — implemented. Landscape menu drawer overflow fixed:
the body now scrolls when content exceeds the viewport. `MenuDrawerOverlay` passes
`DrawerScaffold(scrollable = true, suppressOverscrollWhenFits = true)`. The scaffold
hoists `rememberScrollState()` and tracks `canScroll` via
`derivedStateOf { scrollState.maxValue > 0 }`, passing `overscrollEffect = null` while
content fits. Portrait (content fits, zero scroll range) therefore renders identically
to before with no overscroll glow; landscape and any future menu growth scroll
automatically. The fixed header (back + "Maro II" + settings gear) stays pinned. The new
opt-in `suppressOverscrollWhenFits` param defaults to false, leaving MarkerDrawer and
TrackInfoDrawer unchanged. BUILD SUCCESSFUL.

**Target files:**
- `DrawerScaffold.kt`, `MenuDrawerOverlay.kt`

**Plans:**
- `xTrack/Ui_General/260816_FEAT_PLN_Ui_General_landscape-menu-drawer.md`

**Last Bake:** 2026-08-16 08:05 UTC
