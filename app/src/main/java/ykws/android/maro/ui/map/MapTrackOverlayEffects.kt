package ykws.android.maro.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import org.osmdroid.views.MapView
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.MapRenderFocus
import ykws.android.maro.data.model.TrackSelectionPolicy
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.config.HeatmapRamp
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.TrackSummary

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
    // a default-colour edit cannot rebuild a ramp-painted map. The two counts and every transparency
    // range stay whatever the axes say, because D8 makes the fade live in every combination.
    val rebuildKeys: List<Any?> = buildList {
        add(mapView)
        add(showSettings)
        add(highlightedTrackId)
        add(trackArrows)
        add(trackColours)
        add(eyeOverride)
        add(appSettings.tracksVisible)
        add(appSettings.trackingRenderNb)
        add(appSettings.traceRenderNb)
        add(appSettings.trackMapFilter)
        add(appSettings.trackFilterLinked)
        add(allTrackSummaries)
        add(appSettings.trackingTransparencyNewest)
        add(appSettings.trackingTransparencyOldest)
        add(appSettings.trackingTransparencyPinnedNewest)
        add(appSettings.trackingTransparencyPinnedOldest)
        // The trace role's own four values, and its two gates: a trace reads them in every mode, so
        // they are keys in every combination.
        add(appSettings.trackingTransparencyTraceNewest)
        add(appSettings.trackingTransparencyTraceOldest)
        add(appSettings.traceSpeedColor)
        add(appSettings.traceSpeedArrows)
        // The per-type widths are read on every path in every mode, so the seven join the list
        // unconditionally rather than behind a mode test — the casing's width with them, since the
        // selection it outlines is drawn in all three, and the trace's, which a trace takes whatever
        // its pin says.
        add(AppConfig.trackWidthLiveDp)
        add(AppConfig.trackWidthSelectedDp)
        add(AppConfig.trackWidthNewestDp)
        add(AppConfig.trackWidthPinnedDp)
        add(AppConfig.trackWidthHistoryDp)
        add(AppConfig.trackWidthTraceDp)
        add(AppConfig.trackWidthSelectedCasingDp)
        if (!trackColours) {
            // Colours off means the default colours are the fill, so their four keys join here.
            add(appSettings.trackingColorPastFrom)
            add(appSettings.trackingColorPastTo)
            add(appSettings.trackingColorPinnedFrom)
            add(appSettings.trackingColorPinnedTo)
        }
        if (!appSettings.traceSpeedColor) {
            // A trace paints from its own pair while its colour gate is off — whatever the chips say —
            // so the pair joins the list for the same reason the default colours do above.
            add(appSettings.trackingColorTraceFrom)
            add(appSettings.trackingColorTraceTo)
        }
        // The ramp is read whenever Colours bands the stored tracks *or* a trace may band on its own.
        if (trackColours || appSettings.traceSpeedColor) {
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
            add(AppConfig.trackArrowScaleKneeDp)
            add(AppConfig.trackArrowTemper)
        }
    }

    LaunchedEffect(*rebuildKeys.toTypedArray()) {
        val mv = mapView ?: return@LaunchedEffect

        // Direction-arrow spacing provider: uniform (px) or speed-linear (dp → px).
        val densityScale = mv.paintDensity
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
        // The trace role's own count, bounding the trace set alone (R35): the not-pinned count above
        // goes on limiting recorded tracks.
        val traceNb = appSettings.traceRenderNb.coerceIn(0, 20)
        focus.highlight(highlightedTrackId)
        // The three sets one pass draws — the recorded half, the trace half and the pinned escape — from
        // one entry point, so the two counts and the pin's exemption cannot drift (see
        // [storedTrackSelection]).
        val selection = storedTrackSelection(
            summaries = storedSummaries,
            highlightedTrackId = highlightedTrackId,
            filter = appSettings.trackMapFilter,
            focus = focus,
            tracksVisible = appSettings.tracksVisible,
            todayMidnightMs = midnightMs,
            recordingNb = nbToRender,
            traceNb = traceNb
        )
        val historyList = selection.recorded
        val traceList = selection.traces

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
                trace = false,
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
                density = densityScale,
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
                    rendering.directionOverlay(
                        track.trackPoints,
                        directionSpacingProvider,
                        "track_arrow_${summary.id}",
                        densityScale
                    )
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

        // ── The traces: the trace role's own pair, ladder, stroke and gates, above the recorded
        // tracks. The fade is interpolated over the trace set alone, never the history set's --.
        val traceOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in traceList.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val selected = summary.id == highlightedTrackId
            val width = storedTrackWidth(selected = selected, trace = true, pinned = false, newest = false)
            val rendering = storedTrackRendering(
                points = track.trackPoints,
                title = "track_hist_${summary.id}",
                plan = traceTrackRenderPlan(
                    trackArrows = trackArrows,
                    trackColours = trackColours,
                    selected = selected,
                    eyeOverride = eyeOverride,
                    traceSpeedColour = appSettings.traceSpeedColor,
                    traceSpeedArrows = appSettings.traceSpeedArrows
                ),
                ramp = AppConfig.trackHeatmapRamp,
                strokeWidth = width,
                density = densityScale,
                fade = trackFadeAlpha(
                    index = index,
                    total = traceList.size,
                    transparencyNewest = appSettings.trackingTransparencyTraceNewest,
                    transparencyOldest = appSettings.trackingTransparencyTraceOldest
                ),
                plainAppearance = {
                    computeTrackPolylineAppearance(
                        index = index,
                        total = traceList.size,
                        transparencyNewest = appSettings.trackingTransparencyTraceNewest,
                        transparencyOldest = appSettings.trackingTransparencyTraceOldest,
                        colorFrom = appSettings.trackingColorTraceFrom,
                        colorTo = appSettings.trackingColorTraceTo,
                        strokeWidth = width
                    )
                }
            )
            trackOverlays.addAll(rendering.overlays)
            if (rendering.drawArrows) {
                trackOverlays.add(
                    rendering.directionOverlay(
                        track.trackPoints,
                        directionSpacingProvider,
                        "track_arrow_${summary.id}",
                        densityScale
                    )
                )
            }

            traceOverlays.add(trackOverlays)
            // Same rule as the recorded pass: no stroke, no paint to key the legend on.
            if (rendering.overlays.isNotEmpty()) painted.add(summary.id)
        }
        traceOverlays.reverse()
        for (trackOverlays in traceOverlays) {
            mv.overlays.addAll(trackOverlays)
        }

        // ── Pinned tracks: always render all, separate colors/opacity ──
        val toRemovePinned = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_pin_") == true
        }
        mv.overlays.removeAll(toRemovePinned)

        // The pin's escape, from the same entry point as the two counted sets: a pinned trace is drawn
        // whatever the trace count says (R34, R35).
        val pinnedSummaries = selection.pinned

        val pinnedTotal = pinnedSummaries.size
        val pinnedOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in pinnedSummaries.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val selected = summary.id == highlightedTrackId
            // A pinned trace is drawn whatever the trace count says — the pin is what marks a route
            // already saved — and it draws as an *unpinned* trace does: the trace role's own pair,
            // ladder and stroke, never the pinned ones. The pin buys the escape from the count and
            // nothing else (R34, R35).
            val isTrace = summary.trace
            val width = storedTrackWidth(selected = selected, trace = isTrace, pinned = true, newest = false)
            val rendering = storedTrackRendering(
                points = track.trackPoints,
                title = "track_pin_${summary.id}",
                plan = pinnedTrackRenderPlan(
                    summary = summary,
                    trackArrows = trackArrows,
                    trackColours = trackColours,
                    selected = selected,
                    eyeOverride = eyeOverride,
                    traceSpeedColour = appSettings.traceSpeedColor,
                    traceSpeedArrows = appSettings.traceSpeedArrows
                ),
                ramp = AppConfig.trackHeatmapRamp,
                strokeWidth = width,
                density = densityScale,
                // D8: a pinned track fades across its own range, exactly as it does today — a trace
                // across the trace ladder instead, so the pin changes nothing about its appearance.
                fade = trackFadeAlpha(
                    index = index,
                    total = pinnedTotal,
                    transparencyNewest = if (isTrace) appSettings.trackingTransparencyTraceNewest
                                         else appSettings.trackingTransparencyPinnedNewest,
                    transparencyOldest = if (isTrace) appSettings.trackingTransparencyTraceOldest
                                         else appSettings.trackingTransparencyPinnedOldest
                ),
                plainAppearance = {
                    computeTrackPolylineAppearance(
                        index = index,
                        total = pinnedTotal,
                        transparencyNewest = if (isTrace) appSettings.trackingTransparencyTraceNewest
                                             else appSettings.trackingTransparencyPinnedNewest,
                        transparencyOldest = if (isTrace) appSettings.trackingTransparencyTraceOldest
                                             else appSettings.trackingTransparencyPinnedOldest,
                        colorFrom = if (isTrace) appSettings.trackingColorTraceFrom
                                    else appSettings.trackingColorPinnedFrom,
                        colorTo = if (isTrace) appSettings.trackingColorTraceTo
                                  else appSettings.trackingColorPinnedTo,
                        strokeWidth = width
                    )
                }
            )
            trackOverlays.addAll(rendering.overlays)
            if (rendering.drawArrows) {
                trackOverlays.add(
                    rendering.directionOverlay(
                        track.trackPoints,
                        directionSpacingProvider,
                        "track_arrow_${summary.id}",
                        densityScale
                    )
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
 * loop's position: the selected track takes `track.width.selected` whatever its class, a **trace**
 * `track.width.trace` whatever its pin says, a pinned track `track.width.pinned`, and a history track
 * `track.width.newest` when it is the newest track of the set being drawn, else `track.width.history`.
 * Every rendering role reads the same table (D11), and the trace's rank above the pin is the whole of
 * what R34 asks for here.
 */
internal fun storedTrackWidth(
    selected: Boolean,
    trace: Boolean,
    pinned: Boolean,
    newest: Boolean
): Float = when {
    selected -> AppConfig.trackWidthSelectedDp
    trace -> AppConfig.trackWidthTraceDp
    pinned -> AppConfig.trackWidthPinnedDp
    newest -> AppConfig.trackWidthNewestDp
    else -> AppConfig.trackWidthHistoryDp
}

/**
 * The stored set split by the flag: the traces, which are their own role from the first decision on,
 * and the recorded tracks beside them.
 *
 * This decides membership alone — which **count** bounds which half is [storedTrackSelection]'s
 * decision, and what pair each paints from is the effect's (R34, R35) — but it is the one home of
 * "a trace is not a track", so the two loops cannot disagree about which summary is which.
 */
internal data class StoredTrackSets(
    val traces: List<ykws.android.maro.data.track.TrackSummary>,
    val recorded: List<ykws.android.maro.data.track.TrackSummary>
)

internal fun storedTrackSets(
    summaries: List<ykws.android.maro.data.track.TrackSummary>
): StoredTrackSets = StoredTrackSets(
    traces = summaries.filter { it.trace },
    recorded = summaries.filterNot { it.trace }
)

/** The three sets one pass draws — the recorded half, the trace half and the pinned escape. */
internal data class StoredTrackSelection(
    val recorded: List<TrackSummary>,
    val traces: List<TrackSummary>,
    val pinned: List<TrackSummary>
)

/**
 * What one pass asks each of the three roles for, from one entry point so the two counts and the pin's
 * exemption cannot drift apart:
 *
 * - [StoredTrackSelection.recorded] — the recorded half, ranked, filtered and bounded by
 *   [recordingNb]'s own count;
 * - [StoredTrackSelection.traces] — the trace half, bounded by [traceNb] and **never** by the recorded
 *   count: the two sibling counts limit their own role alone (R35);
 * - [StoredTrackSelection.pinned] — every pinned summary, **uncapped**: the pin is what marks a route
 *   already saved, so a pinned trace is drawn whatever [traceNb] says (R34, R35).
 *
 * Membership is [storedTrackSets]' decision; this one only decides what each half is asked for. The
 * two caps are taken here rather than at the call site, so a test can hold the count's home.
 */
internal fun storedTrackSelection(
    summaries: List<TrackSummary>,
    highlightedTrackId: String?,
    filter: ListFilter,
    focus: MapRenderFocus,
    tracksVisible: Boolean,
    todayMidnightMs: Long,
    recordingNb: Int,
    traceNb: Int
): StoredTrackSelection {
    if (!tracksVisible) return StoredTrackSelection(emptyList(), emptyList(), emptyList())
    val sets = storedTrackSets(summaries)
    val policy = TrackSelectionPolicy()
    return StoredTrackSelection(
        recorded = policy.select(
            items = sets.recorded,
            filter = filter,
            cap = recordingNb.coerceIn(0, 20),
            focus = focus,
            todayMidnightMs = todayMidnightMs
        ),
        traces = policy.select(
            items = sets.traces,
            filter = filter,
            cap = traceNb.coerceIn(0, 20),
            focus = focus,
            todayMidnightMs = todayMidnightMs
        ),
        pinned = summaries
            .filter { it.pinned && (it.id == highlightedTrackId || it.matchesFilter(filter, todayMidnightMs)) }
            .sortedByDescending { it.startTimeMs }
    )
}

/**
 * The plan an **unpinned trace** draws by: the trace role whatever the chips say, its two gates
 * trace-scoped (R34, R37, R38). One home, so the counted pass and the test that pins the role read the
 * same arguments.
 */
internal fun traceTrackRenderPlan(
    trackArrows: Boolean,
    trackColours: Boolean,
    selected: Boolean,
    eyeOverride: Boolean?,
    traceSpeedColour: Boolean,
    traceSpeedArrows: Boolean
): TrackRenderPlan = trackRenderPlan(
    trackArrows = trackArrows,
    trackColours = trackColours,
    selected = selected,
    eyeOverride = eyeOverride,
    trace = true,
    traceSpeedColour = traceSpeedColour,
    traceSpeedArrows = traceSpeedArrows
)

/**
 * The plan a **pinned** summary draws by, read off the summary's own flag rather than its pin: a pinned
 * trace takes the trace role — its own pair, ladder and stroke, never the pinned ones — so the pin buys
 * the escape from the trace count and nothing else (R34, R35), while a pinned recorded track is planned
 * as the stored track it is.
 */
internal fun pinnedTrackRenderPlan(
    summary: TrackSummary,
    trackArrows: Boolean,
    trackColours: Boolean,
    selected: Boolean,
    eyeOverride: Boolean?,
    traceSpeedColour: Boolean,
    traceSpeedArrows: Boolean
): TrackRenderPlan = trackRenderPlan(
    trackArrows = trackArrows,
    trackColours = trackColours,
    selected = selected,
    eyeOverride = eyeOverride,
    trace = summary.trace,
    traceSpeedColour = traceSpeedColour,
    traceSpeedArrows = traceSpeedArrows
)

/**
 * The widest width the stored table can hand a track — the reference the chevron length's ceiling is
 * taken from (2.5 × this, see `chevronLength`), so no class the file ships is flattened by its own
 * core while a retuned file cannot invert the chevrons against the line they sit on either. The live
 * width is not part of it: the live recording line draws no chevrons.
 */
internal fun widestStoredTrackWidth(): Float = maxOf(
    AppConfig.trackWidthSelectedDp,
    AppConfig.trackWidthNewestDp,
    AppConfig.trackWidthPinnedDp,
    AppConfig.trackWidthHistoryDp,
    AppConfig.trackWidthTraceDp
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
    TrackPolylineAppearance(SELECTED_TRACK_CASING_ARGB, AppConfig.trackWidthSelectedCasingDp)

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

/**
 * The paths a stored track can take: the three recorded ones (D1) and the trace role's own pair.
 *
 * [TRACE] is the trace's own colour pair rather than a copy of [PLAIN]: it is the same shape of
 * rendering — one appearance, iterated by the chevrons — but it is decided by the trace's own gate
 * and never by the Colours chip, so it is named for the role that decides it.
 */
internal enum class TrackRenderPath { PLAIN, GOLD_HIGHLIGHT, BANDED, TRACE }

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
    eyeOverride: Boolean?,
    trace: Boolean = false,
    traceSpeedColour: Boolean = false,
    traceSpeedArrows: Boolean = true
): TrackRenderPlan {
    // The trace role is decided **before** the pinned one and before the chips: a trace paints from
    // its own pair whatever its pin says, and the Colours chip and the drawer eye never reach it. Its
    // two gates are trace-scoped instead — the colour one replaces the ramp for a trace, the arrow one
    // can only veto the chevrons — so a trace's appearance is the same pinned or not (R34, R37, R38).
    if (trace) {
        return TrackRenderPlan(
            path = if (traceSpeedColour) TrackRenderPath.BANDED else TrackRenderPath.TRACE,
            drawArrows = trackArrows && traceSpeedArrows,
            selected = selected
        )
    }
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
 * history, trace and pinned alike, asked of the same planner the map renders by ([trackRenderPlan]), so
 * the tracks layer being off, a painted set that is empty — count 0, or every summary's detail failed
 * to load — or a set whose only banded candidate is the selection itself all hide the scale whatever
 * the flags say.
 *
 * [selectionOpen] and [selectionBanded] are the second half, and they follow the fill the drawer has
 * open: with a track selected the scale lives and dies with *that* track's fill — read by
 * [selectionBandedFor], so a selected trace follows its own gate where the chips never reach it — while
 * with nothing selected the painted strokes are the whole answer, because the ramp can then be on the
 * map without the Colours chip having raised it: a trace's own gate bands a stroke too (R37), so a
 * fallback on the chip alone would hide the scale from the very fill it keys.
 */
private fun legendVisibleFor(
    storedOnMap: Boolean,
    selectionOpen: Boolean,
    selectionBanded: Boolean
): Boolean = storedOnMap && (!selectionOpen || selectionBanded)

/**
 * The fill the open track wears, read exactly as the map paints it: a selected **trace** takes its own
 * pair whatever the chips and the eye say, so only its own colour gate bands it (R34, R37); every other
 * selection is banded by the eye's own value, or by [trackColours] while the eye has never been tapped.
 */
private fun selectionBandedFor(
    highlightedTrackId: String?,
    eyeOverride: Boolean?,
    trackColours: Boolean,
    traceIds: Set<String>,
    traceSpeedColour: Boolean
): Boolean = if (highlightedTrackId != null && highlightedTrackId in traceIds) {
    traceSpeedColour
} else eyeOverride ?: trackColours

/**
 * The same gate taken from the map's own state rather than from its parts, so the composition that
 * asks it and the test that pins it call one entry point: [bandedStrokeOnMap] supplies `storedOnMap`,
 * [highlightedTrackId]'s nullness supplies `selectionOpen`, and [legendVisibleFor] decides. The name
 * says which of the two is which — this one reads state, the predicate above takes the decided parts —
 * so a call site need not count arguments to tell them apart. A caller has no
 * `storedOnMap`/`selectionOpen` pair to swap, which is the one pair a positional mirror of the
 * predicate could not catch.
 *
 * [traceIds] is which painted summaries are routes: without it every painted id is asked of
 * [trackRenderPlan] as a recorded track, so a trace banded by its own gate while the chips are off
 * would leave the scale hidden although the ramp is on the map (R37).
 */
internal fun legendVisibleForState(
    paintedIds: Set<String>,
    trackArrows: Boolean,
    trackColours: Boolean,
    highlightedTrackId: String?,
    eyeOverride: Boolean?,
    tracksVisible: Boolean,
    traceIds: Set<String> = emptySet(),
    traceSpeedColour: Boolean = false,
    traceSpeedArrows: Boolean = true
): Boolean = legendVisibleFor(
    storedOnMap = bandedStrokeOnMap(
        paintedIds = paintedIds,
        trackArrows = trackArrows,
        trackColours = trackColours,
        highlightedTrackId = highlightedTrackId,
        eyeOverride = eyeOverride,
        tracksVisible = tracksVisible,
        traceIds = traceIds,
        traceSpeedColour = traceSpeedColour,
        traceSpeedArrows = traceSpeedArrows
    ),
    selectionOpen = highlightedTrackId != null,
    selectionBanded = selectionBandedFor(
        highlightedTrackId = highlightedTrackId,
        eyeOverride = eyeOverride,
        trackColours = trackColours,
        traceIds = traceIds,
        traceSpeedColour = traceSpeedColour
    )
)

/**
 * Whether the map carries a banded stroke right now — the legend gate's `storedOnMap` input, in one
 * home so the composition that asks it and the test that pins it cannot drift apart. The tracks layer
 * must be on and one of the ids the effect actually painted drawn from the ramp by the same planner
 * the map renders by ([trackRenderPlan]): the layer off, an empty painted set — count 0, or every
 * summary's detail failed to load — or a set whose only banded candidate is a selection the eye has
 * flipped gold all answer false, whatever the flags say.
 *
 * [traceIds] names the painted routes, so each id is read as the role it really is: a trace bands on
 * its own colour gate ([traceSpeedColour]) and never on [trackColours] (R34, R37), which is the reading
 * the map paints by.
 */
internal fun bandedStrokeOnMap(
    paintedIds: Set<String>,
    trackArrows: Boolean,
    trackColours: Boolean,
    highlightedTrackId: String?,
    eyeOverride: Boolean?,
    tracksVisible: Boolean,
    traceIds: Set<String> = emptySet(),
    traceSpeedColour: Boolean = false,
    traceSpeedArrows: Boolean = true
): Boolean = tracksVisible && paintedIds.any { id ->
    trackRenderPlan(
        trackArrows = trackArrows,
        trackColours = trackColours,
        selected = id == highlightedTrackId,
        eyeOverride = eyeOverride,
        trace = id in traceIds,
        traceSpeedColour = traceSpeedColour,
        traceSpeedArrows = traceSpeedArrows
    ).path == TrackRenderPath.BANDED
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
    density: Float,
    fade: Float,
    plainAppearance: () -> TrackPolylineAppearance
): StoredTrackRendering {
    val rendering = when (plan.path) {
        TrackRenderPath.BANDED -> bandedPath(
            points = points,
            title = title,
            ramp = ramp,
            strokeWidth = strokeWidth,
            density = density,
            fade = storedTrackFade(plan.selected, fade)
        )
        TrackRenderPath.GOLD_HIGHLIGHT -> goldHighlightPath(points, title, strokeWidth, density)
        // The trace role's own pair arrives through the same lazily-built appearance the plain path
        // takes — the caller owns which pair a trace paints from — so no helper is forked for it.
        TrackRenderPath.TRACE -> plainPath(points, title, plainAppearance(), density)
        TrackRenderPath.PLAIN -> plainPath(points, title, plainAppearance(), density)
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
        overlays = buildSegmentOverlays(points, casing, title, density) + rendering.overlays,
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
    appearance: TrackPolylineAppearance,
    density: Float
): StoredTrackRendering = StoredTrackRendering(
    overlays = buildSegmentOverlays(points, appearance, title, density),
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
    strokeWidth: Float,
    density: Float
): StoredTrackRendering {
    val gold = TrackPolylineAppearance(0xFFFFD700.toInt(), strokeWidth)
    return StoredTrackRendering(
        overlays = buildSegmentOverlays(points, gold, title, density),
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
    density: Float,
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
        overlays = bands.flatMap { buildBandSegmentOverlays(points, it, title, density) },
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
    title: String,
    density: Float
): org.osmdroid.views.overlay.Overlay = TrackDirectionOverlay(
    points = points,
    appearances = arrowAppearances,
    spacingPx = spacingPx,
    colorResolver = arrowColorResolver,
    chevronMetrics = arrowAppearances.firstOrNull().takeIf { arrowColorResolver != null },
    casingAppearance = chevronCasing,
    density = density
).apply { this.title = title }

/** The live recording line's stroke in px: its dp key through the map's own density. */
private fun liveTrackWidthPx(mv: MapView): Float = dpToPx(AppConfig.trackWidthLiveDp, mv.paintDensity)

/** The GAP bridge's dash in px — two values, because the stroke it rides varies by track class. */
private fun gapDashPx(mv: MapView): FloatArray = floatArrayOf(
    dpToPx(TRACK_GAP_DASH_ON_DP, mv.paintDensity),
    dpToPx(TRACK_GAP_DASH_OFF_DP, mv.paintDensity)
)

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
                    outlinePaint.strokeWidth = liveTrackWidthPx(mv)
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
                        outlinePaint.strokeWidth = liveTrackWidthPx(mv)
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(gapDashPx(mv), 0f)
                        isVisible = true
                        setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(point.lat, point.lon)))
                    }
                    mv.overlays.add(gapLine)
                }
                val resumedLine = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = liveTrackWidthPx(mv)
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
                    outlinePaint.strokeWidth = liveTrackWidthPx(mv)
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
                        outlinePaint.strokeWidth = liveTrackWidthPx(mv)
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
                    outlinePaint.strokeWidth = liveTrackWidthPx(mv)
                    isVisible = true
                    setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(displayPos.latitude, displayPos.longitude)))
                }
                mv.overlays.add(trailing)
                OverlayZOrder.reorder(mv)
                mv.invalidate()
            }
    }
}
