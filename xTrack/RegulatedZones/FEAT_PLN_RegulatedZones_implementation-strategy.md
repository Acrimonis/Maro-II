# Regulated Zones — Implementation Strategy

## Will We Have a Functional Product?

**Yes — after all 4 phases.** Each phase produces a testable increment:

| Phase | What Works | What Doesn't Yet | Testable? |
|-------|-----------|-----------------|-----------|
| **1. Data Extraction** | SHOM + INPN data flows, .bin generated, bbox expanded | New fields not populated, old format still in use | Run prebake, check console output |
| **2. New Data Format** | 14-field `RegulatedZone`, sealed `classification`, `SpeedSource` | Old .bin incompatible (regenerate required) | Run prebake, inspect .bin bytes |
| **3. Icon Provider** | 8 categories registered with correct colours/strikes/alpha | No zones produce ENVIRONMENTAL/INFORMATION yet | Unit test `displayCategories()` |
| **4. Display Logic** | `displayCategories()` enhanced: RESTRN=10 fix, keyword scanning, fallbacks | Nothing — this completes the chain | Run app, see icons on map |

After Phase 4: **functional product** — zones from all sources appear on map with correct icons.

---

## Strategy: How the UI/Client Adapts

### Principle: Minimal UI Changes

The existing UI already handles categories **dynamically** — the [`RegulatedZoneWarningStrip`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2929) composable calls `zone.displayCategories()` and renders whatever categories it returns. Adding 2 new categories requires only:

1. **`RegulatedZoneIconProvider.kt`** — add emoji/colour/alpha for `ENVIRONMENTAL` and `INFORMATION`
2. **`MapScreen.kt`** — verify `hasStrike` logic and switch `colorForCategory` to return blue for the new ones

No structural UI changes needed.

### Data Flow After Changes

```mermaid
flowchart LR
    subgraph Sources
        A[SHOM INSPIRE] --> C[ShomRegulationClient]
        B[SHOM INSPIRE REGNAV] --> C
        D[INPN] --> E[InpnRegulationClient]
        F[Seeds] --> G[RegulationSeeds]
    end

    subgraph Pipeline
        C --> H[RegulationAggregator]
        E --> H
        G --> H
        H --> I[regulateZone -> displayCategories]
    end

    subgraph UI
        I --> J[RegulatedZoneWarningStrip<br/>reads displayCategories()]
        I --> K[drawRegulatedZones<br/>reads zoneType for polygon colour]
    end

    J --> L{Match?}
    L -->|SPEED_LIMIT| M[5/10 on Red]
    L -->|NO_ANCHOR| N[⚓ + strike on Blue]
    L -->|NO_ACCESS| O[🚤 + strike on Blue]
    L -->|MOORING| P[🛥️ on Blue]
    L -->|NO_DIVING| Q[🤿 + strike on Blue]
    L -->|SEAPLANE| R[✈️ on Grey]
    L -->|ENVIRONMENTAL| S[🌿 on Blue]
    L -->|INFORMATION| T[ℹ️ on Blue]
```

### What Changes in the Client

| Layer | Current Behaviour | After Changes | Change Required |
|-------|-----------------|---------------|-----------------|
| Map polygon colour | `regulatedZoneColor(zoneType)` — unchanged | Same — no change | **None** |
| Strip icon emoji | `emojiForCategory()` — 6 categories | 8 categories | Add 2 entries |
| Strip icon colour | `colorForCategory()` | All blue except SPEED/GREY | Update 6 entries |
| Strip icon alpha | `alphaForCategory()` | Same pattern | Add 2 entries |
| Stripe icon strike | `hasStrike = category in [NO_ANCHOR, NO_DIVING, NO_ACCESS]` | Same — new categories don't have strikes | **None** |
| Zone name/desc on tap | Not implemented (future `add-zone-text` subfeature) | Not yet | **None** |

### Adaptation Order

1. **Data format** → rewrite `RegulatedZone.kt`, `ShomRegulationClient.kt`, create `InpnRegulationClient.kt`
2. **Run prebake** → generate new `nice-frejus.bin` with populated `classification` and `speedSource`
3. **Update `displayCategories()`** → add RESTRN=10 code check, keyword scanning, fallback logic
4. **Update icon provider** → register 2 new categories, fix all backgrounds to blue
5. **Verify on device** → run app, check strip shows correct icons for each zone type
