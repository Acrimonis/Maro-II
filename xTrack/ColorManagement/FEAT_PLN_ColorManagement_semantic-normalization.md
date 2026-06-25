# Color Normalization — 5 Semantic Tokens

> **Feature:** ColorManagement | **Status:** Design — ready
> **Created:** 2026-06-22

---

## 5 Semantic base tokens

```properties
semantic.danger     = #CCB71C1C   # red, 80% alpha
semantic.caution    = #CCEF6C00   # amber, 80% alpha
semantic.compliant  = #CC4CAF50   # green, 80% alpha
semantic.info       = #FF1565C0   # blue, full alpha
semantic.inactive   = #33FFFFFF   # white, 20% alpha
```

## Full alias map (force all)

### Dashboard status → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.dashboard.status.success` | `#CC4CAF50` | `${semantic.compliant}` |
| `ui.dashboard.status.warning` | `#CCEF6C00` | `${semantic.caution}` |
| `ui.dashboard.status.error` | `#CCB71C1C` | `${semantic.danger}` |
| `ui.dashboard.status.neutral` | `#AA4FC3F7` | `${semantic.info}` |
| `ui.dashboard.status.absent` | `#AA37474F` | `${semantic.inactive}` |

### Dashboard zone → semantics  

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.dashboard.zone.safe` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `ui.dashboard.zone.caution` | `#EF6C00` | `${semantic.caution}` |
| `ui.dashboard.zone.danger` | `#C62828` | `${semantic.danger}` |
| `ui.dashboard.zone.compliant` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `ui.dashboard.zone.normal` | `${ui.dashboard.status.absent}` | `${semantic.inactive}` |
| `ui.dashboard.zone.dangerDark` | `#B71C1C` | `${semantic.danger}` |

### Dashboard distance → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.dashboard.distance.entry` | `#E65100` | `${semantic.caution}` |
| `ui.dashboard.distance.exit` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |

### GPS status → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `status.gps.demo` | `#FFFFFF` | `${semantic.inactive}` |
| `status.gps.acquiring` | `#FFA726` | `${semantic.caution}` |
| `status.gps.healthy` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `status.gps.idle` | `#1565C0` | `${semantic.info}` |
| `status.gps.stale` | `#F44336` | `${semantic.danger}` |

### EarthWater → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `status.earthWater.water` | `#1565C0` | `${semantic.info}` |
| `status.earthWater.land` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `status.earthWater.inactive` | `#EEFFFFFF` | `${semantic.inactive}` |

### Tracking → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `status.tracking.healthy` | `#CC4CAF50` | `${semantic.compliant}` |
| `status.tracking.idle` | `#FF1565C0` | `${semantic.info}` |
| `status.tracking.off` | `#FFFFFFFF` | `${semantic.inactive}` |
| `status.tracking.dot.recording` | `#FFF44336` | `${semantic.danger}` |
| `status.tracking.dot.idle` | `#CCFFFFFF` | `${semantic.inactive}` |

### Depth readout → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.dashboard.readout.collision` | `#FFEF5350` | `${semantic.danger}` |
| `ui.dashboard.readout.shallow` | `#FFFFB74D` | `${semantic.caution}` |
| `ui.dashboard.readout.deep` | `#FF4FC3F7` | `${semantic.info}` |

### Settings → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.settings.accent` | `#FF1565C0` | `${semantic.info}` |
| `ui.settings.danger` | `#FFE53935` | `${semantic.danger}` |
| `ui.settings.switch.track.inactive` | `#33FFFFFF` | `${semantic.inactive}` |

### Map overlays → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `overlay.lowDepth.color` | `${ui.dashboard.status.error}` | `${semantic.danger}` |
| `map.zoneAhead.line` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `map.isobar.litto3d.color` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |

### Regulated zones → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `regulatedZone.type.environmental` | `${ui.dashboard.status.success}` | `${semantic.compliant}` |
| `regulatedZone.type.speedLimit` | `#FF1565C0` | `${semantic.info}` |
| `regulatedZone.type.anchoringProhibited` | `#FFFF8F00` | `${semantic.caution}` |
| `regulatedZone.type.accessProhibited` | `#FFE53935` | `${semantic.danger}` |

### Navigation → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `map.navigation.arrow.color` | `#1565C0` | `${semantic.info}` |

### Progress/error → semantics

| Token | Old value | New value |
|-------|-----------|-----------|
| `ui.progress.accent` | `#FF1565C0` | `${semantic.info}` |
| `ui.error.card` | `#CCC62828` | `${semantic.danger}` |
| `ui.error.button.text` | `#FFC62828` | `${semantic.danger}` |

---

## Tokens NOT aliased (structural, type-specific, alpha values)

These stay literal — they're not semantic status colors:

- All `ui.dashboard.background`, `ui.dashboard.card.background`, `ui.dashboard.text.*` — structural
- All `ui.button.*` — structural  
- All `ui.settings.background`, `ui.settings.toast.*`, `ui.settings.text.*`, `ui.settings.card.*`, `ui.settings.divider`, `ui.settings.input.*`, `ui.settings.footer.*` — structural
- `map.coastline.*` — type-specific
- `map.depth.nodata.color` — type-specific
- `map.navigation.line.color` — derived (30% alpha variant of semantic.info, can't alias with ${})
- `map.hazard.*` — type-specific
- `map.zoneAhead.cone.*` — type-specific
- `map.zone300.*` — type-specific
- `map.isobar.emodnet.color`, `map.isobar.default.color` — type-specific
- `regulatedZone.type.mooring`, `.fishingProhibited`, `.navigationRestriction`, `.other` — type-specific
- `ui.dashboard.dullAlpha` — alpha value, not color
- All `*.alpha.*` — alpha values
- `ui.progress.track` — derived from accent
- `ui.error.button.background`, `ui.error.text` — structural
- All `map.depth.ramp.*` — channel values
- `overlay.lowDepth.minOpacity` — not a color
- `map.isobar.*.width` — not colors

---

## Visual changes summary

Colors that will look different after normalization:

| Area | Old | New | Impact |
|------|-----|-----|--------|
| Zone danger tile | `#C62828` bright red | `#CCB71C1C` dark red | Slightly darker |
| Zone dangerDark | `#B71C1C` full red | `#CCB71C1C` 80% red | Slightly transparent |
| Zone caution | `#EF6C00` full amber | `#CCEF6C00` 80% amber | Slightly transparent |
| Distance entry | `#E65100` deep amber | `#CCEF6C00` lighter | Lighter amber |
| Status neutral | `#AA4FC3F7` cyan | `#FF1565C0` blue | **Cyan → blue** |
| Status absent | `#AA37474F` blue-grey | `#33FFFFFF` white 20% | **Blue-grey → near-invisible** |
| Zone normal | blue-grey (via absent) | white 20% | Zone "no data" nearly invisible |
| GPS acquiring | `#FFA726` orange | `#CCEF6C00` amber | Orange → amber |
| GPS stale | `#F44336` material red | `#CCB71C1C` dark red | Brighter → darker |
| GPS demo | `#FFFFFF` pure white | `#33FFFFFF` 20% white | Bright → dim |
| EarthWater inactive | `#EEFFFFFF` 93% white | `#33FFFFFF` 20% | Bright → dim |
| Tracking off | `#FFFFFFFF` pure white | `#33FFFFFF` 20% | Bright → dim |
| Tracking dot recording | `#FFF44336` red | `#CCB71C1C` dark red | Brighter → darker |
| Tracking dot idle | `#CCFFFFFF` white 80% | `#33FFFFFF` white 20% | Brighter → dimmer |
| Depth collision | `#FFEF5350` salmon | `#CCB71C1C` dark red | Salmon → dark red |
| Depth shallow | `#FFFFB74D` light amber | `#CCEF6C00` darker | Lighter → darker |
| Depth deep | `#FF4FC3F7` cyan | `#FF1565C0` blue | **Cyan → blue** |
| Settings danger | `#FFE53935` bright red | `#CCB71C1C` dark red | Bright → dark |
| Error card | `#CCC62828` medium red | `#CCB71C1C` dark red | Medium → dark |
| Error button text | `#FFC62828` medium red | `#CCB71C1C` dark red | Medium → dark |
| Anchoring prohibited | `#FFFF8F00` orange | `#CCEF6C00` amber | Orange → amber |
| Access prohibited | `#FFE53935` bright red | `#CCB71C1C` dark red | Bright → dark |
