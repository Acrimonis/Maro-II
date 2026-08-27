# Hydration: Ui_General

**Session:** screen-lock — implemented. Touch-input lock (splash guard): a 📵 toggle
(right of the Earth/Water icon) locks the screen so the map ignores all touch except the
unlock toggle and the zoom +/− buttons (double-tap only while locked — single splash taps
ignored). Full-screen consume-all `LockScrim`; top-most duplicate unlock button +
`ZoomControls` + `LockBanner` (exit-toast style, 2s auto-dismiss); locked state colour =
`semantic.info` (blue). `status.lock.*` tokens in `colors.properties` + `AppConfig`.
BUILD SUCCESSFUL.

**Target files:**
- `MapScreen.kt`, `MapControlButton.kt`, `colors.properties`, `AppConfig.kt`, `strings.xml` (EN+FR)

**Plans:**
- `xTrack/Ui_General/260827_FEAT_PLN_Ui_General_touch-input-lock.md`

**Last Bake:** 2026-08-27 14:04 UTC
