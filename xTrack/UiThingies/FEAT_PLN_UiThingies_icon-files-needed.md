# PNG Files to Generate for Maro II App

## A. App Icon — Legacy (from Maro_II_app.png — has background)
Used as fallback on pre-API-26 devices.
1. `app/src/main/res/mipmap-mdpi/ic_launcher.png` → 48×48px ✅ EXISTS
2. `app/src/main/res/mipmap-hdpi/ic_launcher.png` → 72×72px ✅ EXISTS
3. `app/src/main/res/mipmap-xhdpi/ic_launcher.png` → 96×96px ✅ EXISTS
4. `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` → 144×144px ✅ EXISTS
5. `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` → 192×192px ✅ EXISTS

## B. Adaptive Foreground (from Maro_II.png — transparent bg)
Canvas MUST be 108dp. Logo must sit within the 72dp safe zone (inner 66.67%).
6. `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png` → 108×108px ✅
7. `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png` → 162×162px ✅
8. `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png` → 216×216px ✅
9. `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png` → 324×324px ✅
10. `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png` → 432×432px ✅

## C. Marker Icon (from Maro_II.png — transparent bg)
11. `app/src/main/res/drawable/maro_marker.png` → 172×172px ✅ EXISTS

## D. Adaptive Icon XML (mipmap-anydpi-v26)
12. `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` ✅ EXISTS
13. `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` ✅ EXISTS

## E. Background Layer
14. `app/src/main/res/drawable/ic_launcher_background.xml` → solid #FF184B8C ✅ EXISTS

---

## Regeneration Instructions (for items 6–10):
- Source: `Maro_II.png` (1589×1589, transparent background)
- Canvas: 108dp square at each density (108/162/216/324/432 px)
- Safe zone: center the logo within the inner 72dp (66.67%) circle
- The logo should NOT touch the edges of the 108dp canvas
- Output: PNG with transparency (32-bit RGBA)
