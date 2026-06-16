<!-- scope: reference -->
# Maro-II Color Scheme

> Canonical reference for all colour tokens used in the Maro-II app.
> All colours are now loaded at runtime from
> [`colors.properties`](../app/src/main/assets/colors.properties) via
> [`AppConfig.init()`](../app/src/main/java/ykws/android/maro/ui/map/AppConfig.kt:126).
> Edit the `.properties` file, rebuild the APK — no code changes needed.
>
> 🎨 <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1A1A2E;vertical-align:middle;margin:0 2px;border:1px solid rgba(255,255,255,0.2);"></span> Swatches show the actual colour.

---

## 1. Dashboard Palette (hardcoded)

**Source:** [`DashboardPanel.kt:46`](../app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt:46) — `DashboardColors` object.

### Backgrounds & Text

| Token | Value | Swatch | Usage |
|---|---|---|---|
| `background` | `0xFF1A1A2E` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1A1A2E;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Dashboard outer panel, Settings overlay fullscreen bg |
| `cardBg` | `0xFF16213E` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#16213E;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Dashboard tile cards, exit-toast Surface |
| `textPrimary` | `0xFFE0E0E0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E0E0E0;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Dashboard tile labels and values |
| `textMuted` | `0xFF90A4AE` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#90A4AE;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Secondary/muted text labels |

### Semantic Status Colours

| Token | Value | Swatch | Usage |
|---|---|---|---|
| `success` | `0xCC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | General OK, validation passed |
| `warning` | `0xCCEF6C00` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#EF6C00;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Caution, validation warning (orange) |
| `error` | `0xCCB71C1C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#B71C1C;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Error / alert (dark red) |
| `neutral` | `0xAA4FC3F7` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4FC3F7;opacity:0.67;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Neutral/informational (cyan, 67% alpha) |
| `absent` | `0xAA37474F` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#37474F;opacity:0.67;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Absent/no-data (blue-grey, 67% alpha) |
| `validationOk` | → `success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Code alias to `success` |
| `validationWarn` | → `warning` = `#CCEF6C00` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#EF6C00;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Code alias to `warning` |
| `zoneNormal` | → `absent` = `0xAA37474F` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#37474F;opacity:0.67;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Code alias to `absent` |
| `overlay.lowDepth` | → `error` = `0xCCB71C1C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#B71C1C;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Low-depth warning overlay aliased to `error` |

### Zone / Speed Status Colours

| Token | Value | Swatch | Usage |
|---|---|---|---|
| `zoneDanger` | `0xFFB71C1C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#B71C1C;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | In-zone danger (dark red) |
| `zoneNormal` | → `ui.dashboard.status.absent` = `0xAA37474F` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#37474F;opacity:0.67;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | In-zone normal/default (alias to absent) |
| `zoneCompliant` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Inside zone, speed-compliant (green) |
| `speedSafe` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Speed ≤ limit inside zone (green) |
| `speedCaution` | `0xFFEF6C00` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#EF6C00;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Speed borderline inside zone (orange) |
| `speedDanger` | `0xFFC62828` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#C62828;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Speed > limit inside zone (red) |

### Distance Tile Colours

| Token | Value | Swatch | Usage |
|---|---|---|---|
| `zoneEntry` | `0xFFE65100` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E65100;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Amber — zone boundary ahead (entry distance tile) |
| `zoneExit` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Green — exiting to open sea (exit distance tile) |

### Alpha Constants

| Token | Value | Usage |
|---|---|---|
| `dullAlpha` | `0.33f` | Subdued dashboard states: no-data, on-land, far-from-zone, deep-water placeholder |

### Depth Readout Tints

**Property prefix:** `ui.dashboard.readout.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.uiDashboardReadout*`

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.dashboard.readout.collision` | `uiDashboardReadoutCollision` | `#FFEF5350` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#EF5350;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Depth readout ≤ 5 m (collision band) |
| `ui.dashboard.readout.shallow` | `uiDashboardReadoutShallow` | `#FFFFB74D` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFB74D;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Depth readout ≤ 10 m (shallow tier) |
| `ui.dashboard.readout.deep` | `uiDashboardReadoutDeep` | `#FF4FC3F7` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4FC3F7;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Depth readout > 10 m (profiling cyan) |

---

## 2. Action Button Colours (runtime)

**Property prefix:** `ui.button.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.uiButton*` → [`ButtonColors`](../app/src/main/java/ykws/android/maro/ui/map/FanIconComponents.kt:28)

Affects all right-edge control-stack buttons: Settings gear, fan parent, fan children (depth, regulated zones, 300m zone, danger), zoom +/−, and the active-child badge.

| Property | Default | AppConfig field | Swatch | Usage |
|---|---|---|---|---|
| `ui.button.background` | `#CCFFFFFF` | `buttonActionBgColor` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Button circle fill (80% opaque white) |
| `ui.button.icon` | `#1565C0` | `buttonActionIconColor` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | All icon symbols (gear, +/−, layer glyphs, badge) |
| `ui.button.iconActiveAlpha` | `1.0` | `buttonActionIconActiveAlpha` | — | Opacity when toggle is ON or icon is static |
| `ui.button.iconInactiveAlpha` | `0.25` | `buttonActionIconInactiveAlpha` | — | Opacity when toggle is OFF |

**Toggle state logic:** Active = `icon` @ `iconActiveAlpha`; Inactive = `icon` @ `iconInactiveAlpha`. Same hue, alpha-only distinction.

---

## 3. Configurable Map Colours (runtime from `maro.properties`)

**Source:** [`AppConfig.kt`](../app/src/main/java/ykws/android/maro/ui/map/AppConfig.kt) — loaded from both `zone.properties` and `maro.properties`.

### Arrow & Direction Line

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `cap.arrow.color` | `capArrowColor` | `0xFF1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Heading/speed cap arrow (boat marker) |
| `direction.line.color` | `directionLineColor` | `0x4D1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;opacity:0.3;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Direction line from boat center (30% alpha blue) |

### Depth Overlay

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `overlay.lowDepth.color` | `overlayLowDepthColor` | → `ui.dashboard.status.error` = `0xCCB71C1C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#B71C1C;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Low-depth warning overlay (dark red, 80% opacity) — alpha is depth-graded at runtime |
| `overlay.lowDepth.minOpacity` | `overlayLowDepthMinOpacity` | `25` | — | Minimum opacity % at the threshold depth (0–100) |
| `map.depth.nodata.color` | `mapDepthNodataColor` | `0x60FFF59D` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFF59D;opacity:0.38;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | NoData cells (pale yellow, ~38% alpha) |

### Depth Colour Ramp

**Property prefix:** `map.depth.ramp.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.mapDepthRamp*`

The hypsometric ramp interpolates between shallow (pale cyan) and deep (navy) endpoints, with a red-orange warning blend in the 0–5 m collision band.

| Property | AppConfig field | Default | Usage |
|---|---|---|---|
| `map.depth.ramp.shallow.r` | `mapDepthRampShallowR` | `200` | Shallow end red channel |
| `map.depth.ramp.shallow.g` | `mapDepthRampShallowG` | `232` | Shallow end green channel |
| `map.depth.ramp.shallow.b` | `mapDepthRampShallowB` | `255` | Shallow end blue channel |
| `map.depth.ramp.deep.r` | `mapDepthRampDeepR` | `10` | Deep end red channel |
| `map.depth.ramp.deep.g` | `mapDepthRampDeepG` | `30` | Deep end green channel |
| `map.depth.ramp.deep.b` | `mapDepthRampDeepB` | `90` | Deep end blue channel |
| `map.depth.ramp.warning.r` | `mapDepthRampWarningR` | `255` | Collision warning blend red |
| `map.depth.ramp.warning.g` | `mapDepthRampWarningG` | `80` | Collision warning blend green |
| `map.depth.ramp.warning.b` | `mapDepthRampWarningB` | `60` | Collision warning blend blue |
| `map.depth.ramp.alpha` | `mapDepthRampAlpha` | `160` | Overlay alpha (0–255) |

---

### Icon Background Opacity

| Property | AppConfig field | Default | Usage |
|---|---|---|---|
| `icon.back.active.transparency` | `iconBackActiveAlpha` | `75` (→ 191/255) | GPS status icon and EarthWater icon active state bg opacity % |
| `icon.back.inactive.transparency` | `iconBackInactiveAlpha` | `50` (→ 128/255) | GPS DEMO icon bg opacity % |

### Isobath Colours

| Property | Default | Swatch | Usage |
|---|---|---|---|
| `isobar.color.litto3d` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Litto3D isobath lines |
| `isobar.color.emodnet` | `0xFF00008B` (dark blue) | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#00008B;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | EMODnet isobath lines |
| `isobar.color.default` | `0xFF37474F` (blue-grey) | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#37474F;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Fallback for any unlisted source |
| `isobar.width.litto3d` | `+1px` | — | Litto3D line width bonus |
| `isobar.width.emodnet` | `-1px` | — | EMODnet line width bonus |

---

## 4. GPS Status Icon States (hardcoded)

**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) (`status.gps.*`) → [`MapScreen.kt:1646`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1646)

44×44 dp rounded square in top-left of the map.

| State | Property | AppConfig field | Default | Swatch | Alpha | Usage |
|---|---|---|---|---|---|---|
| `DEMO` | `status.gps.demo` | `statusGpsDemo` | `#FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | `statusGpsAlphaDimmed` | GPS off, dimmed |
| `ACQUIRING` | `status.gps.acquiring` | `statusGpsAcquiring` | `#FFA726` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFA726;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | `statusGpsAlphaActive` | Searching for fix |
| `HEALTHY` | `status.gps.healthy` | `statusGpsHealthy` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | `statusGpsAlphaActive` | GPS fix good |
| `IDLE` | `status.gps.idle` | `statusGpsIdle` | `#1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | `statusGpsAlphaActive` | GPS fix but stationary |
| `STALE` | `status.gps.stale` | `statusGpsStale` | `#F44336` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#F44336;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | `statusGpsAlphaActive` | GPS lost / error |

---

## 5. EarthWater Icon (hardcoded)

**Source:** [`MapScreen.kt:824`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:824)

44×44 dp rounded square in top-left, beside GPS icon.

| State | Property | AppConfig field | Default | Swatch | Alpha | Emoji |
|---|---|---|---|---|---|---|
| Water (active) | `status.earthWater.water` | `statusEarthWaterWater` | `#1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | `statusGpsAlphaActive` | 🌊 |
| Land (active) | `status.earthWater.land` | `statusEarthWaterLand` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | `statusGpsAlphaActive` | 🏔️ |
| Inactive | `status.earthWater.inactive` | `statusEarthWaterInactive` | `#EEFFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.93;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | — | — |

---

## 6. Map Overlay Colours (runtime)

**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig` map fields.

### Coastlines

| Property | AppConfig field | Default | Swatch | Location |
|---|---|---|---|---|
| `map.coastline.mainland.color` | `mapCoastlineMainlandColor` | `#1545C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1545C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | [`MapScreen.kt:3137`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3137) |
| `map.coastline.island.color` | `mapCoastlineIslandColor` | `#08805C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#08805C;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | [`MapScreen.kt:3138`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3138) |

### Hazard Discs (isolated offshore dangers)

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `map.hazard.disc.fill` | `mapHazardDiscFill` | `#FFE800` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFE800;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Vivid yellow fill for isolated offshore danger discs |
| `map.hazard.outline` | `mapHazardOutline` | `#000000` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#000000;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Black outline ring + cross on hazard markers |

### Zone-Ahead Line & Cone

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `map.zoneAhead.line` | `mapZoneAheadLine` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Dashed line from boat to zone intersection (green) |
| `map.zoneAhead.cone.fill` | `mapZoneAheadConeFill` | `#FFEB00` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFEB00;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Translucent yellow cone fill (search area) |
| `map.zoneAhead.cone.outline` | `mapZoneAheadConeOutline` | `#FFC800` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFC800;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Cone outline (search area) |

### 300 m Band

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `map.zone300.fill` | `mapZone300Fill` | `#E53935` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E53935;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Translucent red fill for 300 m band (water only) |
| `map.zone300.boundary` | `mapZone300Boundary` | `#E53935` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E53935;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Seaward boundary line of the 300 m band |

---

## 7. Settings Overlay (runtime)

**Property prefix:** `ui.settings.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.uiSettings*`

### Background & Layout

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.settings.background` | `uiSettingsBackground` | `#1A1A2E` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1A1A2E;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Fullscreen settings overlay background |
| `ui.settings.card.background` | `uiSettingsCardBackground` | `#1AFFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.10;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Section card surfaces (~10% white) |
| `ui.settings.divider` | `uiSettingsDivider` | `#14FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.08;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Dividers between sections (~8% white) |

### Text Colours

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.settings.text.primary` | `uiSettingsTextPrimary` | `#FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Section titles, primary labels |
| `ui.settings.text.muted` | `uiSettingsTextMuted` | `#B0BEC5` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#B0BEC5;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Descriptive text, switch labels |
| `ui.settings.text.secondary` | `uiSettingsTextSecondary` | `#78909C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#78909C;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Tab inactive text, secondary info |
| `ui.settings.footer.text` | `uiSettingsFooterText` | `#546E7A` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#546E7A;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Version footer text |

### Accent & Interactive Elements

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.settings.accent` | `uiSettingsAccent` | `#1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Switch checked thumb/track, slider thumb/track, buttons, selected tab indicator |
| `ui.settings.switch.track.inactive` | `uiSettingsSwitchTrackInactive` | `#33FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.2;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Switch track when unchecked (20% white) |
| `ui.settings.input.border` | `uiSettingsInputBorder` | `#66FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.4;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Unfocused text field border (40% white) |
| `ui.settings.danger` | `uiSettingsDanger` | `#E53935` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E53935;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Delete/danger action button backgrounds |

### Toast

| Property | AppConfig field | Default | Swatch | Location |
|---|---|---|---|---|
| `ui.settings.toast.background` | `uiSettingsToastBackground` | `#16213E` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#16213E;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Exit-toast surface, system window background |
| `ui.settings.toast.text` | `uiSettingsToastText` | `#FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Exit-toast text |

---

## 8. Arc Anchor Button (runtime)

**Property prefix:** `ui.arc.anchor.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.uiArcAnchor*`
**Usage:** [`ArcLayoutToggle.kt:60`](../app/src/main/java/ykws/android/maro/ui/map/ArcLayoutToggle.kt:60)

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.arc.anchor.color` | `uiArcAnchorColor` | `#FF1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Arc anchor icon and badge background |
| `ui.arc.anchor.background` | `uiArcAnchorBackground` | `#CCFFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.8;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Arc anchor button circle fill |

---

## 9. Regulated Zone Type Colours (runtime)

**Property prefix:** `regulatedZone.type.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.regulatedZoneType*`
**Usage:** [`RegulatedZoneIconProvider.kt:31`](../app/src/main/java/ykws/android/maro/ui/map/RegulatedZoneIconProvider.kt:31), [`MapScreen.kt:3364`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:3364)

| Property | AppConfig field | Default | Swatch | Zone Type |
|---|---|---|---|---|
| `regulatedZone.type.speedLimit` | `regulatedZoneTypeSpeedLimit` | `#1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Speed limit zones |
| `regulatedZone.type.anchoringProhibited` | `regulatedZoneTypeAnchoringProhibited` | `#FF8F00` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FF8F00;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Anchoring prohibited |
| `regulatedZone.type.accessProhibited` | `regulatedZoneTypeAccessProhibited` | `#E53935` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#E53935;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Access prohibited |
| `regulatedZone.type.environmental` | `regulatedZoneTypeEnvironmental` | → `ui.dashboard.status.success` = `#CC4CAF50` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#4CAF50;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Environmental protection |
| `regulatedZone.type.mooring` | `regulatedZoneTypeMooring` | `#00897B` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#00897B;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Mooring areas |
| `regulatedZone.type.fishingProhibited` | `regulatedZoneTypeFishingProhibited` | `#FDD835` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FDD835;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Fishing prohibited |
| `regulatedZone.type.navigationRestriction` | `regulatedZoneTypeNavigationRestriction` | `#8E24AA` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#8E24AA;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Navigation restriction |
| `regulatedZone.type.other` | `regulatedZoneTypeOther` | `#78909C` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#78909C;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Other / uncategorised |

These colours are also used for the **display category** background in the warning strip (via `RegulatedZoneIconProvider.colorForCategory`), where most categories use `regulatedZoneTypeSpeedLimit` (blue) as a uniform background, and `SEAPLANE` uses `regulatedZoneTypeOther` (grey).

---

## 10. Progress / Error Overlay (runtime)

**Property prefix:** `ui.progress.*` / `ui.error.*`
**Source:** [`colors.properties`](../app/src/main/assets/colors.properties) → `AppConfig.uiProgress*` / `uiError*`
**Usage:** [`MapScreen.kt:1045`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1045)

### Progress Bar

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.progress.accent` | `uiProgressAccent` | `#1565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Progress bar fill, title, percentage text |
| `ui.progress.track` | `uiProgressTrack` | `#401565C0` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#1565C0;opacity:0.25;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Progress bar track background (~25% accent) |

### Error Card

| Property | AppConfig field | Default | Swatch | Usage |
|---|---|---|---|---|
| `ui.error.card` | `uiErrorCard` | `#CCC62828` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#C62828;opacity:0.8;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Error card background (dark red, 80% opaque) |
| `ui.error.text` | `uiErrorText` | `#EEFFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;opacity:0.93;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Error message text |
| `ui.error.button.background` | `uiErrorButtonBackground` | `#FFFFFF` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#FFFFFF;vertical-align:middle;border:1px solid rgba(0,0,0,0.15);"></span> | Retry button background |
| `ui.error.button.text` | `uiErrorButtonText` | `#C62828` | <span style="display:inline-block;width:20px;height:20px;border-radius:3px;background:#C62828;vertical-align:middle;border:1px solid rgba(255,255,255,0.2);"></span> | Retry button text |

---

## 11. Colour Flow Diagram

```mermaid
flowchart LR
    subgraph colors.properties
        CP[colors.properties]
    end
    subgraph zone.properties
        ZP[zone.properties<br/>isobar.* only]
    end
    subgraph AppConfig
        ZC[AppConfig.init]
    end
    subgraph UI Layer
        BC[ButtonColors<br/>runtime getters]
        DC[DashboardColors<br/>runtime getters]
        IC[Inline composable<br/>AppConfig field refs]
    end

    CP --> ZC
    ZP --> ZC
    ZC --> BC --> AB[Action Buttons]
    ZC --> DC --> DT[Dashboard Tiles]
    ZC --> IC --> GI[GPS Icon]
    ZC --> IC --> EI[EarthWater Icon]
    ZC --> IC --> MO[Map Overlays<br/>coastline, cap arrow,<br/>direction line]
    ZC --> IC --> LW[Low Depth Warning]
    ZC --> IC --> SP[Settings Panel]
    ZC --> IC --> AA[Arc Anchor Button]
    ZC --> IC --> RZ[Regulated Zone Overlays]
    ZC --> IC --> PE[Progress / Error Overlay]
    ZC --> IC --> DR[Depth Colour Ramp]
    ZP --> IC --> IB[Isobath Lines]
```

Change a colour: edit `colors.properties` (or `zone.properties` for isobaths), rebuild APK. No code changes needed.
