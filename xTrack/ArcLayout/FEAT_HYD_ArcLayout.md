**Last Bake:** 2026-06-13 12:51 (UTC+2)
**State:** All todos complete. Feature functional.

**Session summary:**
- Fixed dp/px mismatch in arc positioning math (R and half-btn offset were raw pixels, not density-scaled dp)
- Added collapse animation (200ms retract to anchor, 400ms keep-alive before overlay removal)
- Added dummy anchor (fully opaque white + matching 3-stripe icon + badge) rendered on top of arc buttons — buttons emerge from behind it as they fan outward
- Arc radius set to R=80dp (16dp gaps between 64dp buttons)
- Badge moved outside CircleShape clip (was being cropped at top-right of circle)
- Badge text centering fixed with explicit inner Box wrapping
- 3-stripe icon flipped vertically
- Settings/Zoom +/- fade out/in when arc opens/closes (220ms tween)
- Badge now also renders on dummy anchor (activeLayerCount passed through)

**Target files:**
- `ArcLayoutToggle.kt` — all arc menu logic
- `MapScreen.kt` — fade animation, arcExpanded param pass-through
- `SettingsManager.kt` — settings persistence (unchanged)
- `CoastlineViewModel.kt` — toggle methods (unchanged)

**Next step:** None — feature complete.
