<!-- scope: feature -->
# Regulated Zones — Final Icon Classification (Most → Least Restrictive)

## Ordered by Restrictiveness

| # | Display Key | Icon | Bg | Strike | Type | Meaning |
|---|-------------|------|----|--------|------|---------|
| 1 | `SPEED_LIMIT` | **5/10** | 🔴 Red | — | 🛑 Hard rule | "Max speed N kn — fine if exceeded" |
| 2 | `NO_ACCESS` | 🚤 | 🔵 Blue | ✓ | 🛑 Hard rule | "No entry — stay out" |
| 3 | `ANCHORING_PROHIBITED` | ⚓ | 🔵 Blue | ✓ | 🛑 Hard rule | "No anchoring ever — fine applies" |
| 4 | `NO_DIVING` | 🤿 | 🔵 Blue | ✓ | 🛑 Hard rule | "No diving/underwater activities" |
| 5 | **`ANCHOR_CAUTION`** | **⚓** | **🟢 Green** | **—** | ⚠️ Advisory | "Don't anchor on Posidonia seagrass. Sand is fine." |
| 6 | `MOORING` | 🛥️ | 🔵 Blue | — | ✅ Permissive | "Mooring area — park here" |
| 7 | `ENVIRONMENTAL` | 🌿 | 🔵 Blue | — | ℹ️ Info | "Protected marine area — be aware" |
| 8 | `INFORMATION` | ℹ️ | 🔵 Blue | — | ℹ️ Info | "Navigation restrictions — read details" |
| 9 | `SEAPLANE` | ✈️ | ⚪ Grey | — | ℹ️ Info | "Seaplane activity — watch out" |

## Key Changes from Previous Version

| Change | Why |
|--------|-----|
| Split `NO_ANCHOR` → `ANCHORING_PROHIBITED` + `ANCHOR_CAUTION` | Hard prohibition (RESTRN 1/2) vs ecological advisory (Posidonia) are different user actions |
| `ANCHOR_CAUTION` on **green** background, no strike | Green = ecological context, no strike = not a hard ban |
| Removed strike from `NO_ACCESS` icon | Used a different icon (🚤) to differentiate from other struck icons — keeps strike for hard prohibitions |
| Ordered 1–9 by sailor impact | Hard rules first, then advisory, then permissive, then informational |

## Trigger Logic

| Display Key | Triggered By |
|-------------|-------------|
| `SPEED_LIMIT` | CATREA=27, restrn=1, vitesse_max, INFORM/TXTDSC speed, keyword `vitesse`/`noeud`/`knot` |
| `NO_ACCESS` | RESTRN=7/8, restrn=10/11/12, INPN biotope, keyword `interdit`/`prohibé`/`accès` |
| `ANCHORING_PROHIBITED` | RESTRN=1/2, restrn=7, keyword `mouillage interdit`/`ancrage interdit` (**not** posidonia) |
| `NO_DIVING` | RESTRN=10 code check, keyword `plongée`/`diving`/`subaquatique` |
| `ANCHOR_CAUTION` | **New:** keyword `posidonie`/`herbier`/`posidonia` (ecological anchoring caution) |
| `MOORING` | restrn=18, keyword `mooring`/`amarrage`/`corps mort` |
| `ENVIRONMENTAL` | ENVIRONMENTAL type with no keywords matched (generic Natura 2000 / restrn=28) |
| `INFORMATION` | NAVIGATION_RESTRICTION type with no keywords matched (generic CATREA=12 / Réserve Nat. / restrn=27) |
| `SEAPLANE` | Keyword `seaplane`/`hydravion` |

## `ZoneDisplayCategory` Enum

```kotlin
enum class ZoneDisplayCategory {
    SPEED_LIMIT,           // 5/10 on red        — hard speed rule
    NO_ACCESS,             // 🚤 + strike blue    — hard access ban
    ANCHORING_PROHIBITED,  // ⚓ + strike blue    — hard anchor ban
    NO_DIVING,             // 🤿 + strike blue    — hard diving ban
    ANCHOR_CAUTION,        // ⚓ on green         — ecological anchor caution
    MOORING,               // 🛥️ on blue         — mooring permitted
    ENVIRONMENTAL,         // 🌿 on blue          — protected area
    INFORMATION,           // ℹ️ on blue          — navigation restriction
    SEAPLANE,              // ✈️ on grey          — seaplane activity
}
```

