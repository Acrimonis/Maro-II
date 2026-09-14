package ykws.android.maro.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import org.osmdroid.views.MapView
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.MapRenderFocus
import ykws.android.maro.data.model.TrackSelectionPolicy
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.config.HeatmapRamp
import ykws.android.maro.config.TrackHeatmapMode
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackPoint

/**
 * History/pinned track overlay incremental-diff (extracted from MapScreen).
 * Highlighted-track id is a read-only key (cross-cutting in MapScreen); never relocated.
 */
@Composable
internal fun MapTrackOverlayHistoryDiff(
    mapView: MapView?,
    showSettings: Boolean,
    highlightedTrackId: String?,
    /**
     * Runtime rendering mode for the selected track. Read once before composition, so an edited
     * `maro.properties` needs a relaunch — only this session value belongs in the key list.
     */
    heatmapMode: TrackHeatmapMode,
    allTrackSummaries: List<ykws.android.maro.data.track.TrackSummary>,
    focus: MapRenderFocus,
    appSettings: AppSettings,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel
) {
    // ── Track overlay: incremental diff for history tracks with fading opacity ──
    // Track the set of currently-rendered track IDs to avoid full teardown+rebuild.
    val renderedTrackIds = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(mapView, showSettings, appSettings.tracksVisible, appSettings.tracksDirectionVisible, appSettings.trackingRenderNb,
        appSettings.trackingColorPastFrom, appSettings.trackingColorPastTo,
        appSettings.trackingTransparencyNewest, appSettings.trackingTransparencyOldest,
        appSettings.trackingColorPinnedFrom, appSettings.trackingColorPinnedTo,
        appSettings.trackingTransparencyPinnedNewest, appSettings.trackingTransparencyPinnedOldest,
        appSettings.trackMapFilter, appSettings.trackFilterLinked, allTrackSummaries, highlightedTrackId,
        heatmapMode,
        appSettings.trackDirectionDensity, appSettings.trackDirectionMinSpacingDp, appSettings.trackDirectionMaxSpacingDp,
        appSettings.trackDirectionSpeedFloorKn, appSettings.trackDirectionSpeedCeilingKn) {
        val mv = mapView ?: return@LaunchedEffect

        // Direction-arrow spacing provider: uniform (px) or speed-linear (dp → px).
        val densityScale = mv.context.resources.displayMetrics.density
        val minSpacingPx = appSettings.trackDirectionMinSpacingDp * densityScale
        val maxSpacingPx = appSettings.trackDirectionMaxSpacingDp * densityScale
        val directionSpacingProvider: (Float) -> Float = { speedKn ->
            when (appSettings.trackDirectionDensity) {
                TrackDirectionDensity.UNIFORM -> DIRECTION_ARROW_SPACING_DP * densityScale
                TrackDirectionDensity.SPEED -> spacingPxForSpeed(
                    speedKn,
                    appSettings.trackDirectionSpeedFloorKn,
                    appSettings.trackDirectionSpeedCeilingKn,
                    minSpacingPx,
                    maxSpacingPx
                )
            }
        }

        // Selection is a pure projection: the shared policy owns eligibility + ranking + cap,
        // including the focus override (highlighted / session-boosted ids). Source = UNFILTERED
        // summaries so the map stays independent of the list filter when unlinked. The shell only
        // executes the selection; layer toggles are still applied here, never by the policy.
        val midnightMs = ykws.android.maro.data.model.todayMidnightMs()
        // The live recording line is drawn by the dedicated live effects and is never filterable, so it
        // is excluded here: a resumed recording must not also render as a stale stored-track overlay.
        val storedSummaries = allTrackSummaries.filter { !it.isLive }
        val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
        focus.highlight(highlightedTrackId)
        val historyList = if (appSettings.tracksVisible) {
            TrackSelectionPolicy().select(
                items = storedSummaries,
                filter = appSettings.trackMapFilter,
                cap = nbToRender,
                focus = focus,
                todayMidnightMs = midnightMs
            )
        } else emptyList()
        val desiredIds = historyList.map { it.id }.toSet()

        // Remove all existing track history overlays + direction arrows — rebuild from scratch
        val toRemove = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_hist_") == true ||
            (overlay as? TrackDirectionOverlay)?.title?.startsWith("track_arrow_") == true
        }
        mv.overlays.removeAll(toRemove)

        val sortedDesired = historyList

        val total = nbToRender

        val historyOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in sortedDesired.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            if (summary.id == highlightedTrackId) {
                // One dispatcher, two paths: the selected track's strokes and chevrons come from
                // selectedTrackRendering, so the history and pinned loops cannot drift apart.
                val rendering = selectedTrackRendering(
                    points = track.trackPoints,
                    title = "track_hist_${summary.id}",
                    mode = heatmapMode,
                    ramp = AppConfig.trackHeatmapRamp,
                    carryMaxSec = AppConfig.trackHeatmapCarryMaxSec
                )
                trackOverlays.addAll(rendering.overlays)
                if (appSettings.tracksDirectionVisible) {
                    trackOverlays.add(
                        rendering.directionOverlay(track.trackPoints, directionSpacingProvider, "track_arrow_${summary.id}")
                    )
                }
            } else {
                val appearance = computeTrackPolylineAppearance(
                    index = index,
                    total = total,
                    transparencyNewest = appSettings.trackingTransparencyNewest,
                    transparencyOldest = appSettings.trackingTransparencyOldest,
                    colorFrom = appSettings.trackingColorPastFrom,
                    colorTo = appSettings.trackingColorPastTo,
                    strokeWidth = if (index == 0) 8f else 6f
                )
                // Solid segments between gaps, dashed for gap segments (split in buildSegmentOverlays).
                trackOverlays.addAll(
                    buildSegmentOverlays(track.trackPoints, appearance, "track_hist_${summary.id}")
                )
                if (appSettings.tracksDirectionVisible) {
                    TrackDirectionOverlay(
                        points = track.trackPoints,
                        appearances = listOf(appearance),
                        spacingPx = directionSpacingProvider
                    ).apply { title = "track_arrow_${summary.id}" }.also { trackOverlays.add(it) }
                }
            }

            historyOverlays.add(trackOverlays)
        }
        historyOverlays.reverse()
        for (trackOverlays in historyOverlays) {
            mv.overlays.addAll(trackOverlays)
        }

        // ── Pinned tracks: always render all, separate colors/opacity ──
        val toRemovePinned = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_pin_") == true
        }
        mv.overlays.removeAll(toRemovePinned)

        val pinnedSummaries = if (appSettings.tracksVisible) {
            storedSummaries
                .filter { it.pinned && (it.id == highlightedTrackId || it.matchesFilter(appSettings.trackMapFilter, midnightMs)) }
                .sortedByDescending { it.startTimeMs }
        } else emptyList()

        val pinnedTotal = pinnedSummaries.size
        val pinnedOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in pinnedSummaries.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            if (summary.id == highlightedTrackId) {
                val rendering = selectedTrackRendering(
                    points = track.trackPoints,
                    title = "track_pin_${summary.id}",
                    mode = heatmapMode,
                    ramp = AppConfig.trackHeatmapRamp,
                    carryMaxSec = AppConfig.trackHeatmapCarryMaxSec
                )
                trackOverlays.addAll(rendering.overlays)
                if (appSettings.tracksDirectionVisible) {
                    trackOverlays.add(
                        rendering.directionOverlay(track.trackPoints, directionSpacingProvider, "track_arrow_${summary.id}")
                    )
                }
            } else {
                val appearance = computeTrackPolylineAppearance(
                    index = index,
                    total = pinnedTotal,
                    transparencyNewest = appSettings.trackingTransparencyPinnedNewest,
                    transparencyOldest = appSettings.trackingTransparencyPinnedOldest,
                    colorFrom = appSettings.trackingColorPinnedFrom,
                    colorTo = appSettings.trackingColorPinnedTo,
                    strokeWidth = 6f
                )
                // Solid segments between gaps, dashed for gap segments (split in buildSegmentOverlays).
                trackOverlays.addAll(
                    buildSegmentOverlays(track.trackPoints, appearance, "track_pin_${summary.id}")
                )
                if (appSettings.tracksDirectionVisible) {
                    TrackDirectionOverlay(
                        points = track.trackPoints,
                        appearances = listOf(appearance),
                        spacingPx = directionSpacingProvider
                    ).apply { title = "track_arrow_${summary.id}" }.also { trackOverlays.add(it) }
                }
            }

            pinnedOverlays.add(trackOverlays)
        }
        pinnedOverlays.reverse()
        for (trackOverlays in pinnedOverlays) {
            mv.overlays.addAll(trackOverlays)
        }

        // Ensure active track stays on top of pinned (z-order: history → pinned → active)
        val activePolyline = mv.overlays.firstOrNull {
            (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
        }
        if (activePolyline != null) {
            mv.overlays.remove(activePolyline)
            mv.overlays.add(activePolyline)
        }

        // Move highlighted track above active (z-order: ... → active → highlighted)
        if (highlightedTrackId != null) {
            val highlightedOverlays = mv.overlays.filter { overlay ->
                val polyTitle = (overlay as? org.osmdroid.views.overlay.Polyline)?.title
                val arrowTitle = (overlay as? TrackDirectionOverlay)?.title
                polyTitle == "track_hist_$highlightedTrackId" ||
                polyTitle == "track_pin_$highlightedTrackId" ||
                arrowTitle == "track_arrow_$highlightedTrackId"
            }
            mv.overlays.removeAll(highlightedOverlays)
            mv.overlays.addAll(highlightedOverlays)
        }

        renderedTrackIds.value = desiredIds
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }
}

/** The selected track's dark casing — the fixed 16f stroke that carries the selection cue. */
private val SELECTED_CASING = TrackPolylineAppearance(0xCC000000.toInt(), 16f)

/**
 * Stroke width (px) of the selected track's core — declared here once and read by both paths: the
 * gold core in highlight mode and every heatmap band.
 */
internal const val SELECTED_CORE_STROKE_WIDTH = 8f

/** The selected track's gold core — the default highlight mode's fill, unchanged. */
private val SELECTED_GOLD_CORE = TrackPolylineAppearance(0xFFFFD700.toInt(), SELECTED_CORE_STROKE_WIDTH)

/**
 * A selected track's strokes, beside the inputs its direction chevrons need: the appearance list in
 * gold mode, or a per-anchor colour resolver plus the casing and metrics that go with it in heatmap
 * mode (null resolver → today's per-appearance iteration, so no other track changes behaviour).
 */
private data class SelectedTrackRendering(
    val overlays: List<org.osmdroid.views.overlay.Overlay>,
    val arrowAppearances: List<TrackPolylineAppearance>,
    val arrowColorResolver: ((ArrowAnchor) -> TrackPolylineAppearance)? = null
)

/**
 * Selected-track dispatcher: picks the path by [mode] and returns that path's strokes and chevron
 * inputs. Both the history loop and the pinned loop call this one entry point.
 */
private fun selectedTrackRendering(
    points: List<TrackPoint>,
    title: String,
    mode: TrackHeatmapMode,
    ramp: HeatmapRamp,
    carryMaxSec: Int
): SelectedTrackRendering = when (mode) {
    TrackHeatmapMode.HIGHLIGHT -> highlightCorePath(points, title)
    TrackHeatmapMode.SPEED -> heatmapCorePath(points, title, ramp, carryMaxSec)
}

/**
 * Highlight path — today's rendering, unchanged: a 16f dark casing under an 8f gold core, one title
 * per track, and chevrons iterating that appearance list the way they always have.
 */
private fun highlightCorePath(points: List<TrackPoint>, title: String): SelectedTrackRendering {
    val appearances = listOf(SELECTED_CASING, SELECTED_GOLD_CORE)
    return SelectedTrackRendering(
        overlays = appearances.flatMap { buildSegmentOverlays(points, it, title) },
        arrowAppearances = appearances
    )
}

/**
 * Heatmap path (D1): the casing marks the selection and the banded core carries the speed, so no
 * gold is left to meet the ramp. Every band and every chevron keeps the track's single [title] —
 * the z-lift matches exact titles while teardown matches prefixes, so a per-band suffix would drop
 * the selected track below the others at the end of the effect (A6).
 */
private fun heatmapCorePath(
    points: List<TrackPoint>,
    title: String,
    ramp: HeatmapRamp,
    carryMaxSec: Int
): SelectedTrackRendering {
    val speeds = resolveSpeeds(points, carryMaxSec)
    val bands = bandedAppearances(points, speeds, ramp)
    // The band table is the pure mapping [bandTable] owns; its shared-boundary rule is unit-tested.
    val bandByIndex = bandTable(points.size, bands)
    val metrics = bands.firstOrNull()?.appearance
        ?: TrackPolylineAppearance(ramp.unknownArgb, SELECTED_CORE_STROKE_WIDTH)
    val resolver: (ArrowAnchor) -> TrackPolylineAppearance = { anchor ->
        bandByIndex.getOrNull(anchor.segmentIndex)?.appearance ?: metrics
    }
    return SelectedTrackRendering(
        overlays = buildSegmentOverlays(points, SELECTED_CASING, title) +
            bands.flatMap { buildBandSegmentOverlays(points, it, title) },
        arrowAppearances = listOf(metrics),
        arrowColorResolver = resolver
    )
}

/** The selected track's chevrons: the appearance iteration in gold mode, the resolver in heatmap. */
private fun SelectedTrackRendering.directionOverlay(
    points: List<TrackPoint>,
    spacingPx: (Float) -> Float,
    title: String
): org.osmdroid.views.overlay.Overlay = TrackDirectionOverlay(
    points = points,
    appearances = arrowAppearances,
    spacingPx = spacingPx,
    colorResolver = arrowColorResolver,
    chevronMetrics = arrowAppearances.firstOrNull().takeIf { arrowColorResolver != null },
    casingAppearance = SELECTED_CASING.takeIf { arrowColorResolver != null }
).apply { this.title = title }

/**
 * Live-recording overlay effects (extracted from MapScreen): active trace polyline
 * create/remove, incremental point appending, and trailing dead-reckon segment.
 */
@Composable
internal fun MapTrackOverlayLiveEffects(
    mapView: MapView?,
    viewModel: NavigationViewModel,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    trackRecorderState: ykws.android.maro.data.track.TrackRecorderUiState,
    appSettings: AppSettings
) {
    // ── Active recording trace: incremental polyline via newPoint stream ────
    // Polyline lifecycle (create/remove) is keyed on the recorder state itself. Reading the state
    // from inside a snapshotFlow would observe the frozen parameter value (this seam receives a plain
    // TrackRecorderUiState), so the OFF → ON transition would never be seen and the live line would
    // never be created.
    LaunchedEffect(mapView, trackRecorderState.state, appSettings.trackingColorActive) {
        val mv = mapView ?: return@LaunchedEffect
        if (trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON) {
            val existing = mv.overlays.firstOrNull {
                (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
            }
            if (existing == null) {
                val polyline = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                }
                mv.overlays.add(polyline)
                OverlayZOrder.reorder(mv)
                mv.invalidate()
            }
        } else {
            val removed = mv.overlays.removeAll {
                (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
            }
            if (removed) mv.invalidate()
        }
    }

    // ── Incremental point appending: observe newPoint stream for live polyline ─┐
    // Keyed on recorder state so a stop→restart cycle re-obtains the new SharedFlow.
    // GAP markers split the live polyline: solid for normal segments, dashed for gaps.
    LaunchedEffect(mapView, trackRecorderState.state) {
        val mv = mapView ?: return@LaunchedEffect
        val stream = trackViewModel.newPointStream ?: return@LaunchedEffect
        stream.collect { point ->
            if (point.type == ykws.android.maro.data.track.PointType.GAP) {
                // Find the last active solid polyline and finalize it
                val lastSolid = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline
                val lastPt = lastSolid?.actualPoints?.lastOrNull()
                if (lastPt != null) {
                    val gapLine = org.osmdroid.views.overlay.Polyline().apply {
                        title = "track_recording"
                        outlinePaint.color = appSettings.trackingColorActive
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                        isVisible = true
                        setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(point.lat, point.lon)))
                    }
                    mv.overlays.add(gapLine)
                }
                val resumedLine = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                    addPoint(org.osmdroid.util.GeoPoint(point.lat, point.lon))
                }
                mv.overlays.add(resumedLine)
                OverlayZOrder.reorder(mv)
                mv.invalidate()
            } else {
                val existing = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline
                // Self-healing: create the solid line on demand instead of dropping the point when the
                // creation effect has not run yet (state/overlay ordering is not guaranteed).
                val polyline = existing ?: org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                }.also {
                    mv.overlays.add(it)
                    OverlayZOrder.reorder(mv)
                }
                polyline.addPoint(org.osmdroid.util.GeoPoint(point.lat, point.lon))
                mv.invalidate()
            }
        }
    }

    // ── Trailing polyline: interpolated segment from last accepted point ──
    // to the dead-reckoned display position, updated at 20 Hz. Display only —
    // never recorded. Semi-transparent solid (not dashed) to distinguish from
    // GAP markers. Cleaned up on recorder OFF.
    LaunchedEffect(mapView, trackRecorderState.state) {
        val mv = mapView ?: return@LaunchedEffect
        if (trackRecorderState.state != ykws.android.maro.data.track.TrackRecorderState.ON) {
            mv.overlays.removeAll { (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_trailing" }
            mv.invalidate()
            return@LaunchedEffect
        }
        snapshotFlow { viewModel.displayPosition.value }
            .collect { displayPos ->
                // Remove previous trailing polyline
                mv.overlays.removeAll { (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_trailing" }
                if (displayPos == null) return@collect
                // Find the last accepted point from the recording polyline. Self-healing: create the
                // solid line on demand so a not-yet-created (or just-removed) line cannot suppress the
                // trailing segment.
                val recordingLine = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline
                    ?: org.osmdroid.views.overlay.Polyline().apply {
                        title = "track_recording"
                        outlinePaint.color = appSettings.trackingColorActive
                        outlinePaint.strokeWidth = 10f
                        isVisible = true
                    }.also {
                        mv.overlays.add(it)
                        OverlayZOrder.reorder(mv)
                    }
                val lastPt = recordingLine.actualPoints?.lastOrNull() ?: return@collect
                // Draw trailing segment: last accepted point → display position
                val trailing = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_trailing"
                    outlinePaint.color = (appSettings.trackingColorActive and 0x00FFFFFF) or (0x66000000.toInt())  // ~40% alpha
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                    setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(displayPos.latitude, displayPos.longitude)))
                }
                mv.overlays.add(trailing)
                OverlayZOrder.reorder(mv)
                mv.invalidate()
            }
    }
}
