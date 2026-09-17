package ykws.android.maro.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import org.osmdroid.views.MapView
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.MapRenderFocus
import ykws.android.maro.data.model.TrackSelectionPolicy
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.config.HeatmapRamp
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
     * The arrows axis (D1, D3): the menu's twin box is its only writer and this effect only reads it.
     * It decides the chevrons alone — a stored track's *fill* is [trackColours]' business.
     */
    trackArrows: Boolean,
    /**
     * The colours axis (D1, D3): on, every stored track paints from the speed ramp; off, they keep the
     * stored default colours. Same single writer as [trackArrows].
     */
    trackColours: Boolean,
    /**
     * The drawer eye's own value (D10), persisted on the selection rather than session-only: null
     * follows [trackColours] — an install whose eye was never tapped holds no value at all — true bands
     * the selected track, false paints it gold. It decides that one track's *fill* and nothing else:
     * the chevrons follow [trackArrows] alone (see [trackRenderPlan]), and because the value belongs to
     * the selection rather than to a track id, it applies to whichever track the drawer has open.
     */
    eyeOverride: Boolean?,
    allTrackSummaries: List<ykws.android.maro.data.track.TrackSummary>,
    focus: MapRenderFocus,
    appSettings: AppSettings,
    /**
     * The ids whose overlays this pass actually painted, history and pinned alike: the effect writes
     * them where the overlays are added rather than from the set it asked for, so a summary whose detail
     * failed to load counts for nothing. The legend gate reads it (see [legendVisibleForState]).
     */
    paintedTrackIds: MutableState<Set<String>>,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    /**
     * Bumped once at the end of every pass. Inspect mode's own candidate overlay is not part of this
     * rebuild — it is added and removed by the mode — but a rebuild can float other tracks above it,
     * so the mode re-stacks on each bump: one list operation, not a repaint.
     */
    rebuildGeneration: MutableState<Int>
) {
    // ── Track overlay: incremental diff for history tracks with fading opacity ──
    // What the loops below actually paint, published at the end of the pass: the legend gate asks this
    // set rather than the selection policy, which is stateful and runs once, here.
    val painted = mutableSetOf<String>()

    // Axis-aware rebuild keys (§3b): values the current axes do not read must not trigger a rebuild, so
    // a default-colour edit cannot rebuild a ramp-painted map. The count and both transparency ranges
    // stay whatever the axes say, because D8 makes the fade live in every combination.
    val rebuildKeys: List<Any?> = buildList {
        add(mapView)
        add(showSettings)
        add(highlightedTrackId)
        add(trackArrows)
        add(trackColours)
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
        // The per-type widths are read on every path in every mode, so the six join the list
        // unconditionally rather than behind a mode test — the casing's width with them, since the
        // selection it outlines is drawn in all three.
        add(AppConfig.trackWidthLive)
        add(AppConfig.trackWidthSelected)
        add(AppConfig.trackWidthNewest)
        add(AppConfig.trackWidthPinned)
        add(AppConfig.trackWidthHistory)
        add(AppConfig.trackWidthSelectedCasing)
        if (!trackColours) {
            // Colours off means the default colours are the fill, so their four keys join here.
            add(appSettings.trackingColorPastFrom)
            add(appSettings.trackingColorPastTo)
            add(appSettings.trackingColorPinnedFrom)
            add(appSettings.trackingColorPinnedTo)
        } else {
            // Colours on paints from the ramp instead.
            add(AppConfig.trackHeatmapRamp)
        }
        if (trackArrows) {
            // The chevrons' group belongs to the arrows flag alone — the eye never draws them, so it
            // cannot gate this. The tempering rides with the spacing: it is read on every chevron draw
            // and nowhere else.
            add(appSettings.trackDirectionDensity)
            add(appSettings.trackDirectionMinSpacingDp)
            add(appSettings.trackDirectionMaxSpacingDp)
            add(appSettings.trackDirectionSpeedFloorKn)
            add(appSettings.trackDirectionSpeedCeilingKn)
            add(AppConfig.trackArrowScaleKnee)
            add(AppConfig.trackArrowTemper)
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
                plan = trackRenderPlan(trackArrows, trackColours, selected, eyeOverride),
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
            // Only where a stroke actually landed: a single-point track emits no segment at all —
            // splitTrackSegments returns none — so it must not count as a banded stroke on the map.
            if (rendering.overlays.isNotEmpty()) painted.add(summary.id)
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
                plan = trackRenderPlan(trackArrows, trackColours, selected, eyeOverride),
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
            // Same rule as the history loop: no stroke, no paint to key the legend on.
            if (rendering.overlays.isNotEmpty()) painted.add(summary.id)
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

        paintedTrackIds.value = painted.toSet()
        OverlayZOrder.reorder(mv)
        mv.invalidate()
        // The pass is done: whoever paints a line of their own on top of the track band re-stacks now.
        rebuildGeneration.value = rebuildGeneration.value + 1
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
 * The widest width the stored table can hand a track — the reference the chevron length's ceiling is
 * taken from (2.5 × this, see `chevronLength`), so no class the file ships is flattened by its own
 * core while a retuned file cannot invert the chevrons against the line they sit on either. The live
 * width is not part of it: the live recording line draws no chevrons.
 */
internal fun widestStoredTrackWidth(): Float = maxOf(
    AppConfig.trackWidthSelected,
    AppConfig.trackWidthNewest,
    AppConfig.trackWidthPinned,
    AppConfig.trackWidthHistory
)

/**
 * The alpha a stored track is drawn at: the selection takes full alpha whatever its own class
 * transparency is set to, and every other track keeps the fade it earned (D8). This is the one place
 * the selection's opacity rule lands — the gold path is opaque already and the plain path never
 * receives a selection, so the banded path is the only one with a fade to override.
 */
internal fun storedTrackFade(selected: Boolean, fade: Float): Float = if (selected) 1f else fade

/**
 * The selected track's casing: the legacy `#CC000000` restored verbatim — black at 80 %, the dark
 * under-stroke the selection has always worn — at `track.width.selected.casing`, and that width is
 * the *line's*. Its chevrons take that colour and the width's rim: they are drawn at the coloured V's
 * own stroke, shifted outward by half this casing's excess over the line's own width — the very rim
 * the line wears, and never the chevron's tempered core (see `chevronCasingOffset`)
 * rather than as a thicker stroke of the same V, on every path a selection can take: the banded path
 * resolves a band per anchor and the gold path resolves the gold, so both cross the resolver seam this
 * casing rides on.
 */
internal fun selectedTrackCasing(): TrackPolylineAppearance =
    TrackPolylineAppearance(SELECTED_TRACK_CASING_ARGB, AppConfig.trackWidthSelectedCasing)

/** The casing's colour and alpha: not a file key in this pass, the restored legacy token. */
private val SELECTED_TRACK_CASING_ARGB = 0xCC000000.toInt()

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
 * The axes-to-path decision (D1, D10) as a pure function, so the history and pinned loops share one
 * answer and the mapping is unit-testable. The two axes are independent, which is what gives the four
 * combinations — neither, arrows only, colours only, both:
 *
 * - [trackColours] bands every stored track; off, they keep the stored default colours.
 * - [trackArrows] draws the chevrons; off, none is drawn in any combination.
 *
 * The selected track's own override — the drawer eye, [eyeOverride] — moves that one track's *fill*
 * and nothing else: true bands it whatever the flag says, false paints it gold, null follows
 * [trackColours]. The chevrons are [trackArrows]' alone: a gold selection with the flag on still
 * carries them and a banded selection with it off does not, whatever the eye says. In every
 * combination the selected track keeps its z-lift and its casing.
 */
internal fun trackRenderPlan(
    trackArrows: Boolean,
    trackColours: Boolean,
    selected: Boolean,
    eyeOverride: Boolean?
): TrackRenderPlan {
    val banded = if (selected) {
        eyeOverride ?: trackColours
    } else trackColours
    val path = when {
        banded -> TrackRenderPath.BANDED
        selected -> TrackRenderPath.GOLD_HIGHLIGHT
        else -> TrackRenderPath.PLAIN
    }
    return TrackRenderPlan(
        path = path,
        drawArrows = trackArrows,
        selected = selected
    )
}

/**
 * What a tap on the drawer eye writes: the selection's banded value *after* that tap. Before the first
 * one [current] is null and the selection mirrors [trackColours], so the tap turns that reading
 * around; afterwards it flips its own value and the flag no longer reaches it. The answer is never
 * null, which is what makes the first tap the write that puts the key on disk — and the only write
 * that can.
 */
internal fun selectionBandedAfterTap(current: Boolean?, trackColours: Boolean): Boolean =
    !(current ?: trackColours)

/**
 * Whether the speed legend belongs on the map. It keys the fill the ramp paints and nothing else, so it
 * is drawn exactly while that fill is on the map — settled 2026-09-17, and narrowed that same day: the
 * painted strokes first, then the open track's own fill.
 *
 * [storedOnMap] is the stroke half, and it leads the answer: the ids the effect actually painted,
 * history and pinned alike, asked of the same planner the map renders by ([trackRenderPlan]), so the
 * tracks layer being off, a painted set that is empty — count 0, or every summary's detail failed to
 * load — or a set whose only banded candidate is the selection itself all hide the scale whatever the
 * flags say.
 *
 * [selectionOpen] and [eyeOverride] are the second half, and they follow the fill the drawer has open:
 * with a track selected the scale lives and dies with *that* track's fill — the eye's own value, or
 * [trackColours] while the eye has never been tapped — so flipping the selection gold takes the key
 * away even while the other painted tracks stay banded, and a selection the eye has banded is the one
 * banded stroke a run with the flag off can still raise it for. With nothing selected it falls back to
 * [trackColours] alone, since the other tracks then carry the ramp or nobody does.
 */
private fun legendVisibleFor(
    trackColours: Boolean,
    storedOnMap: Boolean,
    selectionOpen: Boolean,
    eyeOverride: Boolean?
): Boolean = storedOnMap && (if (selectionOpen) (eyeOverride ?: trackColours) else trackColours)

/**
 * The same gate taken from the map's own state rather than from its parts, so the composition that
 * asks it and the test that pins it call one entry point: [bandedStrokeOnMap] supplies `storedOnMap`,
 * [highlightedTrackId]'s nullness supplies `selectionOpen`, and [legendVisibleFor] decides. The name
 * says which of the two is which — this one reads state, the predicate above takes the decided parts —
 * so a call site need not count arguments to tell them apart. A caller has no
 * `storedOnMap`/`selectionOpen` pair to swap, which is the one pair a positional mirror of the
 * predicate could not catch.
 */
internal fun legendVisibleForState(
    paintedIds: Set<String>,
    trackArrows: Boolean,
    trackColours: Boolean,
    highlightedTrackId: String?,
    eyeOverride: Boolean?,
    tracksVisible: Boolean
): Boolean = legendVisibleFor(
    trackColours = trackColours,
    storedOnMap = bandedStrokeOnMap(
        paintedIds = paintedIds,
        trackArrows = trackArrows,
        trackColours = trackColours,
        highlightedTrackId = highlightedTrackId,
        eyeOverride = eyeOverride,
        tracksVisible = tracksVisible
    ),
    selectionOpen = highlightedTrackId != null,
    eyeOverride = eyeOverride
)

/**
 * Whether the map carries a banded stroke right now — the legend gate's `storedOnMap` input, in one
 * home so the composition that asks it and the test that pins it cannot drift apart. The tracks layer
 * must be on and one of the ids the effect actually painted drawn from the ramp by the same planner
 * the map renders by ([trackRenderPlan]): the layer off, an empty painted set — count 0, or every
 * summary's detail failed to load — or a set whose only banded candidate is a selection the eye has
 * flipped gold all answer false, whatever the flags say.
 */
internal fun bandedStrokeOnMap(
    paintedIds: Set<String>,
    trackArrows: Boolean,
    trackColours: Boolean,
    highlightedTrackId: String?,
    eyeOverride: Boolean?,
    tracksVisible: Boolean
): Boolean = tracksVisible && paintedIds.any { id ->
    trackRenderPlan(trackArrows, trackColours, id == highlightedTrackId, eyeOverride).path == TrackRenderPath.BANDED
}

/**
 * A stored track's strokes, beside the inputs its direction chevrons need: the appearance list of
 * the path it took, or — on the banded path — a per-anchor colour resolver with the metrics that go
 * with it (a null resolver keeps the per-appearance iteration).
 *
 * Internal because inspect mode paints its candidate through this same dispatcher: the gold the
 * sweep shows and the gold the card's selection wears are one function, never two.
 */
internal data class StoredTrackRendering(
    val overlays: List<org.osmdroid.views.overlay.Overlay>,
    val arrowAppearances: List<TrackPolylineAppearance>,
    val arrowColorResolver: ((ArrowAnchor) -> TrackPolylineAppearance)? = null,
    /** Whether this track's path draws chevrons at all (D1: Simple never does). */
    val drawArrows: Boolean = false,
    /**
     * The dark casing the resolver path draws once beneath its chevrons, or null where no casing
     * belongs: every track that is not selected, and any path whose chevrons iterate an appearance
     * list instead of resolving one. The overlays themselves never carry it — it is the chevrons'
     * own input — and both halves of it reach them: its colour paints the dark V, and its width,
     * read against the line's own width, sets how far outside the coloured V that dark V sits.
     */
    val chevronCasing: TrackPolylineAppearance? = null
)

/**
 * Stored-track dispatcher: picks the path by [plan] and returns that path's strokes and chevron
 * inputs. Both the history loop and the pinned loop call this one entry point, so the two cannot
 * drift apart, and the live recording line is not a caller at all (D2).
 *
 * [strokeWidth] is the D11 width this track earned and [fade] its own recency alpha (D8), both
 * already resolved by the caller; [plainAppearance] is built on demand, so the paths that do not
 * paint default colours never compute one.
 *
 * The selection's two rules are applied here and nowhere else, so the history loop and the pinned
 * loop cannot disagree about them: its fade is forced to full alpha, and its casing is laid beneath
 * whichever path it took.
 */
internal fun storedTrackRendering(
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
            fade = storedTrackFade(plan.selected, fade)
        )
        TrackRenderPath.GOLD_HIGHLIGHT -> goldHighlightPath(points, title, strokeWidth)
        TrackRenderPath.PLAIN -> plainPath(points, title, plainAppearance())
    }
    if (!plan.selected) return rendering.copy(drawArrows = plan.drawArrows)
    // The casing returns beneath the selected track on every path — under the gold core and under
    // the bands alike — drawn by the core's own segment builder so it dashes across GAP seams, and
    // sharing the track's title so prefix teardown and the exact-title z-lift keep catching it. Its
    // chevrons take the same dark wherever they cross the resolver seam, which every path a selection
    // can take now does — the banded one resolves a band per anchor, the gold one the gold; the plain
    // path iterates an appearance list and carries no casing input at all.
    val casing = selectedTrackCasing()
    return rendering.copy(
        overlays = buildSegmentOverlays(points, casing, title) + rendering.overlays,
        chevronCasing = casing.takeIf { plan.path != TrackRenderPath.PLAIN },
        drawArrows = plan.drawArrows
    )
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
 * chevrons drawn through the same resolver seam the banded path uses, resolving the gold for every
 * anchor. The seam is what carries the selection's chevron casing — the appearance-iteration path
 * has no casing input at all — so the gold selection wears cased chevrons in Simple and Dir & Speed
 * exactly as a banded one does in Colours. The core's width is the selected track's own, read from
 * `track.width.selected` like every other path's.
 */
private fun goldHighlightPath(
    points: List<TrackPoint>,
    title: String,
    strokeWidth: Float
): StoredTrackRendering {
    val gold = TrackPolylineAppearance(0xFFFFD700.toInt(), strokeWidth)
    return StoredTrackRendering(
        overlays = buildSegmentOverlays(points, gold, title),
        arrowAppearances = listOf(gold),
        arrowColorResolver = { gold }
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

/**
 * A stored track's chevrons: the appearance iteration for the paths that resolve no colour — every
 * track that is not selected, and the plain path no selection ever takes — and the resolver seam for
 * the banded and gold paths, which is where the selection's casing rides.
 */
private fun StoredTrackRendering.directionOverlay(
    points: List<TrackPoint>,
    spacingPx: (Float) -> Float,
    title: String
): org.osmdroid.views.overlay.Overlay = TrackDirectionOverlay(
    points = points,
    appearances = arrowAppearances,
    spacingPx = spacingPx,
    colorResolver = arrowColorResolver,
    chevronMetrics = arrowAppearances.firstOrNull().takeIf { arrowColorResolver != null },
    casingAppearance = chevronCasing
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
