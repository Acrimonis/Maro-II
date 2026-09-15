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
import ykws.android.maro.config.TrackRenderMode
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
     * The stored render mode (D1, D3): it decides the path for every stored track, so the menu
     * switch is its only writer and this effect only reads it.
     */
    renderMode: TrackRenderMode,
    /**
     * The drawer eye's own value (D10): null follows [renderMode], true bands the selected track,
     * false draws it in the default colours without arrows. Session-only and never persisted.
     */
    eyeOverride: Boolean?,
    allTrackSummaries: List<ykws.android.maro.data.track.TrackSummary>,
    focus: MapRenderFocus,
    appSettings: AppSettings,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel
) {
    // ── Track overlay: incremental diff for history tracks with fading opacity ──
    // Track the set of currently-rendered track IDs to avoid full teardown+rebuild.
    val renderedTrackIds = remember { mutableStateOf(setOf<String>()) }

    // Mode-aware rebuild keys (§3b): values the current mode does not read must not trigger a rebuild,
    // so a default-colour edit cannot rebuild a heat-mapped map. The count and both transparency
    // ranges stay in every mode, because D8 makes the fade live in all three.
    val rebuildKeys: List<Any?> = buildList {
        add(mapView)
        add(showSettings)
        add(highlightedTrackId)
        add(renderMode)
        add(eyeOverride)
        add(appSettings.tracksVisible)
        add(appSettings.trackingRenderNb)
        add(appSettings.trackMapFilter)
        add(appSettings.trackFilterLinked)
        add(allTrackSummaries)
        add(appSettings.trackingTransparencyNewest)
        add(appSettings.trackingTransparencyOldest)
        add(appSettings.trackingTransparencyPinnedNewest)
        add(appSettings.trackingTransparencyPinnedOldest)
        // The per-type widths are read on every path in every mode, so the five join the list
        // unconditionally rather than behind a mode test.
        add(AppConfig.trackWidthLive)
        add(AppConfig.trackWidthSelected)
        add(AppConfig.trackWidthNewest)
        add(AppConfig.trackWidthPinned)
        add(AppConfig.trackWidthHistory)
        if (renderMode != TrackRenderMode.HEATMAP) {
            // Simple and Dir & Speed paint the stored default colours.
            add(appSettings.trackingColorPastFrom)
            add(appSettings.trackingColorPastTo)
            add(appSettings.trackingColorPinnedFrom)
            add(appSettings.trackingColorPinnedTo)
        } else {
            // Colours paints from the ramp instead.
            add(AppConfig.trackHeatmapRamp)
        }
        if (renderMode != TrackRenderMode.SIMPLE) {
            // Arrows are drawn in Dir & Speed and in Colours, never in Simple.
            add(appSettings.trackDirectionDensity)
            add(appSettings.trackDirectionMinSpacingDp)
            add(appSettings.trackDirectionMaxSpacingDp)
            add(appSettings.trackDirectionSpeedFloorKn)
            add(appSettings.trackDirectionSpeedCeilingKn)
        }
    }

    LaunchedEffect(*rebuildKeys.toTypedArray()) {
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

        val total = nbToRender

        val historyOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        // The ranking above puts the focused track first, so position 0 is whoever the user selected
        // rather than the newest track: the newest width is chosen by recency, not by loop position.
        val newestId = newestTrackId(historyList)
        for ((index, summary) in historyList.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val selected = summary.id == highlightedTrackId
            val width = storedTrackWidth(
                selected = selected,
                pinned = false,
                newest = summary.id == newestId
            )
            // One dispatcher, three paths: the track's strokes and chevron inputs come from
            // storedTrackRendering, so the history and pinned loops cannot drift apart.
            val rendering = storedTrackRendering(
                points = track.trackPoints,
                title = "track_hist_${summary.id}",
                plan = trackRenderPlan(renderMode, selected, eyeOverride),
                ramp = AppConfig.trackHeatmapRamp,
                strokeWidth = width,
                fade = trackFadeAlpha(
                    index = index,
                    total = total,
                    transparencyNewest = appSettings.trackingTransparencyNewest,
                    transparencyOldest = appSettings.trackingTransparencyOldest
                ),
                plainAppearance = {
                    computeTrackPolylineAppearance(
                        index = index,
                        total = total,
                        transparencyNewest = appSettings.trackingTransparencyNewest,
                        transparencyOldest = appSettings.trackingTransparencyOldest,
                        colorFrom = appSettings.trackingColorPastFrom,
                        colorTo = appSettings.trackingColorPastTo,
                        strokeWidth = width
                    )
                }
            )
            trackOverlays.addAll(rendering.overlays)
            if (rendering.drawArrows) {
                trackOverlays.add(
                    rendering.directionOverlay(track.trackPoints, directionSpacingProvider, "track_arrow_${summary.id}")
                )
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

            val selected = summary.id == highlightedTrackId
            val width = storedTrackWidth(selected = selected, pinned = true, newest = false)
            val rendering = storedTrackRendering(
                points = track.trackPoints,
                title = "track_pin_${summary.id}",
                plan = trackRenderPlan(renderMode, selected, eyeOverride),
                ramp = AppConfig.trackHeatmapRamp,
                strokeWidth = width,
                // D8: a pinned track fades across its own range, exactly as it does today.
                fade = trackFadeAlpha(
                    index = index,
                    total = pinnedTotal,
                    transparencyNewest = appSettings.trackingTransparencyPinnedNewest,
                    transparencyOldest = appSettings.trackingTransparencyPinnedOldest
                ),
                plainAppearance = {
                    computeTrackPolylineAppearance(
                        index = index,
                        total = pinnedTotal,
                        transparencyNewest = appSettings.trackingTransparencyPinnedNewest,
                        transparencyOldest = appSettings.trackingTransparencyPinnedOldest,
                        colorFrom = appSettings.trackingColorPinnedFrom,
                        colorTo = appSettings.trackingColorPinnedTo,
                        strokeWidth = width
                    )
                }
            )
            trackOverlays.addAll(rendering.overlays)
            if (rendering.drawArrows) {
                trackOverlays.add(
                    rendering.directionOverlay(track.trackPoints, directionSpacingProvider, "track_arrow_${summary.id}")
                )
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

/**
 * The width one stored track earns, read from `maro.properties` by track type rather than by the
 * loop's position: the selected track takes `track.width.selected` whatever its class, a pinned track
 * `track.width.pinned`, and a history track `track.width.newest` when it is the newest track of the
 * set being drawn, else `track.width.history`. All three rendering modes read the same table (D11).
 */
internal fun storedTrackWidth(selected: Boolean, pinned: Boolean, newest: Boolean): Float = when {
    selected -> AppConfig.trackWidthSelected
    pinned -> AppConfig.trackWidthPinned
    newest -> AppConfig.trackWidthNewest
    else -> AppConfig.trackWidthHistory
}

/**
 * The newest track of a set, by the recency the list sorts on: the greatest `startTimeMs`, with
 * `lastPointTimeMs` breaking a tie, which is the order the selection policy's own ranking reads them
 * in. The policy ranks the focused track first, so a loop over its result opens on the user's
 * selection rather than on the newest track — which is why the newest width is decided here and never
 * by loop position. Null when the set is empty.
 */
internal fun newestTrackId(summaries: List<ykws.android.maro.data.track.TrackSummary>): String? =
    summaries.maxWithOrNull(compareBy({ it.startTimeMs }, { it.lastPointTimeMs }))?.id

/** The three self-contained rendering paths a stored track can take (D1). */
internal enum class TrackRenderPath { PLAIN, GOLD_HIGHLIGHT, BANDED }

/** A stored track's path, whether it draws arrows, and whether it is the selected track. */
internal data class TrackRenderPlan(
    val path: TrackRenderPath,
    val drawArrows: Boolean,
    val selected: Boolean
)

/**
 * The mode-to-path decision (D1, D10) as a pure function, so the history and pinned loops share one
 * answer and the mapping is unit-testable:
 *
 * - Colours bands every stored track, and draws its arrows.
 * - Dir & Speed keeps the default colours and adds the arrows.
 * - Simple keeps the default colours alone.
 *
 * The selected track's own override — the drawer eye, [eyeOverride] — moves that one track and
 * nothing else: true bands it whatever the mode says, false draws it in the default colours without
 * arrows, null follows the mode. In every mode the selected track keeps its z-lift, and gold stays
 * its fill wherever it is not banded.
 */
internal fun trackRenderPlan(
    mode: TrackRenderMode,
    selected: Boolean,
    eyeOverride: Boolean?
): TrackRenderPlan {
    val effective = if (selected) {
        when (eyeOverride) {
            true -> TrackRenderMode.HEATMAP
            false -> TrackRenderMode.SIMPLE
            null -> mode
        }
    } else mode
    val path = when {
        effective == TrackRenderMode.HEATMAP -> TrackRenderPath.BANDED
        selected -> TrackRenderPath.GOLD_HIGHLIGHT
        else -> TrackRenderPath.PLAIN
    }
    return TrackRenderPlan(
        path = path,
        drawArrows = effective != TrackRenderMode.SIMPLE,
        selected = selected
    )
}

/**
 * A stored track's strokes, beside the inputs its direction chevrons need: the appearance list of
 * the path it took, or — on the banded path — a per-anchor colour resolver with the metrics that go
 * with it (a null resolver keeps the per-appearance iteration).
 */
private data class StoredTrackRendering(
    val overlays: List<org.osmdroid.views.overlay.Overlay>,
    val arrowAppearances: List<TrackPolylineAppearance>,
    val arrowColorResolver: ((ArrowAnchor) -> TrackPolylineAppearance)? = null,
    /** Whether this track's path draws chevrons at all (D1: Simple never does). */
    val drawArrows: Boolean = false
)

/**
 * Stored-track dispatcher: picks the path by [plan] and returns that path's strokes and chevron
 * inputs. Both the history loop and the pinned loop call this one entry point, so the two cannot
 * drift apart, and the live recording line is not a caller at all (D2).
 *
 * [strokeWidth] is the D11 width this track earned and [fade] its own recency alpha (D8), both
 * already resolved by the caller; [plainAppearance] is built on demand, so the paths that do not
 * paint default colours never compute one.
 */
private fun storedTrackRendering(
    points: List<TrackPoint>,
    title: String,
    plan: TrackRenderPlan,
    ramp: HeatmapRamp,
    strokeWidth: Float,
    fade: Float,
    plainAppearance: () -> TrackPolylineAppearance
): StoredTrackRendering {
    val rendering = when (plan.path) {
        TrackRenderPath.BANDED -> bandedPath(
            points = points,
            title = title,
            ramp = ramp,
            strokeWidth = strokeWidth,
            fade = fade
        )
        TrackRenderPath.GOLD_HIGHLIGHT -> goldHighlightPath(points, title, strokeWidth)
        TrackRenderPath.PLAIN -> plainPath(points, title, plainAppearance())
    }
    return rendering.copy(drawArrows = plan.drawArrows)
}

/**
 * Plain path — today's unselected rendering, unchanged: the stored colours with this track's own
 * fade, D11's width, solid segments between gaps and dashed for gap segments.
 */
private fun plainPath(
    points: List<TrackPoint>,
    title: String,
    appearance: TrackPolylineAppearance
): StoredTrackRendering = StoredTrackRendering(
    overlays = buildSegmentOverlays(points, appearance, title),
    arrowAppearances = listOf(appearance)
)

/**
 * Gold-highlight path — the selected track's rendering: one gold core, one title per track, and
 * chevrons iterating that appearance list the way they always have. The core's width is the selected
 * track's own, read from `track.width.selected` like every other path's.
 */
private fun goldHighlightPath(
    points: List<TrackPoint>,
    title: String,
    strokeWidth: Float
): StoredTrackRendering {
    val appearances = listOf(TrackPolylineAppearance(0xFFFFD700.toInt(), strokeWidth))
    return StoredTrackRendering(
        overlays = appearances.flatMap { buildSegmentOverlays(points, it, title) },
        arrowAppearances = appearances
    )
}

/**
 * Banded path (D1): the banded core carries the speed, so no gold is left to meet the ramp. Every
 * band and every chevron keeps the track's single
 * [title] — the z-lift matches exact titles while teardown matches prefixes, so a per-band suffix
 * would drop the selected track below the others at the end of the effect (A6).
 */
private fun bandedPath(
    points: List<TrackPoint>,
    title: String,
    ramp: HeatmapRamp,
    strokeWidth: Float,
    fade: Float
): StoredTrackRendering {
    val speeds = resolveSpeeds(points)
    val bands = bandedAppearances(points, speeds, ramp, strokeWidth, fade)
    // The band table is the pure mapping [bandTable] owns; its shared-boundary rule is unit-tested.
    val bandByIndex = bandTable(points.size, bands)
    val metrics = bands.firstOrNull()?.appearance
        ?: TrackPolylineAppearance(ramp.unknownArgb, strokeWidth)
    val resolver: (ArrowAnchor) -> TrackPolylineAppearance = { anchor ->
        bandByIndex.getOrNull(anchor.segmentIndex)?.appearance ?: metrics
    }
    return StoredTrackRendering(
        overlays = bands.flatMap { buildBandSegmentOverlays(points, it, title) },
        arrowAppearances = listOf(metrics),
        arrowColorResolver = resolver
    )
}

/** A stored track's chevrons: the appearance iteration off the banded path, the resolver on it. */
private fun StoredTrackRendering.directionOverlay(
    points: List<TrackPoint>,
    spacingPx: (Float) -> Float,
    title: String
): org.osmdroid.views.overlay.Overlay = TrackDirectionOverlay(
    points = points,
    appearances = arrowAppearances,
    spacingPx = spacingPx,
    colorResolver = arrowColorResolver,
    chevronMetrics = arrowAppearances.firstOrNull().takeIf { arrowColorResolver != null }
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
                    outlinePaint.strokeWidth = AppConfig.trackWidthLive
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
                        outlinePaint.strokeWidth = AppConfig.trackWidthLive
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                        isVisible = true
                        setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(point.lat, point.lon)))
                    }
                    mv.overlays.add(gapLine)
                }
                val resumedLine = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = AppConfig.trackWidthLive
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
                    outlinePaint.strokeWidth = AppConfig.trackWidthLive
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
                        outlinePaint.strokeWidth = AppConfig.trackWidthLive
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
                    outlinePaint.strokeWidth = AppConfig.trackWidthLive
                    isVisible = true
                    setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(displayPos.latitude, displayPos.longitude)))
                }
                mv.overlays.add(trailing)
                OverlayZOrder.reorder(mv)
                mv.invalidate()
            }
    }
}
