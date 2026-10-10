package ykws.android.maro.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.regulation.ZoneDisplayCategory

/**
 * User-facing settings persisted via SharedPreferences.
 *
 * Exposes a reactive [settings] [StateFlow] so the Compose UI observes changes
 * without polling. Call [update] to apply a partial or full mutation.
 *
 * @property defaultLatitude   Initial map center latitude  (WGS84, °N) — used when
 *                             no persisted position exists.
 * @property defaultLongitude  Initial map center longitude (WGS84, °E).
 * @property coastlineVisible  Whether the coastline polyline overlay is drawn.
 * @property showLandWaterIcon Whether the top-left row's land/water status square is drawn.
 * @property zone300Visible    Whether the 300 m regulatory band overlay is drawn.
 * @property zone300AutoShowGps   GPS mode: auto-reveal the hidden 300 m band on approach.
 *                             When off, the band stays under manual control in GPS mode.
 * @property zone300AutoShowDemo  Demo mode: same approach auto-reveal, driven by pan speed.
 * @property mapCenterLat      Persisted map center latitude  (NaN = not yet saved).
 * @property mapCenterLon      Persisted map center longitude (NaN = not yet saved).
 * @property zoomLevel         Persisted map zoom level (0.0 = not yet saved).
 * @property isWater           Last known water/land status — persisted so the
 *                             boat marker renders at the correct size on restart.
 * @property distanceToShore   Last known distance to coast (m, NaN = unknown) —
 *                             persisted so the boat marker renders at the correct
 *                             size on restart without waiting for the shore pipeline.
 * @property gpsMode           When true, the device GPS drives the map center and the map
 *                             rotates heading-up; when false, free-pan "demo" mode.
 * @property recenterDelaySeconds  GPS mode: seconds of no user pan before auto-follow/-orient
 *                             resumes (1–10). Default 5.
 * @property gpsActiveIntervalSec   GPS mode (moving): minimum seconds between fixes (1–10). The
 *                             acquisition presets write this + [gpsActiveMinDistanceM]. Default 2.
 * @property gpsActiveMinDistanceM  GPS mode (moving): minimum metres of movement between fixes
 *                             (1–25). Default 5.
 * @property stopDetectionEnabled   Master toggle for stop detection. When off, policy always ACTIVE.
 * @property stopDetectionTimeSec   Seconds of sub-threshold movement before isStill() returns true
 *                             (10–90). Default 45.
 * @property stopDetectionDistanceM Max displacement (m) still counted as "stationary" (10–30).
 *                             Default 15.
 * @property stopDetectionDelayGps  When true, GPS fixes space out when isStill() (battery saving).
 * @property mapRefreshFps     GPS auto-follow re-render ceiling in frames/s (5–50). Lower = fewer
 *                             whole-map repaints = less battery. Default 25.
 * @property emodnetShallowCutoffM  EMODnet shallow cutoff (m): EMODnet point readings shallower
 *                             than this are coarse (115 m cell over rocks/coast) and unreliable →
 *                             presented as no-data. 0 disables the gate. Default 2.0.
 */
data class AppSettings(
    val defaultLatitude: Double = 43.55,
    val defaultLongitude: Double = 7.00,
    val coastlineVisible: Boolean = BuildConfig.LAYER_COASTLINE_DEFAULT,
    val showLandWaterIcon: Boolean = true,
    val zone300Visible: Boolean = BuildConfig.LAYER_ZONE300_DEFAULT,
    val zoneAutoRevealDistanceM: Float = 100f,
    val zoneAutoRevealTimeS: Int = 10,
    val approachAutoShowGps: Boolean = true,
    val approachAutoShowDemo: Boolean = true,
    val zone300AutoShow: Boolean = true,
    val speedZoneAutoShow: Boolean = true,
    val regulatedZoneAutoShow: Boolean = true,
    val gpsMode: Boolean = false,
    val recenterDelaySeconds: Int = 5,
    val gpsActiveIntervalSec: Int = 2,
    val gpsActiveMinDistanceM: Float = 5f,
    val stopDetectionEnabled: Boolean = true,
    val stopDetectionTimeSec: Int = 45,
    val stopDetectionDistanceM: Int = 15,
    val stopDetectionDelayGps: Boolean = true,
    val mapRefreshFps: Int = 25,
    val mapCenterLat: Double = Double.NaN,
    val mapCenterLon: Double = Double.NaN,
    val zoomLevel: Double = 0.0,
    val isWater: Boolean = true,
    val distanceToShore: Double = Double.NaN,
    /** App language: "system" (device locale, English fallback), "en", or "fr". */
    val languageCode: String = "system",
    /**
     * Master "don't lock the phone while the app is open" setting: holds `FLAG_KEEP_SCREEN_ON` on
     * the window while the app is in front. Default `false`, matching the read fallback below and
     * the shipped behaviour — the screen may sleep unless the user asks otherwise.
     *
     * With [keepScreenOnMovementGate] off the screen is held the whole time the app is in front.
     * With it on, the hold follows [keepScreenOnSpeedThresholdKn] and [keepScreenOnGraceMinutes].
     * See `xTrack/Performance/FEAT_DSC_Performance.md` (power management).
     */
    val keepScreenOn: Boolean = false,
    /** Hold the screen only while moving, or shortly after the last touch, instead of always. */
    val keepScreenOnMovementGate: Boolean = false,
    /** Movement gate: speed over ground (knots) above which the boat counts as moving. */
    val keepScreenOnSpeedThresholdKn: Float =
        ykws.android.maro.config.AppConfig.powerScreenMovementThresholdKn,
    /** Movement gate: minutes (1–15, default from `maro.properties`) without movement or interaction. */
    val keepScreenOnGraceMinutes: Int =
        ykws.android.maro.config.AppConfig.powerScreenGraceDefaultMinutes,
    /**
     * True once the battery-optimization prompt has been shown and answered, so it never reappears.
     * Absorbed from the legacy `maro_battery_prefs` store on first run (see the migration in `init`).
     */
    val batteryOptimizationPrompted: Boolean = false,
    /** Highlight charted shallow water as a bright grounding-hazard overlay. */
    val lowDepthWarningVisible: Boolean = true,
    /** Crash depth (m): the overlay is fully opaque from the surface down to this depth. */
    val lowDepthCrashDepthM: Float = DepthConstants.LOW_DEPTH_CRASH_DEPTH_M.toFloat(),
    /** Start-warning depth (m): the warning begins here and is transparent at/beyond this depth. */
    val lowDepthStartWarningM: Float = DepthConstants.LOW_DEPTH_START_WARNING_M.toFloat(),
    /**
     * 300 m band fill + boundary colour (opaque ARGB). Fill alpha is derived from
     * [zone300FillTransparencyPct]. Seeded from `map.zone300.boundary` in `maro.properties`, so the
     * property stays the single home of the value rather than a literal here.
     */
    val zone300Color: Int = ykws.android.maro.config.AppConfig.mapZone300Boundary,
    /** 300 m band fill transparency % (0–100, higher = more invisible). Default 80 ≈ today's 0x30 fill alpha. */
    val zone300FillTransparencyPct: Int = 80,
    /** 300 m band seaward boundary transparency % (0–100). */
    val zone300BoundaryTransparencyPct: Int = 20,
    /** 300 m band seaward boundary stroke width (dp). Seeded from `map.zone300.boundary.widthDp`. */
    val zone300BoundaryWidthDp: Float = ykws.android.maro.config.AppConfig.mapZone300BoundaryWidthDp,
    /** Regulated zone polygon fill transparency % (0–100, higher = more invisible). Defaults mirror the 300 m band. */
    val regulatedZoneFillTransparencyPct: Int = 80,
    /** Regulated zone polygon outline transparency % (0–100). Defaults mirror the 300 m band. */
    val regulatedZoneBoundaryTransparencyPct: Int = 20,
    /** Regulated zone outline stroke width (dp). Seeded from `map.regulatedZone.outline.widthDp`. */
    val regulatedZoneOutlineWidthDp: Float = ykws.android.maro.config.AppConfig.mapRegulatedZoneOutlineWidthDp,
    /** Coastline stroke width (dp), mainland and island alike. Seeded from `map.coastline.widthDp`. */
    val coastlineWidthDp: Float = ykws.android.maro.config.AppConfig.mapCoastlineWidthDp,
    /** Coastline stroke transparency % (0–100, higher = more invisible). Seeded from `map.coastline.transparencyPct`. */
    val coastlineTransparencyPct: Int = ykws.android.maro.config.AppConfig.mapCoastlineTransparencyPct,
    /** Coastline mainland stroke colour (opaque ARGB). Seeded from `map.coastline.mainland.color`. */
    val coastlineMainlandColor: Int = ykws.android.maro.config.AppConfig.mapCoastlineMainlandColor,
    /** Coastline island stroke colour (opaque ARGB). Seeded from `map.coastline.island.color`. */
    val coastlineIslandColor: Int = ykws.android.maro.config.AppConfig.mapCoastlineIslandColor,
    /** Idle threshold (s) before a BoatMarker snapshot + auto-marker is captured. */
    val boatMarkerIdleThresholdSec: Long = ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec,
    /** Minimum idle duration (s) before an auto-marker becomes permanent. */
    val boatMarkerAutoMarkerMinDurationSec: Long = ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerMinDurationSec,
    /** Dedup radius (m) — skip auto-marker creation when an existing IDLE_AUTO marker is within this distance. */
    val boatMarkerAutoMarkerDedupRadiusM: Double = ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerDedupRadiusM,
    /** EMODnet shallow cutoff (m): EMODnet point readings shallower than this are coarse
     *  (115 m cell over rocks/coast) and unreliable → presented as no-data. 0 disables the gate. */
    val emodnetShallowCutoffM: Float = 2.0f,
    /**
     * Free-water pace (kn, 3–40) a route's trip figure plans at until the boat's own observed pace
     * replaces it. Seeded from `route.freeWaterPaceKn`, so the property stays the value's one home.
     */
    val routeFreeWaterPaceKn: Float = ykws.android.maro.config.AppConfig.routeFreeWaterPaceKn,
    /**
     * The **aversion λ** (0–5) the route search bends around slow water at, seeded from
     * `route.avoid.speedZone.softCostAversion`, so the property stays the value's one home and the
     * shipped 4 is the day-one value until the user moves the dial. The user-facing preference of the
     * slow-water model — 0 prices slow water as open water, 1 minimises real time, and 5 stays out.
     */
    val routeSlowWaterAversion: Float =
        ykws.android.maro.config.AppConfig.routeAvoidSpeedZoneSoftCostAversion.toFloat(),
    /**
     * The **slow-water budget** (per cent of a trip, 0–100) the λ loop aims at, seeded from
     * `route.avoid.speedZone.timeBudgetPct`, so the property stays the value's one home. How much
     * slow water a trip may use is a preference rather than a tuning constant — the pace's own
     * counterpart, and the reason it carries a Settings row of its own.
     */
    val routeSlowWaterBudgetPct: Int =
        ykws.android.maro.config.AppConfig.routeAvoidSpeedZoneTimeBudgetPct,
    /**
     * The route algorithm the harness arms with — the id the registry resolves, seeded from
     * `route.engine.id`, so the property stays the value's one home and the user's choice persists
     * here. An id nothing claims falls back to the registry's default and is reported at startup.
     */
    val routeEngineId: String = ykws.android.maro.config.AppConfig.routeEngineId,
    /**
     * The speed-zone ids the user has excluded from route planning, default empty. Persisted so an
     * exclusion survives a restart; the avoid engine drops these ids before the fill, the ETA and the
     * forced-crossing report alike.
     */
    val excludedSpeedZoneIds: Set<String> = emptySet(),
    /**
     * **The start the drawer arms the acquisition on** — a
     * [ykws.android.maro.data.route.RouteEndSelection] token, one value per navigation mode so GPS and
     * demo remember their own. Empty reads as unresolved, and
     * [ykws.android.maro.data.route.RouteEndSelection.resolve] answers the end's first entry for it.
     */
    val routeStartSelectionGps: String = "",
    /** The start the drawer arms on in demo mode — the same token family, its own memory. */
    val routeStartSelectionDemo: String = "",
    /** **The destination the drawer arms the acquisition on** — the same tokens, per navigation mode. */
    val routeDestinationSelectionGps: String = "",
    /** The destination the drawer arms on in demo mode. */
    val routeDestinationSelectionDemo: String = "",
    /** Regenerate: reload depth grid from assets. */
    val regenGrid: Boolean = true,
    /** Regenerate: re-derive isobath contours. */
    val regenIsobaths: Boolean = true,
    /** Regenerate: rebuild + re-cache depth colour raster. */
    val regenColour: Boolean = true,
    /** Regenerate: rebuild + re-cache shallow warning raster. */
    val regenWarning: Boolean = true,
    /** Show the dashed heading direction line from the boat marker to the map edge. */
    val headingLineVisible: Boolean = true,
    /** Show the speed-proportional cap arrow projecting from the boat marker. */
    val capArrowVisible: Boolean = false,
    /** Heading line stroke width (dp). Seeded from `map.navigation.line.widthDp`. */
    val navigationLineWidthDp: Float = ykws.android.maro.config.AppConfig.mapNavigationLineWidthDp,
    /** Heading line stroke transparency % (0 = opaque, 100 = invisible). Seeded from `map.navigation.line.transparencyPct`. */
    val navigationLineTransparencyPct: Int = ykws.android.maro.config.AppConfig.mapNavigationLineTransparencyPct,
    /** Heading line colour (opaque ARGB) — the transparency above carries its alpha. Seeded from `map.navigation.line.color`. */
    val navigationLineColor: Int = ykws.android.maro.config.AppConfig.mapNavigationLineColor,
    /** Cap arrow shaft width (dp); the head is derived from it. Seeded from `map.navigation.arrow.widthDp`. */
    val navigationArrowWidthDp: Float = ykws.android.maro.config.AppConfig.mapNavigationArrowWidthDp,
    /** Cap arrow transparency % (0 = opaque, 100 = invisible). Seeded from `map.navigation.arrow.transparencyPct`. */
    val navigationArrowTransparencyPct: Int = ykws.android.maro.config.AppConfig.mapNavigationArrowTransparencyPct,
    /** Cap arrow manual colour, painted while the Speed Colour mode is off. Seeded from `map.navigation.arrow.color`. */
    val navigationArrowColor: Int = ykws.android.maro.config.AppConfig.mapNavigationArrowColor,
    /** Cap arrow colour mode — true follows the speed the boat carries. Seeded from `map.navigation.arrow.followSpeedColor`. */
    val navigationArrowFollowSpeedColour: Boolean =
        ykws.android.maro.config.AppConfig.mapNavigationArrowFollowSpeedColour,
    /** Show the hypsometric depth colour map and isobath contour overlays. */
    val depthLayerVisible: Boolean = true,
    /** Whether the regulated zones overlay (speed limits, anchoring, access, …) is drawn. */
    val regulatedZonesVisible: Boolean = BuildConfig.LAYER_REGULATED_ZONES_DEFAULT,
    /** Show zone info text panel beside the icon stack. */
    val regulationInfoVisible: Boolean = false,
    /** Boat length in metres — used to filter out zones with vessel size exemptions. */
    val boatSizeM: Double = BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M,
    /** Per-category visibility toggles for the regulated zone warning strip. */
    val showCategoryNoAnchor: Boolean = true,
    val showCategoryMooring: Boolean = false,
    val showCategorySpeedLimit: Boolean = true,
    val showCategoryNoDiving: Boolean = true,
    val showCategorySeaplane: Boolean = false,
    val showCategoryNoAccess: Boolean = true,
    val showCategoryFishingProhibited: Boolean = false,
    val showCategoryEnvironmental: Boolean = true,
    val showCategoryInformation: Boolean = false,
    /**
     * GPS idle mode: minimum metres of movement between fixes when the adaptive policy
     * has switched to [AcquisitionMode.IDLE] (device stationary). Default 0 so even tiny
     * drifts update the position at the idle cadence — prevents the perception of a
     * "stuck" position when anchored or drifting slowly.
     */
    /** Demo mode: rotate map heading-up (pan-direction-derived bearing) instead of north-up. */
    val demoHeadingUp: Boolean = false,
    val gpsIdleMinDistanceM: Float = 0f,
    /** Enable track recording. */
    val trackEnabled: Boolean = BuildConfig.TRACK_ENABLED_DEFAULT,
    /** Geofence origin latitude for auto start/stop (Port Salis). */
    val trackOriginLat: Double = BuildConfig.TRACK_ORIGIN_LAT,
    /** Geofence origin longitude for auto start/stop (Port Salis). */
    val trackOriginLon: Double = BuildConfig.TRACK_ORIGIN_LON,
    /** Geofence radius in metres for auto start/stop. */
    val trackGeofenceRadiusM: Double = BuildConfig.TRACK_GEOFENCE_RADIUS_M,
    /** When false, recording starts on movement alone (no geofence check). */
    val trackGeofenceEnabled: Boolean = true,
    /** Maximum GPS self-reported accuracy (m) for a fix to be recorded. Fixes with worse accuracy are rejected. */
    val maxRecordingAccuracyM: Float = 30f,
    /** Whether the tracks overlay layer is visible on the map. */
    /** Binary marker layer visibility: HIDDEN or SHOW_ALL. */
    val markerLayerState: ykws.android.maro.ui.map.MarkerLayerState = ykws.android.maro.ui.map.MarkerLayerState.SHOW_ALL,
    /** Whether marker zone shapes (circle outlines, corridor parallels) render.
     *  When false, only center dots are drawn. Proximity previews follow this toggle. */
    val markerZonesVisible: Boolean = true,
    /** Halo size % (0-100) — scales the halo ring radius relative to its anchor
     *  (dot or icon). Default 50 = medium. */
    val markerHaloSize: Int = 50,
    /** Marker point/icon rendering zoom % (50-150). 100 = current size. Scales the
     *  rendered dot radius and icon glyph; the halo ring follows via the same factor. */
    val markerPointIconZoom: Int = 100,
    /** Pinned halo colour (opaque ARGB) drawn as a static ring behind each confirmed
     *  pinned marker's centre dot/icon. */
    val markerHaloPinnedColor: Int = 0xFFFFFFFF.toInt(),
    /** Unpinned halo colour (opaque ARGB) — distinct from pinned so the two states
     *  are visually separable. */
    val markerHaloUnpinnedColor: Int = 0xFF81D4FA.toInt(),
    /** Pinned halo inside-fill ("zone") transparency % (0-100). Default 75 = subtle disc. */
    val markerHaloPinnedFillTransparencyPct: Int = 75,
    /** Pinned halo border/stroke transparency % (0-100). Default 20 = strong ring. */
    val markerHaloPinnedBorderTransparencyPct: Int = 20,
    /** Unpinned halo inside-fill ("zone") transparency % (0-100). Default 90 = faint disc. */
    val markerHaloUnpinnedFillTransparencyPct: Int = 90,
    /** Unpinned halo border/stroke transparency % (0-100). Default 60 = faint ring. */
    val markerHaloUnpinnedBorderTransparencyPct: Int = 60,
    /**
     * The two kinds' own map visibility — the drawer headers' eye toggles — and, above them, the layer
     * fan's own master gate. [routeTracksVisible] is the fan's word and rules both kinds: neither is
     * drawn while it is off, whatever the eyes say, and the fan's badge reads it (2026-10-10).
     * Under it, [tracksVisible] gates recorded tracks (pinned tracks with them) and [routesVisible]
     * gates routes (pinned routes with them); each eye is a **render switch alone**, so the menu counts
     * follow the filters and never these flags.
     */
    val routeTracksVisible: Boolean = true,
    val tracksVisible: Boolean = true,
    val routesVisible: Boolean = true,
    /**
     * Which face the map's speed-scale control wears: true is the expanded card, false the collapsed
     * toggle square. An unwritten key means expanded, i.e. exactly the behaviour before the toggle
     * existed, so nothing migrates and the field is deliberately non-null — the save chain already
     * writes every non-null field unconditionally.
     */
    val trackLegendExpanded: Boolean = true,
    /**
     * **The tracks kind's arrows axis** (2026-10-07): whether recorded tracks wear direction chevrons.
     * One half of the pair the menu's twin box owns — no kind is master over another now — and the
     * arrows' only owner: the eye never moves it, so a gold selection keeps its chevrons and a banded
     * one does not. Seeded from `path.track.arrow.enabled`; default off.
     */
    val trackArrows: Boolean = ykws.android.maro.config.AppConfig.trackArrowEnabledSeed,
    /**
     * **The tracks kind's speed-colours axis** (2026-10-07): whether recorded tracks paint from the
     * speed ramp rather than the default colours. It is the *fill* alone — the chevrons stay
     * [trackArrows]' business — and it governs the **recorded kind only**: a route reads its own
     * [routeSpeedColor], so with this off a route is untouched. Seeded from `path.track.heatmap.enabled`;
     * default on.
     */
    val trackColours: Boolean = ykws.android.maro.config.AppConfig.trackHeatmapEnabledSeed,
    /**
     * **The route kind's speed-colours axis** (2026-10-07): the route's own counterpart to [trackColours],
     * with no master chip above it — a route bands exactly while this is on. Seeded from
     * `path.route.heatmap.enabled`; default off, so a route opens on its own colour pair.
     */
    val routeSpeedColor: Boolean = ykws.android.maro.config.AppConfig.routeHeatmapEnabledSeed,
    /**
     * **The route kind's arrows axis** (2026-10-07): the route's own counterpart to [trackArrows]. Seeded
     * from `path.route.arrow.enabled`; default on. The `acquisition` class leaf silences the search's own
     * rung on both axes independently of this value.
     */
    val routeSpeedArrows: Boolean = ykws.android.maro.config.AppConfig.routeArrowEnabledSeed,
    /** Direction-arrow density mode: uniform on-screen spacing or speed-based. */
    val trackDirectionDensity: ykws.android.maro.ui.map.TrackDirectionDensity = ykws.android.maro.ui.map.TrackDirectionDensity.UNIFORM,
    /** Speed (kn) below which direction arrows use minimum spacing. */
    val trackDirectionSpeedFloorKn: Float = ykws.android.maro.config.AppConfig.trackDirectionSpeedFloorKn,
    /** Speed (kn) above which direction arrows use maximum spacing. */
    val trackDirectionSpeedCeilingKn: Float = ykws.android.maro.config.AppConfig.trackDirectionSpeedCeilingKn,
    /** Minimum on-screen spacing (dp) between direction arrows. */
    val trackDirectionMinSpacingDp: Int = ykws.android.maro.config.AppConfig.trackDirectionMinSpacingDp,
    /** Maximum on-screen spacing (dp) between direction arrows. */
    val trackDirectionMaxSpacingDp: Int = ykws.android.maro.config.AppConfig.trackDirectionMaxSpacingDp,
    /** Number of historical tracks to render on the map (0-20) — seeded from `path.count`. */
    val trackingRenderNb: Int = ykws.android.maro.config.AppConfig.pathCount,
    /**
     * Number of routes to render, non-pinned ones alone (0-20): a pinned route is drawn
     * whatever this says, the pin being what marks a route already saved. Its own count rather than
     * [trackingRenderNb]'s, which goes on counting recorded tracks alone.
     */
    val routeRenderNb: Int = ykws.android.maro.config.AppConfig.pathCount,
    /** ARGB color for the active recording track. */
    val trackingColorActive: Int = ykws.android.maro.config.AppConfig.trackColourActive,
    /**
     * ARGB start color for past track gradient (newest track).
     * Interpolates toward [trackingColorPastTo] for older tracks.
     */
    val trackingColorPastFrom: Int = ykws.android.maro.config.AppConfig.trackColourFrom,
    /**
     * ARGB end color for past track gradient (oldest track).
     * Interpolated from [trackingColorPastFrom] for newer tracks.
     */
    val trackingColorPastTo: Int = ykws.android.maro.config.AppConfig.trackColourTo,
    /**
     * ARGB start colour of the route gradient — the newest route, taken on a route's own unpinned
     * path; a pinned route draws from the pinned-route pair instead, through the shared pinned path
     * (D5). `from` is the newest and `to` the oldest, interpolated across the route set exactly as
     * the past pair is across the historical one.
     */
    val trackingColorRouteFrom: Int = ykws.android.maro.config.AppConfig.routeStoredColourFrom,
    /** ARGB end colour of the route gradient — the oldest route. */
    val trackingColorRouteTo: Int = ykws.android.maro.config.AppConfig.routeStoredColourTo,
    /**
     * ARGB colour of the line the app is following — the route's own line, seeded from
     * `path.line.color.live` in `maro.properties`, which stays the one home for that fact. Settings'
     * **Active route** row is what writes it.
     */
    val routeLineColor: Int = ykws.android.maro.config.AppConfig.routeLineColor,
    /**
     * Transparency % (0-100) for the NEWEST past (history) track.
     * 0 = fully opaque, 100 = fully invisible.
     * Lower value = newest track more visible.
     */
    val trackingTransparencyNewest: Int = ykws.android.maro.config.AppConfig.trackFadeFrom,
    /**
     * Transparency % (0-100) for the OLDEST past (history) track.
     * 0 = fully opaque, 100 = fully invisible.
     * Higher value = oldest track more faded.
     */
    val trackingTransparencyOldest: Int = ykws.android.maro.config.AppConfig.trackFadeTo,
    /**
     * Transparency % (0-100) for the NEWEST pinned track.
     * 0 = fully opaque, 100 = fully invisible.
     */
    val trackingTransparencyPinnedNewest: Int = ykws.android.maro.config.AppConfig.trackFadePinnedFrom,
    /**
     * Transparency % (0-100) for the OLDEST pinned track.
     * 0 = fully opaque, 100 = fully invisible.
     */
    val trackingTransparencyPinnedOldest: Int = ykws.android.maro.config.AppConfig.trackFadePinnedTo,
    /** Transparency % (0-100) for the NEWEST route drawn — the route ladder's own range. */
    val trackingTransparencyRouteNewest: Int = ykws.android.maro.config.AppConfig.routeStoredFadeFrom,
    /** Transparency % (0-100) for the OLDEST route drawn. */
    val trackingTransparencyRouteOldest: Int = ykws.android.maro.config.AppConfig.routeStoredFadeTo,
    /**
     * ARGB start color for pinned track gradient.
     */
    val trackingColorPinnedFrom: Int = ykws.android.maro.config.AppConfig.trackColourPinnedFrom,
    /**
     * ARGB end color for pinned track gradient.
     */
    val trackingColorPinnedTo: Int = ykws.android.maro.config.AppConfig.trackColourPinnedTo,
    /**
     * Transparency % (0-100) for the NEWEST **pinned route** (D5, D8): a pinned route runs the one
     * pinned path, so it takes a pinned ladder of its own rather than the route ladder an unpinned
     * route reads.
     */
    val trackingTransparencyPinnedRouteNewest: Int = ykws.android.maro.config.AppConfig.routePinnedFadeFrom,
    /** Transparency % (0-100) for the OLDEST pinned route. */
    val trackingTransparencyPinnedRouteOldest: Int = ykws.android.maro.config.AppConfig.routePinnedFadeTo,
    /** ARGB start colour of the pinned-route gradient — the newest. */
    val trackingColorPinnedRouteFrom: Int = ykws.android.maro.config.AppConfig.routePinnedColourFrom,
    /** ARGB end colour of the pinned-route gradient — the oldest. */
    val trackingColorPinnedRouteTo: Int = ykws.android.maro.config.AppConfig.routePinnedColourTo,
    /** Enable track point simplification at finalize (Douglas-Peucker + speed-aware). */
    val trackSimplifyEnabled: Boolean = true,
    /** Douglas-Peucker spatial tolerance (metres). */
    val trackSimplifyEpsilonM: Double = 3.0,
    /** Speed deviation threshold for reinsertion during simplification (knots). */
    val trackSimplifySpeedDeltaKn: Double = 3.0,
    /** Enable WhereAmI debug rays (green/red line-of-sight segments) on the map. */
    val markerDebugRays: Boolean = false,
    /** Sort state for the track history list (field + direction). */
    val trackListSort: ykws.android.maro.data.model.ListSortState = ykws.android.maro.data.model.ListSortState(),
    /** Sort state for the marker management list (field + direction). */
    val markerListSort: ykws.android.maro.data.model.ListSortState = ykws.android.maro.data.model.ListSortState(),
    /** Filter state for the track history list. */
    val trackListFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state for the marker management list. */
    val markerListFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state applied to the MAP overlay for tracks (decoupled from the list via [trackFilterLinked]). */
    val trackMapFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state applied to the MAP overlay for markers (decoupled from the list via [markerFilterLinked]). */
    val markerMapFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** When true the track list and map filters stay identical (editing one writes both). */
    val trackFilterLinked: Boolean = true,
    /** When true the marker list and map filters stay identical (editing one writes both). */
    val markerFilterLinked: Boolean = true,
    /** Sort state for the routes list (field + direction). */
    val routeListSort: ykws.android.maro.data.model.ListSortState = ykws.android.maro.data.model.ListSortState(),
    /** Filter state for the routes list. */
    val routeListFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** Filter state applied to the MAP overlay for routes (decoupled from the list via [routeFilterLinked]). */
    val routeMapFilter: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    /** When true the routes list and map filters stay identical (editing one writes both). */
    val routeFilterLinked: Boolean = true,
    /** Enable automatic map offset in GPS navigation mode. Default true. */
    val mapOffsetGps: Boolean = true,
    /** Enable automatic map offset in demo/manual mode. Default false. */
    val mapOffsetDemo: Boolean = false,
    /** Boat position from screen bottom at full offset (%). 50=centered, 33=default, 5=extreme forward. */
    val mapOffsetBoatFromBottomPct: Int = 33,
) {
    /** Check whether a [ZoneDisplayCategory] is enabled in the current settings. */
    fun isCategoryVisible(cat: ZoneDisplayCategory): Boolean = when (cat) {
        ZoneDisplayCategory.NO_ANCHOR -> showCategoryNoAnchor
        ZoneDisplayCategory.MOORING -> showCategoryMooring
        ZoneDisplayCategory.SPEED_LIMIT -> showCategorySpeedLimit
        ZoneDisplayCategory.NO_DIVING -> showCategoryNoDiving
        ZoneDisplayCategory.SEAPLANE -> showCategorySeaplane
        ZoneDisplayCategory.NO_ACCESS -> showCategoryNoAccess
        ZoneDisplayCategory.FISHING_PROHIBITED -> showCategoryFishingProhibited
        ZoneDisplayCategory.ENVIRONMENTAL -> showCategoryEnvironmental
        ZoneDisplayCategory.INFORMATION -> showCategoryInformation
    }
}

class SettingsManager(
    context: Context,
    private val defaultAutoRevealDistM: Float = 200f,
    private val defaultAutoRevealTimeS: Int = 20
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // No settings versioning. Missing or outdated values fall back to defaults at read time, so
        // basic functionality is always preserved. Idempotent cleanup of keys the code no longer reads
        // (legacy keys replaced by later settings; harmless to remove on every start).
        // One-time absorb of the legacy battery-prompt flag (it used to live in its own prefs file),
        // so nobody is prompted a second time now that the flag is part of AppSettings.
        if (!prefs.contains(KEY_BATTERY_OPT_PROMPTED)) {
            val legacy = context.getSharedPreferences(LEGACY_BATTERY_PREFS, Context.MODE_PRIVATE)
                .getBoolean(LEGACY_BATTERY_OPT_PROMPTED, false)
            if (legacy) prefs.edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, true).apply()
        }

        prefs.edit()
            .remove("user_markers_visible")          // replaced by marker_layer_state (v3)
            // The three stored preferences of the retired track-colour fields, cleaned off an install
            // rather than left behind: nothing that draws ever read them, so there is nothing to
            // migrate — only a key to forget.
            .remove("tracking_color_history")
            .remove("tracking_color_history_end")
            .remove("tracking_color_pinned")
            .remove("zone300_autoshow_gps")          // approach re-display rework (v6)
            .remove("zone300_autoshow_demo")
            .remove("speed_zones_visible")
            .remove("speed_zone_autoshow_gps")
            .remove("speed_zone_autoshow_demo")
            .remove("regulated_zone_autoshow_gps")
            .remove("regulated_zone_autoshow_demo")
            .remove("prefs_version")                 // versioning mechanism removed
            .apply()
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * One-time migration of the retired three-way value, run as the two axes are read: `SIMPLE` meant
     * neither axis, `DIR_SPEED` the chevrons alone and `HEATMAP` — the shipped token for the visible
     * *Colours* — both. It writes the two flags **and** erases the old key in one edit, so storage
     * carries the new axes alone and the retired word never becomes a second home for a value; absent
     * means a device that never wrote it, which takes the fresh-install default instead.
     *
     * Called from the `trackArrows` argument, whose read precedes `trackColours`': the first migrates
     * and the second then reads the flag the first wrote, rather than migrating twice.
     */
    private fun migrateRenderAxes(): String? {
        if (prefs.contains(KEY_TRACK_ARROWS) || prefs.contains(KEY_TRACK_COLOURS)) return null
        val legacy = prefs.getString(KEY_TRACK_RENDER_MODE, null) ?: return null
        prefs.edit()
            .putBoolean(KEY_TRACK_ARROWS, legacy != "SIMPLE")
            .putBoolean(KEY_TRACK_COLOURS, legacy == "HEATMAP")
            .remove(KEY_TRACK_RENDER_MODE)
            .apply()
        return legacy
    }

    private fun load(): AppSettings = AppSettings(
        defaultLatitude  = prefs.getFloat(KEY_DEFAULT_LAT, 43.55f).toDouble(),
        defaultLongitude = prefs.getFloat(KEY_DEFAULT_LON, 7.00f).toDouble(),
        coastlineVisible = prefs.getBoolean(KEY_COASTLINE_VISIBLE, BuildConfig.LAYER_COASTLINE_DEFAULT),
        showLandWaterIcon = prefs.getBoolean(KEY_SHOW_LAND_WATER_ICON, true),
        zone300Visible   = prefs.getBoolean(KEY_ZONE300_VISIBLE, BuildConfig.LAYER_ZONE300_DEFAULT),
        zoneAutoRevealDistanceM = prefs.getFloat(KEY_ZONE_AUTOREVEAL_DIST_M, defaultAutoRevealDistM),
        zoneAutoRevealTimeS     = prefs.getInt(KEY_ZONE_AUTOREVEAL_TIME_S, defaultAutoRevealTimeS),
        approachAutoShowGps = prefs.getBoolean(KEY_APPROACH_AUTOSHOW_GPS, true),
        approachAutoShowDemo = prefs.getBoolean(KEY_APPROACH_AUTOSHOW_DEMO, true),
        zone300AutoShow = prefs.getBoolean(KEY_ZONE300_AUTOSHOW, true),
        speedZoneAutoShow = prefs.getBoolean(KEY_SPEED_ZONE_AUTOSHOW, true),
        regulatedZoneAutoShow = prefs.getBoolean(KEY_REGULATED_ZONE_AUTOSHOW, true),
        gpsMode          = prefs.getBoolean(KEY_GPS_MODE, false),
        recenterDelaySeconds = prefs.getInt(KEY_RECENTER_DELAY_S, 5),
        gpsActiveIntervalSec = prefs.getInt(KEY_GPS_INTERVAL_S, 1),
        gpsActiveMinDistanceM = prefs.getFloat(KEY_GPS_MIN_DISTANCE_M, 0f),
        stopDetectionEnabled     = prefs.getBoolean(KEY_STOP_DETECTION_ENABLED, true),
        stopDetectionTimeSec     = prefs.getInt(KEY_STOP_DETECTION_TIME_S, 45),
        stopDetectionDistanceM   = prefs.getInt(KEY_STOP_DETECTION_DISTANCE_M, 15),
        stopDetectionDelayGps    = prefs.getBoolean(KEY_STOP_DETECTION_DELAY_GPS, true),
        mapRefreshFps    = prefs.getInt(KEY_MAP_REFRESH_FPS, 25),
        mapCenterLat     = prefs.getFloat(KEY_MAP_CENTER_LAT, Float.NaN).toDouble(),
        mapCenterLon     = prefs.getFloat(KEY_MAP_CENTER_LON, Float.NaN).toDouble(),
        zoomLevel        = prefs.getFloat(KEY_ZOOM_LEVEL, 0f).toDouble(),
        isWater          = prefs.getBoolean(KEY_IS_WATER, true),
        distanceToShore  = prefs.getFloat(KEY_DISTANCE_TO_SHORE, Float.NaN).toDouble(),
        languageCode     = prefs.getString(KEY_LANGUAGE_CODE, "system") ?: "system",
        keepScreenOn     = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
        keepScreenOnMovementGate = prefs.getBoolean(KEY_KEEP_SCREEN_ON_MOVEMENT_GATE, false),
        keepScreenOnSpeedThresholdKn = prefs.getFloat(
            KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN,
            ykws.android.maro.config.AppConfig.powerScreenMovementThresholdKn
        ),
        keepScreenOnGraceMinutes = prefs.getInt(
            KEY_KEEP_SCREEN_ON_GRACE_MIN,
            ykws.android.maro.config.AppConfig.powerScreenGraceDefaultMinutes
        ).coerceIn(
            ykws.android.maro.config.AppConfig.powerScreenGraceMinMinutes,
            ykws.android.maro.config.AppConfig.powerScreenGraceMaxMinutes
        ),
        batteryOptimizationPrompted = prefs.getBoolean(KEY_BATTERY_OPT_PROMPTED, false),
        lowDepthWarningVisible = prefs.getBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, true),
        lowDepthCrashDepthM = prefs.getFloat(KEY_LOW_DEPTH_CRASH_DEPTH_M, DepthConstants.LOW_DEPTH_CRASH_DEPTH_M.toFloat()),
        lowDepthStartWarningM = prefs.getFloat(KEY_LOW_DEPTH_START_WARNING_M, DepthConstants.LOW_DEPTH_START_WARNING_M.toFloat()),
        zone300Color = prefs.getInt(KEY_ZONE300_COLOR, ykws.android.maro.config.AppConfig.mapZone300Boundary),
        zone300FillTransparencyPct = prefs.getInt(KEY_ZONE300_FILL_TRANSPARENCY_PCT, 80),
        zone300BoundaryTransparencyPct = prefs.getInt(KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT, 20),
        // The three widths and the coastline transparency are clamped on read: a slider's span is a UI
        // fact, never a guarantee about what a stored value holds.
        zone300BoundaryWidthDp = prefs.getFloat(
            KEY_ZONE300_BOUNDARY_WIDTH_DP,
            ykws.android.maro.config.AppConfig.mapZone300BoundaryWidthDp
        ).coerceIn(WIDTH_MIN_DP, WIDTH_MAX_DP),
        regulatedZoneFillTransparencyPct = prefs.getInt(KEY_REGULATED_ZONE_FILL_TRANSPARENCY_PCT, 80),
        regulatedZoneBoundaryTransparencyPct = prefs.getInt(KEY_REGULATED_ZONE_BOUNDARY_TRANSPARENCY_PCT, 20),
        regulatedZoneOutlineWidthDp = prefs.getFloat(
            KEY_REGULATED_ZONE_OUTLINE_WIDTH_DP,
            ykws.android.maro.config.AppConfig.mapRegulatedZoneOutlineWidthDp
        ).coerceIn(WIDTH_MIN_DP, WIDTH_MAX_DP),
        coastlineWidthDp = prefs.getFloat(
            KEY_COASTLINE_WIDTH_DP,
            ykws.android.maro.config.AppConfig.mapCoastlineWidthDp
        ).coerceIn(WIDTH_MIN_DP, WIDTH_MAX_DP),
        coastlineTransparencyPct = prefs.getInt(
            KEY_COASTLINE_TRANSPARENCY_PCT,
            ykws.android.maro.config.AppConfig.mapCoastlineTransparencyPct
        ).coerceIn(0, 100),
        coastlineMainlandColor = prefs.getInt(
            KEY_COASTLINE_MAINLAND_COLOR,
            ykws.android.maro.config.AppConfig.mapCoastlineMainlandColor
        ),
        coastlineIslandColor = prefs.getInt(
            KEY_COASTLINE_ISLAND_COLOR,
            ykws.android.maro.config.AppConfig.mapCoastlineIslandColor
        ),
        boatMarkerIdleThresholdSec = prefs.getLong(KEY_BOAT_MARKER_IDLE_THRESHOLD_S, ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec),
        boatMarkerAutoMarkerMinDurationSec = prefs.getLong(KEY_BOAT_MARKER_AUTO_MIN_DURATION_S, ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerMinDurationSec),
        boatMarkerAutoMarkerDedupRadiusM = prefs.getFloat(KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M, ykws.android.maro.config.AppConfig.boatMarkerAutoMarkerDedupRadiusM.toFloat()).toDouble(),
        emodnetShallowCutoffM = prefs.getFloat(KEY_EMODNET_SHALLOW_CUTOFF_M, 2.0f),
        // Clamped on read like every other bounded slider: a stored value is never a promise.
        routeFreeWaterPaceKn = prefs.getFloat(
            KEY_ROUTE_FREE_WATER_PACE_KN,
            ykws.android.maro.config.AppConfig.routeFreeWaterPaceKn
        ).let { ykws.android.maro.config.AppConfig.snapFreeWaterPaceKn(it) },
        routeSlowWaterAversion = prefs.getFloat(
            KEY_ROUTE_SLOW_WATER_AVERSION,
            ykws.android.maro.config.AppConfig.routeAvoidSpeedZoneSoftCostAversion.toFloat()
        ).coerceIn(
            ykws.android.maro.config.AppConfig.ROUTE_SLOW_WATER_AVERSION_MIN.toFloat(),
            ykws.android.maro.config.AppConfig.ROUTE_SLOW_WATER_AVERSION_MAX.toFloat()
        ),
        routeSlowWaterBudgetPct = prefs.getInt(
            KEY_ROUTE_SLOW_WATER_BUDGET_PCT,
            ykws.android.maro.config.AppConfig.routeAvoidSpeedZoneTimeBudgetPct
        ).coerceIn(
            ykws.android.maro.config.AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MIN,
            ykws.android.maro.config.AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MAX
        ),
        routeEngineId = prefs.getString(
            KEY_ROUTE_ENGINE_ID,
            ykws.android.maro.config.AppConfig.routeEngineId
        ) ?: ykws.android.maro.config.AppConfig.routeEngineId,
        excludedSpeedZoneIds = prefs.getStringSet(KEY_EXCLUDED_SPEED_ZONE_IDS, emptySet())?.toSet()
            ?: emptySet(),
        routeStartSelectionGps = prefs.getString(KEY_ROUTE_START_SELECTION_GPS, "") ?: "",
        routeStartSelectionDemo = prefs.getString(KEY_ROUTE_START_SELECTION_DEMO, "") ?: "",
        routeDestinationSelectionGps = prefs.getString(KEY_ROUTE_DESTINATION_SELECTION_GPS, "") ?: "",
        routeDestinationSelectionDemo = prefs.getString(KEY_ROUTE_DESTINATION_SELECTION_DEMO, "") ?: "",
        regenGrid    = prefs.getBoolean(KEY_REGEN_GRID, true),
        regenIsobaths = prefs.getBoolean(KEY_REGEN_ISOBATHS, true),
        regenColour  = prefs.getBoolean(KEY_REGEN_COLOUR, true),
        regenWarning = prefs.getBoolean(KEY_REGEN_WARNING, true),
        headingLineVisible = prefs.getBoolean(KEY_HEADING_LINE_VISIBLE, true),
        capArrowVisible   = prefs.getBoolean(KEY_CAP_ARROW_VISIBLE, false),
        // The two widths and the two transparencies are clamped on read, like the px widths above:
        // a slider's span is a UI fact, never a guarantee about what a stored value holds.
        navigationLineWidthDp = prefs.getFloat(
            KEY_NAVIGATION_LINE_WIDTH_DP,
            ykws.android.maro.config.AppConfig.mapNavigationLineWidthDp
        ).coerceIn(NAV_LINE_WIDTH_MIN_DP, NAV_LINE_WIDTH_MAX_DP),
        navigationLineTransparencyPct = prefs.getInt(
            KEY_NAVIGATION_LINE_TRANSPARENCY_PCT,
            ykws.android.maro.config.AppConfig.mapNavigationLineTransparencyPct
        ).coerceIn(0, 100),
        navigationLineColor = prefs.getInt(
            KEY_NAVIGATION_LINE_COLOR,
            ykws.android.maro.config.AppConfig.mapNavigationLineColor
        ),
        navigationArrowWidthDp = prefs.getFloat(
            KEY_NAVIGATION_ARROW_WIDTH_DP,
            ykws.android.maro.config.AppConfig.mapNavigationArrowWidthDp
        ).coerceIn(NAV_ARROW_WIDTH_MIN_DP, NAV_ARROW_WIDTH_MAX_DP),
        navigationArrowTransparencyPct = prefs.getInt(
            KEY_NAVIGATION_ARROW_TRANSPARENCY_PCT,
            ykws.android.maro.config.AppConfig.mapNavigationArrowTransparencyPct
        ).coerceIn(0, 100),
        navigationArrowColor = prefs.getInt(
            KEY_NAVIGATION_ARROW_COLOR,
            ykws.android.maro.config.AppConfig.mapNavigationArrowColor
        ),
        navigationArrowFollowSpeedColour = prefs.getBoolean(
            KEY_NAVIGATION_ARROW_FOLLOW_SPEED_COLOUR,
            ykws.android.maro.config.AppConfig.mapNavigationArrowFollowSpeedColour
        ),
        depthLayerVisible = prefs.getBoolean(KEY_DEPTH_LAYER_VISIBLE, true),
        regulatedZonesVisible = prefs.getBoolean(KEY_REGULATED_ZONES_VISIBLE, BuildConfig.LAYER_REGULATED_ZONES_DEFAULT),
        regulationInfoVisible = prefs.getBoolean(KEY_REGULATION_INFO_VISIBLE, false),
        boatSizeM = prefs.getFloat(KEY_BOAT_SIZE_M, BuildConfig.REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M.toFloat()).toDouble(),
        showCategoryNoAnchor = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_ANCHOR, true),
        showCategoryMooring = prefs.getBoolean(KEY_SHOW_CATEGORY_MOORING, false),
        showCategorySpeedLimit = prefs.getBoolean(KEY_SHOW_CATEGORY_SPEED_LIMIT, true),
        showCategoryNoDiving = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_DIVING, true),
        showCategorySeaplane = prefs.getBoolean(KEY_SHOW_CATEGORY_SEAPLANE, false),
        showCategoryNoAccess = prefs.getBoolean(KEY_SHOW_CATEGORY_NO_ACCESS, true),
        showCategoryFishingProhibited = prefs.getBoolean(KEY_SHOW_CATEGORY_FISHING_PROHIBITED, false),
        showCategoryEnvironmental = prefs.getBoolean(KEY_SHOW_CATEGORY_ENVIRONMENTAL, true),
        showCategoryInformation = prefs.getBoolean(KEY_SHOW_CATEGORY_INFORMATION, false),
        demoHeadingUp = prefs.getBoolean(KEY_DEMO_HEADING_UP, false),
        gpsIdleMinDistanceM = prefs.getFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, 0f),
        trackEnabled = prefs.getBoolean(KEY_TRACK_ENABLED, BuildConfig.TRACK_ENABLED_DEFAULT),
        trackOriginLat = prefs.getFloat(KEY_TRACK_ORIGIN_LAT, BuildConfig.TRACK_ORIGIN_LAT.toFloat()).toDouble(),
        trackOriginLon = prefs.getFloat(KEY_TRACK_ORIGIN_LON, BuildConfig.TRACK_ORIGIN_LON.toFloat()).toDouble(),
        trackGeofenceRadiusM = prefs.getFloat(KEY_TRACK_GEOFENCE_RADIUS_M, BuildConfig.TRACK_GEOFENCE_RADIUS_M.toFloat()).toDouble(),
        trackGeofenceEnabled = prefs.getBoolean(KEY_TRACK_GEOFENCE_ENABLED, true),
        markerLayerState = try {
            ykws.android.maro.ui.map.MarkerLayerState.valueOf(
                prefs.getString(KEY_MARKER_LAYER_STATE, "SHOW_ALL") ?: "SHOW_ALL")
        } catch (_: Exception) { ykws.android.maro.ui.map.MarkerLayerState.SHOW_ALL },
        markerZonesVisible = prefs.getBoolean(KEY_MARKER_ZONES_VISIBLE, true),
        markerHaloSize = prefs.getInt(KEY_MARKER_HALO_SIZE, 50),
        markerPointIconZoom = prefs.getInt(KEY_MARKER_POINT_ICON_ZOOM, 100),
        markerHaloPinnedColor = prefs.getInt(KEY_MARKER_HALO_PINNED_COLOR, 0xFFFFFFFF.toInt()),
        markerHaloUnpinnedColor = prefs.getInt(KEY_MARKER_HALO_UNPINNED_COLOR, 0xFF81D4FA.toInt()),
        markerHaloPinnedFillTransparencyPct = prefs.getInt(KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT, 75),
        markerHaloPinnedBorderTransparencyPct = prefs.getInt(KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT, 20),
        markerHaloUnpinnedFillTransparencyPct = prefs.getInt(KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT, 90),
        markerHaloUnpinnedBorderTransparencyPct = prefs.getInt(KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT, 60),
        routeTracksVisible = prefs.getBoolean(KEY_ROUTE_TRACKS_VISIBLE, true),
        tracksVisible = prefs.getBoolean(KEY_TRACKS_VISIBLE, true),
        routesVisible = prefs.getBoolean(KEY_ROUTES_VISIBLE, true),
        // Absent means expanded: today's behaviour is the fallback, so no install has anything to migrate.
        trackLegendExpanded = prefs.getBoolean(KEY_TRACK_LEGEND_EXPANDED, true),
        // The two render axes: the arrows' argument runs the retired-value migration and the colours'
        // then reads the flag that migration wrote, so the pair can never be read half-migrated. Each
        // axis's fallback is its own kind's `path.*` seed (2026-10-07), so a fresh install opens on what
        // `maro.properties` declares without any of the four having to migrate.
        trackArrows = prefs.getBoolean(
            KEY_TRACK_ARROWS,
            migrateRenderAxes()?.let { it != "SIMPLE" } ?: ykws.android.maro.config.AppConfig.trackArrowEnabledSeed
        ),
        trackColours = prefs.getBoolean(KEY_TRACK_COLOURS, ykws.android.maro.config.AppConfig.trackHeatmapEnabledSeed),
        routeSpeedColor = prefs.getBoolean(KEY_ROUTE_SPEED_COLOR, ykws.android.maro.config.AppConfig.routeHeatmapEnabledSeed),
        routeSpeedArrows = prefs.getBoolean(KEY_ROUTE_SPEED_ARROWS, ykws.android.maro.config.AppConfig.routeArrowEnabledSeed),
        trackDirectionDensity = try {
            ykws.android.maro.ui.map.TrackDirectionDensity.valueOf(
                prefs.getString(KEY_TRACK_DIRECTION_DENSITY, "UNIFORM") ?: "UNIFORM")
        } catch (_: Exception) { ykws.android.maro.ui.map.TrackDirectionDensity.UNIFORM },
        trackDirectionSpeedFloorKn = prefs.getFloat(KEY_TRACK_DIRECTION_SPEED_FLOOR_KN, ykws.android.maro.config.AppConfig.trackDirectionSpeedFloorKn),
        trackDirectionSpeedCeilingKn = prefs.getFloat(KEY_TRACK_DIRECTION_SPEED_CEILING_KN, ykws.android.maro.config.AppConfig.trackDirectionSpeedCeilingKn),
        trackDirectionMinSpacingDp = prefs.getInt(KEY_TRACK_DIRECTION_MIN_SPACING_DP, ykws.android.maro.config.AppConfig.trackDirectionMinSpacingDp),
        trackDirectionMaxSpacingDp = prefs.getInt(KEY_TRACK_DIRECTION_MAX_SPACING_DP, ykws.android.maro.config.AppConfig.trackDirectionMaxSpacingDp),
        trackingRenderNb = prefs.getInt(KEY_TRACKING_RENDER_NB, ykws.android.maro.config.AppConfig.pathCount).coerceIn(0, 20),
        routeRenderNb = prefs.getInt(KEY_TRACKING_ROUTE_RENDER_NB, ykws.android.maro.config.AppConfig.pathCount).coerceIn(0, 20),
        trackingColorActive = prefs.getInt(KEY_TRACKING_COLOR_ACTIVE, ykws.android.maro.config.AppConfig.trackColourActive),
        trackingColorPastFrom = prefs.getInt(KEY_TRACKING_COLOR_PAST_FROM, ykws.android.maro.config.AppConfig.trackColourFrom),
        trackingColorPastTo = prefs.getInt(KEY_TRACKING_COLOR_PAST_TO, ykws.android.maro.config.AppConfig.trackColourTo),
        trackingColorRouteFrom = prefs.getInt(KEY_TRACKING_COLOR_ROUTE_FROM, ykws.android.maro.config.AppConfig.routeStoredColourFrom),
        trackingColorRouteTo = prefs.getInt(KEY_TRACKING_COLOR_ROUTE_TO, ykws.android.maro.config.AppConfig.routeStoredColourTo),
        // The followed line's colour: seeded by the same `maro.properties` key its readers once read
        // directly, so an install that never touched the row keeps painting exactly what it did.
        routeLineColor = prefs.getInt(
            KEY_ROUTE_LINE_COLOR, ykws.android.maro.config.AppConfig.routeLineColor
        ),
        trackingTransparencyNewest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_NEWEST, ykws.android.maro.config.AppConfig.trackFadeFrom),
        trackingTransparencyOldest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_OLDEST, ykws.android.maro.config.AppConfig.trackFadeTo),
        trackingTransparencyPinnedNewest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST, ykws.android.maro.config.AppConfig.trackFadePinnedFrom),
        trackingTransparencyPinnedOldest = prefs.getInt(KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST, ykws.android.maro.config.AppConfig.trackFadePinnedTo),
        trackingColorPinnedFrom = prefs.getInt(KEY_TRACKING_COLOR_PINNED_FROM, ykws.android.maro.config.AppConfig.trackColourPinnedFrom),
        trackingColorPinnedTo = prefs.getInt(KEY_TRACKING_COLOR_PINNED_TO, ykws.android.maro.config.AppConfig.trackColourPinnedTo),
        // The route ladder is clamped on read like every other bounded pair: a stored value is never
        // a promise about what the slider's span allows.
        trackingTransparencyRouteNewest = prefs.getInt(
            KEY_TRACKING_TRANSPARENCY_ROUTE_NEWEST, ykws.android.maro.config.AppConfig.routeStoredFadeFrom
        ).coerceIn(0, 100),
        trackingTransparencyRouteOldest = prefs.getInt(
            KEY_TRACKING_TRANSPARENCY_ROUTE_OLDEST, ykws.android.maro.config.AppConfig.routeStoredFadeTo
        ).coerceIn(0, 100),
        trackingTransparencyPinnedRouteNewest = prefs.getInt(
            KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_NEWEST, ykws.android.maro.config.AppConfig.routePinnedFadeFrom
        ).coerceIn(0, 100),
        trackingTransparencyPinnedRouteOldest = prefs.getInt(
            KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_OLDEST, ykws.android.maro.config.AppConfig.routePinnedFadeTo
        ).coerceIn(0, 100),
        trackingColorPinnedRouteFrom = prefs.getInt(
            KEY_TRACKING_COLOR_PINNED_ROUTE_FROM, ykws.android.maro.config.AppConfig.routePinnedColourFrom
        ),
        trackingColorPinnedRouteTo = prefs.getInt(
            KEY_TRACKING_COLOR_PINNED_ROUTE_TO, ykws.android.maro.config.AppConfig.routePinnedColourTo
        ),
        trackSimplifyEnabled = prefs.getBoolean(KEY_TRACK_SIMPLIFY_ENABLED, true),
        trackSimplifyEpsilonM = prefs.getFloat(KEY_TRACK_SIMPLIFY_EPSILON_M, 3.0f).toDouble(),
        trackSimplifySpeedDeltaKn = prefs.getFloat(KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN, 3.0f).toDouble(),
        markerDebugRays = prefs.getBoolean(KEY_MARKER_DEBUG_RAYS, false),
        trackListSort = ykws.android.maro.data.model.ListSortState.parse(prefs.getString(KEY_TRACK_LIST_SORT, null)),
        markerListSort = ykws.android.maro.data.model.ListSortState.parse(prefs.getString(KEY_MARKER_LIST_SORT, null)),
        // The retired Kind axis is stripped on read: an install that persisted `route=TRACKS|ROUTES`
        // must not keep filtering on an axis the code no longer reads.
        trackListFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_TRACK_LIST_FILTER, null)).withoutAxis("route"),
        markerListFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_MARKER_LIST_FILTER, null)),
        trackMapFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_TRACK_MAP_FILTER, null)).withoutAxis("route"),
        markerMapFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_MARKER_MAP_FILTER, null)),
        trackFilterLinked = prefs.getBoolean(KEY_TRACK_FILTER_LINKED, true),
        markerFilterLinked = prefs.getBoolean(KEY_MARKER_FILTER_LINKED, true),
        routeListSort = ykws.android.maro.data.model.ListSortState.parse(prefs.getString(KEY_ROUTE_LIST_SORT, null)),
        routeListFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_ROUTE_LIST_FILTER, null)),
        routeMapFilter = ykws.android.maro.data.model.ListFilter.parse(prefs.getString(KEY_ROUTE_MAP_FILTER, null)),
        routeFilterLinked = prefs.getBoolean(KEY_ROUTE_FILTER_LINKED, true),
        mapOffsetGps = prefs.getBoolean(KEY_MAP_OFFSET_GPS, true),
        mapOffsetDemo = prefs.getBoolean(KEY_MAP_OFFSET_DEMO, false),
        mapOffsetBoatFromBottomPct = prefs.getInt(KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT, 33).coerceIn(5, 50),
        maxRecordingAccuracyM = prefs.getFloat(KEY_MAX_RECORDING_ACCURACY_M, 30f),
    )

    /**
     * Atomically update one or more settings fields and persist to disk.
     *
     * Usage: `settingsManager.update { it.copy(coastlineVisible = false) }`
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        val current = _settings.value
        val updated = transform(current)

        // Skip disk write + emission when nothing actually changed
        if (updated == current) return

        _settings.value = updated
        prefs.edit()
            .putFloat(KEY_DEFAULT_LAT, updated.defaultLatitude.toFloat())
            .putFloat(KEY_DEFAULT_LON, updated.defaultLongitude.toFloat())
            .putBoolean(KEY_COASTLINE_VISIBLE, updated.coastlineVisible)
            .putBoolean(KEY_SHOW_LAND_WATER_ICON, updated.showLandWaterIcon)
            .putBoolean(KEY_ZONE300_VISIBLE, updated.zone300Visible)
            .putFloat(KEY_ZONE_AUTOREVEAL_DIST_M, updated.zoneAutoRevealDistanceM)
            .putInt(KEY_ZONE_AUTOREVEAL_TIME_S, updated.zoneAutoRevealTimeS)
            .putBoolean(KEY_APPROACH_AUTOSHOW_GPS, updated.approachAutoShowGps)
            .putBoolean(KEY_APPROACH_AUTOSHOW_DEMO, updated.approachAutoShowDemo)
            .putBoolean(KEY_ZONE300_AUTOSHOW, updated.zone300AutoShow)
            .putBoolean(KEY_SPEED_ZONE_AUTOSHOW, updated.speedZoneAutoShow)
            .putBoolean(KEY_REGULATED_ZONE_AUTOSHOW, updated.regulatedZoneAutoShow)
            .putBoolean(KEY_GPS_MODE, updated.gpsMode)
            .putInt(KEY_RECENTER_DELAY_S, updated.recenterDelaySeconds)
            .putInt(KEY_GPS_INTERVAL_S, updated.gpsActiveIntervalSec)
            .putFloat(KEY_GPS_MIN_DISTANCE_M, updated.gpsActiveMinDistanceM)
            .putBoolean(KEY_STOP_DETECTION_ENABLED, updated.stopDetectionEnabled)
            .putInt(KEY_STOP_DETECTION_TIME_S, updated.stopDetectionTimeSec)
            .putInt(KEY_STOP_DETECTION_DISTANCE_M, updated.stopDetectionDistanceM)
            .putBoolean(KEY_STOP_DETECTION_DELAY_GPS, updated.stopDetectionDelayGps)
            .putInt(KEY_MAP_REFRESH_FPS, updated.mapRefreshFps)
            .putFloat(KEY_MAP_CENTER_LAT, updated.mapCenterLat.toFloat())
            .putFloat(KEY_MAP_CENTER_LON, updated.mapCenterLon.toFloat())
            .putFloat(KEY_ZOOM_LEVEL, updated.zoomLevel.toFloat())
            .putBoolean(KEY_IS_WATER, updated.isWater)
            .putFloat(KEY_DISTANCE_TO_SHORE, updated.distanceToShore.toFloat())
            .putString(KEY_LANGUAGE_CODE, updated.languageCode)
            .putBoolean(KEY_KEEP_SCREEN_ON, updated.keepScreenOn)
            .putBoolean(KEY_KEEP_SCREEN_ON_MOVEMENT_GATE, updated.keepScreenOnMovementGate)
            .putFloat(KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN, updated.keepScreenOnSpeedThresholdKn)
            .putInt(KEY_KEEP_SCREEN_ON_GRACE_MIN, updated.keepScreenOnGraceMinutes)
            .putBoolean(KEY_BATTERY_OPT_PROMPTED, updated.batteryOptimizationPrompted)
            .putBoolean(KEY_LOW_DEPTH_WARNING_VISIBLE, updated.lowDepthWarningVisible)
            .putFloat(KEY_LOW_DEPTH_CRASH_DEPTH_M, updated.lowDepthCrashDepthM)
            .putFloat(KEY_LOW_DEPTH_START_WARNING_M, updated.lowDepthStartWarningM)
            .putFloat(KEY_ROUTE_FREE_WATER_PACE_KN, updated.routeFreeWaterPaceKn)
            .putFloat(KEY_ROUTE_SLOW_WATER_AVERSION, updated.routeSlowWaterAversion)
            .putInt(KEY_ROUTE_SLOW_WATER_BUDGET_PCT, updated.routeSlowWaterBudgetPct)
            .putString(KEY_ROUTE_ENGINE_ID, updated.routeEngineId)
            .putStringSet(KEY_EXCLUDED_SPEED_ZONE_IDS, updated.excludedSpeedZoneIds)
            .putString(KEY_ROUTE_START_SELECTION_GPS, updated.routeStartSelectionGps)
            .putString(KEY_ROUTE_START_SELECTION_DEMO, updated.routeStartSelectionDemo)
            .putString(KEY_ROUTE_DESTINATION_SELECTION_GPS, updated.routeDestinationSelectionGps)
            .putString(KEY_ROUTE_DESTINATION_SELECTION_DEMO, updated.routeDestinationSelectionDemo)
            .putInt(KEY_ZONE300_COLOR, updated.zone300Color)
            .putInt(KEY_ZONE300_FILL_TRANSPARENCY_PCT, updated.zone300FillTransparencyPct)
            .putInt(KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT, updated.zone300BoundaryTransparencyPct)
            .putFloat(KEY_ZONE300_BOUNDARY_WIDTH_DP, updated.zone300BoundaryWidthDp)
            .putInt(KEY_REGULATED_ZONE_FILL_TRANSPARENCY_PCT, updated.regulatedZoneFillTransparencyPct)
            .putInt(KEY_REGULATED_ZONE_BOUNDARY_TRANSPARENCY_PCT, updated.regulatedZoneBoundaryTransparencyPct)
            .putFloat(KEY_REGULATED_ZONE_OUTLINE_WIDTH_DP, updated.regulatedZoneOutlineWidthDp)
            .putFloat(KEY_COASTLINE_WIDTH_DP, updated.coastlineWidthDp)
            .putInt(KEY_COASTLINE_TRANSPARENCY_PCT, updated.coastlineTransparencyPct)
            .putInt(KEY_COASTLINE_MAINLAND_COLOR, updated.coastlineMainlandColor)
            .putInt(KEY_COASTLINE_ISLAND_COLOR, updated.coastlineIslandColor)
            .putLong(KEY_BOAT_MARKER_IDLE_THRESHOLD_S, updated.boatMarkerIdleThresholdSec)
            .putLong(KEY_BOAT_MARKER_AUTO_MIN_DURATION_S, updated.boatMarkerAutoMarkerMinDurationSec)
            .putFloat(KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M, updated.boatMarkerAutoMarkerDedupRadiusM.toFloat())
            .putFloat(KEY_EMODNET_SHALLOW_CUTOFF_M, updated.emodnetShallowCutoffM)
            .putBoolean(KEY_REGEN_GRID, updated.regenGrid)
            .putBoolean(KEY_REGEN_ISOBATHS, updated.regenIsobaths)
            .putBoolean(KEY_REGEN_COLOUR, updated.regenColour)
            .putBoolean(KEY_REGEN_WARNING, updated.regenWarning)
            .putBoolean(KEY_HEADING_LINE_VISIBLE, updated.headingLineVisible)
            .putBoolean(KEY_CAP_ARROW_VISIBLE, updated.capArrowVisible)
            .putFloat(KEY_NAVIGATION_LINE_WIDTH_DP, updated.navigationLineWidthDp)
            .putInt(KEY_NAVIGATION_LINE_TRANSPARENCY_PCT, updated.navigationLineTransparencyPct)
            .putInt(KEY_NAVIGATION_LINE_COLOR, updated.navigationLineColor)
            .putFloat(KEY_NAVIGATION_ARROW_WIDTH_DP, updated.navigationArrowWidthDp)
            .putInt(KEY_NAVIGATION_ARROW_TRANSPARENCY_PCT, updated.navigationArrowTransparencyPct)
            .putInt(KEY_NAVIGATION_ARROW_COLOR, updated.navigationArrowColor)
            .putBoolean(KEY_NAVIGATION_ARROW_FOLLOW_SPEED_COLOUR, updated.navigationArrowFollowSpeedColour)
            .putBoolean(KEY_DEPTH_LAYER_VISIBLE, updated.depthLayerVisible)
            .putBoolean(KEY_REGULATED_ZONES_VISIBLE, updated.regulatedZonesVisible)
            .putFloat(KEY_BOAT_SIZE_M, updated.boatSizeM.toFloat())
            .putBoolean(KEY_SHOW_CATEGORY_NO_ANCHOR, updated.showCategoryNoAnchor)
            .putBoolean(KEY_SHOW_CATEGORY_MOORING, updated.showCategoryMooring)
            .putBoolean(KEY_SHOW_CATEGORY_SPEED_LIMIT, updated.showCategorySpeedLimit)
            .putBoolean(KEY_SHOW_CATEGORY_NO_DIVING, updated.showCategoryNoDiving)
            .putBoolean(KEY_SHOW_CATEGORY_SEAPLANE, updated.showCategorySeaplane)
            .putBoolean(KEY_SHOW_CATEGORY_NO_ACCESS, updated.showCategoryNoAccess)
            .putBoolean(KEY_SHOW_CATEGORY_FISHING_PROHIBITED, updated.showCategoryFishingProhibited)
            .putBoolean(KEY_SHOW_CATEGORY_ENVIRONMENTAL, updated.showCategoryEnvironmental)
            .putBoolean(KEY_SHOW_CATEGORY_INFORMATION, updated.showCategoryInformation)
            .putBoolean(KEY_REGULATION_INFO_VISIBLE, updated.regulationInfoVisible)
            .putBoolean(KEY_DEMO_HEADING_UP, updated.demoHeadingUp)
            .putFloat(KEY_GPS_IDLE_MIN_DISTANCE_M, updated.gpsIdleMinDistanceM)
            .putBoolean(KEY_TRACK_ENABLED, updated.trackEnabled)
            .putFloat(KEY_TRACK_ORIGIN_LAT, updated.trackOriginLat.toFloat())
            .putFloat(KEY_TRACK_ORIGIN_LON, updated.trackOriginLon.toFloat())
            .putFloat(KEY_TRACK_GEOFENCE_RADIUS_M, updated.trackGeofenceRadiusM.toFloat())
            .putBoolean(KEY_TRACK_GEOFENCE_ENABLED, updated.trackGeofenceEnabled)
            .putString(KEY_MARKER_LAYER_STATE, updated.markerLayerState.name)
            .putBoolean(KEY_MARKER_ZONES_VISIBLE, updated.markerZonesVisible)
            .putInt(KEY_MARKER_HALO_SIZE, updated.markerHaloSize)
            .putInt(KEY_MARKER_POINT_ICON_ZOOM, updated.markerPointIconZoom)
            .putInt(KEY_MARKER_HALO_PINNED_COLOR, updated.markerHaloPinnedColor)
            .putInt(KEY_MARKER_HALO_UNPINNED_COLOR, updated.markerHaloUnpinnedColor)
            .putInt(KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT, updated.markerHaloPinnedFillTransparencyPct)
            .putInt(KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT, updated.markerHaloPinnedBorderTransparencyPct)
            .putInt(KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT, updated.markerHaloUnpinnedFillTransparencyPct)
            .putInt(KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT, updated.markerHaloUnpinnedBorderTransparencyPct)
            .putBoolean(KEY_ROUTE_TRACKS_VISIBLE, updated.routeTracksVisible)
            .putBoolean(KEY_TRACKS_VISIBLE, updated.tracksVisible)
            .putBoolean(KEY_ROUTES_VISIBLE, updated.routesVisible)
            .putBoolean(KEY_TRACK_LEGEND_EXPANDED, updated.trackLegendExpanded)
            .putBoolean(KEY_TRACK_ARROWS, updated.trackArrows)
            .putBoolean(KEY_TRACK_COLOURS, updated.trackColours)
            .putString(KEY_TRACK_DIRECTION_DENSITY, updated.trackDirectionDensity.name)
            .putFloat(KEY_TRACK_DIRECTION_SPEED_FLOOR_KN, updated.trackDirectionSpeedFloorKn)
            .putFloat(KEY_TRACK_DIRECTION_SPEED_CEILING_KN, updated.trackDirectionSpeedCeilingKn)
            .putInt(KEY_TRACK_DIRECTION_MIN_SPACING_DP, updated.trackDirectionMinSpacingDp)
            .putInt(KEY_TRACK_DIRECTION_MAX_SPACING_DP, updated.trackDirectionMaxSpacingDp)
            .putBoolean(KEY_ROUTE_SPEED_COLOR, updated.routeSpeedColor)
            .putBoolean(KEY_ROUTE_SPEED_ARROWS, updated.routeSpeedArrows)
            .putInt(KEY_TRACKING_RENDER_NB, updated.trackingRenderNb)
            .putInt(KEY_TRACKING_ROUTE_RENDER_NB, updated.routeRenderNb)
            .putInt(KEY_TRACKING_COLOR_ACTIVE, updated.trackingColorActive)
            .putInt(KEY_TRACKING_COLOR_PAST_FROM, updated.trackingColorPastFrom)
            .putInt(KEY_TRACKING_COLOR_PAST_TO, updated.trackingColorPastTo)
            .putInt(KEY_TRACKING_COLOR_ROUTE_FROM, updated.trackingColorRouteFrom)
            .putInt(KEY_TRACKING_COLOR_ROUTE_TO, updated.trackingColorRouteTo)
            .putInt(KEY_ROUTE_LINE_COLOR, updated.routeLineColor)
            .putInt(KEY_TRACKING_TRANSPARENCY_NEWEST, updated.trackingTransparencyNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_OLDEST, updated.trackingTransparencyOldest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST, updated.trackingTransparencyPinnedNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST, updated.trackingTransparencyPinnedOldest)
            .putInt(KEY_TRACKING_COLOR_PINNED_FROM, updated.trackingColorPinnedFrom)
            .putInt(KEY_TRACKING_COLOR_PINNED_TO, updated.trackingColorPinnedTo)
            .putInt(KEY_TRACKING_TRANSPARENCY_ROUTE_NEWEST, updated.trackingTransparencyRouteNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_ROUTE_OLDEST, updated.trackingTransparencyRouteOldest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_NEWEST, updated.trackingTransparencyPinnedRouteNewest)
            .putInt(KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_OLDEST, updated.trackingTransparencyPinnedRouteOldest)
            .putInt(KEY_TRACKING_COLOR_PINNED_ROUTE_FROM, updated.trackingColorPinnedRouteFrom)
            .putInt(KEY_TRACKING_COLOR_PINNED_ROUTE_TO, updated.trackingColorPinnedRouteTo)
            .putBoolean(KEY_TRACK_SIMPLIFY_ENABLED, updated.trackSimplifyEnabled)
            .putFloat(KEY_TRACK_SIMPLIFY_EPSILON_M, updated.trackSimplifyEpsilonM.toFloat())
            .putFloat(KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN, updated.trackSimplifySpeedDeltaKn.toFloat())
            .putBoolean(KEY_MARKER_DEBUG_RAYS, updated.markerDebugRays)
            .putString(KEY_TRACK_LIST_SORT, ykws.android.maro.data.model.ListSortState.format(updated.trackListSort))
            .putString(KEY_MARKER_LIST_SORT, ykws.android.maro.data.model.ListSortState.format(updated.markerListSort))
            .putString(KEY_TRACK_LIST_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.trackListFilter))
            .putString(KEY_MARKER_LIST_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.markerListFilter))
            .putString(KEY_TRACK_MAP_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.trackMapFilter))
            .putString(KEY_MARKER_MAP_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.markerMapFilter))
            .putBoolean(KEY_TRACK_FILTER_LINKED, updated.trackFilterLinked)
            .putBoolean(KEY_MARKER_FILTER_LINKED, updated.markerFilterLinked)
            .putString(KEY_ROUTE_LIST_SORT, ykws.android.maro.data.model.ListSortState.format(updated.routeListSort))
            .putString(KEY_ROUTE_LIST_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.routeListFilter))
            .putString(KEY_ROUTE_MAP_FILTER, ykws.android.maro.data.model.ListFilter.format(updated.routeMapFilter))
            .putBoolean(KEY_ROUTE_FILTER_LINKED, updated.routeFilterLinked)
            .putBoolean(KEY_MAP_OFFSET_GPS, updated.mapOffsetGps)
            .putBoolean(KEY_MAP_OFFSET_DEMO, updated.mapOffsetDemo)
            .putInt(KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT, updated.mapOffsetBoatFromBottomPct)
            .putFloat(KEY_MAX_RECORDING_ACCURACY_M, updated.maxRecordingAccuracyM)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "maro_settings"
        private const val KEY_DEFAULT_LAT = "default_lat"
        private const val KEY_DEFAULT_LON = "default_lon"
        private const val KEY_COASTLINE_VISIBLE = "coastline_visible"
        private const val KEY_SHOW_LAND_WATER_ICON = "show_land_water_icon"
        private const val KEY_ZONE300_VISIBLE = "zone300_visible"
        private const val KEY_ZONE_AUTOREVEAL_DIST_M = "zone_autoreveal_dist_m"
        private const val KEY_ZONE_AUTOREVEAL_TIME_S = "zone_autoreveal_time_s"
        private const val KEY_APPROACH_AUTOSHOW_GPS = "approach_autoshow_gps"
        private const val KEY_APPROACH_AUTOSHOW_DEMO = "approach_autoshow_demo"
        private const val KEY_ZONE300_AUTOSHOW = "zone300_autoshow"
        private const val KEY_SPEED_ZONE_AUTOSHOW = "speed_zone_autoshow"
        private const val KEY_REGULATED_ZONE_AUTOSHOW = "regulated_zone_autoshow"
        private const val KEY_GPS_MODE = "gps_mode"
        private const val KEY_RECENTER_DELAY_S = "recenter_delay_s"
        private const val KEY_GPS_INTERVAL_S = "gps_interval_s"
        private const val KEY_GPS_MIN_DISTANCE_M = "gps_min_distance_m"
        private const val KEY_STOP_DETECTION_ENABLED = "stop_detection_enabled"
        private const val KEY_STOP_DETECTION_TIME_S = "stop_detection_time_s"
        private const val KEY_STOP_DETECTION_DISTANCE_M = "stop_detection_distance_m"
        private const val KEY_STOP_DETECTION_DELAY_GPS = "stop_detection_delay_gps"
        private const val KEY_MAP_REFRESH_FPS = "map_refresh_fps"
        private const val KEY_MAP_CENTER_LAT = "map_center_lat"
        private const val KEY_MAP_CENTER_LON = "map_center_lon"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_IS_WATER = "is_water"
        private const val KEY_DISTANCE_TO_SHORE = "distance_to_shore"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_KEEP_SCREEN_ON_MOVEMENT_GATE = "keep_screen_on_movement_gate"
        private const val KEY_KEEP_SCREEN_ON_SPEED_THRESHOLD_KN = "keep_screen_on_speed_threshold_kn"
        private const val KEY_KEEP_SCREEN_ON_GRACE_MIN = "keep_screen_on_grace_min"
        private const val KEY_BATTERY_OPT_PROMPTED = "battery_optimization_prompted"
        /** Legacy store the prompt flag used to live in, plus its key — read once by the migration. */
        private const val LEGACY_BATTERY_PREFS = "maro_battery_prefs"
        private const val LEGACY_BATTERY_OPT_PROMPTED = "battery_opt_prompted"
        private const val KEY_LOW_DEPTH_WARNING_VISIBLE = "low_depth_warning_visible"
        private const val KEY_LOW_DEPTH_CRASH_DEPTH_M = "low_depth_crash_depth_m"
        private const val KEY_LOW_DEPTH_START_WARNING_M = "low_depth_start_warning_m"
        private const val KEY_ZONE300_COLOR = "zone300_fill_color"
        private const val KEY_ZONE300_FILL_TRANSPARENCY_PCT = "zone300_fill_transparency_pct"
        private const val KEY_ZONE300_BOUNDARY_TRANSPARENCY_PCT = "zone300_boundary_transparency_pct"
        private const val KEY_ZONE300_BOUNDARY_WIDTH_DP = "zone300_boundary_width_dp"
        private const val KEY_REGULATED_ZONE_FILL_TRANSPARENCY_PCT = "regulated_zone_fill_transparency_pct"
        private const val KEY_REGULATED_ZONE_BOUNDARY_TRANSPARENCY_PCT = "regulated_zone_boundary_transparency_pct"
        private const val KEY_REGULATED_ZONE_OUTLINE_WIDTH_DP = "regulated_zone_outline_width_dp"
        private const val KEY_COASTLINE_WIDTH_DP = "coastline_width_dp"
        private const val KEY_COASTLINE_TRANSPARENCY_PCT = "coastline_transparency_pct"
        private const val KEY_COASTLINE_MAINLAND_COLOR = "coastline_mainland_color"
        private const val KEY_COASTLINE_ISLAND_COLOR = "coastline_island_color"
        private const val KEY_NAVIGATION_LINE_WIDTH_DP = "navigation_line_width_dp"
        private const val KEY_NAVIGATION_LINE_TRANSPARENCY_PCT = "navigation_line_transparency_pct"
        private const val KEY_NAVIGATION_LINE_COLOR = "navigation_line_color"
        private const val KEY_NAVIGATION_ARROW_WIDTH_DP = "navigation_arrow_width_dp"
        private const val KEY_NAVIGATION_ARROW_TRANSPARENCY_PCT = "navigation_arrow_transparency_pct"
        private const val KEY_NAVIGATION_ARROW_COLOR = "navigation_arrow_color"
        private const val KEY_NAVIGATION_ARROW_FOLLOW_SPEED_COLOUR = "navigation_arrow_follow_speed_colour"

        /**
         * Width slider span (dp) — the settled dp grid every stored width is clamped into on read:
         * 0.5 to 8 dp covers the 1–20 px the rows used to offer, at the step the rows now snap to.
         */
        private const val WIDTH_MIN_DP = 0.5f
        private const val WIDTH_MAX_DP = 8f

        /** Heading line and cap arrow width spans (dp), the settled 0.5–4 and 1–8 ranges, applied on read. */
        private const val NAV_LINE_WIDTH_MIN_DP = 0.5f
        private const val NAV_LINE_WIDTH_MAX_DP = 4f
        private const val NAV_ARROW_WIDTH_MIN_DP = 1f
        private const val NAV_ARROW_WIDTH_MAX_DP = 8f
        private const val KEY_BOAT_MARKER_IDLE_THRESHOLD_S = "boat_marker_idle_threshold_s"
        private const val KEY_BOAT_MARKER_AUTO_MIN_DURATION_S = "boat_marker_auto_min_duration_s"
        private const val KEY_BOAT_MARKER_AUTO_DEDUP_RADIUS_M = "boat_marker_auto_dedup_radius_m"
        private const val KEY_EMODNET_SHALLOW_CUTOFF_M = "emodnet_shallow_cutoff_m"
        private const val KEY_ROUTE_FREE_WATER_PACE_KN = "route_free_water_pace_kn"
        /** The persisted aversion λ (0–5), seeded from `route.avoid.speedZone.softCostAversion`. */
        private const val KEY_ROUTE_SLOW_WATER_AVERSION = "route_slow_water_aversion"
        /** The persisted slow-water budget (per cent), seeded from `route.avoid.speedZone.timeBudgetPct`. */
        private const val KEY_ROUTE_SLOW_WATER_BUDGET_PCT = "route_slow_water_budget_pct"
        /** The persisted route algorithm id — the user's choice, seeded from `route.engine.id`. */
        private const val KEY_ROUTE_ENGINE_ID = "route_engine_id"
        /** The persisted set of speed-zone ids the user excluded from route planning. */
        private const val KEY_EXCLUDED_SPEED_ZONE_IDS = "excluded_speed_zone_ids"
        /** The persisted start the drawer arms on in GPS mode — a `RouteEndSelection` token. */
        private const val KEY_ROUTE_START_SELECTION_GPS = "route_start_selection_gps"
        /** The same for demo mode. */
        private const val KEY_ROUTE_START_SELECTION_DEMO = "route_start_selection_demo"
        /** The persisted destination the drawer arms on in GPS mode. */
        private const val KEY_ROUTE_DESTINATION_SELECTION_GPS = "route_destination_selection_gps"
        /** The same for demo mode. */
        private const val KEY_ROUTE_DESTINATION_SELECTION_DEMO = "route_destination_selection_demo"
        private const val KEY_REGEN_GRID = "regen_grid"
        private const val KEY_REGEN_ISOBATHS = "regen_isobaths"
        private const val KEY_REGEN_COLOUR = "regen_colour"
        private const val KEY_REGEN_WARNING = "regen_warning"
        private const val KEY_HEADING_LINE_VISIBLE = "heading_line_visible"
        private const val KEY_CAP_ARROW_VISIBLE = "cap_arrow_visible"
        private const val KEY_DEPTH_LAYER_VISIBLE = "depth_layer_visible"
        private const val KEY_REGULATED_ZONES_VISIBLE = "regulated_zones_visible"
        private const val KEY_BOAT_SIZE_M = "boat_size_m"
        private const val KEY_SHOW_CATEGORY_NO_ANCHOR = "show_category_no_anchor"
        private const val KEY_SHOW_CATEGORY_MOORING = "show_category_mooring"
        private const val KEY_SHOW_CATEGORY_SPEED_LIMIT = "show_category_speed_limit"
        private const val KEY_SHOW_CATEGORY_NO_DIVING = "show_category_no_diving"
        private const val KEY_SHOW_CATEGORY_SEAPLANE = "show_category_seaplane"
        private const val KEY_SHOW_CATEGORY_NO_ACCESS = "show_category_no_access"
        private const val KEY_SHOW_CATEGORY_FISHING_PROHIBITED = "show_category_fishing_prohibited"
        private const val KEY_SHOW_CATEGORY_ENVIRONMENTAL = "show_category_environmental"
        private const val KEY_SHOW_CATEGORY_INFORMATION = "show_category_information"
        private const val KEY_REGULATION_INFO_VISIBLE = "regulation_info_visible"
        private const val KEY_DEMO_HEADING_UP = "demo_heading_up"
        private const val KEY_GPS_IDLE_MIN_DISTANCE_M = "gps_idle_min_distance_m"
        private const val KEY_TRACK_ENABLED = "track_enabled"
        private const val KEY_TRACK_ORIGIN_LAT = "track_origin_lat"
        private const val KEY_TRACK_ORIGIN_LON = "track_origin_lon"
        private const val KEY_TRACK_GEOFENCE_RADIUS_M = "track_geofence_radius_m"
        private const val KEY_TRACK_GEOFENCE_ENABLED = "track_geofence_enabled"
        /** The layer fan's master gate for the route-and-tracks family (see [AppSettings.routeTracksVisible]). */
        private const val KEY_ROUTE_TRACKS_VISIBLE = "route_tracks_visible"
        private const val KEY_TRACKS_VISIBLE = "tracks_visible"
        /** The routes' own map visibility, the routes header's eye (see [AppSettings.routesVisible]). */
        private const val KEY_ROUTES_VISIBLE = "routes_visible"
        /** The speed-scale control's face; non-null, so an unwritten key simply reads back as expanded. */
        private const val KEY_TRACK_LEGEND_EXPANDED = "track_legend_expanded"
        /** Whether stored tracks wear direction chevrons; the menu's twin box is its only writer. */
        private const val KEY_TRACK_ARROWS = "track_arrows"
        /** Whether stored tracks are painted from the speed ramp; the same writer, the other chip. */
        private const val KEY_TRACK_COLOURS = "track_colours"
        /** The retired triple: read once by the migration, which erases it in the same edit. */
        private const val KEY_TRACK_RENDER_MODE = "track_render_mode"
        private const val KEY_TRACK_DIRECTION_DENSITY = "track_direction_density"
        private const val KEY_TRACK_DIRECTION_SPEED_FLOOR_KN = "track_direction_speed_floor_kn"
        private const val KEY_TRACK_DIRECTION_SPEED_CEILING_KN = "track_direction_speed_ceiling_kn"
        private const val KEY_TRACK_DIRECTION_MIN_SPACING_DP = "track_direction_min_spacing_dp"
        private const val KEY_TRACK_DIRECTION_MAX_SPACING_DP = "track_direction_max_spacing_dp"
        private const val KEY_MARKER_LAYER_STATE = "marker_layer_state"
        private const val KEY_MARKER_ZONES_VISIBLE = "marker_zones_visible"
        private const val KEY_MARKER_HALO_SIZE = "marker_halo_size"
        private const val KEY_MARKER_POINT_ICON_ZOOM = "marker_point_icon_zoom"
        private const val KEY_MARKER_HALO_PINNED_COLOR = "marker_halo_pinned_color"
        private const val KEY_MARKER_HALO_UNPINNED_COLOR = "marker_halo_unpinned_color"
        private const val KEY_MARKER_HALO_PINNED_FILL_TRANSPARENCY_PCT = "marker_halo_pinned_fill_transparency_pct"
        private const val KEY_MARKER_HALO_PINNED_BORDER_TRANSPARENCY_PCT = "marker_halo_pinned_border_transparency_pct"
        private const val KEY_MARKER_HALO_UNPINNED_FILL_TRANSPARENCY_PCT = "marker_halo_unpinned_fill_transparency_pct"
        private const val KEY_MARKER_HALO_UNPINNED_BORDER_TRANSPARENCY_PCT = "marker_halo_unpinned_border_transparency_pct"
        private const val KEY_TRACKING_RENDER_NB = "tracking_render_nb"
        /** The route count's own key: the sibling above counts recorded tracks alone. */
        private const val KEY_TRACKING_ROUTE_RENDER_NB = "tracking_route_render_nb"
        private const val KEY_TRACKING_COLOR_ACTIVE = "tracking_color_active"
        private const val KEY_TRACKING_COLOR_PAST_FROM = "tracking_color_past_from"
        private const val KEY_TRACKING_COLOR_PAST_TO = "tracking_color_past_to"
        /** The route colour pair's own keys, and the ladder's, beside their siblings'. */
        private const val KEY_TRACKING_COLOR_ROUTE_FROM = "tracking_color_route_from"
        private const val KEY_TRACKING_COLOR_ROUTE_TO = "tracking_color_route_to"
        /** The followed route line's own colour, seeded from `maro.properties`' `path.line.color.live`. */
        private const val KEY_ROUTE_LINE_COLOR = "route_line_color"
        private const val KEY_TRACKING_TRANSPARENCY_NEWEST = "tracking_transparency_newest"
        private const val KEY_TRACKING_TRANSPARENCY_OLDEST = "tracking_transparency_oldest"
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_NEWEST = "tracking_transparency_pinned_newest"
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_OLDEST = "tracking_transparency_pinned_oldest"
        private const val KEY_TRACKING_COLOR_PINNED_FROM = "tracking_color_pinned_from"
        private const val KEY_TRACKING_COLOR_PINNED_TO = "tracking_color_pinned_to"
        /** The route ladder's prefs keys; their defaults are the file's own injected pair. */
        private const val KEY_TRACKING_TRANSPARENCY_ROUTE_NEWEST = "tracking_transparency_route_newest"
        private const val KEY_TRACKING_TRANSPARENCY_ROUTE_OLDEST = "tracking_transparency_route_oldest"
        /** The pinned route's own pair (D5, D8), beside the pinned track's. */
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_NEWEST = "tracking_transparency_pinned_route_newest"
        private const val KEY_TRACKING_TRANSPARENCY_PINNED_ROUTE_OLDEST = "tracking_transparency_pinned_route_oldest"
        private const val KEY_TRACKING_COLOR_PINNED_ROUTE_FROM = "tracking_color_pinned_route_from"
        private const val KEY_TRACKING_COLOR_PINNED_ROUTE_TO = "tracking_color_pinned_route_to"
        /** The route-scoped rendering gates, the route-scoped twins of the two chips. */
        private const val KEY_ROUTE_SPEED_COLOR = "route_speed_color"
        private const val KEY_ROUTE_SPEED_ARROWS = "route_speed_arrows"
        private const val KEY_TRACK_SIMPLIFY_ENABLED = "track_simplify_enabled"
        private const val KEY_TRACK_SIMPLIFY_EPSILON_M = "track_simplify_epsilon_m"
        private const val KEY_TRACK_SIMPLIFY_SPEED_DELTA_KN = "track_simplify_speed_delta_kn"
        private const val KEY_MARKER_DEBUG_RAYS = "marker_debug_rays"
        private const val KEY_TRACK_LIST_SORT = "track_list_sort"
        private const val KEY_MARKER_LIST_SORT = "marker_list_sort"
        private const val KEY_TRACK_LIST_FILTER = "track_list_filter"
        private const val KEY_MARKER_LIST_FILTER = "marker_list_filter"
        private const val KEY_TRACK_MAP_FILTER = "track_map_filter"
        private const val KEY_MARKER_MAP_FILTER = "marker_map_filter"
        private const val KEY_TRACK_FILTER_LINKED = "track_filter_linked"
        private const val KEY_MARKER_FILTER_LINKED = "marker_filter_linked"
        private const val KEY_ROUTE_LIST_SORT = "route_list_sort"
        private const val KEY_ROUTE_LIST_FILTER = "route_list_filter"
        private const val KEY_ROUTE_MAP_FILTER = "route_map_filter"
        private const val KEY_ROUTE_FILTER_LINKED = "route_filter_linked"
        private const val KEY_MAP_OFFSET_GPS = "map_offset_gps"
        private const val KEY_MAP_OFFSET_DEMO = "map_offset_demo"
        private const val KEY_MAP_OFFSET_BOAT_FROM_BOTTOM_PCT = "map_offset_boat_from_bottom_pct"
        private const val KEY_MAX_RECORDING_ACCURACY_M = "max_recording_accuracy_m"
    }
}
