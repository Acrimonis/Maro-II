# Hydration: Ui_General

**Session:** Drawer menu cards + icon size normalization.

**State:**
- `menu [x]` — card-wrapped GPS toggle and Track Recording rows in TrackDrawerOverlay; live stats merged into Track Recording card with divider; "Manage Tracks" → nav action with trailing chevron; row height normalized to 48dp matching Switch touch target
- `icon size [x]` — promoted `ICON_SIZE_DP=28` to `ButtonColors.iconSizeDp`, Settings gear icon now matches map action icons
- `ui guidelines [x]` — added §8 Drawer Card Pattern to settings-page-guidelines.md; discussed rename to ui-guidelines.md

**What happened:**
- Wrapped GPS toggle and Track Recording rows in card `Column`s with `uiCardBackground` + `RoundedCornerShape(12dp)` + `padding(16dp h, 10dp v)`.
- Merged live stats into Track Recording card with `HorizontalDivider(0.5dp)` when recording.
- Changed "Manage Tracks..." → "Manage Tracks" with trailing `KeyboardArrowRight` chevron as navigation affordance.
- Added `heightIn(min = 48.dp)` to Manage Tracks row matching Switch touch target.
- Promoted `ICON_SIZE_DP=28` from private const to `ButtonColors.iconSizeDp`; Settings gear icon sized to match map buttons.
- Added §8 Drawer Card Pattern to `settings-page-guidelines.md`.
- Discussed renaming `settings-page-guidelines.md` → `ui-guidelines.md`.

**Key Files:**
- `app/src/main/java/ykws/android/maro/ui/map/TrackDrawerOverlay.kt` — 2 cards, merged stats, chevron, 48dp row
- `app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt` — `ButtonColors.iconSizeDp = 28`
- `docs/settings-page-guidelines.md` — §8 Drawer Card Pattern

**Last Bake:** 2026-06-24 12:07 UTC
