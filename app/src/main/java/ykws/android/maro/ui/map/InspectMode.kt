package ykws.android.maro.ui.map

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.TrackViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Inspect mode — the sleuth square, the cursor and the sweep
//
// The mode lets the user drag the map until the target is the closest thing on screen, watch the
// highlight follow it, and open the item without leaving the map or moving the camera. This file
// owns the mode's own chrome and its sweep; the ranking it reads lives in InspectRanking.kt, and the
// two cards it opens stay owned by their drawers.
// ─────────────────────────────────────────────────────────────────────────────

/** Title prefix of the mode's own candidate overlay. Registered in [OverlayZOrder]'s track band. */
internal const val INSPECT_TRACK_TITLE_PREFIX = "track_inspect_"

/**
 * The toggle's glyph: U+1F575 U+1F3FD — the sleuth, which reads as "what is under here", the very
 * question the mode answers. Both code points are needed: the detective alone renders monochrome, so
 * the skin-tone modifier is what makes it an emoji at the row's shared size.
 */
private const val INSPECT_GLYPH = "\uD83D\uDD75\uD83C\uDFFD"

/**
 * The inspect toggle: the fourth square of the row's own family, between the earth/water square and
 * the lock square. Armed it wears the app accent at the shared active alpha; disarmed the inactive
 * face.
 *
 * While armed it is always tappable — a filter change must not trap the user in a mode they cannot
 * switch off — so [enabled] only ever comes from the disarmed-and-nothing-inspectable gate, and a
 * disabled square simply carries no tap.
 */
@Composable
internal fun InspectToggleButton(
    armed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = if (armed) mapSurfaceFaceActive(ComposeColor(AppConfig.uiAccent))
    else mapSurfaceFaceInactive()
    MapToggleSquare(
        face = face,
        onClick = if (enabled || armed) onToggle else null,
        modifier = modifier
    ) {
        // Hard-coded like the row's other glyphs.
        Text(text = INSPECT_GLYPH, fontSize = TOP_TOGGLE_ICON_SIZE)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The inspect cursor — the merged walk over the frozen ladder (plan §5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The one walk an inspect-opened card steps through: the ladder frozen at the pick plus the index
 * it currently sits on — `(id, kind, distance)` and a position, held above the two drawers because
 * the ladder mixes markers and tracks where each drawer walks its own kind alone.
 *
 * The steps are pure: [step] answers the cursor on the next slot, or null when there is none, which
 * is what keeps a Prev/Next button dark until the pass knows there is a target that way.
 *
 * The cursor also carries the ladder's own [resolveMarker], so the walk and the lookup that opens a
 * marker slot cannot disagree: the ranking is built from the map-filtered set, and a shelf that is
 * narrower there must not be able to take a step's target out from under it (plan §5).
 */
internal data class InspectCursor(
    val ladder: List<InspectRank>,
    val index: Int,
    /**
     * The collection the ranking was built from, as a resolver rather than a copied entity: a held
     * entity would answer with the item as it stood when the ladder froze, so an edit would show a
     * stale card and a delete would resurrect one, while a resolver always answers with the item as
     * it stands now (and with nothing once it is gone). The line half needs no counterpart — the
     * ranking and the opener both load through the very same `loadTrackDetailCached`.
     */
    val resolveMarker: (String) -> UserMarker?
) {
    val current: InspectRank? get() = ladder.getOrNull(index)
    val canPrev: Boolean get() = index > 0
    val canNext: Boolean get() = index in 0 until ladder.lastIndex

    /** The ladder's ids, in walk order — what the track drawer's own buttons step through. */
    val ladderIds: List<String> get() = ladder.map { it.id }

    /**
     * The marker the slot at [at] opens, resolved in the ladder's own world: null when the ladder
     * holds no marker there, and null when that world no longer holds the id at all — the one case a
     * step may legitimately die on. Resolving anywhere else is what let a narrowed list filter kill a
     * step whose marker the sweep could see perfectly well (plan §5).
     */
    fun markerAt(at: Int = index): UserMarker? =
        ladder.getOrNull(at)?.takeIf { it.kind == InspectKind.MARKER }?.let { resolveMarker(it.id) }

    /** The cursor one slot along, or null at either end. */
    fun step(delta: Int): InspectCursor? {
        val target = index + delta
        return if (target in ladder.indices) copy(index = target) else null
    }

    companion object {
        /** The cursor a pick lands on: the picked slot of its own frozen ladder. */
        fun at(
            ladder: List<InspectRank>,
            pickedId: String,
            resolveMarker: (String) -> UserMarker?
        ): InspectCursor? {
            val idx = ladder.indexOfFirst { it.id == pickedId }
            return if (idx < 0) null else InspectCursor(ladder, idx, resolveMarker)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The step's hand-off: the successor, the predecessor held for it, and its landing (plan §5)
// ─────────────────────────────────────────────────────────────────────────────

/** What an inspect open is flying towards: the successor's own id and its kind. */
internal data class InspectTarget(val id: String, val kind: InspectKind)

/**
 * The successor a step or a pick is opening, and the predecessor card [held] on screen until that
 * successor lands — null when there was none to hold, or when the predecessor was not the mode's own
 * card and therefore had to close at the step.
 *
 * One value owns "an open is in flight". There is no separate in-flight flag to leave stuck true: the
 * successor landing and the open being abandoned are both simply this becoming null, and a predecessor
 * is never held without a successor on its way to release it.
 */
internal data class InspectHandoff(val target: InspectTarget, val held: InspectKind?)

/**
 * True when the successor [target] is actually on screen — its own provenance, never "some card is
 * open".
 *
 * A marker successor is the marker card showing that id, a track successor the track drawer open on
 * that id. That id is the whole narrowing: the predecessor of a same-type step is the *same kind* of
 * card still on screen showing the *previous* id, so "a Viewing card exists" fires at the step itself,
 * spends the in-flight mark, disarms the mode early and then reads the successor's own arrival as the
 * user having closed the card (plan §5).
 */
internal fun inspectLanded(
    target: InspectTarget,
    /** The id the marker card is showing, or null while no marker card is open. */
    viewingMarkerId: String?,
    /** Whether the track drawer is open, and the id it holds. */
    trackOpen: Boolean,
    trackId: String?
): Boolean = when (target.kind) {
    InspectKind.MARKER -> viewingMarkerId == target.id
    InspectKind.TRACK -> trackOpen && trackId == target.id
}

/**
 * The predecessor card an inspect open holds on screen for its successor, or null when there is none
 * to hold (plan §5).
 *
 * Only the mode's own card may be held, and the plain answer is the other kind: a step of the merged
 * ladder is the one open that finds a card already on screen, and that card is the predecessor whose
 * close would empty the one selected-item slot. Each provenance is read where the card keeps it — the
 * marker card's source, the track card's ladder — so a list- or map-opened card is never held: its
 * close restores the pre-navigation frame, and running that restore after the successor's own camera
 * would yank the frame the open had just set.
 */
internal fun inspectHeldCard(
    /** The kind of card this open is about to put on screen. */
    opening: InspectKind,
    /** Whether a marker card is on screen, and whether the mode's own open put it there. */
    markerCardOpen: Boolean,
    markerCardInspectSourced: Boolean,
    /** Whether the track card is on screen, and whether it carries the frozen ladder. */
    trackCardOpen: Boolean,
    trackCardInspectSourced: Boolean
): InspectKind? = when (opening) {
    InspectKind.TRACK -> if (markerCardOpen && markerCardInspectSourced) InspectKind.MARKER else null
    InspectKind.MARKER -> if (trackCardOpen && trackCardInspectSourced) InspectKind.TRACK else null
}

// ─────────────────────────────────────────────────────────────────────────────
// The mode's exit: the retained capture and when it lands (plan §6)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Whether an inspect exit is the mode's *single* exit — the one that applies the capture taken at
 * arming (plan §6).
 *
 * A step of the merged ladder closes its predecessor at the successor's landing, the toggle may be
 * pressed with a card still standing, and an abandonment (a step that dies, an open that lands
 * nothing) leaves the predecessor up with the mode still armed: in all three windows an inspect card
 * is open or the mode is armed, so the capture waits. Whichever exit runs with neither left applies
 * it, once — the card is what carries the mode's intent past the armed half, not the armed flag.
 */
internal fun inspectLastExit(inspectArmed: Boolean, inspectCardOpen: Boolean): Boolean =
    !inspectArmed && !inspectCardOpen

/**
 * Whether the mode's single exit re-pins the map to the boat, or hands the centre back on the
 * ordinary delay instead (plan §6).
 *
 * A map that was following at arming re-pins in the same frame, restoring what the capture recorded.
 * A map already panned at arming keeps the position the user swept to and starts a full delay from
 * this moment — never the remainder of the timer the mode cancelled, which would snap the boat back
 * the instant the card closes. A map the user moved *after* the card opened is the canonical case:
 * the close leaves the frame where they put it, exactly as a list-opened card does, so the capture is
 * spent on the ordinary delay rather than on a snap the user did not ask for.
 */
internal fun inspectExitRePins(
    capturedSuppressed: Boolean,
    capturedTimerLive: Boolean,
    mapMovedByUser: Boolean
): Boolean = !mapMovedByUser && !capturedSuppressed && !capturedTimerLive

// ─────────────────────────────────────────────────────────────────────────────
// The trigger clock — one wait over one key (plan §5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The one key the trigger clock ticks on: the candidate under the gold, the single activity tick,
 * and whether a panel owns the screen.
 *
 * Everything that must cancel a running wait folds in here. A candidate arrives or leaves (the
 * viewport emptied, or found another item), the user did something ([activity] — the lift itself, a
 * pan, a zoom, the screen lock, a layer chip, a settings write) or a panel took the screen: each of
 * them is a new key, so the clock needs no counter of its own per input.
 */
internal data class InspectDwellKey(
    /** The highlighted candidate, or null when the viewport holds none. */
    val candidateId: String?,
    /** The single activity tick: any map motion, and everything else the user did. */
    val activity: Int,
    /** True while a panel owns the screen — the menu, settings, either list or the layer fan. */
    val panelOpen: Boolean,
    /**
     * The lift latch: false until a genuine finger lift arrives in this armed session, true from then
     * on. It is a key of its own so that a tap that never moved the map still changes the key the
     * clock debounces — on such a tap the candidate, the activity tick and the panel state are all
     * still, and with the lift held outside the key no new key would ever reach the clock, so the
     * release the mode exists to answer could never pick (plan §5).
     */
    val lift: Boolean
)

/**
 * The trigger clock as one wait: a debounce over [keys], so it only ever expires after [dwellMs] in
 * which no new key arrived, and every key restarts it from the full dwell with nothing carried over.
 *
 * A key that completes while a panel is open is dropped and never reaches the pick — the panel owns
 * the screen — which also means the wait the panel interrupted is gone rather than paused: the key
 * emitted when the panel closes starts a full fresh wait instead of resuming the suspended one.
 * Android-free, so both halves are unit-tested without a device.
 */
@OptIn(FlowPreview::class)
internal fun inspectDwell(keys: Flow<InspectDwellKey>, dwellMs: Long): Flow<InspectDwellKey> =
    keys.debounce(dwellMs).filter { !it.panelOpen }

// ─────────────────────────────────────────────────────────────────────────────
// Anchor, warning set and warming
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The anchor: the geo point under the centre marker.
 *
 * **Checked in code (plan §2):** osmdroid 6.1.18's `MapView.getMapCenter()` is
 * `getProjection().fromPixels(width/2, height/2)` — the *plain* screen centre — while
 * `setMapCenterOffset` is consumed by `Projection.getScreenCenterX/Y`, which anchors the map's own
 * centre at `centre + offset`. So `mapView.mapCenter` does **not** carry the offset, and with a
 * non-zero offset it is a point ahead of the boat rather than under the marker. The plan's
 * documented fallback is therefore the implementation, not an alternative.
 */
internal fun inspectAnchor(mapView: MapView, centerOffsetPx: Int): LatLng? {
    val projection = mapView.projection ?: return null
    val geo = projection.fromPixels(mapView.width / 2, mapView.height / 2 + centerOffsetPx) ?: return null
    return LatLng(geo.latitude, geo.longitude)
}

/**
 * The inspectable set: the map-filtered items whose layer is showing, warmed once at arming.
 *
 * The render cap is not a restriction — a filtered track beyond it is a legitimate pick and the
 * mode's own overlay draws it — while a hidden marker layer or hidden tracks contribute nothing, so
 * the mode can never resurrect what the user switched off. The live recording is excluded: it has
 * no card behind it.
 *
 * This is also where every candidate's bbox is derived (plan §2): a point's own position, a line's
 * extent over the very points the metric measures, both cached on the candidate so the per-frame
 * eligibility test never walks geometry again.
 */
internal suspend fun warmInspectCandidates(
    markers: List<UserMarker>,
    trackIds: List<String>,
    loadTrack: suspend (String) -> ykws.android.maro.data.track.Track?
): List<InspectCandidate> {
    val markerCandidates = markers.map { marker ->
        when (val geometry = marker.geometry) {
            is MarkerGeometry.Pin -> InspectCandidate.point(marker.id, InspectKind.MARKER, geometry.position)
            is MarkerGeometry.Circle -> InspectCandidate.point(marker.id, InspectKind.MARKER, geometry.center)
            // A corridor measures to its centre line: the band's width is not distance.
            is MarkerGeometry.Corridor -> InspectCandidate.line(
                marker.id,
                InspectKind.MARKER,
                listOf(TrackPoint(geometry.p1.latitude, geometry.p1.longitude), TrackPoint(geometry.p2.latitude, geometry.p2.longitude))
            )
        }
    }
    val trackCandidates = trackIds.mapNotNull { id ->
        val track = loadTrack(id) ?: return@mapNotNull null
        if (track.trackPoints.isEmpty()) return@mapNotNull null
        InspectCandidate.line(id, InspectKind.TRACK, track.trackPoints)
    }
    return markerCandidates + trackCandidates
}

// ─────────────────────────────────────────────────────────────────────────────
// The mode's effects: warm, sweep, trigger, overlay
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Everything the mode does to the map, in one place:
 *
 * - **warming** the layer-visible filtered geometry once at arming, off the UI thread, so a drag
 *   frame never reads a file and no candidate's bbox is derived twice (plan §3);
 * - the **sweep**, an O(N) minimum scan per frame off the UI thread, where the only eligible
 *   candidates are those whose cached bbox overlaps the viewport: stale motion ticks are conflated
 *   and a scan in flight is never cancelled, so every tick publishes and the previous highlight
 *   stands until its successor lands (plan §2, §3);
 * - the **trigger clock**, purely the clock: a dwell [AppConfig.uiMapInspectDwellMs] of an idle map
 *   after a genuine finger lift, with a candidate on screen, no panel open and nothing already
 *   picked — where the idleness is the user's own, folded into one activity tick;
 * - the **candidate overlay**, the mode's own single line, built from the *same* plan inputs the
 *   canonical rebuild reads ([trackArrows], [trackColours], [eyeOverride]) so the preview and the
 *   committed selection are one look rather than two.
 *
 * The quiescence is structural rather than measured: the clock is one debounce over one key holding
 * the candidate, the activity tick and the panel state, so a pan, a zoom, a fling, a lift, a lock
 * flip, a chip, a settings write and a changed candidate each restart it, and while a panel owns the
 * screen the wait cannot complete into a pick. Nothing but a genuine lift opens the clock — the
 * arming seed has none, so the gold goes live before any touch and picks nothing (plan §5).
 */
@Composable
internal fun MapInspectEffects(
    mapView: MapView?,
    armed: Boolean,
    centerOffsetPx: Int,
    markers: List<UserMarker>,
    trackIds: List<String>,
    trackViewModel: TrackViewModel,
    /** Bumped at every genuine finger lift: the only event that may start the dwell clock. */
    liftId: Int,
    /**
     * Bumped by every other thing the user did that is neither map motion nor a panel change — the
     * screen-lock square, a layer chip, a settings write. One input rather than a reset of its own
     * scattered through the map's call sites; the mode folds it with the map's own motion below.
     */
    activityId: Int,
    /** True while a panel owns the screen: the menu, settings, either list or the layer fan. */
    panelOpen: Boolean,
    highlightedTrackId: String?,
    rebuildGeneration: Int,
    /** The canonical plan inputs the committed selection is painted from (plan §4). */
    trackArrows: Boolean,
    trackColours: Boolean,
    eyeOverride: Boolean?,
    onSweep: (InspectRank?) -> Unit,
    onPick: (InspectRank, List<InspectRank>) -> Unit
) {
    // ── Warm geometry: once per armed session and per candidate set ──────────
    var warm by remember { mutableStateOf<List<InspectCandidate>>(emptyList()) }
    LaunchedEffect(armed, markers, trackIds) {
        warm = if (!armed) emptyList()
        else withContext(Dispatchers.Default) {
            warmInspectCandidates(markers, trackIds, trackViewModel::loadTrackDetailCached)
        }
    }

    // ── Activity: the one tick the trigger clock keys on ─────────────────────
    // Map motion arrives through the listeners below and everything else the user did arrives as
    // [activityId]; both fold into this single tick, so the clock has one activity input rather than
    // a counter of its own per source (plan §5).
    val activityTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(activityId) { activityTick.intValue++ }

    // ── Motion: the raw centre, the viewport, and the tick every consumer keys on ──
    // The anchor is the projection read with the offset, exactly as it stands; the viewport is the
    // projection's own visible bounds, which is the whole of the sweep's eligibility test (plan §2).
    val anchor = remember { mutableStateOf<LatLng?>(null) }
    val viewport = remember { mutableStateOf<InspectBounds?>(null) }
    val motionTick = remember { mutableIntStateOf(0) }
    val offsetState = rememberUpdatedState(centerOffsetPx)
    DisposableEffect(mapView, armed) {
        val mv = mapView
        if (mv == null || !armed) {
            onDispose { }
        } else {
            // One motion handler for pan and zoom alike: both reset the trigger clock, and both the
            // anchor and the viewport are re-read from the projection on every one of them.
            fun refreshMotion() {
                anchor.value = inspectAnchor(mv, offsetState.value)
                viewport.value = mv.boundingBox?.let {
                    InspectBounds(it.latNorth, it.lonEast, it.latSouth, it.lonWest)
                }
                motionTick.intValue++
                // A motion is something the user did, so it restarts the clock's wait as well as
                // re-ranking the sweep.
                activityTick.intValue++
            }
            val listener = object : MapListener {
                override fun onScroll(event: ScrollEvent): Boolean {
                    refreshMotion()
                    return false
                }

                override fun onZoom(event: ZoomEvent): Boolean {
                    refreshMotion()
                    return false
                }
            }
            mv.addMapListener(listener)
            // Seed immediately: arming must sweep before the user touches anything, so the gold is
            // live on the first frame. The seed carries no lift, so it starts no clock.
            refreshMotion()
            onDispose { mv.removeMapListener(listener) }
        }
    }

    // ── Sweep: the closest candidate whose box overlaps the viewport, always published ──
    // A tick-driven pipeline, so an empty viewport publishes its own null (the gold clears) and the
    // scan then stands idle until the map moves again — nothing polls the world in between. The
    // conflate-and-never-cancel half lives in `inspectSweep`, unit-tested without a device (plan §3).
    val highlight = remember { mutableStateOf<InspectRank?>(null) }
    LaunchedEffect(armed, warm) {
        if (!armed || warm.isEmpty()) {
            highlight.value = null
            onSweep(null)
            return@LaunchedEffect
        }
        inspectSweep(snapshotFlow { motionTick.intValue }) {
            withContext(Dispatchers.Default) {
                val a = anchor.value ?: return@withContext null
                val v = viewport.value ?: return@withContext null
                InspectRanking.nearest(a, warm, v)
            }
        }.collect { found ->
            highlight.value = found
            onSweep(found)
        }
    }

    // ── Trigger clock: a lift, then the dwell on a map that has come to rest ──
    val dwellMs = AppConfig.uiMapInspectDwellMs
    val lastPickedId = remember { mutableStateOf<String?>(null) }
    // The lift is the instruction and the quiet is the confirmation: only a genuine finger lift may
    // start the clock, so a resting finger never dries it out and the arming seed picks nothing. The
    // whole flag is reset per armed session, as is the no-re-fire guard: the pick's own camera move
    // is a pan event and would otherwise reopen the same item, while a fresh session must stay free
    // to pick it.
    val liftSeen = remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        lastPickedId.value = null
        liftSeen.value = false
    }
    var seenLiftId by remember { mutableIntStateOf(liftId) }
    LaunchedEffect(armed, liftId) {
        if (!armed) {
            seenLiftId = liftId
            return@LaunchedEffect
        }
        // Arming is not a lift: only a lift that arrives while armed opens the clock.
        if (liftId == seenLiftId) return@LaunchedEffect
        seenLiftId = liftId
        liftSeen.value = true
    }
    // The clock itself is one debounced key: a candidate change, any activity the user performed, a
    // lift and a panel taking or giving back the screen are all the same event to it — a new key,
    // which restarts the wait from the full dwell. The lift rides in the key as the latch itself,
    // because a tap that never moved the map moves nothing else: [panelOpen] is a key of the effect
    // as well as part of the key it emits — opening a panel rebuilds the flow, so the wait it
    // interrupted is gone, and closing it starts a fresh wait rather than resuming the suspended one.
    LaunchedEffect(armed, warm, panelOpen) {
        if (!armed || warm.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            InspectDwellKey(
                candidateId = highlight.value?.id,
                activity = activityTick.intValue,
                panelOpen = panelOpen,
                lift = liftSeen.value
            )
        }.let { inspectDwell(it, dwellMs) }
            .collect { key ->
                val candidate = highlight.value ?: return@collect
                // The key the wait completed on is the candidate still under the gold: a later one
                // would have arrived as a new key and restarted the wait.
                if (candidate.id != key.candidateId) return@collect
                // The key's own lift, not the latch as it stands now: the wait that completed has to
                // be the one a lift opened (plan §5).
                if (!key.lift) return@collect
                if (candidate.id == lastPickedId.value) return@collect
                lastPickedId.value = candidate.id
                // The ladder is a single bounded pass over the same warm geometry, with the anchor
                // snapshotted at this moment, so the sequence is reproducible and never re-ranks.
                val ladder = withContext(Dispatchers.Default) {
                    val a = anchor.value ?: return@withContext emptyList()
                    InspectRanking.rank(a, warm)
                }
                onPick(candidate, ladder)
            }
    }

    // ── The mode's own overlay: one line, swapped in one non-suspending block ──
    LaunchedEffect(
        armed, highlight.value?.id, highlight.value?.kind, warm,
        rebuildGeneration, highlightedTrackId
    ) {
        val mv = mapView ?: return@LaunchedEffect
        val candidate = highlight.value
        // The commit path: once the canonical rebuild paints the picked track, the mode drops its
        // own line in the same block that would have re-stacked it — no flash, one author.
        val owned = mv.overlays.filter { overlay ->
            (overlay as? Polyline)?.title?.startsWith(INSPECT_TRACK_TITLE_PREFIX) == true
        }
        if (!armed || candidate == null || candidate.kind != InspectKind.TRACK ||
            candidate.id == highlightedTrackId
        ) {
            if (owned.isNotEmpty()) {
                mv.overlays.removeAll(owned)
                mv.invalidate()
            }
            return@LaunchedEffect
        }
        val points = (warm.firstOrNull { it.id == candidate.id }?.geometry as? InspectGeometry.Line)?.points
        if (points.isNullOrEmpty()) {
            if (owned.isNotEmpty()) {
                mv.overlays.removeAll(owned)
                mv.invalidate()
            }
            return@LaunchedEffect
        }
        // The selection's own look, from the same style functions the canonical path uses *and the
        // same plan inputs*, so the swap when the card opens is invisible: gold only where a
        // selection would be gold, banded where it would be banded. The chevrons are ignored while
        // sweeping — no TrackDirectionOverlay work mid-gesture — which is why the preview line is
        // built from the plan's strokes and never from its arrow overlay.
        val rendering = storedTrackRendering(
            points = points,
            title = "$INSPECT_TRACK_TITLE_PREFIX${candidate.id}",
            plan = trackRenderPlan(trackArrows, trackColours, selected = true, eyeOverride = eyeOverride),
            ramp = AppConfig.trackHeatmapRamp,
            strokeWidth = AppConfig.trackWidthSelected,
            fade = 1f,
            // The gold path never reads this — it is built on demand for the plain path alone.
            plainAppearance = { selectedTrackCasing() }
        )
        val fresh = rendering.overlays
        // Warm geometry is in memory by now, so the swap itself never suspends: remove, add and
        // invalidate land together — a suspension here would blank tracks for a frame.
        mv.overlays.removeAll(owned)
        mv.overlays.addAll(fresh)
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }
}
