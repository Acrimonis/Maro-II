# Route — route card's left bar dashed

**Topic:** The routes-list card's left accent bar becomes dashed so a route card reads apart from a track card at a glance.

## Decision

- **Dashed, not double, not colour-only.** The route card's leading bar draws the saved-route dash rhythm; the track card keeps the solid strip.
- The map already speaks that language — saved routes are drawn dashed, recorded tracks solid — so the list card mirrors the map instead of inventing a second code.

## Scope

- `ListSelectionRail` gains a `dashed` parameter (default `false`), drawing the 6 dp bar with `PathEffect.dashPathEffect` when set.
- `TrackCardContent` passes `dashed = summary.route`, so only route cards dash; track cards and the marker card's rail are untouched.
- The dash reuses `AppConfig.trackRouteDashOnDp` / `AppConfig.trackRouteDashOffDp` — the same rhythm the map's saved-route stroke reads.

## Rejected

- **Double line** — invents geometry the map never uses, and at 6 dp two strokes blur into one at low density unless the rail widens and reflows both cards.
- **Colour-only** — the bar's colour already encodes rank (newest→oldest fade), so a second hue channel would muddy it.
