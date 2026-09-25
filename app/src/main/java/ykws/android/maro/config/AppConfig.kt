package ykws.android.maro.config

import android.content.Context
import android.graphics.Color
import ykws.android.maro.data.model.DepthSource
import java.util.Properties

/**
 * Runtime loader for all `.properties` files bundled in assets.
 *
 * Loads [maro.properties](app/src/main/assets/maro.properties),
 * [ui.properties](app/src/main/assets/ui.properties), and
 * [colors.properties](app/src/main/assets/colors.properties) — each file
 * overrides the previous on key collision, so `colors.properties` always
 * wins for colour keys.
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

    /**
     * Free-water pace (kn) a route's trip figure plans at until the boat's own observed pace has
     * something to say — `route.freeWaterPaceKn`, default 28.
     *
     * The span's bounds live beside it so the properties loader, the settings clamp and the Settings
     * row all read one definition rather than each spelling 3 and 40 for itself.
     */
    var routeFreeWaterPaceKn = 28f
        private set

    /** Lowest free-water pace (kn) the setting accepts. */
    const val ROUTE_FREE_WATER_PACE_MIN_KN = 3f

    /** Highest free-water pace (kn) the setting accepts. */
    const val ROUTE_FREE_WATER_PACE_MAX_KN = 40f

    /**
     * The route engine id the harness ships as its default — `route.engine.id`, default `dummy`.
     *
     * The registry resolves a stored id through this value, so an id nothing claims falls back to
     * whichever row this names. One home for the value: the property is parsed here and the
     * `AppSettings.routeEngineId` preference is seeded from it.
     */
    var routeEngineId: String = "dummy"
        private set

    /**
     * The route line's colour — `route.line.color`, default a green that reads as "the way to go"
     * against both the blue water and the amber tracks.
     *
     * The line's three drawing values live here and in `maro.properties` alone, following the rule
     * that every drawing value has one home: the overlay reads these and gains no Settings row until
     * one is asked for.
     */
    var routeLineColor: Int = 0xFF2ECC71.toInt()
        private set

    /** The route line's transparency (0 = opaque, 100 = invisible) — `route.line.transparencyPct`. */
    var routeLineTransparencyPct: Int = 15
        private set

    /** The route line's stroke width (dp) — `route.line.widthDp`. */
    var routeLineWidthDp: Float = 6f
        private set

    /** The destination pin's fill colour — `route.pin.color`. */
    var routePinColor: Int = 0xFF2ECC71.toInt()
        private set

    /** The ring drawn around the destination pin (dp) — `route.pin.ringWidthDp`. */
    var routePinRingWidthDp: Float = 3f
        private set

    /**
     * Ground move (m) the aim must have made before it is worth a search — `route.ask.minTargetMoveM`,
     * default 25.
     *
     * The number that already shipped as `RouteOverlay`'s own literal, moved home rather than changed:
     * it is half of the ask policy, the settle beside it being the other half, and together they are
     * what keeps a live drag from flooding the one worker.
     */
    var routeAskMinTargetMoveM: Double = 25.0
        private set

    /**
     * Quiet time (ms) the drag must then stand still for before the search is asked —
     * `route.ask.settleMs`, default 300.
     *
     * Load-bearing rather than a courtesy: with one computation at a time, this is what stops a live
     * drag from flooding the worker, and it is why nothing is asked on the arming frame.
     */
    var routeAskSettleMs: Long = 300L
        private set

    /**
     * The refused end's crosshair colour — `route.target.color`, default bold red spelled
     * `#AARRGGBB` as the line's own key is.
     *
     * One colour for both ends: a refused aim and a refused origin read the same.
     */
    var routeTargetColor: Int = 0xFFD32F2F.toInt()
        private set

    /** Stroke width (dp) of the refused end's crosshair — `route.target.widthDp`. */
    var routeTargetWidthDp: Float = 3f
        private set

    /** Pulse period (ms) of the refused crosshair's 1 -> 0.3 beat — `route.target.pulseMs`. */
    var routeTargetPulseMs: Int = 800
        private set

    /**
     * Seconds since the standing route was answered at which the following mode may ask again —
     * `route.refresh.intervalSec`, default 30.
     *
     * One of the two thresholds the **app** owns; the engine's `isReadyToRecompute()` may only veto.
     */
    var routeRefreshIntervalSec: Int = 30
        private set

    /**
     * Distance (m) the boat may stand off the standing route before the mode may ask again —
     * `route.refresh.offRouteM`, default 100.
     *
     * The other threshold the app owns; either one alone opens the moment.
     */
    var routeRefreshOffRouteM: Double = 100.0
        private set

    /**
     * How many of the **oldest** replaced routes the ladder keeps beside the standing one —
     * `route.ladder.oldest.nb`, default 1.
     *
     * Stale means *replaced*: a failed refresh stales nothing, so the ladder only ever grows on a new
     * answer arriving.
     */
    var routeLadderOldestNb: Int = 1
        private set

    /** How many of the **newest** replaced routes the ladder keeps — `route.ladder.latest.nb`, default 3. */
    var routeLadderLatestNb: Int = 3
        private set

    /** Clearance (m) the avoid route keeps off land, islands and hazard rings — `route.avoid.obstacleMarginM`, default 25. */
    var routeAvoidObstacleMarginM: Double = 25.0
        private set

    /** Side (m) of one corridor-grid cell — `route.avoid.gridCellM`, default 50. */
    var routeAvoidGridCellM: Double = 50.0
        private set

    /** How far (m) the corridor box reaches past the start-aim line — `route.avoid.corridorReachM`, default 1852 (1 NM). */
    var routeAvoidCorridorReachM: Double = 1852.0
        private set

    /** The 300 m band's own margin, read only from stage 2 on — `route.avoid.zone300MarginM`, default 25. */
    var routeAvoidZone300MarginM: Double = 25.0
        private set

    /**
     * Depth (m) below which a corridor cell is excluded from the route — `route.avoid.minDepthM`,
     * default 3.0, read only while `route.avoid.depthGate.enabled` is true. The gate is a coarse
     * guard on the route being written, not a fine sounding: a known depth under this number paints
     * the cell land, and everything at or above it — whatever its source or confidence — is ignored.
     */
    var routeAvoidMinDepthM: Double = 3.0
        private set

    /**
     * Whether the 3 m depth gate is armed — `route.avoid.depthGate.enabled`, default true. False runs
     * the avoid engine on the coastline alone: no depth grid is loaded and no corridor cell is
     * excluded on a sounding.
     */
    var routeAvoidDepthGateEnabled: Boolean = true
        private set

    /**
     * How much dearer the time spent inside the 300 m band is to the search — `route.avoid.softCostAversion`,
     * default 1.5. 1.0 prices the band as open water; the excess over 1.0 is the price a metre inside it
     * carries, so 1.5 makes every metre in the band cost half a metre more.
     */
    var routeAvoidSoftCostAversion: Double = 1.5
        private set

    /**
     * Whether the 300 m band is priced — `route.avoid.zone300.enabled`, default true. False prices the
     * band as open water and writes no BAND tag, so `route.avoid.softCostAversion` stays the value
     * that says how dear the band is when it is on.
     */
    var routeAvoidZone300Enabled: Boolean = true
        private set

    /** Hysteresis deadband (meters) for speed zone boundary detection — prevents GPS jitter from flapping inside/outside state. */
    var speedZoneHysteresisM: Double = 5.0
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
     *  Default 1.0. Set via `ui.button.icon.active.alpha` in colors.properties. */
    var buttonActionIconActiveAlpha: Float = 1.0f
        private set

    /** Alpha (0.0–1.0) for inactive/toggled-off icon state.
     *  Default 0.25. Set via `ui.button.icon.inactive.alpha` in colors.properties. */
    var buttonActionIconInactiveAlpha: Float = 0.25f
        private set

    // ── Map surface & its two control families (from colors.properties) ───────────
    // The `ui.map.surface.*` block below is the family's one set of settings, read through the single
    // painting path in ui/map/MapSurface.kt, which names the surfaces that paint through it and the boxes
    // that stay outside. What stays per family is only what is genuinely family-specific: the toggle row's
    // square, gutter and glyph size, and the overlay cards' text tokens.

    /** ARGB fill of the shared map surface. Default `#A8FFFFFF` (white at 66 %).
     *  Set via `ui.map.surface.inactive` in colors.properties. */
    var uiMapSurfaceInactive: Int = 0xA8FFFFFF.toInt()
        private set

    /** Corner radius (dp) every surface clips to. Default 8.
     *  Set via `ui.map.surface.corner.radius`. */
    var uiMapSurfaceCornerRadius: Float = 8f
        private set

    /** Padding (dp) the surface applies inside its own edge — inert on a centred square. Default 6.
     *  Set via `ui.map.surface.padding`. */
    var uiMapSurfacePadding: Float = 6f
        private set

    /** ARGB border colour every surface draws. Default `#14FFFFFF` (alias of `ui.divider.color`).
     *  Set via `ui.map.surface.border.color`. */
    var uiMapSurfaceBorderColor: Int = 0x14FFFFFF.toInt()
        private set

    /** Border width (dp) every surface draws. Default 1. Set via `ui.map.surface.border.width`. */
    var uiMapSurfaceBorderWidth: Float = 1f
        private set

    /** Alpha (0.0–1.0) an inactive surface dims its *content* to, never its fill. Default 0.75.
     *  Set via `ui.map.surface.inactive.content.alpha` in colors.properties. */
    var uiMapSurfaceInactiveContentAlpha: Float = 0.75f
        private set

    /** Background alpha (0.0–1.0) an active surface paints its own state colour at. Default 0.65.
     *  Set via `ui.map.surface.active.alpha`. */
    var uiMapSurfaceActiveAlpha: Float = 0.65f
        private set

    /** Side (dp) of one square in the row. Default 44. Set via `ui.map.toggle.square`. */
    var uiMapToggleSquare: Float = 44f
        private set

    /** Gutter (dp) between two squares — the row's own start inset too. Default 6.
     *  Set via `ui.map.toggle.gutter` in colors.properties. */
    var uiMapToggleGutter: Float = 6f
        private set

    /** Emoji glyph size (sp) inside one square. Default 22.
     *  Set via `ui.map.toggle.icon.size` in colors.properties. */
    var uiMapToggleIconSize: Float = 22f
        private set

    // ── Inspect mode (from maro.properties) ──────────────────────────────────────────────────────
    // The sleuth square in the status row arms a viewport pick. Eligibility is the screen itself — a
    // candidate's own bbox against the projection's visible bounds — so the only key left is the
    // trigger's quiet time; the sweep, the ordering, the metric and the walk are all structural.

    /** Quiet time (ms) the map must stand still after a genuine finger lift before the highlighted
     *  item is picked. Default 666. Set via `ui.map.inspect.dwell.ms`. */
    var uiMapInspectDwellMs: Long = 666L
        private set

    /** Text colour on the shared surface. Default `#FF78909C`
     *  (alias of `ui.text.secondary`). Set via `ui.map.overlay.text.color`. */
    var uiMapOverlayTextColor: Int = 0xFF78909C.toInt()
        private set

    /** Text weight on the shared surface, parsed as an `Int` clamped to 100–900 and mapped with
     *  `FontWeight(…)` at the call site. Default 700. Set via `ui.map.overlay.text.weight`. */
    var uiMapOverlayTextWeight: Int = 700
        private set

    /** Text size (sp) on the shared surface. Default 10.
     *  Set via `ui.map.overlay.text.size`. */
    var uiMapOverlayTextSize: Float = 10f
        private set

    /** Line height (sp) of the zone-info line's text. Default 14.
     *  Set via `ui.map.overlay.text.line.height`. */
    var uiMapOverlayTextLineHeight: Float = 14f
        private set

    /** Gap (dp) between an overlay card's parts. Default 6. Set via `ui.map.overlay.gap`. */
    var uiMapOverlayGap: Float = 6f
        private set

    /** Gap (dp) between two rows of the zone-info line's column. Default 2.
     *  Set via `ui.map.overlay.line.spacing`. */
    var uiMapOverlayLineSpacing: Float = 2f
        private set

    /** ARGB colour for badge count text.
     *  Default #E0E0E0. Set via `ui.button.badge.text` in colors.properties. */
    var uiButtonBadgeText: Int = 0xFFE0E0E0.toInt()
        private set
    /** Alpha (0.0–1.0) for badge when the arc/fan is OPEN (expanded).
     *  Default 1.0. Set via `ui.button.badge.active.alpha` in colors.properties. */
    var buttonBadgeActiveAlpha: Float = 1.0f
        private set
    /** Alpha (0.0–1.0) for badge when the arc/fan is CLOSED (collapsed).
     *  Default 0.25. Set via `ui.button.badge.inactive.alpha` in colors.properties. */
    var buttonBadgeInactiveAlpha: Float = 0.25f
        private set

    /** Semantic danger colour (red). Default #CCB71C1C. Set via `semantic.danger` in colors.properties. */
    var semanticDanger: Int = 0xCCB71C1C.toInt()
        private set
    /** Semantic caution colour (amber). Default #CCEF6C00. Set via `semantic.caution` in colors.properties. */
    var semanticCaution: Int = 0xCCEF6C00.toInt()
        private set
    /** Semantic compliant colour (green). Default #CC4CAF50. Set via `semantic.compliant` in colors.properties. */
    var semanticCompliant: Int = 0xCC4CAF50.toInt()
        private set
    /** Semantic info colour (blue). Default #FF1565C0. Set via `semantic.info` in colors.properties. */
    var semanticInfo: Int = 0xFF1565C0.toInt()
        private set
    /** Semantic inactive colour (white-transparent). Default #33FFFFFF. Set via `semantic.inactive` in colors.properties. */
    var semanticInactive: Int = 0x33FFFFFF.toInt()
        private set

    /** Default proximity range (m) for Pin-type user markers. Set via `marker.proximity.pin_m` in maro.properties. */
    var markerProximityPinM: Double = 200.0
        private set
    /** Direction-arrow density speed floor (kn) — below this, arrows use min spacing. */
    var trackDirectionSpeedFloorKn: Float = 3.0f
        private set
    /** Direction-arrow density speed ceiling (kn) — above this, arrows use max spacing. */
    var trackDirectionSpeedCeilingKn: Float = 35.0f
        private set
    /** Direction-arrow minimum on-screen spacing (dp). */
    var trackDirectionMinSpacingDp: Int = 32
        private set
    /** Direction-arrow maximum on-screen spacing (dp). Default 400, the shipped file's value; the
     *  Settings slider reads it between the same bounds it always had. */
    var trackDirectionMaxSpacingDp: Int = 400
        private set
    /** Chevron tempering knee (dp): at or below this core a chevron is drawn at the core itself.
     *  Default 3.3333333 — the 10 px the table stated on the 3× device it was tuned on.
     *  Set via `track.arrow.scaleKnee`. */
    var trackArrowScaleKneeDp: Float = 10f / 3f
        private set
    /** Chevron tempering factor above the knee: the core becomes `knee + (core − knee) × temper`.
     *  Default 0.5. Set via `track.arrow.temper`. */
    var trackArrowTemper: Float = 0.5f
        private set

    // ── Track outlines: per-type widths (from maro.properties) ───────────
    // All three rendering modes read this table: the widths are what the map draws stored tracks and
    // the live recording line from. Widths are dp — the reference density is the 3× device the numbers
    // were chosen on, and the caller multiplies by the density at the paint site — and every default
    // mirrors the shipped file key for key.
    /** Stroke width (dp) of the live recording line, its GAP bridges and its trailing segment.
     *  Default 4 (the 12 px of the 3× reference). Set via `track.width.live`. */
    var trackWidthLiveDp: Float = 4f
        private set
    /** Stroke width (dp) of the selected stored track's core. Default 3.3333333 (the 10 px of the 3×
     *  reference). Set via `track.width.selected`. */
    var trackWidthSelectedDp: Float = 10f / 3f
        private set
    /** Stroke width (dp) of the newest history track. Default 3.6666667 (the 11 px of the 3×
     *  reference). Set via `track.width.newest`. */
    var trackWidthNewestDp: Float = 11f / 3f
        private set
    /** Stroke width (dp) of every pinned track. Default 3 (the 9 px of the 3× reference).
     *  Set via `track.width.pinned`. */
    var trackWidthPinnedDp: Float = 3f
        private set
    /** Stroke width (dp) of every other history track. Default 2.6666667 (the 8 px of the 3×
     *  reference). Set via `track.width.history`. */
    var trackWidthHistoryDp: Float = 8f / 3f
        private set
    /** Stroke width (dp) of every route's line — a route. It joins the same table and is taken
     *  whatever the pin says, the route role having its own stroke rather than the pinned or history
     *  one. Default 2.6666667 (the 8 px of the 3× reference). Set via `track.width.route`. */
    var trackWidthRouteDp: Float = 8f / 3f
        private set
    /** Stroke width (dp) of the dark casing drawn beneath the selected track's core — 1 dp a side over
     *  the shipped 3.333 dp core, the legacy pair's own rim, with the casing still standing wider than
     *  the core it sits under.
     *  The key sets two things, and both are that same rim: the line's casing takes this width whole,
     *  while a selection's chevrons take half its excess over the line's own width as the outward
     *  offset of their dark V from the coloured one — 1 dp at the shipped pair, which clears the
     *  coloured centreline by 0.167 dp. Default 5.3333335 (the 16 px of the 3× reference).
     *  Set via `track.width.selected.casing`. */
    var trackWidthSelectedCasingDp: Float = 16f / 3f
        private set

    // ── Speed heatmap ramp (from maro.properties) ────────────────────────
    /** Parsed speed ramp: families as a list — each carrying its own draw step — and the neutral
     *  tint. There is no count key: the read walks `familyN` from 1 upward and stops at the
     *  first index missing a key, so the file alone decides the ramp's length.
     *  Default: the seven families the shipped file holds, mirrored key for key — flat green inside
     *  the 5 kn limit, the two 30 % tolerance changeovers at 0.25 and 0.5, then 13 / 22 / 32 / 70 kn.
     *  The file is the source of truth and this default follows it, not the reverse.
     *  Set via `track.heatmap.familyN.*` / `.unknownColor`. */
    var trackHeatmapRamp: HeatmapRamp = HeatmapRamp(
        families = listOf(
            HeatmapFamily(5f, 0xFF1A6B1A.toInt(), 0xFF2AAB2A.toInt(), 1.0f),    // flat green inside the 5 kn limit
            HeatmapFamily(7f, 0xFF2AAB2A.toInt(), 0xFF105189.toInt(), 0.25f),  // green to blue across the 30 % tolerance
            HeatmapFamily(10f, 0xFF105189.toInt(), 0xFF4EA7FF.toInt(), 0.5f),  // blue out to the 10 kn limit
            HeatmapFamily(13f, 0xFF4EA7FF.toInt(), 0xFFFFD164.toInt(), 0.5f),  // blue to amber across the tolerance
            HeatmapFamily(22f, 0xFFFFD164.toInt(), 0xFFEF6C00.toInt(), 1.0f),  // amber to orange, 13 to 22
            HeatmapFamily(32f, 0xFFEF6C00.toInt(), 0xFF8B1515.toInt(), 1.0f),  // orange to dark red, 22 to 32
            HeatmapFamily(70f, 0xFF8B1515.toInt(), 0xFF791FB0.toInt(), 2.0f)   // dark red to purple out to 70 kn
        ),
        unknownArgb = 0xFFF5F5DC.toInt()
    )
        private set
    /** The legend's tick table: one row per printed label, holding the position it sits at on the bar's
     *  linear minimum → last-position scale beside the text printed for it. The table is the
     *  specification — every row prints, with no rule dropping or merging one — so the text may
     *  deliberately differ from the position, and the first two rows carry a compliance limit off its
     *  own boundary by design. Default: the five rows the shipped file holds, mirrored key for key.
     *  Set via `track.heatmap.scaleTicks`; the table's last position is the bar's own top. */
    var trackHeatmapScaleTicks: List<HeatmapScaleTick> = listOf(
        HeatmapScaleTick(7f, "5"),
        HeatmapScaleTick(13f, "10"),
        HeatmapScaleTick(22f, "20"),
        HeatmapScaleTick(30f, "30"),
        HeatmapScaleTick(35f, "35")
    )
        private set
    /** Foot of the legend's scale (kn): the bar runs from here to the tick table's last position.
     *  Default 2, mirroring the shipped file. Set via `track.heatmap.scaleMinKn`; an absent key
     *  leaves this default standing. */
    var trackHeatmapScaleMinKn: Float = 2f
        private set
    /** Default proximity multiplier for Circle/Corridor user markers. Set via `marker.proximity.zone_multiplier` in maro.properties. */
    var markerProximityZoneMultiplier: Double = 3.0
        private set
    /** Share of the smaller displayed map dimension a selected corridor fills when its dashboard
     *  opens. Set via `marker.focus.corridor_share` in maro.properties. */
    var markerFocusCorridorShare: Double = 0.50
        private set
    /** The same measure for a selected Circle zone. Set via `marker.focus.zone_share`. */
    var markerFocusZoneShare: Double = 0.30
        private set
    /** Nominal footprint (metres) a selected Pin frames, standing in for the default zone's
     *  diameter. Set via `marker.focus.pin_footprint_m`. */
    var markerFocusPinFootprintM: Double = 200.0
        private set

    /** Idle threshold (seconds) for BoatMarker snapshot + drawer auto-open. */
    var boatMarkerIdleThresholdSec: Long = 60
    /** Minimum idle duration (seconds) before 🕐 auto-marker pin becomes permanent. */
    var boatMarkerAutoMarkerMinDurationSec: Long = 120
    /** Auto-marker icon transparency on map (0-100, 0=opaque, 100=invisible). */
    var boatMarkerIdleTransparencyPct: Int = 50

    /** Proximity range (m) for 🕐 auto-marker pins. Set via `track.boatMarker.autoMarker.proximityM` in maro.properties. */
    var boatMarkerAutoMarkerProximityM: Double = 300.0
        private set

    /** Dedup radius (m) — skip auto-marker creation if an existing IDLE_AUTO marker is within this distance. */
    var boatMarkerAutoMarkerDedupRadiusM: Double = 25.0
        private set
    /** Minimum cumulative track point distance (m) between idle periods at same location to consider them separate stops. */
    var boatMarkerMinTravelBetweenStopsM: Double = 25.0
        private set

    /** Gap distance threshold (m) for inserting GAP markers on track resume. Set via `tracking.gapDistanceThresholdM` in maro.properties. */
    var trackingGapDistanceThresholdM: Double = 200.0
        private set
    /** Gap time threshold (s) for inserting GAP markers on track resume. Set via `tracking.gapTimeThresholdSec` in maro.properties. */
    var trackingGapTimeThresholdSec: Long = 120L
        private set

    // ── Power management — screen hold ───────────────────────────────
    // Bounds, default and developer constants only. The value the user picks lives in
    // SettingsManager prefs; these define the slider range and the fallbacks.

    /** Lock-delay lower bound (minutes). Set via `power.screen.grace.minMinutes` in maro.properties. */
    var powerScreenGraceMinMinutes: Int = 1
        private set
    /** Lock-delay upper bound (minutes). Set via `power.screen.grace.maxMinutes` in maro.properties. */
    var powerScreenGraceMaxMinutes: Int = 15
        private set
    /** Default lock delay (minutes), clamped into the bounds above. Set via `power.screen.grace.defaultMinutes`. */
    var powerScreenGraceDefaultMinutes: Int = 5
        private set
    /** Speed over ground (knots) above which the boat counts as moving. Set via `power.screen.movementThresholdKn`. */
    var powerScreenMovementThresholdKn: Float = 1.0f
        private set
    /** Age (ms) beyond which the last speed reading is treated as lost. Set via `power.screen.stalenessBoundMs`. */
    var powerScreenStalenessBoundMs: Long = 30_000L
        private set

    // ── Marker sort scoring ─────────────────────────────────────────
    /** Pin type weight. Lower = higher priority. */
    var markerSortTypeWeightPin: Double = 0.5
        private set
    /** Circle type weight. Baseline = 1.0. */
    var markerSortTypeWeightCircle: Double = 1.0
        private set
    /** Corridor type weight. Higher = lower priority. */
    var markerSortTypeWeightCorridor: Double = 2.0
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
    /** Dashboard status neutral (informational). Default from semantic.info = #FF1565C0 (blue). Set via `ui.dashboard.status.neutral` in colors.properties. */
    var uiDashboardStatusNeutral: Int = 0xFF1565C0.toInt()
        private set
    /** Dashboard status absent (no-data, placeholder). Default from semantic.inactive = #33FFFFFF (white 20%). Set via `ui.dashboard.status.absent` in colors.properties. */
    var uiDashboardStatusAbsent: Int = 0x33FFFFFF.toInt()
        private set
    /** Dashboard zone speed-safe. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.zone.safe` in colors.properties. */
    var uiDashboardZoneSafe: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard zone speed-caution. Default from semantic.caution = #CCEF6C00 (amber 80%). Set via `ui.dashboard.zone.caution` in colors.properties. */
    var uiDashboardZoneCaution: Int = 0xCCEF6C00.toInt()
        private set
    /** Dashboard zone speed-danger. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.dashboard.zone.danger` in colors.properties. */
    var uiDashboardZoneDanger: Int = 0xCCB71C1C.toInt()
        private set
    /** Dashboard zone speed-compliant. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.zone.compliant` in colors.properties. */
    var uiDashboardZoneCompliant: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard zone normal. Default from semantic.inactive = #33FFFFFF (white 20%). Set via `ui.dashboard.zone.normal` in colors.properties. */
    var uiDashboardZoneNormal: Int = 0x33FFFFFF.toInt()
        private set
    /** Dashboard zone danger-dark. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.dashboard.zone.dangerDark` in colors.properties. */
    var uiDashboardZoneDangerDark: Int = 0xCCB71C1C.toInt()
        private set
    /** Dashboard distance entry (amber). Default from semantic.caution = #CCEF6C00 (amber 80%). Set via `ui.dashboard.distance.entry` in colors.properties. */
    var uiDashboardDistanceEntry: Int = 0xCCEF6C00.toInt()
        private set
    /** Dashboard distance exit (green). Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `ui.dashboard.distance.exit` in colors.properties. */
    var uiDashboardDistanceExit: Int = 0xCC4CAF50.toInt()
        private set
    /** Dashboard dull alpha. Default 0.33. Set via `ui.dashboard.dullAlpha` in colors.properties. */
    var uiDashboardDullAlpha: Float = 0.33f
        private set

    /** Settings overlay background. Default #1A1A2E. Set via `ui.background` in colors.properties. */
    var uiBackground: Int = 0xFF1A1A2E.toInt()
        private set
    /** Settings exit-toast surface. Default #16213E. Set via `ui.toast.background` in colors.properties. */
    var uiToastBackground: Int = 0xFF16213E.toInt()
        private set
    /** Settings exit-toast text. Default #FFFFFF. Set via `ui.toast.text` in colors.properties. */
    var uiToastText: Int = 0xFFFFFFFF.toInt()
        private set

    /** Coastline mainland stroke colour. Default #1545C0. Set via `map.coastline.mainland.color` in maro.properties. */
    var mapCoastlineMainlandColor: Int = 0xFF1545C0.toInt()
        private set
    /** Coastline island stroke colour. Default #08805C. Set via `map.coastline.island.color` in maro.properties. */
    var mapCoastlineIslandColor: Int = 0xFF08805C.toInt()
        private set
    /** Coastline stroke width (dp) — one width for mainland and island alike. Default 3.3333333, the
     *  10 px of the 3× reference. Set via `map.coastline.widthDp` in maro.properties. */
    var mapCoastlineWidthDp: Float = 10f / 3f
        private set
    /** Coastline stroke transparency % (0 = opaque, 100 = invisible). Default 50, the shipped
     *  half-strength stroke (an alpha of 127). Set via `map.coastline.transparencyPct` in maro.properties. */
    var mapCoastlineTransparencyPct: Int = 50
        private set
    /** 300 m band seaward boundary stroke width (dp). Default 2, the 6 px of the 3× reference.
     *  Set via `map.zone300.boundary.widthDp` in maro.properties. */
    var mapZone300BoundaryWidthDp: Float = 2f
        private set
    /** Regulated zone outline stroke width (dp). Default 1, the 3 px of the 3× reference.
     *  Set via `map.regulatedZone.outline.widthDp` in maro.properties. */
    var mapRegulatedZoneOutlineWidthDp: Float = 1f
        private set

    /** Navigation arrow colour — the manual colour, painted while the Speed Colour mode is off.
     *  Default #1565C0. Set via `map.navigation.arrow.color` in maro.properties. */
    var mapNavigationArrowColor: Int = 0xFF1565C0.toInt()
        private set
    /** Cap arrow shaft width (dp) — the head is derived from it at the shipped 4 : 1 ratio.
     *  Default 2.25. Set via `map.navigation.arrow.widthDp` in maro.properties. */
    var mapNavigationArrowWidthDp: Float = 2.25f
        private set
    /** Cap arrow transparency % (0 = opaque, 100 = invisible). Default 0, the shipped opaque arrow.
     *  Set via `map.navigation.arrow.transparencyPct` in maro.properties. */
    var mapNavigationArrowTransparencyPct: Int = 0
        private set
    /** Cap arrow colour mode: true = the colour follows the speed the boat carries, read from the
     *  track speed ramp; false = the arrow's own colour key. Default false.
     *  Set via `map.navigation.arrow.followSpeedColour` in maro.properties. */
    var mapNavigationArrowFollowSpeedColour: Boolean = false
        private set
    /** Navigation direction line colour — the opaque hue; its alpha has one home in the transparency
     *  key below. Default #1565C0. Set via `map.navigation.line.color` in maro.properties. */
    var mapNavigationLineColor: Int = 0xFF1565C0.toInt()
        private set
    /** Direction line stroke width (dp). Default 1.
     *  Set via `map.navigation.line.widthDp` in maro.properties. */
    var mapNavigationLineWidthDp: Float = 1f
        private set
    /** Direction line transparency % (0 = opaque, 100 = invisible). Default 70, the 30 % alpha the
     *  colour key used to pack. Set via `map.navigation.line.transparencyPct` in maro.properties. */
    var mapNavigationLineTransparencyPct: Int = 70
        private set

    /** Speed (knots) at which the map look-ahead offset reaches its maximum.
     *  Default 20.0. Set via `map.offset.lookahead.maxspeedKn` in maro.properties.
     *  Range: 1–50. */
    var mapOffsetLookaheadMaxSpeedKn: Double = 20.0
        private set
    /** Boat position from screen bottom at full offset, as percentage of screen height.
     *  50 = centered / disabled. 33 = ~1/3 from bottom (default). Lower = more forward view.
     *  Set via `map.offset.lookahead.boatFromBottomPct` in maro.properties. Range: 5–50. */
    var mapOffsetLookaheadBoatFromBottomPct: Int = 33
        private set
    /** Enable automatic map offset in GPS navigation mode.
     *  Default true. Set via `map.offset.gps` in maro.properties. */
    var mapOffsetGps: Boolean = true
        private set
    /** Enable automatic map offset in demo/manual mode.
     *  Default false. Set via `map.offset.demo` in maro.properties. */
    var mapOffsetDemo: Boolean = false
        private set
    /** Landscape panel width scale applied to the portrait width (menu + settings).
     *  Default 1.2. Set via `ui.landscape.panel.widthScale` in maro.properties. Range: 0.5–3.0. */
    var uiLandscapePanelWidthScale: Float = 1.2f
        private set

    /** Depth NoData cell colour. Default #60FFF59D (pale yellow, ~38% alpha). Set via `map.depth.nodata.color` in colors.properties. */
    var mapDepthNodataColor: Int = 0x60FFF59D.toInt()
        private set

    /** Low-depth warning overlay colour. Default #CCB71C1C (dark red, 80% opacity, alias to ui.dashboard.status.error). Set via `overlay.lowDepth.color` in colors.properties. */
    var overlayLowDepthColor: Int = 0xCCB71C1C.toInt()
        private set
    /** GPS icon DEMO state background colour. Default from semantic.inactive = #33FFFFFF (white 20%). Set via `status.gps.demo` in colors.properties. */
    var statusGpsDemo: Int = 0x33FFFFFF.toInt()
        private set
    /** GPS icon ACQUIRING state background colour. Default from semantic.caution = #CCEF6C00 (amber 80%). Set via `status.gps.acquiring` in colors.properties. */
    var statusGpsAcquiring: Int = 0xCCEF6C00.toInt()
        private set
    /** GPS icon HEALTHY state background colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `status.gps.healthy` in colors.properties. */
    var statusGpsHealthy: Int = 0xCC4CAF50.toInt()
        private set
    /** GPS icon IDLE state background colour. Default #1565C0. Set via `status.gps.idle` in colors.properties. */
    var statusGpsIdle: Int = 0xFF1565C0.toInt()
        private set
    /** GPS icon STALE state background colour. Default from semantic.danger = #CCB71C1C (red 80%). Set via `status.gps.stale` in colors.properties. */
    var statusGpsStale: Int = 0xCCB71C1C.toInt()
        private set
    /** GPS icon ESTIMATING state background colour (dead reckoning). Default #FFB300 (amber).
     *  Code-only: `colors.properties` has no `status.gps.estimating` key, so this default is not a
     *  palette setting — the file's GPS block holds the other five states. */
    var statusGpsEstimating: Int = 0xFFFFB300.toInt()
        private set
    /** EarthWater icon water-state colour. Default #1565C0. Set via `status.earthWater.water` in colors.properties. */
    var statusEarthWaterWater: Int = 0xFF1565C0.toInt()
        private set
    /** EarthWater icon land-state colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `status.earthWater.land` in colors.properties. */
    var statusEarthWaterLand: Int = 0xCC4CAF50.toInt()
        private set
    /** EarthWater icon inactive-state colour. Default from semantic.inactive = #33FFFFFF (white 20%). Set via `status.earthWater.inactive` in colors.properties. */
    var statusEarthWaterInactive: Int = 0x33FFFFFF.toInt()
        private set

    /** Screen-lock icon OFF (unlocked) background colour. Default from semantic.inactive = #33FFFFFF (white 20%). Set via `status.lock.off` in colors.properties. */
    var statusLockOff: Int = 0x33FFFFFF.toInt()
        private set
    /** Screen-lock icon ON (locked) background colour. Default from semantic.info = #FF1565C0 (blue). Set via `status.lock.on` in colors.properties. */
    var statusLockOn: Int = 0xFF1565C0.toInt()
        private set

    /** Tracking icon HEALTHY state (ON + moving, recording) colour. Default #CC4CAF50. Set via `status.tracking.healthy` in colors.properties. */
    var statusTrackingHealthy: Int = 0xCC4CAF50.toInt()
        private set
    /** Tracking icon IDLE state (ON + stationary, not recording) colour. Default #FF1565C0. Set via `status.tracking.idle` in colors.properties. */
    var statusTrackingIdle: Int = 0xFF1565C0.toInt()
        private set
    /** Tracking icon OFF state (not tracking) colour. Default from semantic.inactive = #33FFFFFF (white 20%). Set via `status.tracking.off` in colors.properties. */
    var statusTrackingOff: Int = 0x33FFFFFF.toInt()
        private set
    /** Tracking icon dot colour when recording (moving). Default from semantic.danger = #CCB71C1C (red 80%). Set via `status.tracking.dot.recording` in colors.properties. */
    var statusTrackingDotRecording: Int = 0xCCB71C1C.toInt()
        private set
    /** Tracking icon dot colour when idle (stationary). Default from semantic.danger = #CCB71C1C (red 80%). Set via `status.tracking.dot.idle` in colors.properties. */
    var statusTrackingDotIdle: Int = 0xCCB71C1C.toInt()
        private set

    // ── Dashboard depth readout tints ─────────────────────────────────────────
    /** Dashboard depth readout collision tint. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.dashboard.readout.collision` in colors.properties. */
    var uiDashboardReadoutCollision: Int = 0xCCB71C1C.toInt()
        private set
    /** Dashboard depth readout shallow tint. Default from semantic.caution = #CCEF6C00 (amber 80%). Set via `ui.dashboard.readout.shallow` in colors.properties. */
    var uiDashboardReadoutShallow: Int = 0xCCEF6C00.toInt()
        private set
    /** Dashboard depth readout deep tint. Default from semantic.info = #FF1565C0 (blue). Set via `ui.dashboard.readout.deep` in colors.properties. */
    var uiDashboardReadoutDeep: Int = 0xFF1565C0.toInt()
        private set

    // ── Shared UI colours ─────────────────────────────────────────────────────
    /** Settings panel primary text. Default #FFFFFFFF. Set via `ui.text.primary` in colors.properties. */
    var uiTextPrimary: Int = 0xFFFFFFFF.toInt()
        private set
    /** Settings panel muted text. Default #FFB0BEC5. Set via `ui.text.muted` in colors.properties. */
    var uiTextMuted: Int = 0xFFB0BEC5.toInt()
        private set
    /** Settings panel secondary text. Default #FF78909C. Set via `ui.text.secondary` in colors.properties. */
    var uiTextSecondary: Int = 0xFF78909C.toInt()
        private set
    /** Settings panel accent colour. Default #FF1565C0. Set via `ui.accent` in colors.properties. */
    var uiAccent: Int = 0xFF1565C0.toInt()
        private set
    /** Settings panel slider value readout colour. Default #FF48a7f5. Set via `ui.value.text` in colors.properties. */
    var uiValueText: Int = 0xFF48a7f5.toInt()
        private set
    /** Scrim background for map overlay info text. Default #4D16213E (deep navy 30%). Set via `ui.text.scrim` in colors.properties. */
    var uiTextScrim: Int = 0x4D16213E.toInt()
        private set
    /** Settings panel card background. Default #33FFFFFF (20% white). Set via `ui.card.background` in colors.properties. */
    var uiCardBackground: Int = 0x33FFFFFF.toInt()
        private set
    /** Settings panel divider colour. Default #14FFFFFF. Set via `ui.divider.color` in colors.properties. */
    var uiDividerColor: Int = 0x14FFFFFF.toInt()
        private set
    /** Settings panel switch track inactive colour. Default #33FFFFFF. Set via `ui.switch.track.inactive` in colors.properties. */
    var uiSwitchTrackInactive: Int = 0x33FFFFFF.toInt()
        private set
    /** Settings panel input border colour. Default #66FFFFFF. Set via `ui.input.border` in colors.properties. */
    var uiInputBorder: Int = 0x66FFFFFF.toInt()
        private set
    /** Settings panel footer text colour. Default #FF546E7A. Set via `ui.footer.text` in colors.properties. */
    var uiFooterText: Int = 0xFF546E7A.toInt()
        private set
    /** Settings panel danger/delete colour. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.danger` in colors.properties. */
    var uiDanger: Int = 0xCCB71C1C.toInt()
        private set

    // ── Regulated zone type colours ───────────────────────────────────────────
    /** Regulated zone speed-limit colour. Default #FF1565C0. Set via `regulatedZone.type.speedLimit` in colors.properties. */
    var regulatedZoneTypeSpeedLimit: Int = 0xFF1565C0.toInt()
        private set
    /** Regulated zone anchoring-prohibited colour. Default from semantic.caution = #CCEF6C00 (amber 80%). Set via `regulatedZone.type.anchoringProhibited` in colors.properties. */
    var regulatedZoneTypeAnchoringProhibited: Int = 0xCCEF6C00.toInt()
        private set
    /** Regulated zone access-prohibited colour. Default from semantic.danger = #CCB71C1C (red 80%). Set via `regulatedZone.type.accessProhibited` in colors.properties. */
    var regulatedZoneTypeAccessProhibited: Int = 0xCCB71C1C.toInt()
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
    /** Hazard disc fill colour. Default #FFFFE800. Set via `map.hazard.disc.fill` in maro.properties. */
    var mapHazardDiscFill: Int = 0xFFFFE800.toInt()
        private set
    /** Hazard disc outline colour. Default #FF000000. Set via `map.hazard.outline` in maro.properties. */
    var mapHazardOutline: Int = 0xFF000000.toInt()
        private set
    /** Zone-ahead line colour. Default #CC4CAF50 (alias of ${ui.dashboard.status.success}). Set via `map.zoneAhead.line` in maro.properties. */
    var mapZoneAheadLine: Int = 0xCC4CAF50.toInt()
        private set
    /** Zone-ahead cone fill colour. Default #FFFFEB00. Set via `map.zoneAhead.cone.fill` in maro.properties. */
    var mapZoneAheadConeFill: Int = 0xFFFFEB00.toInt()
        private set
    /** Zone-ahead cone outline colour. Default #FFFFC800. Set via `map.zoneAhead.cone.outline` in maro.properties. */
    var mapZoneAheadConeOutline: Int = 0xFFFFC800.toInt()
        private set
    /** Zone300 fill colour (~19 % alpha, vestigial — the fill's alpha comes from the transparency setting).
     *  Default #30E53935. Set via `map.zone300.fill` in maro.properties. */
    var mapZone300Fill: Int = 0x30E53935.toInt()
        private set
    /** Zone300 boundary colour — the seed of `AppSettings.zone300Color`. Default #FFE53935.
     *  Set via `map.zone300.boundary` in maro.properties. */
    var mapZone300Boundary: Int = 0xFFE53935.toInt()
        private set
    /** Gold wash over the boat, pulsed by an accepted Where-Am-I tap — the gold the
     *  Where-Am-I result already uses, plain and opaque. The overlay beats it once, up to
     *  [mapMarkerTapFlashAlpha] and back out to nothing.
     *  Set via `map.marker.tap.flash.color` in maro.properties. */
    var mapMarkerTapFlashColor: Int = 0xFFFFD700.toInt()
        private set

    /** Ceiling the accepted tap's beat rises to, on the 0.0–1.0 scale the `*.alpha` keys share —
     *  clamped to 0–1 so the drawn alpha can never exceed it. Default 0.33, the shipped file's own
     *  value: the file is the source of truth and this default follows it. The alpha's one carrier:
     *  the flash colour is opaque. Set via `map.marker.tap.flash.alpha` in maro.properties. */
    var mapMarkerTapFlashAlpha: Float = 0.33f
        private set

    /** Diameter (dp) of the boat's round tap zone — fixed at any zoom, never following the sprite.
     *  Default 48. Set via `map.marker.tap.zoneDiameterDp` in maro.properties. */
    var mapMarkerTapZoneDiameterDp: Float = 48f
        private set

    /** Diameter (dp) of the accepted tap's pulse disc — twice the zone, so it haloes the hull.
     *  Default 96. Set via `map.marker.tap.flashDiameterDp` in maro.properties. */
    var mapMarkerTapFlashDiameterDp: Float = 96f
        private set

    /** Total duration (ms) of the accepted tap's single beat — the rise, then the way back out.
     *  Default 540. Set via `map.marker.tap.flashDurationMs` in maro.properties. */
    var mapMarkerTapFlashDurationMs: Long = 540L
        private set

    /** Where the beat peaks, as a fraction of its duration — clamped to 0–1 so the rise can never
     *  overrun the beat. Default 0.3333. Set via `map.marker.tap.flashPeakRatio` in maro.properties. */
    var mapMarkerTapFlashPeakRatio: Float = 0.3333f
        private set

    /** Exponent of the growth curve the centre sprite (boat / land dot), the wizard's crosshair and
     *  the cap arrow share: `size = baseAtRefZoom × 2^(exponent × (zoom − 12))`. 0.0 = one fixed size
     *  at every zoom, 1.0 = grows exactly like the ground — clamped to 0–1 so it can never outgrow the
     *  map. What it multiplies is the base pair of accessors below; the reference zoom, the coast-shrink
     *  pair and the arrow's own speed clamps stay code constants in `ui/map/MapOverlays.kt`.
     *  Default 0.35, settled 2026-09-19 for the 11–20 zoom range.
     *  Set via `map.marker.size.zoomExponent` in maro.properties. */
    var mapMarkerSizeZoomExponent: Float = 0.35f
        private set

    /** Base dp of the boat sprite at the reference zoom — the size the exponent and the distance ramp
     *  then move. Default 36.8, raised 15 % from 32 on 2026-09-19. Clamped to 1–128 so a stray value
     *  cannot collapse the layout or fill the screen. Set via `map.marker.size.boatBaseDp` in
     *  maro.properties. */
    var mapMarkerSizeBoatBaseDp: Float = 36.8f
        private set

    /** Base dp of the land dot at the reference zoom, kept at 0.25 × the boat's so the two read as one
     *  marker in two states. Default 9.2, raised 15 % from 8. Clamped to 1–128 like the boat's.
     *  Set via `map.marker.size.dotBaseDp` in maro.properties. */
    var mapMarkerSizeDotBaseDp: Float = 9.2f
        private set

    // ── Progress/error overlay colours ────────────────────────────────────────
    /** Progress overlay accent colour. Default #FF1565C0. Set via `ui.progress.accent` in colors.properties. */
    var uiProgressAccent: Int = 0xFF1565C0.toInt()
        private set
    /** Progress overlay track colour. Default #401565C0. Set via `ui.progress.track` in colors.properties. */
    var uiProgressTrack: Int = 0x401565C0.toInt()
        private set
    /** Error card background colour. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.error.card` in colors.properties. */
    var uiErrorCard: Int = 0xCCB71C1C.toInt()
        private set
    /** Error card text colour. Default #EEFFFFFF. Set via `ui.error.text` in colors.properties. */
    var uiErrorText: Int = 0xEEFFFFFF.toInt()
        private set
    /** Error card button background. Default #FFFFFFFF. Set via `ui.error.button.background` in colors.properties. */
    var uiErrorButtonBackground: Int = 0xFFFFFFFF.toInt()
        private set
    /** Error card button text. Default from semantic.danger = #CCB71C1C (red 80%). Set via `ui.error.button.text` in colors.properties. */
    var uiErrorButtonText: Int = 0xCCB71C1C.toInt()
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

    // ── UI tokens from ui.properties (spacing/padding/radius/font/divider/nested-card) ──
    // Values are raw numbers (dp or sp); composables apply the `.dp`/`.sp` extension.
    // Defaults mirror ui.properties so the UI renders identically if the file is absent.

    // Spacing (dp)
    var uiSpacingCardGap: Float = 12f; private set
    var uiSpacingSectionGap: Float = 14f; private set
    var uiSpacingHeaderBottom: Float = 6f; private set
    var uiSpacingGroupedRowGap: Float = 8f; private set
    var uiSpacingGroupedAfterExpander: Float = 4f; private set
    var uiSpacingLabelControl: Float = 16f; private set
    var uiSpacingExpanderToContent: Float = 4f; private set

    // Padding (dp)
    var uiPaddingCardVertical: Float = 8f; private set
    var uiPaddingCardHorizontal: Float = 16f; private set
    var uiPaddingToggleVertical: Float = 2f; private set
    var uiPaddingContentComfortable: Float = 12f; private set
    var uiPaddingExpanderVertical: Float = 6f; private set
    var uiPaddingHeaderVertical: Float = 6f; private set

    // Shared scrim dim alpha 0.0-1.0 (drawers + dialogs)
    var uiScrimAlpha: Float = 0.50f; private set

    // Corner radius (dp)
    var uiRadiusCard: Float = 12f; private set
    var uiRadiusExpander: Float = 8f; private set

    // Font sizes (sp)
    var uiFontSectionSize: Float = 18f; private set
    var uiFontSubsectionSize: Float = 16f; private set
    var uiFontTabSize: Float = 18f; private set
    var uiFontToggleSize: Float = 16f; private set
    var uiFontDescSize: Float = 13f; private set
    var uiFontCommentSize: Float = 12f; private set
    var uiFontValueSize: Float = 16f; private set
    var uiFontRangeSize: Float = 14f; private set

    // Nested-card colours (ARGB)
    var uiNestedCardBg: Int = 0x0DFFFFFF.toInt(); private set
    var uiNestedCardBorder: Int = 0x40FFFFFF.toInt(); private set

    // Divider (dp height/gap)
    var uiDividerHeight: Float = 1f; private set
    var uiDividerGap: Float = 6f; private set

    /**
     * Hash of the colours **one** raster step is painted from, folded into [RasterCache.Key] by
     * `RasterCache.keyFor` — taken per step, so touching one raster's palette misses that raster
     * and leaves the other cached. The colour map paints from the ramp (shallow → deep, plus the
     * collision tint) at [mapDepthRampAlpha], and colours its NoData cells [mapDepthNodataColor];
     * the warning overlay has a single hue, and grades it by its own two depth thresholds, which
     * are [RasterCache.Key] fields of their own.
     */
    val depthRasterColorsHash: Int get() = listOf(
        mapDepthRampShallowR, mapDepthRampShallowG, mapDepthRampShallowB,
        mapDepthRampDeepR, mapDepthRampDeepG, mapDepthRampDeepB,
        mapDepthRampWarningR, mapDepthRampWarningG, mapDepthRampWarningB,
        mapDepthRampAlpha,
        mapDepthNodataColor,
    ).hashCode()

    /** Hue of the low-depth warning overlay. Its alpha is graded per cell, so it is not a colour. */
    val lowDepthRasterColorsHash: Int get() = listOf(overlayLowDepthColor).hashCode()

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
     * 1. maro.properties  — spatial/behavioural tunables
     * 2. ui.properties    — UI spacing/dimension/font tokens
     * 3. colors.properties — ALL colour values (colors win)
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

            // Load ui.properties (optional — UI spacing/dimension/font tokens).
            // Loaded AFTER maro.properties but BEFORE colors.properties so that any
            // colour keys still resolve from colors.properties (colors win).
            try {
                context.assets.open("ui.properties").use { stream ->
                    props.load(stream)
                }
            } catch (_: Exception) {
                // ui.properties is optional; defaults apply if absent.
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
            props.getProperty("route.freeWaterPaceKn")?.toFloatOrNull()?.let {
                routeFreeWaterPaceKn =
                    it.coerceIn(ROUTE_FREE_WATER_PACE_MIN_KN, ROUTE_FREE_WATER_PACE_MAX_KN)
            }
            props.getProperty("route.engine.id")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                routeEngineId = it
            }
            props.getProperty("speedZone.hysteresisM")?.toDoubleOrNull()?.let {
                speedZoneHysteresisM = it.coerceIn(0.0, 50.0)
            }
            // ── User marker proximity defaults ──────────────────────────────────
            props.getProperty("marker.proximity.pin_m")?.toDoubleOrNull()?.let {
                markerProximityPinM = it.coerceAtLeast(0.0)
            }
            props.getProperty("marker.proximity.zone_multiplier")?.toDoubleOrNull()?.let {
                markerProximityZoneMultiplier = it.coerceIn(0.0, 20.0)
            }
            // ── Marker focus framing ────────────────────────────────────────────
            props.getProperty("marker.focus.corridor_share")?.toDoubleOrNull()?.let {
                markerFocusCorridorShare = it.coerceIn(0.1, 1.0)
            }
            props.getProperty("marker.focus.zone_share")?.toDoubleOrNull()?.let {
                markerFocusZoneShare = it.coerceIn(0.1, 1.0)
            }
            props.getProperty("marker.focus.pin_footprint_m")?.toDoubleOrNull()?.let {
                markerFocusPinFootprintM = it.coerceIn(20.0, 2000.0)
            }
            // ── Auto-marker idle tracking ───────────────────────────────────
            props.getProperty("track.boatMarker.autoMarker.idleThresholdSec")?.toLongOrNull()?.let {
                boatMarkerIdleThresholdSec = it.coerceIn(10, 600)
            }
            props.getProperty("track.boatMarker.autoMarker.minDurationSec")?.toLongOrNull()?.let {
                boatMarkerAutoMarkerMinDurationSec = it.coerceIn(30, 3600)
            }
            props.getProperty("track.boatMarker.autoMarker.transparency")?.toIntOrNull()?.let {
                boatMarkerIdleTransparencyPct = it.coerceIn(0, 100)
            }
            props.getProperty("track.boatMarker.autoMarker.proximityM")?.toDoubleOrNull()?.let {
                boatMarkerAutoMarkerProximityM = it.coerceAtLeast(0.0)
            }
            props.getProperty("track.boatMarker.autoMarker.dedupRadiusM")?.toDoubleOrNull()?.let {
                boatMarkerAutoMarkerDedupRadiusM = it.coerceAtLeast(1.0)
            }
            props.getProperty("track.boatMarker.autoMarker.minTravelBetweenStopsM")?.toDoubleOrNull()?.let {
                boatMarkerMinTravelBetweenStopsM = it.coerceAtLeast(1.0)
            }

            // ── Track gap detection (resume after crash) ────────────────────
            props.getProperty("tracking.gapDistanceThresholdM")?.toDoubleOrNull()?.let {
                trackingGapDistanceThresholdM = it.coerceAtLeast(0.0)
            }
            props.getProperty("tracking.gapTimeThresholdSec")?.toLongOrNull()?.let {
                trackingGapTimeThresholdSec = it.coerceAtLeast(0L)
            }

            // ── Power management — screen hold ──────────────────────────────
            props.getProperty("power.screen.grace.minMinutes")?.toIntOrNull()?.let {
                powerScreenGraceMinMinutes = it.coerceIn(1, 60)
            }
            props.getProperty("power.screen.grace.maxMinutes")?.toIntOrNull()?.let {
                powerScreenGraceMaxMinutes = it.coerceIn(1, 60)
            }
            props.getProperty("power.screen.grace.defaultMinutes")?.toIntOrNull()?.let {
                powerScreenGraceDefaultMinutes = it.coerceIn(1, 60)
            }
            props.getProperty("power.screen.movementThresholdKn")?.toFloatOrNull()?.let {
                powerScreenMovementThresholdKn = it.coerceIn(0.1f, 20f)
            }
            props.getProperty("power.screen.stalenessBoundMs")?.toLongOrNull()?.let {
                powerScreenStalenessBoundMs = it.coerceAtLeast(1_000L)
            }
            // A malformed file must not produce an inverted range (the KDoc promises no crashes on bad
            // config), so fall back to the shipped bounds and clamp the default inside them.
            if (powerScreenGraceMinMinutes >= powerScreenGraceMaxMinutes) {
                powerScreenGraceMinMinutes = 1
                powerScreenGraceMaxMinutes = 15
            }
            powerScreenGraceDefaultMinutes = powerScreenGraceDefaultMinutes
                .coerceIn(powerScreenGraceMinMinutes, powerScreenGraceMaxMinutes)

            // ── Track direction arrows (speed-based density) ────────────────
            props.getProperty("track.direction.speedFloorKn")?.toFloatOrNull()?.let {
                trackDirectionSpeedFloorKn = it.coerceIn(2f, 64f)
            }
            props.getProperty("track.direction.speedCeilingKn")?.toFloatOrNull()?.let {
                trackDirectionSpeedCeilingKn = it.coerceIn(2f, 64f)
            }
            props.getProperty("track.direction.minSpacingDp")?.toIntOrNull()?.let {
                trackDirectionMinSpacingDp = it.coerceIn(4, 640)
            }
            props.getProperty("track.direction.maxSpacingDp")?.toIntOrNull()?.let {
                trackDirectionMaxSpacingDp = it.coerceIn(4, 640)
            }
            // Chevron tempering: the knee is a core width in dp, the temper a fraction of the excess
            // above it. Read against the widths it compares with, so the boundary moves with them.
            props.getProperty("track.arrow.scaleKnee")?.toFloatOrNull()?.let {
                trackArrowScaleKneeDp = it.coerceAtLeast(0f)
            }
            props.getProperty("track.arrow.temper")?.toFloatOrNull()?.let {
                trackArrowTemper = it.coerceIn(0f, 1f)
            }

            // ── Track outlines: per-type widths ─────────────────────────────
            // Clamped where a value could break the draw — a width at or below the 1 px floor of the
            // old unit, expressed here in dp, is not drawable, and a width past this bound would
            // swallow the map. An unreadable value leaves the default.
            props.getProperty("track.width.live")?.toFloatOrNull()?.let { trackWidthLiveDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.selected")?.toFloatOrNull()?.let { trackWidthSelectedDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.newest")?.toFloatOrNull()?.let { trackWidthNewestDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.pinned")?.toFloatOrNull()?.let { trackWidthPinnedDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.history")?.toFloatOrNull()?.let { trackWidthHistoryDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.route")?.toFloatOrNull()?.let { trackWidthRouteDp = it.coerceAtLeast(1f / 3f) }
            props.getProperty("track.width.selected.casing")?.toFloatOrNull()?.let {
                trackWidthSelectedCasingDp = it.coerceAtLeast(1f / 3f)
            }

            // ── Speed heatmap ramp ──────────────────────────────────────────
            // The rendering mode is no longer a file key (D3): it is one persisted field in
            // `AppSettings`, so the ramp below is all this block still owns. Read as written: no
            // validation and no warning channel. A family that does not fully parse ends the ramp and
            // the families parsed so far stand; if none parses, the shipped default ramp is kept, so a
            // missing key never leaves the render without a ramp.
            run {
                val families = parseHeatmapFamilies(
                    lookup = { props.getProperty(it) },
                    parseColorHex = { parseColorOrNull(it) }
                )
                if (families.isNotEmpty()) {
                    trackHeatmapRamp = trackHeatmapRamp.copy(families = families)
                }
            }
            // The scale's foot: the file's written value, or the shipped default when the key is absent
            // or unreadable — the same policy the tick table and the ramp itself follow.
            trackHeatmapScaleMinKn = parseHeatmapScaleMinKn(
                lookup = { props.getProperty(it) },
                fallbackKn = trackHeatmapScaleMinKn
            )
            // The legend's tick table, read in the same shape as the families: parsed rows replace the
            // shipped default, while an absent or unreadable table leaves that default in place so the
            // render never loses its scale.
            run {
                val ticks = parseHeatmapScaleTicks(lookup = { props.getProperty(it) })
                if (ticks.isNotEmpty()) {
                    trackHeatmapScaleTicks = ticks
                }
            }
            props.getProperty("track.heatmap.unknownColor")?.let { parseColorOrNull(it) }?.let {
                trackHeatmapRamp = trackHeatmapRamp.copy(unknownArgb = it)
            }

            // ── Marker sort scoring ──
            props.getProperty("marker.sort.typeWeight.Pin")?.toDoubleOrNull()?.let {
                markerSortTypeWeightPin = it.coerceIn(0.0, 1.0)
            }
            props.getProperty("marker.sort.typeWeight.Circle")?.toDoubleOrNull()?.let {
                markerSortTypeWeightCircle = it.coerceIn(0.0, 5.0)
            }
            props.getProperty("marker.sort.typeWeight.Corridor")?.toDoubleOrNull()?.let {
                markerSortTypeWeightCorridor = it.coerceIn(0.0, 10.0)
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
            props.getProperty("ui.button.icon.active.alpha")?.toFloatOrNull()?.let {
                buttonActionIconActiveAlpha = it.coerceIn(0f, 1f)
            }
            props.getProperty("ui.button.icon.inactive.alpha")?.toFloatOrNull()?.let {
                buttonActionIconInactiveAlpha = it.coerceIn(0f, 1f)
            }
            props.getProperty("ui.button.badge.text")?.let { parseColorOrNull(it) }?.let { uiButtonBadgeText = it }
            props.getProperty("ui.button.badge.active.alpha")?.toFloatOrNull()?.let { buttonBadgeActiveAlpha = it.coerceIn(0f, 1f) }
            props.getProperty("ui.button.badge.inactive.alpha")?.toFloatOrNull()?.let { buttonBadgeInactiveAlpha = it.coerceIn(0f, 1f) }
            // ── Map surface & its two control families ───────────────────────
            props.getProperty("ui.map.surface.inactive")?.let { parseColorOrNull(it) }?.let { uiMapSurfaceInactive = it }
            props.getProperty("ui.map.surface.corner.radius")?.toFloatOrNull()?.let { uiMapSurfaceCornerRadius = it }
            props.getProperty("ui.map.surface.padding")?.toFloatOrNull()?.let { uiMapSurfacePadding = it }
            props.getProperty("ui.map.surface.border.color")?.let { parseColorOrNull(it) }?.let { uiMapSurfaceBorderColor = it }
            props.getProperty("ui.map.surface.border.width")?.toFloatOrNull()?.let { uiMapSurfaceBorderWidth = it }
            props.getProperty("ui.map.surface.inactive.content.alpha")?.toFloatOrNull()?.let { uiMapSurfaceInactiveContentAlpha = it.coerceIn(0f, 1f) }
            props.getProperty("ui.map.surface.active.alpha")?.toFloatOrNull()?.let { uiMapSurfaceActiveAlpha = it.coerceIn(0f, 1f) }
            props.getProperty("ui.map.toggle.square")?.toFloatOrNull()?.let { uiMapToggleSquare = it }
            props.getProperty("ui.map.toggle.gutter")?.toFloatOrNull()?.let { uiMapToggleGutter = it }
            props.getProperty("ui.map.toggle.icon.size")?.toFloatOrNull()?.let { uiMapToggleIconSize = it }
            // ── Inspect mode ─────────────────────────────────────────────────
            // The clamp keeps a malformed file from breaking the trigger: a dwell of zero would pick
            // on the first frame of every pan.
            props.getProperty("ui.map.inspect.dwell.ms")?.toLongOrNull()?.let {
                uiMapInspectDwellMs = it.coerceIn(0L, 5_000L)
            }
            props.getProperty("ui.map.overlay.text.color")?.let { parseColorOrNull(it) }?.let { uiMapOverlayTextColor = it }
            props.getProperty("ui.map.overlay.text.weight")?.toIntOrNull()?.let { uiMapOverlayTextWeight = it.coerceIn(100, 900) }
            props.getProperty("ui.map.overlay.text.size")?.toFloatOrNull()?.let { uiMapOverlayTextSize = it }
            props.getProperty("ui.map.overlay.text.line.height")?.toFloatOrNull()?.let { uiMapOverlayTextLineHeight = it }
            props.getProperty("ui.map.overlay.gap")?.toFloatOrNull()?.let { uiMapOverlayGap = it }
            props.getProperty("ui.map.overlay.line.spacing")?.toFloatOrNull()?.let { uiMapOverlayLineSpacing = it }
            // ── Boat tap feedback ────────────────────────────────────────────
            // The ratio is clamped so the beat's peak can never overrun its own duration.
            props.getProperty("map.marker.tap.zoneDiameterDp")?.toFloatOrNull()?.let { mapMarkerTapZoneDiameterDp = it }
            props.getProperty("map.marker.tap.flashDiameterDp")?.toFloatOrNull()?.let { mapMarkerTapFlashDiameterDp = it }
            props.getProperty("map.marker.tap.flashDurationMs")?.toLongOrNull()?.let { mapMarkerTapFlashDurationMs = it }
            props.getProperty("map.marker.tap.flashPeakRatio")?.toFloatOrNull()?.let { mapMarkerTapFlashPeakRatio = it.coerceIn(0f, 1f) }
            // Clamped so the curve can never grow the overlays faster than the ground itself.
            props.getProperty("map.marker.size.zoomExponent")?.toFloatOrNull()?.let { mapMarkerSizeZoomExponent = it.coerceIn(0f, 1f) }
            // Clamped positive: a zero or negative dp breaks the layout rather than merely looking wrong.
            props.getProperty("map.marker.size.boatBaseDp")?.toFloatOrNull()?.let { mapMarkerSizeBoatBaseDp = it.coerceIn(1f, 128f) }
            props.getProperty("map.marker.size.dotBaseDp")?.toFloatOrNull()?.let { mapMarkerSizeDotBaseDp = it.coerceIn(1f, 128f) }

            // ── Semantic colours ──────────────────────────────────────────────────
            props.getProperty("semantic.danger")?.let { parseColorOrNull(it) }?.let { semanticDanger = it }
            props.getProperty("semantic.caution")?.let { parseColorOrNull(it) }?.let { semanticCaution = it }
            props.getProperty("semantic.compliant")?.let { parseColorOrNull(it) }?.let { semanticCompliant = it }
            props.getProperty("semantic.info")?.let { parseColorOrNull(it) }?.let { semanticInfo = it }
            props.getProperty("semantic.inactive")?.let { parseColorOrNull(it) }?.let { semanticInactive = it }

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

            props.getProperty("ui.background")?.let { parseColorOrNull(it) }?.let { uiBackground = it }
            props.getProperty("ui.toast.background")?.let { parseColorOrNull(it) }?.let { uiToastBackground = it }
            props.getProperty("ui.toast.text")?.let { parseColorOrNull(it) }?.let { uiToastText = it }

            props.getProperty("map.coastline.mainland.color")?.let { parseColorOrNull(it) }?.let { mapCoastlineMainlandColor = it }
            props.getProperty("map.coastline.island.color")?.let { parseColorOrNull(it) }?.let { mapCoastlineIslandColor = it }
            // The three widths are floats now, the keys carrying their unit: a dp value need not be
            // whole, and 3.333 dp is exactly what the 10 px of the 3× reference becomes.
            props.getProperty("map.coastline.widthDp")?.toFloatOrNull()?.let { mapCoastlineWidthDp = it }
            props.getProperty("map.coastline.transparencyPct")?.toIntOrNull()?.let { mapCoastlineTransparencyPct = it.coerceIn(0, 100) }
            props.getProperty("map.zone300.boundary.widthDp")?.toFloatOrNull()?.let { mapZone300BoundaryWidthDp = it }
            props.getProperty("map.regulatedZone.outline.widthDp")?.toFloatOrNull()?.let { mapRegulatedZoneOutlineWidthDp = it }

            props.getProperty("map.navigation.arrow.color")?.let { parseColorOrNull(it) }?.let { mapNavigationArrowColor = it }
            // The two widths and the two percentages carry no clamp here: the 1–8 dp, 0.5–4 dp and
            // 0–100 % spans are the sliders' own facts, applied once where the setting is read.
            props.getProperty("map.navigation.arrow.widthDp")?.toFloatOrNull()?.let { mapNavigationArrowWidthDp = it }
            props.getProperty("map.navigation.arrow.transparencyPct")?.toIntOrNull()?.let { mapNavigationArrowTransparencyPct = it }
            props.getProperty("map.navigation.arrow.followSpeedColour")?.toBooleanStrictOrNull()?.let {
                mapNavigationArrowFollowSpeedColour = it
            }
            props.getProperty("map.navigation.line.color")?.let { parseColorOrNull(it) }?.let { mapNavigationLineColor = it }
            props.getProperty("map.navigation.line.widthDp")?.toFloatOrNull()?.let { mapNavigationLineWidthDp = it }
            props.getProperty("map.navigation.line.transparencyPct")?.toIntOrNull()?.let { mapNavigationLineTransparencyPct = it }

            // ── Map look-ahead offset (dynamic speed-based center shift) ──
            props.getProperty("map.offset.lookahead.maxspeedKn")?.toDoubleOrNull()?.let {
                mapOffsetLookaheadMaxSpeedKn = it.coerceIn(1.0, 50.0)
            }
            props.getProperty("map.offset.lookahead.boatFromBottomPct")?.toIntOrNull()?.let {
                mapOffsetLookaheadBoatFromBottomPct = it.coerceIn(5, 50)
            }
            props.getProperty("map.offset.gps")?.toBooleanStrictOrNull()?.let {
                mapOffsetGps = it
            }
            props.getProperty("map.offset.demo")?.toBooleanStrictOrNull()?.let {
                mapOffsetDemo = it
            }
            props.getProperty("ui.landscape.panel.widthScale")?.toFloatOrNull()?.let {
                uiLandscapePanelWidthScale = it.coerceIn(0.5f, 3.0f)
            }

            props.getProperty("map.depth.nodata.color")?.let { parseColorOrNull(it) }?.let { mapDepthNodataColor = it }

            props.getProperty("overlay.lowDepth.color")?.let { parseColorOrNull(it) }?.let { overlayLowDepthColor = it }

            props.getProperty("status.gps.demo")?.let { parseColorOrNull(it) }?.let { statusGpsDemo = it }
            props.getProperty("status.gps.acquiring")?.let { parseColorOrNull(it) }?.let { statusGpsAcquiring = it }
            props.getProperty("status.gps.healthy")?.let { parseColorOrNull(it) }?.let { statusGpsHealthy = it }
            props.getProperty("status.gps.idle")?.let { parseColorOrNull(it) }?.let { statusGpsIdle = it }
            props.getProperty("status.gps.stale")?.let { parseColorOrNull(it) }?.let { statusGpsStale = it }
            props.getProperty("status.gps.estimating")?.let { parseColorOrNull(it) }?.let { statusGpsEstimating = it }

            props.getProperty("status.tracking.healthy")?.let { parseColorOrNull(it) }?.let { statusTrackingHealthy = it }
            props.getProperty("status.tracking.idle")?.let { parseColorOrNull(it) }?.let { statusTrackingIdle = it }
            props.getProperty("status.tracking.off")?.let { parseColorOrNull(it) }?.let { statusTrackingOff = it }
            props.getProperty("status.tracking.dot.recording")?.let { parseColorOrNull(it) }?.let { statusTrackingDotRecording = it }
            props.getProperty("status.tracking.dot.idle")?.let { parseColorOrNull(it) }?.let { statusTrackingDotIdle = it }

            props.getProperty("status.earthWater.water")?.let { parseColorOrNull(it) }?.let { statusEarthWaterWater = it }
            props.getProperty("status.earthWater.land")?.let { parseColorOrNull(it) }?.let { statusEarthWaterLand = it }
            props.getProperty("status.earthWater.inactive")?.let { parseColorOrNull(it) }?.let { statusEarthWaterInactive = it }

            props.getProperty("status.lock.off")?.let { parseColorOrNull(it) }?.let { statusLockOff = it }
            props.getProperty("status.lock.on")?.let { parseColorOrNull(it) }?.let { statusLockOn = it }

            // ── Dashboard depth readout tints ─────────────────────────────────
            props.getProperty("ui.dashboard.readout.collision")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutCollision = it }
            props.getProperty("ui.dashboard.readout.shallow")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutShallow = it }
            props.getProperty("ui.dashboard.readout.deep")?.let { parseColorOrNull(it) }?.let { uiDashboardReadoutDeep = it }

            // ── Shared UI colours ─────────────────────────────────────────────
            props.getProperty("ui.text.primary")?.let { parseColorOrNull(it) }?.let { uiTextPrimary = it }
            props.getProperty("ui.text.muted")?.let { parseColorOrNull(it) }?.let { uiTextMuted = it }
            props.getProperty("ui.text.secondary")?.let { parseColorOrNull(it) }?.let { uiTextSecondary = it }
            props.getProperty("ui.accent")?.let { parseColorOrNull(it) }?.let { uiAccent = it }
            // ── The route line and pin (maro.properties, not the palette) ───────
            props.getProperty("route.line.color")?.let { parseColorOrNull(it) }
                ?.let { routeLineColor = it }
            props.getProperty("route.line.transparencyPct")?.toIntOrNull()
                ?.let { routeLineTransparencyPct = it.coerceIn(0, 100) }
            props.getProperty("route.line.widthDp")?.toFloatOrNull()
                ?.let { routeLineWidthDp = it.coerceIn(1f / 3f, 24f) }
            props.getProperty("route.pin.color")?.let { parseColorOrNull(it) }
                ?.let { routePinColor = it }
            props.getProperty("route.pin.ringWidthDp")?.toFloatOrNull()
                ?.let { routePinRingWidthDp = it.coerceIn(0f, 12f) }
            // ── The route's ask policy, its crosshair, its refresh gate and its ladder ───────
            // Read here rather than beside the pace above: every one of them is a drawing or
            // interaction value rather than a behaviour the spatial side reads.
            props.getProperty("route.ask.minTargetMoveM")?.toDoubleOrNull()
                ?.let { routeAskMinTargetMoveM = it.coerceIn(1.0, 500.0) }
            props.getProperty("route.ask.settleMs")?.toLongOrNull()
                ?.let { routeAskSettleMs = it.coerceIn(0L, 5_000L) }
            props.getProperty("route.target.color")?.let { parseColorOrNull(it) }
                ?.let { routeTargetColor = it }
            props.getProperty("route.target.widthDp")?.toFloatOrNull()
                ?.let { routeTargetWidthDp = it.coerceIn(1f / 3f, 12f) }
            props.getProperty("route.target.pulseMs")?.toIntOrNull()
                ?.let { routeTargetPulseMs = it.coerceIn(100, 5_000) }
            props.getProperty("route.refresh.intervalSec")?.toIntOrNull()
                ?.let { routeRefreshIntervalSec = it.coerceIn(1, 3_600) }
            props.getProperty("route.refresh.offRouteM")?.toDoubleOrNull()
                ?.let { routeRefreshOffRouteM = it.coerceIn(10.0, 10_000.0) }
            props.getProperty("route.ladder.oldest.nb")?.toIntOrNull()
                ?.let { routeLadderOldestNb = it.coerceIn(0, 10) }
            props.getProperty("route.ladder.latest.nb")?.toIntOrNull()
                ?.let { routeLadderLatestNb = it.coerceIn(0, 10) }
            // ── The avoid engine's keys (the four stage-1 values, the depth gate, and stage 2's band margin) ──
            props.getProperty("route.avoid.obstacleMarginM")?.toDoubleOrNull()?.let {
                routeAvoidObstacleMarginM = it.coerceIn(1.0, 200.0)
            }
            props.getProperty("route.avoid.gridCellM")?.toDoubleOrNull()?.let {
                routeAvoidGridCellM = it.coerceIn(10.0, 500.0)
            }
            props.getProperty("route.avoid.corridorReachM")?.toDoubleOrNull()?.let {
                routeAvoidCorridorReachM = it.coerceIn(100.0, 20_000.0)
            }
            props.getProperty("route.avoid.zone300MarginM")?.toDoubleOrNull()?.let {
                routeAvoidZone300MarginM = it.coerceIn(0.0, 500.0)
            }
            props.getProperty("route.avoid.minDepthM")?.toDoubleOrNull()?.let {
                routeAvoidMinDepthM = it.coerceIn(0.5, 50.0)
            }
            props.getProperty("route.avoid.depthGate.enabled")?.toBooleanStrictOrNull()?.let {
                routeAvoidDepthGateEnabled = it
            }
            props.getProperty("route.avoid.softCostAversion")?.toDoubleOrNull()?.let {
                routeAvoidSoftCostAversion = it.coerceIn(1.0, 5.0)
            }
            props.getProperty("route.avoid.zone300.enabled")?.toBooleanStrictOrNull()?.let {
                routeAvoidZone300Enabled = it
            }
            props.getProperty("ui.value.text")?.let { parseColorOrNull(it) }?.let { uiValueText = it }
            props.getProperty("ui.text.scrim")?.let { parseColorOrNull(it) }?.let { uiTextScrim = it }
            props.getProperty("ui.card.background")?.let { parseColorOrNull(it) }?.let { uiCardBackground = it }
            props.getProperty("ui.divider.color")?.let { parseColorOrNull(it) }?.let { uiDividerColor = it }
            props.getProperty("ui.switch.track.inactive")?.let { parseColorOrNull(it) }?.let { uiSwitchTrackInactive = it }
            props.getProperty("ui.input.border")?.let { parseColorOrNull(it) }?.let { uiInputBorder = it }
            props.getProperty("ui.footer.text")?.let { parseColorOrNull(it) }?.let { uiFooterText = it }
            props.getProperty("ui.danger")?.let { parseColorOrNull(it) }?.let { uiDanger = it }

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
            props.getProperty("map.marker.tap.flash.color")?.let { parseColorOrNull(it) }?.let { mapMarkerTapFlashColor = it }
            props.getProperty("map.marker.tap.flash.alpha")?.toFloatOrNull()?.let { mapMarkerTapFlashAlpha = it.coerceIn(0f, 1f) }

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

            // ── Isobath stroke width bonuses (dp) ─────────────────────────
            // Clamped to the old -4 to +6 px span, expressed in dp so the boundary moves with the unit.
            props.getProperty("map.isobar.litto3d.widthDp")?.toFloatOrNull()?.let {
                isobarWidthBonuses[DepthSource.LITTO3D] = it.coerceIn(-4f / 3f, 2f)
            }
            props.getProperty("map.isobar.emodnet.widthDp")?.toFloatOrNull()?.let {
                isobarWidthBonuses[DepthSource.EMODNET] = it.coerceIn(-4f / 3f, 2f)
            }

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

            // ── UI tokens from ui.properties ─────────────────────────────────────
            // dp/sp values are stored as raw numbers (suffix stripped); composables
            // apply the `.dp`/`.sp` extension at the call site.
            fun dp(key: String, fallback: Float): Float =
                props.getProperty(key)?.removeSuffix("dp")?.trim()?.toFloatOrNull() ?: fallback
            fun sp(key: String, fallback: Float): Float =
                props.getProperty(key)?.removeSuffix("sp")?.trim()?.toFloatOrNull() ?: fallback

            // Spacing
            uiSpacingCardGap = dp("ui.spacing.card.gap", uiSpacingCardGap)
            uiSpacingSectionGap = dp("ui.spacing.section.gap", uiSpacingSectionGap)
            uiSpacingHeaderBottom = dp("ui.spacing.header.bottom", uiSpacingHeaderBottom)
            uiSpacingGroupedRowGap = dp("ui.spacing.grouped.row.gap", uiSpacingGroupedRowGap)
            uiSpacingGroupedAfterExpander = dp("ui.spacing.grouped.after-expander", uiSpacingGroupedAfterExpander)
            uiSpacingLabelControl = dp("ui.spacing.label.control", uiSpacingLabelControl)
            uiSpacingExpanderToContent = dp("ui.spacing.expander.to-content", uiSpacingExpanderToContent)

            // Padding
            uiPaddingCardVertical = dp("ui.padding.card.vertical", uiPaddingCardVertical)
            uiPaddingCardHorizontal = dp("ui.padding.card.horizontal", uiPaddingCardHorizontal)
            uiPaddingToggleVertical = dp("ui.padding.toggle.vertical", uiPaddingToggleVertical)
            uiPaddingContentComfortable = dp("ui.padding.content.comfortable", uiPaddingContentComfortable)
            uiPaddingExpanderVertical = dp("ui.padding.expander.vertical", uiPaddingExpanderVertical)
            uiPaddingHeaderVertical = dp("ui.padding.header.vertical", uiPaddingHeaderVertical)

            // Dialog
            uiScrimAlpha = props.getProperty("ui.scrim.alpha")?.trim()?.toFloatOrNull() ?: uiScrimAlpha

            // Corner radius
            uiRadiusCard = dp("ui.radius.card", uiRadiusCard)
            uiRadiusExpander = dp("ui.radius.expander", uiRadiusExpander)

            // Font sizes
            uiFontSectionSize = sp("ui.font.section.size", uiFontSectionSize)
            uiFontSubsectionSize = sp("ui.font.subsection.size", uiFontSubsectionSize)
            uiFontTabSize = sp("ui.font.tab.size", uiFontTabSize)
            uiFontToggleSize = sp("ui.font.toggle.size", uiFontToggleSize)
            uiFontDescSize = sp("ui.font.desc.size", uiFontDescSize)
            uiFontCommentSize = sp("ui.font.comment.size", uiFontCommentSize)
            uiFontValueSize = sp("ui.font.value.size", uiFontValueSize)
            uiFontRangeSize = sp("ui.font.range.size", uiFontRangeSize)

            // Nested-card colours
            props.getProperty("ui.nested.card.bg")?.let { parseColorOrNull(it) }?.let { uiNestedCardBg = it }
            props.getProperty("ui.nested.card.border")?.let { parseColorOrNull(it) }?.let { uiNestedCardBorder = it }

            // Divider
            uiDividerHeight = dp("ui.divider.height", uiDividerHeight)
            uiDividerGap = dp("ui.divider.gap", uiDividerGap)

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
