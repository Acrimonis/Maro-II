package ykws.android.maro.config

import android.content.Context
import android.graphics.Color
import ykws.android.maro.data.model.DepthSource
import java.util.Properties

/**
 * Runtime loader for all `.properties` files bundled in assets.
 *
 * Loads [zone.properties](app/src/main/assets/zone.properties),
 * [maro.properties](app/src/main/assets/maro.properties), and
 * [colors.properties](app/src/main/assets/colors.properties) — each file
 * overrides the previous on key collision, so `colors.properties` always
 * wins.
 *
 * If a file is missing or any value cannot be parsed, the corresponding
 * hardcoded default is used so the app never crashes on a bad config.
 *
 * Call [init] once from [MainActivity] before the UI composes.
 */
object AppConfig {

    /** Distance (m) outside the 300 m band edge at which a hidden band auto-reveals (default for the in-app setting).
     *  Also used as the dashboard tile near-exit / near-entry threshold. */
    var zoneAutoRevealDistanceM = 100f
        private set

    /** Time (s) before reaching the 300 m band edge (at SOG) at which a hidden band auto-reveals (default for the in-app setting).
     *  Also used as the dashboard tile near-exit / near-entry time threshold. */
    var zoneAutoRevealTimeS = 10
        private set

    /** Regulatory speed limit (kn) inside the 300 m band — at/below this, an auto-revealed band re-hides. */
    var zoneRegulatorySpeedKn = 5f
        private set

    /** Hysteresis deadband (meters) for speed zone boundary detection — prevents GPS jitter from flapping inside/outside state. */
    var speedZoneHysteresisM: Double = 5.0
        private set

    /** Minimum opacity (%, 0–100) of the low-depth warning at the threshold depth; 100 % at the shoreline fades to this. */
    var lowDepthWarningMinOpacityPct = 25
        private set

    /** ARGB colour for action-button background (right-edge control stack).
     *  Default `#CC16213E` (semi-transparent dark blue). Set via `ui.button.background` in colors.properties. */
    var buttonActionBgColor: Int = 0xCC16213E.toInt()
        private set

    /** ARGB colour for action-button icons.
     *  Default `#FFE0E0E0` (light grey). Set via `ui.button.icon` in colors.properties. */
    var buttonActionIconColor: Int = 0xFFE0E0E0.toInt()
        private set

    /** Alpha (0.0–1.0) for active/toggled-on icon state.
     *  Default 1.0. Set via `ui.button.iconActiveAlpha` in colors.properties. */
    var buttonActionIconActiveAlpha: Float = 1.0f
        private set

    /** Alpha (0.0–1.0) for inactive/toggled-off icon state.
     *  Default 0.25. Set via `ui.button.iconInactiveAlpha` in colors.properties. */
    var buttonActionIconInactiveAlpha: Float = 0.25f
        private set

    // ── Colors from colors.properties ────────────────────────────────────────

    /** Dashboard background. Default #1A1A2E. Set via `ui.dashboard.background` in colors.properties. */
    var uiDashboardBackground: Int = 0xFF1A1A2E.toInt()
        private set
    /** Dashboard card tile background. Default #16213E. Set via `ui.dashboard.card.background` in colors.properties. */
    var uiDashboardCardBackground: Int = 0xFF16213E.toInt()
        private set
    /** Dashboard primary text. Default #E0E0E0. Set via `ui.dashboard.text.primary` in colors.properties. */
    var uiDashboardTextPrimary: Int = 0xFFE0E0E0.toInt()
        private set
    /** Dashboard muted text. Default #90A4AE. Set via `ui.dashboard.text.muted` in colors.properties. */
    var uiDashboardTextMuted: Int = 0xFF90A4AE.toInt()
        private set
    /** Dashboard status success (general OK, validation passed). Default #CC4CAF50 (green, 80% opacity). Set via `ui.dashboard.status.success` in colors.properties. */
    var uiDashboardStatusSuccess: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard status warning (caution, validation warning — orange). Default #CCEF6C00 (orange, 80% opacity). Set via `ui.dashboard.status.warning` in colors.properties. */
    var uiDashboardStatusWarning: Int = 0xCCEF6C00.toInt()
        private set
    /** Dashboard status error (critical alert, failure). Default #CCB71C1C (dark red, 80% opacity). Set via `ui.dashboard.status.error` in colors.properties. */
    var uiDashboardStatusError: Int = 0xCCB71C1C.toInt()
        private set
    /** Dashboard status neutral (informational). Default #AA4FC3F7 (cyan, 67% alpha). Set via `ui.dashboard.status.neutral` in colors.properties. */
    var uiDashboardStatusNeutral: Int = 0xAA4FC3F7.toInt()
        private set
    /** Dashboard status absent (no-data, placeholder). Default #AA37474F (blue-grey, 67% alpha). Set via `ui.dashboard.status.absent` in colors.properties. */
    var uiDashboardStatusAbsent: Int = 0xAA37474F.toInt()
        private set
    /** Dashboard zone speed-safe. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.zone.safe` in colors.properties. */
    var uiDashboardZoneSafe: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard zone speed-caution. Default #EF6C00. Set via `ui.dashboard.zone.caution` in colors.properties. */
    var uiDashboardZoneCaution: Int = 0xFFEF6C00.toInt()
        private set
    /** Dashboard zone speed-danger. Default #C62828. Set via `ui.dashboard.zone.danger` in colors.properties. */
    var uiDashboardZoneDanger: Int = 0xFFC62828.toInt()
        private set
    /** Dashboard zone speed-compliant. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.zone.compliant` in colors.properties. */
    var uiDashboardZoneCompliant: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard zone normal. Default #AA37474F (alias of ${ui.dashboard.status.absent}). Set via `ui.dashboard.zone.normal` in colors.properties. */
    var uiDashboardZoneNormal: Int = 0xAA37474F.toInt()
        private set
    /** Dashboard zone danger-dark. Default #B71C1C. Set via `ui.dashboard.zone.dangerDark` in colors.properties. */
    var uiDashboardZoneDangerDark: Int = 0xFFB71C1C.toInt()
        private set
    /** Dashboard distance entry (amber). Default #E65100. Set via `ui.dashboard.distance.entry` in colors.properties. */
    var uiDashboardDistanceEntry: Int = 0xFFE65100.toInt()
        private set
    /** Dashboard distance exit (green). Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.distance.exit` in colors.properties. */
    var uiDashboardDistanceExit: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard dull alpha. Default 0.33. Set via `ui.dashboard.dullAlpha` in colors.properties. */
    var uiDashboardDullAlpha: Float = 0.33f
        private set

    /** Settings overlay background. Default #1A1A2E. Set via `ui.settings.background` in colors.properties. */
    var uiSettingsBackground: Int = 0xFF1A1A2E.toInt()
        private set
    /** Settings exit-toast surface. Default #16213E. Set via `ui.settings.toast.background` in colors.properties. */
    var uiSettingsToastBackground: Int = 0xFF16213E.toInt()
        private set
    /** Settings exit-toast text. Default #FFFFFF. Set via `ui.settings.toast.text` in colors.properties. */
    var uiSettingsToastText: Int = 0xFFFFFFFF.toInt()
        private set

    /** Coastline mainland colour. Default #1545C0. Set via `map.coastline.mainland.color` in colors.properties. */
    var mapCoastlineMainlandColor: Int = 0xFF1545C0.toInt()
        private set
    /** Coastline mainland stroke width (px). Default 10. Set via `map.coastline.mainland.width` in colors.properties. */
    var mapCoastlineMainlandWidth: Int = 10
        private set
    /** Coastline island colour. Default #08805C. Set via `map.coastline.island.color` in colors.properties. */
    var mapCoastlineIslandColor: Int = 0xFF08805C.toInt()
        private set
    /** Coastline island stroke width (px). Default 10. Set via `map.coastline.island.width` in colors.properties. */
    var mapCoastlineIslandWidth: Int = 10
        private set

    /** Navigation arrow colour. Default #1565C0. Set via `map.navigation.arrow.color` in colors.properties. */
    var mapNavigationArrowColor: Int = 0xFF1565C0.toInt()
        private set
    /** Navigation direction line colour. Default #4D1565C0. Set via `map.navigation.line.color` in colors.properties. */
    var mapNavigationLineColor: Int = 0x4D1565C0.toInt()
        private set

    /** Depth NoData cell colour. Default #60FFF59D (pale yellow, ~38% alpha). Set via `map.depth.nodata.color` in colors.properties. */
    var mapDepthNodataColor: Int = 0x60FFF59D.toInt()
        private set

    /** Low-depth warning overlay colour. Default #CCB71C1C (dark red, 80% opacity, alias to ui.dashboard.status.error). Set via `overlay.lowDepth.color` in colors.properties. */
    var overlayLowDepthColor: Int = 0xCCB71C1C.toInt()
        private set
    /** Low-depth warning minimum opacity %. Default 25. Set via `overlay.lowDepth.minOpacity` in colors.properties. */
    var overlayLowDepthMinOpacity: Int = 25
        private set

    /** GPS icon DEMO state background colour. Default #FFFFFF. Set via `status.gps.demo` in colors.properties. */
    var statusGpsDemo: Int = 0xFFFFFFFF.toInt()
        private set
    /** GPS icon ACQUIRING state background colour. Default #FFA726. Set via `status.gps.acquiring` in colors.properties. */
    var statusGpsAcquiring: Int = 0xFFFFA726.toInt()
        private set
    /** GPS icon HEALTHY state background colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `status.gps.healthy` in colors.properties. */
    var statusGpsHealthy: Int = 0xCC4CAF50.toInt()
        private set
    /** GPS icon IDLE state background colour. Default #1565C0. Set via `status.gps.idle` in colors.properties. */
    var statusGpsIdle: Int = 0xFF1565C0.toInt()
        private set
    /** GPS icon STALE state background colour. Default #F44336. Set via `status.gps.stale` in colors.properties. */
    var statusGpsStale: Int = 0xFFF44336.toInt()
        private set
    /** GPS icon active-state background alpha (0.0–1.0). Default 0.75. Set via `status.gps.alpha.active` in colors.properties. */
    var statusGpsAlphaActive: Float = 0.75f
        private set
    /** GPS icon dimmed-state background alpha (0.0–1.0). Default 0.50. Set via `status.gps.alpha.dimmed` in colors.properties. */
    var statusGpsAlphaDimmed: Float = 0.50f
        private set

    /** EarthWater icon water-state colour. Default #1565C0. Set via `status.earthWater.water` in colors.properties. */
    var statusEarthWaterWater: Int = 0xFF1565C0.toInt()
        private set
    /** EarthWater icon land-state colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `status.earthWater.land` in colors.properties. */
    var statusEarthWaterLand: Int = 0xCC4CAF50.toInt()
        private set
    /** EarthWater icon inactive-state colour. Default #EEFFFFFF. Set via `status.earthWater.inactive` in colors.properties. */
    var statusEarthWaterInactive: Int = 0xEEFFFFFF.toInt()
        private set

    // ── Arc anchor button ─────────────────────────────────────────────────────
    /** Arc anchor button colour. Default #FFE0E0E0 (light grey). Set via `ui.arc.anchor.color` in colors.properties. */
    var uiArcAnchorColor: Int = 0xFFE0E0E0.toInt()
        private set
    /** Arc anchor button background. Default #CC16213E (semi-transparent dark blue). Set via `ui.arc.anchor.background` in colors.properties. */
    var uiArcAnchorBackground: Int = 0xCC16213E.toInt()
        private set

    // ── Dashboard depth readout tints ─────────────────────────────────────────
    /** Dashboard depth readout collision tint. Default #FFEF5350. Set via `ui.dashboard.readout.collision` in colors.properties. */
    var uiDashboardReadoutCollision: Int = 0xFFEF5350.toInt()
        private set
    /** Dashboard depth readout shallow tint. Default #FFFFB74D. Set via `ui.dashboard.readout.shallow` in colors.properties. */
    var uiDashboardReadoutShallow: Int = 0xFFFFB74D.toInt()
        private set
    /** Dashboard depth readout deep tint. Default #FF4FC3F7. Set via `ui.dashboard.readout.deep` in colors.properties. */
    var uiDashboardReadoutDeep: Int = 0xFF4FC3F7.toInt()
        private set

    // ── Settings panel colours ────────────────────────────────────────────────
    /** Settings panel primary text. Default #FFFFFFFF. Set via `ui.settings.text.primary` in colors.properties. */
    var uiSettingsTextPrimary: Int = 0xFFFFFFFF.toInt()
        private set
    /** Settings panel muted text. Default #FFB0BEC5. Set via `ui.settings.text.muted` in colors.properties. */
    var uiSettingsTextMuted: Int = 0xFFB0BEC5.toInt()
        private set
    /** Settings panel secondary text. Default #FF78909C. Set via `ui.settings.text.secondary` in colors.properties. */
    var uiSettingsTextSecondary: Int = 0xFF78909C.toInt()
        private set
    /** Settings panel accent colour. Default #FF1565C0. Set via `ui.settings.accent` in colors.properties. */
    var uiSettingsAccent: Int = 0xFF1565C0.toInt()
        private set
    /** Settings panel card background. Default #1AFFFFFF. Set via `ui.settings.card.background` in colors.properties. */
    var uiSettingsCardBackground: Int = 0x1AFFFFFF.toInt()
        private set
    /** Settings panel divider colour. Default #14FFFFFF. Set via `ui.settings.divider` in colors.properties. */
    var uiSettingsDivider: Int = 0x14FFFFFF.toInt()
        private set
    /** Settings panel switch track inactive colour. Default #33FFFFFF. Set via `ui.settings.switch.track.inactive` in colors.properties. */
    var uiSettingsSwitchTrackInactive: Int = 0x33FFFFFF.toInt()
        private set
    /** Settings panel input border colour. Default #66FFFFFF. Set via `ui.settings.input.border` in colors.properties. */
    var uiSettingsInputBorder: Int = 0x66FFFFFF.toInt()
        private set
    /** Settings panel footer text colour. Default #FF546E7A. Set via `ui.settings.footer.text` in colors.properties. */
    var uiSettingsFooterText: Int = 0xFF546E7A.toInt()
        private set
    /** Settings panel danger/delete colour. Default #FFE53935. Set via `ui.settings.danger` in colors.properties. */
    var uiSettingsDanger: Int = 0xFFE53935.toInt()
        private set

    // ── Regulated zone type colours ───────────────────────────────────────────
    /** Regulated zone speed-limit colour. Default #FF1565C0. Set via `regulatedZone.type.speedLimit` in colors.properties. */
    var regulatedZoneTypeSpeedLimit: Int = 0xFF1565C0.toInt()
        private set
    /** Regulated zone anchoring-prohibited colour. Default #FFFF8F00. Set via `regulatedZone.type.anchoringProhibited` in colors.properties. */
    var regulatedZoneTypeAnchoringProhibited: Int = 0xFFFF8F00.toInt()
        private set
    /** Regulated zone access-prohibited colour. Default #FFE53935. Set via `regulatedZone.type.accessProhibited` in colors.properties. */
    var regulatedZoneTypeAccessProhibited: Int = 0xFFE53935.toInt()
        private set
    /** Regulated zone environmental colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `regulatedZone.type.environmental` in colors.properties. */
    var regulatedZoneTypeEnvironmental: Int = 0xCC4CAF50.toInt()
        private set
    /** Regulated zone mooring colour. Default #FF00897B. Set via `regulatedZone.type.mooring` in colors.properties. */
    var regulatedZoneTypeMooring: Int = 0xFF00897B.toInt()
        private set
    /** Regulated zone fishing-prohibited colour. Default #FFFDD835. Set via `regulatedZone.type.fishingProhibited` in colors.properties. */
    var regulatedZoneTypeFishingProhibited: Int = 0xFFFDD835.toInt()
        private set
    /** Regulated zone navigation-restriction colour. Default #FF8E24AA. Set via `regulatedZone.type.navigationRestriction` in colors.properties. */
    var regulatedZoneTypeNavigationRestriction: Int = 0xFF8E24AA.toInt()
        private set
    /** Regulated zone other colour. Default #FF78909C. Set via `regulatedZone.type.other` in colors.properties. */
    var regulatedZoneTypeOther: Int = 0xFF78909C.toInt()
        private set

    // ── Map overlay colours ───────────────────────────────────────────────────
    /** Hazard disc fill colour. Default #FFFFE800. Set via `map.hazard.disc.fill` in colors.properties. */
    var mapHazardDiscFill: Int = 0xFFFFE800.toInt()
        private set
    /** Hazard disc outline colour. Default #FF000000. Set via `map.hazard.outline` in colors.properties. */
    var mapHazardOutline: Int = 0xFF000000.toInt()
        private set
    /** Zone-ahead line colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `map.zoneAhead.line` in colors.properties. */
    var mapZoneAheadLine: Int = 0xCC4CAF50.toInt()
        private set
    /** Zone-ahead cone fill colour. Default #FFFFEB00. Set via `map.zoneAhead.cone.fill` in colors.properties. */
    var mapZoneAheadConeFill: Int = 0xFFFFEB00.toInt()
        private set
    /** Zone-ahead cone outline colour. Default #FFFFC800. Set via `map.zoneAhead.cone.outline` in colors.properties. */
    var mapZoneAheadConeOutline: Int = 0xFFFFC800.toInt()
        private set
    /** Zone300 fill colour (~19 % alpha). Default #30E53935. Set via `map.zone300.fill` in colors.properties. */
    var mapZone300Fill: Int = 0x30E53935.toInt()
        private set
    /** Zone300 boundary colour. Default #FFE53935. Set via `map.zone300.boundary` in colors.properties. */
    var mapZone300Boundary: Int = 0xFFE53935.toInt()
        private set

    // ── Progress/error overlay colours ────────────────────────────────────────
    /** Progress overlay accent colour. Default #FF1565C0. Set via `ui.progress.accent` in colors.properties. */
    var uiProgressAccent: Int = 0xFF1565C0.toInt()
        private set
    /** Progress overlay track colour. Default #401565C0. Set via `ui.progress.track` in colors.properties. */
    var uiProgressTrack: Int = 0x401565C0.toInt()
        private set
    /** Error card background colour. Default #CCC62828. Set via `ui.error.card` in colors.properties. */
    var uiErrorCard: Int = 0xCCC62828.toInt()
        private set
    /** Error card text colour. Default #EEFFFFFF. Set via `ui.error.text` in colors.properties. */
    var uiErrorText: Int = 0xEEFFFFFF.toInt()
        private set
    /** Error card button background. Default #FFFFFFFF. Set via `ui.error.button.background` in colors.properties. */
    var uiErrorButtonBackground: Int = 0xFFFFFFFF.toInt()
        private set
    /** Error card button text. Default #FFC62828. Set via `ui.error.button.text` in colors.properties. */
    var uiErrorButtonText: Int = 0xFFC62828.toInt()
        private set

    // ── Depth colour ramp endpoints ───────────────────────────────────────────
    /** Depth colour ramp shallow end R. Default 200. Set via `map.depth.ramp.shallow.r` in colors.properties. */
    var mapDepthRampShallowR: Int = 200
        private set
    /** Depth colour ramp shallow end G. Default 232. Set via `map.depth.ramp.shallow.g` in colors.properties. */
    var mapDepthRampShallowG: Int = 232
        private set
    /** Depth colour ramp shallow end B. Default 255. Set via `map.depth.ramp.shallow.b` in colors.properties. */
    var mapDepthRampShallowB: Int = 255
        private set
    /** Depth colour ramp deep end R. Default 10. Set via `map.depth.ramp.deep.r` in colors.properties. */
    var mapDepthRampDeepR: Int = 10
        private set
    /** Depth colour ramp deep end G. Default 30. Set via `map.depth.ramp.deep.g` in colors.properties. */
    var mapDepthRampDeepG: Int = 30
        private set
    /** Depth colour ramp deep end B. Default 90. Set via `map.depth.ramp.deep.b` in colors.properties. */
    var mapDepthRampDeepB: Int = 90
        private set
    /** Depth colour ramp warning R. Default 255. Set via `map.depth.ramp.warning.r` in colors.properties. */
    var mapDepthRampWarningR: Int = 255
        private set
    /** Depth colour ramp warning G. Default 80. Set via `map.depth.ramp.warning.g` in colors.properties. */
    var mapDepthRampWarningG: Int = 80
        private set
    /** Depth colour ramp warning B. Default 60. Set via `map.depth.ramp.warning.b` in colors.properties. */
    var mapDepthRampWarningB: Int = 60
        private set
    /** Depth colour ramp alpha. Default 160. Set via `map.depth.ramp.alpha` in colors.properties. */
    var mapDepthRampAlpha: Int = 160
        private set

    /**
     * Hash of all colour properties that affect cached rasters (depth colour map +
     * low-depth warning overlay). Any colour change → different hash → cache miss on
     * [RasterCache.Key]. Kept in sync with [RasterCache.colorsHash].
     */
    val rasterColorsHash: Int get() = listOf(
        mapDepthNodataColor,
        overlayLowDepthColor,
        mapDepthRampShallowR, mapDepthRampShallowG, mapDepthRampShallowB,
        mapDepthRampDeepR, mapDepthRampDeepG, mapDepthRampDeepB,
        mapDepthRampWarningR, mapDepthRampWarningG, mapDepthRampWarningB,
        mapDepthRampAlpha,
    ).hashCode()

    /** Isobath line colour per data source (ARGB int); a source with no entry falls back to [isobarColorDefault].
     *  Defaults are resolved from colors.properties (`map.isobar.*.color`) which may be overridden
     *  by zone.properties (`isobar.color.*`) via the merged Properties object. */
    private val isobarColors = hashMapOf(
        DepthSource.LITTO3D to 0xFF1B5E20.toInt(), // dark green (Material 900)
        DepthSource.EMODNET to 0xFF00008B.toInt(), // dark blue
    )

    /** Fallback isobath colour for any source without an explicit entry.
     *  Default #FF37474F (muted blue-grey). Set via `map.isobar.default.color` in colors.properties
     *  or `isobar.color.default` in zone.properties. */
    var isobarColorDefault = 0xFF37474F.toInt() // muted blue-grey
        private set

    /** Per-source extra stroke width (px) added on top of the major/minor base; default 0. */
    private val isobarWidthBonuses = hashMapOf(
        DepthSource.LITTO3D to 1f,  // Litto3D (precise nearshore) reads a touch bolder
        DepthSource.EMODNET to -1f, // EMODnet (coarse deep) reads thinner
    )

    /**
     * Load tunables from all `.properties` files bundled in assets.
     * Must be called once (e.g. from [MainActivity.onCreate]) before the UI
     * reads the values. Missing or unparseable entries silently keep the default.
     *
     * Load order (each overrides the previous):
     * 1. maro.properties  — speed zone thresholds, vessel defaults
     * 2. colors.properties — ALL colour values
     */
    fun init(context: Context) {
        try {
            val props = Properties()

            // Load maro.properties (optional — if missing, defaults are kept).
            try {
                context.assets.open("maro.properties").use { stream ->
                    props.load(stream)
                }
            } catch (_: Exception) {
                // maro.properties is optional; defaults apply if absent.
            }

            // Load colors.properties (optional — defaults apply if absent).
            try {
                context.assets.open("colors.properties").use { stream ->
                    props.load(stream)
                }
            } catch (_: Exception) {
                // colors.properties is optional; defaults apply if absent.
            }

            // Resolve ${key} interpolation — run BEFORE individual property reads so that
            // references like ${ui.dashboard.status.success} are expanded in-place.
            val refPattern = Regex("""\$\{([^}]+)\}""")
            for (key in props.stringPropertyNames()) {
                val value = props.getProperty(key) ?: continue
                val resolved = refPattern.replace(value) { match ->
                    props.getProperty(match.groupValues[1]) ?: match.value
                }
                if (resolved != value) {
                    props.setProperty(key, resolved)
                }
            }

            props.getProperty("zoneAutoRevealDistanceM")?.toFloatOrNull()?.let {
                zoneAutoRevealDistanceM = it.coerceIn(50f, 500f)
            }
            props.getProperty("zoneAutoRevealTimeS")?.toIntOrNull()?.let {
                zoneAutoRevealTimeS = it.coerceIn(5, 120)
            }
            props.getProperty("zoneRegulatorySpeedKn")?.toFloatOrNull()?.let {
                zoneRegulatorySpeedKn = it.coerceIn(1f, 20f)
            }
            props.getProperty("speedZone.hysteresisM")?.toDoubleOrNull()?.let {
                speedZoneHysteresisM = it.coerceIn(0.0, 50.0)
            }
            for (src in DepthSource.entries) {
                val key = src.name.lowercase()
                props.getProperty("isobar.color.$key")?.let { parseColorOrNull(it) }?.let { isobarColors[src] = it }
                props.getProperty("isobar.width.$key")?.toFloatOrNull()?.let { isobarWidthBonuses[src] = it.coerceIn(-4f, 6f) }
            }
            props.getProperty("isobar.color.default")?.let { parseColorOrNull(it) }?.let {
                isobarColorDefault = it
            }
            props.getProperty("ui.button.background")?.let { parseColorOrNull(it) }?.let {
                buttonActionBgColor = it
            }
            props.getProperty("ui.button.icon")?.let { parseColorOrNull(it) }?.let {
                buttonActionIconColor = it
            }
            props.getProperty("ui.button.iconActiveAlpha")?.toFloatOrNull()?.let {
                buttonActionIconActiveAlpha = it.coerceIn(0f, 1f)
            }
            props.getProperty("ui.button.iconInactiveAlpha")?.toFloatOrNull()?.let {
                buttonActionIconInactiveAlpha = it.coerceIn(0f, 1f)
            }

            // ── Colors from colors.properties ────────────────────────────────────
            props.getProperty("ui.dashboard.background")?.let { parseColorOrNull(it) }?.let { uiDashboardBackground = it }
            props.getProperty("ui.dashboard.card.background")?.let { parseColorOrNull(it) }?.let { uiDashboardCardBackground = it }
            props.getProperty("ui.dashboard.text.primary")?.let { parseColorOrNull(it) }?.let { uiDashboardTextPrimary = it }
            props.getProperty("ui.dashboard.text.muted")?.let { parseColorOrNull(it) }?.let { uiDashboardTextMuted = it }
            props.getProperty("ui.dashboard.status.success")?.let { parseColorOrNull(it) }?.let { uiDashboardStatusSuccess = it }
            props.getProperty("ui.dashboard.status.warning")?.let { parseColorOrNull(it) }?.let { uiDashboardStatusWarning = it }
            props.getProperty("ui.dashboard.status.error")?.let { parseColorOrNull(it) }?.let { uiDashboardStatusError = it }
            props.getProperty("ui.dashboard.status.neutral")?.let { parseColorOrNull(it) }?.let { uiDashboardStatusNeutral = it }
            props.getProperty("ui.dashboard.status.absent")?.let { parseColorOrNull(it) }?.let { uiDashboardStatusAbsent = it }
            props.getProperty("ui.dashboard.zone.safe")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneSafe = it }
            props.getProperty("ui.dashboard.zone.caution")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneCaution = it }
            props.getProperty("ui.dashboard.zone.danger")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneDanger = it }
            props.getProperty("ui.dashboard.zone.compliant")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneCompliant = it }
            props.getProperty("ui.dashboard.zone.normal")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneNormal = it }
            props.getProperty("ui.dashboard.zone.dangerDark")?.let { parseColorOrNull(it) }?.let { uiDashboardZoneDangerDark = it }
            props.getProperty("ui.dashboard.distance.entry")?.let { parseColorOrNull(it) }?.let { uiDashboardDistanceEntry = it }
            props.getProperty("ui.dashboard.distance.exit")?.let { parseColorOrNull(it) }?.let { uiDashboardDistanceExit = it }
            props.getProperty("ui.dashboard.dullAlpha")?.toFloatOrNull()?.let { uiDashboardDullAlpha = it.coerceIn(0f, 1f) }

            props.getProperty("ui.settings.background")?.let { parseColorOrNull(it) }?.let { uiSettingsBackground = it }
            props.getProperty("ui.settings.toast.background")?.let { parseColorOrNull(it) }?.let { uiSettingsToastBackground = it }
            props.getProperty("ui.settings.toast.text")?.let { parseColorOrNull(it) }?.let { uiSettingsToastText = it }

            props.getProperty("map.coastline.mainland.color")?.let { parseColorOrNull(it) }?.let { mapCoastlineMainlandColor = it }
            props.getProperty("map.coastline.mainland.width")?.toIntOrNull()?.let { mapCoastlineMainlandWidth = it.coerceIn(1, 50) }
            props.getProperty("map.coastline.island.color")?.let { parseColorOrNull(it) }?.let { mapCoastlineIslandColor = it }
            props.getProperty("map.coastline.island.width")?.toIntOrNull()?.let { mapCoastlineIslandWidth = it.coerceIn(1, 50) }

            props.getProperty("map.navigation.arrow.color")?.let { parseColorOrNull(it) }?.let { mapNavigationArrowColor = it }
            props.getProperty("map.navigation.line.color")?.let { parseColorOrNull(it) }?.let { mapNavigationLineColor = it }

            props.getProperty("map.depth.nodata.color")?.let { parseColorOrNull(it) }?.let { mapDepthNodataColor = it }

            props.getProperty("overlay.lowDepth.color")?.let { parseColorOrNull(it) }?.let { overlayLowDepthColor = it }
            props.getProperty("overlay.lowDepth.minOpacity")?.toIntOrNull()?.let { overlayLowDepthMinOpacity = it.coerceIn(0, 100) }

            props.getProperty("status.gps.demo")?.let { parseColorOrNull(it) }?.let { statusGpsDemo = it }
            props.getProperty("status.gps.acquiring")?.let { parseColorOrNull(it) }?.let { statusGpsAcquiring = it }
            props.getProperty("status.gps.healthy")?.let { parseColorOrNull(it) }?.let { statusGpsHealthy = it }
            props.getProperty("status.gps.idle")?.let { parseColorOrNull(it) }?.let { statusGpsIdle = it }
            props.getProperty("status.gps.stale")?.let { parseColorOrNull(it) }?.let { statusGpsStale = it }
            props.getProperty("status.gps.alpha.active")?.toFloatOrNull()?.let { statusGpsAlphaActive = it.coerceIn(0f, 1f) }
            props.getProperty("status.gps.alpha.dimmed")?.toFloatOrNull()?.let { statusGpsAlphaDimmed = it.coerceIn(0f, 1f) }

            props.getProperty("status.earthWater.water")?.let { parseColorOrNull(it) }?.let { statusEarthWaterWater = it }
            props.getProperty("status.earthWater.land")?.let { parseColorOrNull(it) }?.let { statusEarthWaterLand = it }
            props.getProperty("status.earthWater.inactive")?.let { parseColorOrNull(it) }?.let { statusEarthWaterInactive = it }

            // ── Arc anchor button ─────────────────────────────────────────────
            props.getProperty("ui.arc.anchor.color")?.let { parseColorOrNull(it) }?.let { uiArcAnchorColor = it }
            props.getProperty("ui.arc.anchor.background")?.let { parseColorOrNull(it) }?.let { uiArcAnchorBackground = it }

            // ── Dashboard depth readout tints ─────────────────────────────────
            props.getProperty("ui.dashboard.readout.collision")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutCollision = it }
            props.getProperty("ui.dashboard.readout.shallow")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutShallow = it }
            props.getProperty("ui.dashboard.readout.deep")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutDeep = it }

            // ── Settings panel ────────────────────────────────────────────────
            props.getProperty("ui.settings.text.primary")?.let { parseColorOrNull(it) }?.let { uiSettingsTextPrimary = it }
            props.getProperty("ui.settings.text.muted")?.let { parseColorOrNull(it) }?.let { uiSettingsTextMuted = it }
            props.getProperty("ui.settings.text.secondary")?.let { parseColorOrNull(it) }?.let { uiSettingsTextSecondary = it }
            props.getProperty("ui.settings.accent")?.let { parseColorOrNull(it) }?.let { uiSettingsAccent = it }
            props.getProperty("ui.settings.card.background")?.let { parseColorOrNull(it) }?.let { uiSettingsCardBackground = it }
            props.getProperty("ui.settings.divider")?.let { parseColorOrNull(it) }?.let { uiSettingsDivider = it }
            props.getProperty("ui.settings.switch.track.inactive")?.let { parseColorOrNull(it) }?.let { uiSettingsSwitchTrackInactive = it }
            props.getProperty("ui.settings.input.border")?.let { parseColorOrNull(it) }?.let { uiSettingsInputBorder = it }
            props.getProperty("ui.settings.footer.text")?.let { parseColorOrNull(it) }?.let { uiSettingsFooterText = it }
            props.getProperty("ui.settings.danger")?.let { parseColorOrNull(it) }?.let { uiSettingsDanger = it }

            // ── Regulated zone type colours ──────────────────────────────────
            props.getProperty("regulatedZone.type.speedLimit")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeSpeedLimit = it }
            props.getProperty("regulatedZone.type.anchoringProhibited")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeAnchoringProhibited = it }
            props.getProperty("regulatedZone.type.accessProhibited")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeAccessProhibited = it }
            props.getProperty("regulatedZone.type.environmental")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeEnvironmental = it }
            props.getProperty("regulatedZone.type.mooring")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeMooring = it }
            props.getProperty("regulatedZone.type.fishingProhibited")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeFishingProhibited = it }
            props.getProperty("regulatedZone.type.navigationRestriction")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeNavigationRestriction = it }
            props.getProperty("regulatedZone.type.other")?.let { parseColorOrNull(it) }?.let { regulatedZoneTypeOther = it }

            // ── Map overlays ──────────────────────────────────────────────────
            props.getProperty("map.hazard.disc.fill")?.let { parseColorOrNull(it) }?.let { mapHazardDiscFill = it }
            props.getProperty("map.hazard.outline")?.let { parseColorOrNull(it) }?.let { mapHazardOutline = it }
            props.getProperty("map.zoneAhead.line")?.let { parseColorOrNull(it) }?.let { mapZoneAheadLine = it }
            props.getProperty("map.zoneAhead.cone.fill")?.let { parseColorOrNull(it) }?.let { mapZoneAheadConeFill = it }
            props.getProperty("map.zoneAhead.cone.outline")?.let { parseColorOrNull(it) }?.let { mapZoneAheadConeOutline = it }
            props.getProperty("map.zone300.fill")?.let { parseColorOrNull(it) }?.let { mapZone300Fill = it }
            props.getProperty("map.zone300.boundary")?.let { parseColorOrNull(it) }?.let { mapZone300Boundary = it }

            // ── Progress/error overlay ────────────────────────────────────────
            props.getProperty("ui.progress.accent")?.let { parseColorOrNull(it) }?.let { uiProgressAccent = it }
            props.getProperty("ui.progress.track")?.let { parseColorOrNull(it) }?.let { uiProgressTrack = it }
            props.getProperty("ui.error.card")?.let { parseColorOrNull(it) }?.let { uiErrorCard = it }
            props.getProperty("ui.error.text")?.let { parseColorOrNull(it) }?.let { uiErrorText = it }
            props.getProperty("ui.error.button.background")?.let { parseColorOrNull(it) }?.let { uiErrorButtonBackground = it }
            props.getProperty("ui.error.button.text")?.let { parseColorOrNull(it) }?.let { uiErrorButtonText = it }

            // ── Isobath colours (from colors.properties; may also be set via zone.properties) ──
            props.getProperty("map.isobar.litto3d.color")?.let { parseColorOrNull(it) }?.let { isobarColors[DepthSource.LITTO3D] = it }
            props.getProperty("map.isobar.emodnet.color")?.let { parseColorOrNull(it) }?.let { isobarColors[DepthSource.EMODNET] = it }
            props.getProperty("map.isobar.default.color")?.let { parseColorOrNull(it) }?.let { isobarColorDefault = it }

            // ── Isobath stroke widths (from colors.properties; may also be set via zone.properties) ──
            props.getProperty("map.isobar.litto3d.width")?.toFloatOrNull()?.let { isobarWidthBonuses[DepthSource.LITTO3D] = it.coerceIn(-4f, 6f) }
            props.getProperty("map.isobar.emodnet.width")?.toFloatOrNull()?.let { isobarWidthBonuses[DepthSource.EMODNET] = it.coerceIn(-4f, 6f) }

            // ── Depth colour ramp ─────────────────────────────────────────────
            props.getProperty("map.depth.ramp.shallow.r")?.toIntOrNull()?.let { mapDepthRampShallowR = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.shallow.g")?.toIntOrNull()?.let { mapDepthRampShallowG = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.shallow.b")?.toIntOrNull()?.let { mapDepthRampShallowB = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.deep.r")?.toIntOrNull()?.let { mapDepthRampDeepR = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.deep.g")?.toIntOrNull()?.let { mapDepthRampDeepG = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.deep.b")?.toIntOrNull()?.let { mapDepthRampDeepB = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.warning.r")?.toIntOrNull()?.let { mapDepthRampWarningR = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.warning.g")?.toIntOrNull()?.let { mapDepthRampWarningG = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.warning.b")?.toIntOrNull()?.let { mapDepthRampWarningB = it.coerceIn(0, 255) }
            props.getProperty("map.depth.ramp.alpha")?.toIntOrNull()?.let { mapDepthRampAlpha = it.coerceIn(0, 255) }

        } catch (_: Exception) {
            // Keep defaults — properties file missing or corrupt.
        }
    }

    /** Isobath stroke colour (ARGB) for a data source, from zone.properties (defaults baked in). */
    fun isobarColor(source: DepthSource): Int = isobarColors[source] ?: isobarColorDefault

    /** Extra isobath stroke width (px) for a data source (0 if unset). */
    fun isobarWidthBonus(source: DepthSource): Float = isobarWidthBonuses[source] ?: 0f

    private fun parseColorOrNull(s: String): Int? =
        try { Color.parseColor(s.trim()) } catch (_: Exception) { null }
}
