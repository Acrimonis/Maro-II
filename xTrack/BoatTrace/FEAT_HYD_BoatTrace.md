# BoatTrace — Hydration Snapshot

**Baked at:** 2026-06-20 14:29 UTC

## Session Summary

mtrack-setting-opacity subfeature implemented:
- Renamed `trackingOpacity*` → `trackingTransparency*` (0%=opaque, 100%=invisible, defaults 20/80)
- Added `showSettings` to track overlay LaunchedEffect keys → redraw on settings close
- Removed overlay diff guards (early return + skip-existing) → opacity/color changes take effect
- Color picker: 12→16 presets (+Cyan/Lime/Pink/Indigo), grid 150→160→216dp
- Active track swatch aligned to 24dp/4dp (matches pair rows)
- MaterialTheme primary set to blue `#1565C0` (all dialog controls)


Documentation cleanup session:
- Created `FEAT_DOC_BoatTrace_decisions.md` — 40+ functional decisions across 7 categories
- Stripped all completed `#### Todos` from `[x]` subfeatures (9 subfeatures cleaned)
- Moved remaining unchecked items to parent-level `## Todos`
- Condensed `## Implemented` to 20 lines of current-state-only prose
- Removed all deprecated terminology (IDLE/RECORDING/PAUSED/FINALIZING, "Tack", "simplified from")
- Updated `## Rules` throughout to use current OFF/ON/ON nomenclature

## Key Files Modified
- `SettingsManager.kt` — transparency rename, new prefs keys, clean break
- `MapScreen.kt` — transparency inversion, showSettings key, overlay rebuild, color picker fixes
- `build.gradle.kts` — defaults 20/80
- `MainActivity.kt` — MaterialTheme primary blue
- `FEAT_DSC_BoatTrace.md` — subfeature [x], Implemented section
- `GLOBAL_CONTEXT.md` — subfeature pointer, mode-lock rule

## Next Steps
- [ ] E2E verification on device
- [ ] Track list UI polish per FEAT_PLN_BoatTrace_TrackList_Design.md
- [ ] Remaining polish items (tooltip, icon visual verify, drawer layout review)
