# Hydration: Ui_General

**Session:** Landscape drawer/settings sizing + scrim matrix + dashboard close semantics.

1. **Landscape sizing** — menu drawer = its portrait width (75% short edge × scale); settings,
   track history, and marker management = their portrait width (short edge × scale). Scale driven
   by `ui.landscape.panel.widthScale` (maro.properties, default 1.2).
2. **Scrim matrix** — unified scrim 0.50 for menu/settings/track-history/marker-management
   (tap closes); marker view + track view scrim-less and interactive; wizard scrim only while the
   keyboard is open (tap dismisses keyboard, keeps form); fan transparent; screen lock fully visible.
3. **Dashboard close** — Back/menu/create-marker close the marker + track view; the layer fan
   does not.
4. **Build:** SUCCESS (`assembleDebug`).

**Target files:**
- `OverlayLayer.kt`, `MapScreen.kt`, `AppConfig.kt`, `maro.properties`

**Last Bake:** 2026-09-04 21:04 UTC
