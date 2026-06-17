<!-- scope: feature -->
# Icon Rendering Overhaul Plan

## Final Spec

| Category | Background | Emoji | Strike |
|---|---|---|---|
| NO_ANCHOR | `0xFF1565C0` dark blue @75% | ⚓ anchor | Thin red diagonal |
| MOORING | `0xFF1565C0` dark blue @75% | 🚤 speedboat | None |
| SPEED_LIMIT | `0xFFE53935` red @75% | Bold number text | N/A |
| NO_DIVING | `0xFF1565C0` dark blue @75% | 🤿 diving mask | Thin red diagonal |
| SEAPLANE | `0xFF1565C0` dark blue @50% | 🛬 airplane landing | None |
| NO_ACCESS | `0xFF1565C0` dark blue @75% | 🚤 speedboat | Thin red diagonal |

Key changes from current:
1. Background colors all uniform dark blue (except SPEED)
2. Emoji Text restored for all symbols
3. Canvas-drawn thin red diagonal strike overlay on top for NO_ANCHOR, NO_DIVING, NO_ACCESS
4. NO_ACCESS changed from 🚫 → 🚤 (speedboat) with strike
5. MOORING and SEAPLANE: emoji only, no strike
6. Strike thickness: `sw * 0.8f` (thin)

