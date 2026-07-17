# Speed/Enforcement Zone Auto-Show Extension — Design Plan

**Decided:**
1. ✅ Auto-show targets `regulatedZonesVisible` directly, shared with the manual cycle toggle
2. ❌ Non-speed enforcement zones excluded — speed-limited zones only
3. ❌ Warning strip stays coupled to `regulatedZonesVisible` — not independently auto-shown

## Goal

Extend the existing auto-show feature (currently fully wired for the **300m band**, partially wired for **speed zones**) to also cover the **regulated zone polygon overlay** and the **warning strip icon stack** when approaching regulated zones that have speed enforcement.

---

## Background — Current State

### Three Visibility Settings, Two Auto-Show Paths

| Setting | Controls | Auto-Show? | Used in UI? |
|---------|----------|-----------|-------------|
| `zone300Visible` | 300m band polygon overlay on map | ✅ Yes — distance+time, GPS/Demo modes | ✅ Read in MapScreen line 717 |
| `speedZonesVisible` | *(intended for)* speed zone overlay | ✅ Yes — same pattern as 300m | ❌ **Never read** in any UI component — dead code path |
| `regulatedZonesVisible` | All regulated zone polygons + warning strip | ❌ No auto-show | ✅ Read in MapScreen line 719 |

### Key Finding

The `speedZonesVisible` setting is set by `zoneAutoShowDecision()` in CoastlineViewModel (lines 546-547) and persisted in SharedPreferences, but **no UI component reads it**. The speed zone information reaches the user through:

- **SpeedLimitCard** dashboard tile — driven by `zoneSituation` StateFlow (currentZone + zonesAround)
- **Zone-ahead cone + green line** — driven by heading-ahead computation

But the **actual regulated zone polygon overlay** is gated on `regulatedZonesVisible`, which has NO auto-show. The **warning strip icons** are also gated on `regulatedZonesVisible`.

### Existing Auto-Show Architecture (Reusable)

```
zoneAutoShowDecision() in CoastlineViewModel.kt (line 1480+)
  ├─ Called for 300m band (line 497)  → toggles zone300Visible
  └─ Called for speed zones (line 528) → toggles speedZonesVisible (unused)
  
Shared config:
  - Distance threshold: zoneAutoRevealDistanceM (default 100m)
  - Time threshold: zoneAutoRevealTimeS (default 10s)
  - Per-mode toggles: {zone300,speedZone}AutoShow{Gps,Demo}
  - Manual override: {zone300,speedZone}ManuallyHidden + AutoRevealed flags
```

---

## Proposed Design

### Approach

Rather than trying to retrofit `speedZonesVisible` into the rendering pipeline, add a **third auto-show decision block** targeting `regulatedZonesVisible` directly. This is cleaner because:

1. `regulatedZonesVisible` already controls both polygons AND warning strip in one gate
2. No need to add a new rendering path — just reuses what's there
3. The auto-show logic (`zoneAutoShowDecision()`) is already generic and reusable

### New Settings Fields

Add to `AppSettings` in `SettingsManager.kt`:

```kotlin
// Regulated zone overlay auto-show (speed enforcement zones)
val regulatedZoneAutoShowGps: Boolean = true,   // GPS mode auto-show
val regulatedZoneAutoShowDemo: Boolean = true,   // Demo mode auto-show
```

Reuse the existing shared distance/time thresholds (`zoneAutoRevealDistanceM`, `zoneAutoRevealTimeS`).

### Pipeline Change (CoastlineViewModel.kt)

Add a third auto-show block in the `onEach` pipeline, AFTER the speed zone auto-show block (line 551):

```kotlin
// ── Regulated zone overlay auto-show (speed enforcement) ─────
val regAutoShowEnabled = if (cfg.gpsMode) cfg.regulatedZoneAutoShowGps else cfg.regulatedZoneAutoShowDemo
if (!regAutoShowEnabled) {
    regulatedZoneAutoRevealed = false
    lastDistToRegZone = shore.speedZoneQuery.distanceToBoundaryM
} else {
    val regDecision = zoneAutoShowDecision(
        dist = shore.speedZoneQuery.distanceToBoundaryM,
        prevDist = lastDistToRegZone,
        insideZone = shore.speedZoneQuery.insideAnyZone,
        sogKn = sogKn,
        isStopped = isStopped.value,
        armed = regulatedZoneManuallyHidden,
        autoRevealed = regulatedZoneAutoRevealed,
        zoneEntered = false,
        revealDistM = cfg.zoneAutoRevealDistanceM.toDouble(),
        revealTimeS = cfg.zoneAutoRevealTimeS.toDouble(),
        config = ZoneAutoShowConfig(
            hideOnCompliantInside = false,  // stay visible while inside
            hysteresisM = AppConfig.speedZoneHysteresisM
        )
    )
    regulatedZoneAutoRevealed = regDecision.autoRevealed
    when (regDecision.action) {
        AutoShowAction.REVEAL -> settingsManager.update { it.copy(regulatedZonesVisible = true) }
        AutoShowAction.HIDE   -> settingsManager.update { it.copy(regulatedZonesVisible = false) }
        AutoShowAction.NONE   -> {}
    }
    lastDistToRegZone = shore.speedZoneQuery.distanceToBoundaryM
}
```

### Manual Override (CoastlineViewModel.kt)

The existing `toggleCycleZoneLayers()` method (line 768) sets `zone300Visible` and `regulatedZonesVisible` through a 4-state cycle. When the user manually toggles `regulatedZonesVisible` off via the cycle button, we need to set a manual-hide flag so auto-show doesn't immediately re-reveal.

Add a method mirroring `toggleSpeedZonesVisibility()`:

```kotlin
fun toggleRegulatedZonesVisibility() {
    val current = settings.value.regulatedZonesVisible
    settingsManager.update { it.copy(regulatedZonesVisible = !current) }
    if (current) { // was visible → now hiding (user manually hid it)
        regulatedZoneManuallyHidden = true
        regulatedZoneAutoRevealed = false
    } else { // was hidden → now showing
        regulatedZoneManuallyHidden = false
        regulatedZoneAutoRevealed = false
    }
}
```

Also update `toggleCycleZoneLayers()` to set `regulatedZoneManuallyHidden` when the cycle turns regulated zones off.

### Settings UI

Add auto-show toggles in Settings → Navigation section, alongside the existing Z300 and speed zone auto-show toggles:

| Setting | Label | Location |
|---------|-------|----------|
| `regulatedZoneAutoShowGps` | "Alerte zones réglementées (GPS)" | Nav section |
| `regulatedZoneAutoShowDemo` | "Alerte zones réglementées (Démo)" | Nav section |

Reuse the shared distance/time sliders (already in Nav section for Z300 auto-show).

### Warning Strip Behavior

The `RegulatedZoneWarningStrip` is already gated on `regulatedZonesVisible` (line 809 of MapScreen). When auto-show enables `regulatedZonesVisible`, the warning strip will automatically appear because:

1. `regulatedZonesVisible = true` → `visibleRegulatedZones` is computed (line 719)
2. `visibleRegulatedZones` is passed to `RegulatedZoneWarningStrip` (line 809)
3. The strip filters to zones containing boat position (line 3474)

No additional wiring needed — the warning strip auto-shows as a side effect of the overlay being visible.

### `regulationInfoVisible` — Separate Consideration

The `regulationInfoVisible` setting (info text panel) is separate. It stays under manual control. Auto-show of the polygon overlay + icons is sufficient for the approach scenario; the user can manually enable the text panel for details.

---

## Migration Path for `speedZonesVisible`

The `speedZonesVisible` field and its auto-show block become redundant once the regulated zone overlay auto-show is in place. However, to avoid breaking existing saved preferences:

1. Keep `speedZonesVisible` in the data model and persistence layer (for backward compat)
2. Remove the speed zone auto-show block (lines 522-551) that sets it
3. Replace with the new regulated zone overlay auto-show block (targeting `regulatedZonesVisible`)
4. The `speedZonesVisible` field simply stops being written — old pref values remain inert

---

## Phased Implementation Plan

### Phase 1 — Core Auto-Show Wiring (CoastlineViewModel)

- [ ] Add `regulatedZoneManuallyHidden` and `regulatedZoneAutoRevealed` private var fields with `lastDistToRegZone`
- [ ] Add third auto-show block after line 551 targeting `regulatedZonesVisible`
- [ ] Add `toggleRegulatedZonesVisibility()` method
- [ ] Update `toggleCycleZoneLayers()` to set `regulatedZoneManuallyHidden` flag
- [ ] Remove (or comment out) the old speed zone auto-show block that sets `speedZonesVisible`

### Phase 2 — Settings Fields (SettingsManager)

- [ ] Add `regulatedZoneAutoShowGps: Boolean = true` to AppSettings
- [ ] Add `regulatedZoneAutoShowDemo: Boolean = true` to AppSettings
- [ ] Add KEY constants, load in `load()`, save in update block
- [ ] Add Settings UI toggles in Navigation section

### Phase 3 — Build & Validate

- [ ] Build (`apk-build.bat`)
- [ ] Unit test: add `ZoneAutoShowDecisionTest` cases for regulated zone config
- [ ] On-device verify: approach a speed-enforced regulated zone → regulated zone overlay + warning strip auto-show
- [ ] Verify manual override: cycle button turns off → auto-show doesn't re-reveal
- [ ] Verify GPS/Demo mode toggles independently suppress auto-show

### Phase 4 — Polish (Optional)

- [ ] Add a separate auto-show config for non-speed enforcement zones (anchoring, access, environmental) if needed
- [ ] Re-evaluate whether `speedZonesVisible` should be fully removed

---

## Key Files Changed

| File | Change |
|------|--------|
| [`CoastlineViewModel.kt`](../../app/src/main/java/ykws/android/maro/ui/map/CoastlineViewModel.kt) | Add 3rd auto-show block, new manual-hide/auto-reveal fields, `toggleRegulatedZonesVisibility()` |
| [`SettingsManager.kt`](../../app/src/main/java/ykws/android/maro/data/settings/SettingsManager.kt) | Add `regulatedZoneAutoShowGps`, `regulatedZoneAutoShowDemo` fields, keys, persistence |
| [`MapScreen.kt`](../../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt) | Wire cycle button to `toggleRegulatedZonesVisibility()` for manual override |
| SettingsScreen (settings UI) | Add auto-show toggles for regulated zones in Navigation section |

---

## Mermaid — Data Flow After Change

```mermaid
flowchart TD
    subgraph Input
        GPS[GPS position / Demo pan]
        SZI[SpeedZoneIndex query]
    end

    subgraph CoastlineViewModel Pipeline
        SZQ[SpeedZoneQuery: dist, insideAnyZone, speedLimit]
        SZQ --> AS1[Auto-show: 300m band<br/>zoneAutoShowDecision]
        SZQ --> AS2[Auto-show: speed zones<br/>zoneAutoShowDecision]
        SZQ --> AS3[[NEW] Auto-show: reg zones<br/>zoneAutoShowDecision]
    end

    subgraph Settings
        ZA[zone300AutoShowGps/Demo]
        SA[speedZoneAutoShowGps/Demo]
        RA[[NEW] regulatedZoneAutoShowGps/Demo]
    end

    AS1 -->|REVEAL/HIDE| ZV[zone300Visible]
    AS2 -->|REVEAL/HIDE| SV[speedZonesVisible<br/>kept but unused]
    AS3 -->|REVEAL/HIDE| RV[regulatedZonesVisible]

    subgraph MapScreen UI
        RV --> ZP[Regulated zone polygons<br/>drawn on map]
        RV --> WS[Warning strip icons<br/>RegulatedZoneWarningStrip]
    end

    AS1 --> ZA
    AS2 --> SA
    AS3 --> RA
```

---

## Decisions (Confirmed)

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Auto-show target `regulatedZonesVisible` directly? | **Yes** — shared with manual toggle | Same pattern as 300m auto-show; `manuallyHidden` flag prevents override conflicts |
| 2 | Non-speed enforcement zones (anchoring, access, environmental) also get auto-show? | **No** — speed-limited zones only | User specifically requested speed enforcement; extend later if needed |
| 3 | Warning strip auto-show independently of polygon overlay? | **No** — stays coupled | Showing icons without map polygons is confusing; `regulationInfoVisible` provides manual text details |
