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
// The mode answers "what is nearest me?": while armed it re-ranks the inspectable items on every
// sweep and opens the nearest one's dashboard at once, swapping it live as the nearest changes and
// closing it when nothing is left to acquire. The quiet that follows recentres the camera onto the
// acquired item and freezes the ladder its walk steps. The acquire point follows the user's own drag and
// is held across the mode's own recentre, so the recentre moves the view and nothing else. This file owns the
// mode's own chrome, its sweep, its quiet clock and its recentre; the ranking it reads lives in
// InspectRanking.kt, and the two cards it opens stay owned by their drawers.
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
 * The inspect toggle: one square of the row's own family, whose order and visibility live in
 * [TopToggleControl] rather than here. `inspectFace` (`MapToggleFace.kt`) resolves the square's two
 * channels: off is the shared pale face with no dot, armed the app accent with the complete-data green
 * dot.
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
    val face = inspectFace(armed)
    MapToggleSquare(
        face = face.toSurfaceFace(),
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
 * The one key the quiet clock ticks on: the acquired item under the gold, the single activity tick,
 * and whether a panel owns the screen.
 *
 * Everything that must cancel a running wait folds in here. A candidate arrives or leaves (the
 * viewport emptied, or found another item), the user did something ([activity] — a pan, a zoom, the
 * screen lock, a layer chip, a settings write) or a panel took the screen: each of them is a new key,
 * so the clock needs no counter of its own per input. There is no lift in the key any more (plan §1):
 * the open is live and the quiet only decides when the camera is recentred.
 */
internal data class InspectDwellKey(
    /** The acquired candidate, or null when the viewport holds none. */
    val candidateId: String?,
    /** The single activity tick: any map motion, and everything else the user did. */
    val activity: Int,
    /** True while a panel owns the screen — the menu, settings, either list or the layer fan. */
    val panelOpen: Boolean
)

/**
 * The quiet clock as one wait: a debounce over [keys], so it only ever expires after [dwellMs] in
 * which no new key arrived, and every key restarts it from the full dwell with nothing carried over.
 *
 * A key that completes while a panel is open is dropped and never reaches the recentre — the panel
 * owns the screen — which also means the wait the panel interrupted is gone rather than paused: the
 * key emitted when the panel closes starts a full fresh wait instead of resuming the suspended one.
 * Android-free, so both halves are unit-tested without a device.
 */
@OptIn(FlowPreview::class)
internal fun inspectDwell(keys: Flow<InspectDwellKey>, dwellMs: Long): Flow<InspectDwellKey> =
    keys.debounce(dwellMs).filter { !it.panelOpen }

/**
 * The activity tick after a map motion (plan §3): the user's own motion restarts the quiet, while the
 * mode's own recentre does not. The mark is the whole of the rule — while it holds, the key the clock
 * debounces is left exactly as it was, so the quiet the recentre just spent cannot start again.
 */
internal fun inspectActivityAfterMotion(activity: Int, recentring: Boolean): Int =
    if (recentring) activity else activity + 1

// ─────────────────────────────────────────────────────────────────────────────
// The live acquire and its reset boundary (plan §1)
// ─────────────────────────────────────────────────────────────────────────────

/** What the live acquire does with the sweep's answer (plan §1). */
internal enum class InspectAcquire {
    /** Open the answer's dashboard: the nearest is identified and no panel of the mode's stands. */
    OPEN,

    /** Close the panel: the anchor lost its target, while the mode stays armed. */
    CLOSE,

    /** Nothing to do: the answer is already on screen, or an open of its own is still in flight. */
    NONE
}

/**
 * The live acquire as one rule (plan §1): the panel's presence is the target's presence. An open in
 * flight owns the slot until it lands, the anchor losing its target closes the panel without disarming,
 * and an answer already on screen is no change at all — which is what keeps a live swap from reopening
 * its own panel on every sweep.
 */
internal fun inspectAcquireAction(
    /** The acquired item's id, or null when the viewport holds none. */
    rankId: String?,
    /** Whether the mode's card is on screen. */
    cardOpen: Boolean,
    /** Whether an open is in flight: its successor owns the slot until it lands. */
    openInFlight: Boolean,
    /** The id the standing card shows, or null while none stands. */
    showingId: String?
): InspectAcquire = when {
    openInFlight -> InspectAcquire.NONE
    rankId == null -> if (cardOpen) InspectAcquire.CLOSE else InspectAcquire.NONE
    showingId == rankId -> InspectAcquire.NONE
    else -> InspectAcquire.OPEN
}

/**
 * The acquired item, with the margin that damps the panel's churn (plan §1, 2026-10-07): the mode keeps
 * the item it holds unless a new nearest is closer by more than [marginFraction] of that item's own
 * distance.
 *
 * Without the margin two candidates at nearly the same distance hand the panel back and forth for as
 * long as their distances stay close, and every handover replays the card swap — the flicker the margin
 * exists to remove. A fraction of the held distance rather than a fixed number of metres, so it damps a
 * jitter between two candidates near the boat without making the mode deaf to a genuinely closer item
 * farther out.
 *
 * @param held the item the panel is on, or null before the first acquisition.
 * @param heldDistanceM that item's distance **as it stands now**, or null once it has left the candidate
 *   set — in which case the panel hands over rather than sitting on something the world has dropped.
 * @param nearest the true closest, or null when the viewport holds none (the panel closes).
 */
internal fun inspectHoldSelection(
    held: InspectRank?,
    heldDistanceM: Double?,
    nearest: InspectRank?,
    marginFraction: Double
): InspectRank? = when {
    nearest == null -> null
    held == null || heldDistanceM == null -> nearest
    nearest.id == held.id -> nearest
    nearest.distanceM < heldDistanceM * (1.0 - marginFraction) -> nearest
    else -> held.copy(distanceM = heldDistanceM)
}

/** What a settled centre change does to the mode (plan §1). */
internal enum class InspectUserMove {
    /** Not the user's move, or nothing to make of it: the mode's own recentre, or a busy camera. */
    NONE,

    /** Before the recentre: the quiet restarts, and the exit leaves the frame where it stands. */
    REMEMBER,

    /** After the recentre has landed: the drag is the reset — the card, the ladder and the capture. */
    RESET
}

/**
 * The reset boundary (plan §1): once the mode's own recentre has landed, a user drag forgets
 * everything; **before** it, the drag is an ordinary move and only restarts the quiet. The mode's own
 * move is excluded — the mark is what tells the two apart — and so is any window in which an open or a
 * camera of the mode's own is still running.
 */
internal fun inspectUserMoveAction(
    armed: Boolean,
    /** True while the mode's own recentre is in flight: its scroll events are not the user's. */
    recentring: Boolean,
    /** True while an open is in flight: its landing owns the camera and the slot. */
    handoffInFlight: Boolean,
    /** True while a navigate target or a zoom-to-fit is pending. */
    cameraBusy: Boolean,
    /** True once the mode's own recentre has landed. */
    recentreLanded: Boolean
): InspectUserMove = when {
    !armed || recentring || handoffInFlight || cameraBusy -> InspectUserMove.NONE
    recentreLanded -> InspectUserMove.RESET
    else -> InspectUserMove.REMEMBER
}

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
 *   frame never reads a file and no candidate's bbox is derived twice (plan §2);
 * - the **sweep**, an O(N) minimum scan per frame off the UI thread, where the only eligible
 *   candidates are those whose cached bbox overlaps the viewport: stale motion ticks are conflated
 *   and a scan in flight is never cancelled, so every tick publishes and the previous highlight
 *   stands until its successor lands. Each publish is the mode's **live acquire** — the acquired item,
 *   or null — which the caller turns into the panel's presence (plan §1, §2), and a new nearest takes
 *   it over only by the margin `AppConfig.uiMapInspectSwitchMarginPct` settles, so two candidates at
 *   nearly the same distance cannot hand the panel back and forth;
 * - the **quiet clock**, purely the quiet: a dwell [AppConfig.uiMapInspectDwellMs] of an idle map with
 *   a target acquired under the gold and no panel open, whose expiry is the **recentre** — the camera
 *   moved onto the acquired item and the ladder frozen at that instant (plan §1, §2);
 * - the **candidate overlay**, the mode's own single line, built from the *same* plan inputs the
 *   canonical rebuild reads ([trackArrows], [trackColours], [eyeOverride]) so the preview and the
 *   committed selection are one look rather than two.
 *
 * The quiescence is structural rather than measured: the clock is one debounce over one key holding
 * the candidate, the activity tick and the panel state, so a pan, a zoom, a fling, a lock flip, a
 * chip, a settings write and a changed candidate each restart it, and while a panel owns the screen
 * the quiet cannot complete into a recentre. The mode's own recentre is excluded from that activity
 * ([recentring]) so the camera move cannot restart the very quiet that spent it (plan §3).
 */
@Composable
internal fun MapInspectEffects(
    mapView: MapView?,
    armed: Boolean,
    /**
     * The map's live centre offset in px, taken as a provider so the band's animation re-runs the
     * map's own readers rather than this call site's argument (F6).
     */
    centerOffsetPx: () -> Int,
    markers: List<UserMarker>,
    trackIds: List<String>,
    trackViewModel: TrackViewModel,
    /**
     * Bumped by every other thing the user did that is neither map motion nor a panel change — the
     * screen-lock square, a layer chip, a settings write. One input rather than a reset of its own
     * scattered through the map's call sites; the mode folds it with the map's own motion below.
     */
    activityId: Int,
    /** True while a panel owns the screen: the menu, settings, either list or the layer fan. */
    panelOpen: Boolean,
    /**
     * True from the moment the pause's recentre is issued until it lands: the mode's own camera move.
     * Its scroll events must not be read as the user's activity, or the quiet would restart on the
     * very move that spent it (plan §3).
     */
    recentring: Boolean,
    highlightedTrackId: String?,
    rebuildGeneration: Int,
    /** The canonical plan inputs the committed selection is painted from (plan §4). */
    trackArrows: Boolean,
    trackColours: Boolean,
    eyeOverride: Boolean?,
    /** The acquired item — the true closest each sweep — or null when the viewport holds none. */
    onSweep: (InspectRank?) -> Unit,
    /**
     * The pause: the quiet has elapsed with [target] under the gold, so the mode recentres the camera
     * onto it and freezes [ladder] — the one bounded pass over the warm geometry, ranked from the mode's
     * own reference rather than from the camera the recentre is about to move (plan §2).
     */
    onRecentre: (target: InspectRank, ladder: List<InspectRank>) -> Unit
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

    // ── Motion: the mode's reference, the live viewport, and the tick consumers key on ──
    // **The reference follows the user's own motion and nothing else**: a drag re-establishes the acquire
    // point at the geo point under the centre marker, while the pause's recentre moves the view alone —
    // the mark is what tells the two apart, so the mode's own camera move never drags the acquire point
    // with it (plan §1, 2026-10-07). The viewport is the projection's own visible bounds, read fresh on
    // every scan, and it is the whole of the sweep's eligibility test (plan §2): the map moving changes
    // *which* items are candidates, and the user's drag that moves it also moves the point they are ranked
    // from — the acquisition starts again from where they leave the map.
    val reference = remember { mutableStateOf<LatLng?>(null) }
    val motionTick = remember { mutableIntStateOf(0) }
    val offsetState = rememberUpdatedState(centerOffsetPx)
    // The mode's own recentre, read inside the listener so the latest value is seen: its scroll events
    // must not be counted as the user's activity (plan §3).
    val recentringState = rememberUpdatedState(recentring)
    DisposableEffect(mapView, armed) {
        val mv = mapView
        if (mv == null || !armed) {
            reference.value = null
            onDispose { }
        } else {
            // One motion handler for pan and zoom alike: a user's motion re-establishes the acquire point —
            // the geo point under the centre marker as they leave it — and restarts the quiet. The mode's
            // own recentre does neither, so the acquire point stays where the user last put it (plan §1).
            fun refreshMotion() {
                if (!recentringState.value) reference.value = inspectAnchor(mv, offsetState.value())
                motionTick.intValue++
                // A motion is something the user did, so it restarts the clock's wait as well as
                // re-ranking the sweep. The mode's own recentre is the one exception: its scroll events
                // arrive while the mark holds, and counting them would restart the very quiet the
                // recentre just spent (plan §3).
                activityTick.intValue = inspectActivityAfterMotion(activityTick.intValue, recentringState.value)
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
    // How much closer a new nearest must be before it takes the panel over (plan §1, 2026-10-07).
    val switchMargin = AppConfig.uiMapInspectSwitchMarginPct.coerceIn(0, 90) / 100.0
    LaunchedEffect(armed, warm) {
        if (!armed || warm.isEmpty()) {
            highlight.value = null
            onSweep(null)
            return@LaunchedEffect
        }
        inspectSweep(snapshotFlow { motionTick.intValue }) {
            // The viewport is read here rather than remembered, so every scan sees the map as it stands —
            // including the first scan after arming, which no motion has announced yet.
            val v = mapView?.boundingBox?.let {
                InspectBounds(it.latNorth, it.lonEast, it.latSouth, it.lonWest)
            }
            val ref = reference.value
            if (v == null || ref == null) null
            else withContext(Dispatchers.Default) {
                val nearest = InspectRanking.nearest(ref, warm, v)
                // The held item's distance as it stands now, so the margin is judged against the truth
                // rather than against the figure it carried when it was still the closest.
                val heldDistance = highlight.value?.id?.let { id ->
                    warm.firstOrNull { it.id == id }?.let { InspectRanking.distance(ref, it) }
                }
                nearest to heldDistance
            }
        }.collect { found ->
            val (nearest, heldDistance) = found ?: (null to null)
            val selection = inspectHoldSelection(highlight.value, heldDistance, nearest, switchMargin)
            highlight.value = selection
            onSweep(selection)
        }
    }

    // ── The quiet clock: the pause that recentres onto the acquired item ─────
    // The clock is one debounced key — a candidate change, any activity the user performed and a panel
    // taking or giving back the screen are all the same event to it: a new key, which restarts the
    // wait from the full dwell. When it expires with the acquired item still under the gold, the mode
    // moves the camera onto that item and freezes its ladder (plan §2): the one bounded pass over the
    // warm geometry, ranked from the mode's fixed reference, so the sequence is reproducible and never
    // re-ranks. The mode marks its own recentre — [recentring] — so the camera move cannot restart
    // this very clock: its scroll events arrive while the mark holds and are not user activity.
    val dwellMs = AppConfig.uiMapInspectDwellMs
    LaunchedEffect(armed, warm, panelOpen) {
        if (!armed || warm.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            InspectDwellKey(
                candidateId = highlight.value?.id,
                activity = activityTick.intValue,
                panelOpen = panelOpen
            )
        }.let { inspectDwell(it, dwellMs) }
            .collect { key ->
                val candidate = highlight.value ?: return@collect
                // The key the wait completed on is the candidate still under the gold: a later one
                // would have arrived as a new key and restarted the wait.
                if (candidate.id != key.candidateId) return@collect
                // The ladder is a single bounded pass over the same warm geometry, ranked from the
                // mode's fixed reference, so the sequence is reproducible and never re-ranks — and the
                // recentre that follows it cannot change what the walk steps through.
                val ladder = withContext(Dispatchers.Default) {
                    val ref = reference.value ?: return@withContext emptyList()
                    InspectRanking.rank(ref, warm)
                }
                onRecentre(candidate, ladder)
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
            points = points.toRenderPoints(),
            title = "$INSPECT_TRACK_TITLE_PREFIX${candidate.id}",
            plan = lineRenderPlan(trackArrows, trackColours, selected = true, eyeOverride = eyeOverride),
            ramp = AppConfig.trackHeatmapRamp,
            strokeWidth = AppConfig.trackWidthSelectedDp,
            density = mv.paintDensity,
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
