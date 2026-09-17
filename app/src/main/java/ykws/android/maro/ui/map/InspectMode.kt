package ykws.android.maro.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.TrackViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Inspect mode — the sleuth square, the ring, the cursor and the sweep
//
// The mode lets the user drag the map until the target sits under the centre marker, see the range
// being searched, and open the item without leaving the map or moving the camera. This file owns
// the mode's own chrome and its sweep; the ranking it reads lives in InspectRanking.kt, and the two
// cards it opens stay owned by their drawers.
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

/**
 * The ring: a circle of the pick radius around the centre marker, drawn while the mode is armed so
 * it declares its reach before any touch. Layer 0, behind the boat image — it is the first overlay
 * in that cluster, so the boat and the cap arrow paint over it.
 *
 * The radius arrives already derived: [inspectRadiusDp] is the single derivation the pick gate also
 * reads, so the drawn circle and the gate cannot drift apart. Colour, weight and alpha are all
 * tokens: the accent for the colour, and the palette's own ring weight and alpha beside the map
 * surface family, so the ring is tuned where every other map chrome value is.
 */
@Composable
internal fun InspectRingOverlay(
    radiusDp: Float,
    centerOffsetYDp: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val ringColor = ComposeColor(AppConfig.uiAccent)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawCircle(
            color = ringColor.copy(alpha = AppConfig.uiMapInspectRingAlpha),
            radius = radiusDp.dp.toPx(),
            center = Offset(size.width / 2f, size.height / 2f + centerOffsetYDp.toPx()),
            style = Stroke(width = AppConfig.uiMapInspectRingWidthDp.dp.toPx())
        )
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
 */
internal data class InspectCursor(
    val ladder: List<InspectRank>,
    val index: Int
) {
    val current: InspectRank? get() = ladder.getOrNull(index)
    val canPrev: Boolean get() = index > 0
    val canNext: Boolean get() = index in 0 until ladder.lastIndex

    /** The ladder's ids, in walk order — what the track drawer's own buttons step through. */
    val ladderIds: List<String> get() = ladder.map { it.id }

    /** The cursor one slot along, or null at either end. */
    fun step(delta: Int): InspectCursor? {
        val target = index + delta
        return if (target in ladder.indices) copy(index = target) else null
    }

    companion object {
        /** The cursor a pick lands on: the picked slot of its own frozen ladder. */
        fun at(ladder: List<InspectRank>, pickedId: String): InspectCursor? {
            val idx = ladder.indexOfFirst { it.id == pickedId }
            return if (idx < 0) null else InspectCursor(ladder, idx)
        }
    }
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
 *   frame never reads a file (plan §3);
 * - the **sweep**, an O(N) minimum scan per frame, off the UI thread and conflated to at most one
 *   result per frame;
 * - the **trigger clock**, purely the clock: a dwell [AppConfig.uiMapInspectDwellMs] after the last
 *   pan or zoom, once the running gesture has carried the anchor's own point by
 *   [AppConfig.uiMapInspectArmMoveDp] and with a candidate under the anchor;
 * - the **candidate overlay**, the mode's own single line, built from the *same* plan inputs the
 *   canonical rebuild reads ([trackArrows], [trackColours], [eyeOverride]) so the preview and the
 *   committed selection are one look rather than two.
 *
 * The quiescence is structural rather than measured: the clock lives in one effect keyed on the motion
 * tick, the candidate's id and the gesture id, so any pan, zoom or fling restarts it, a changed
 * candidate restarts it, and only a quiet map lets it reach the dwell. The gate that keeps a mere
 * touch — or arming itself — from reaching the pick is read inside that effect's own body (plan §5).
 */
@Composable
internal fun MapInspectEffects(
    mapView: MapView?,
    armed: Boolean,
    centerOffsetPx: Int,
    viewportMinDp: Float,
    zoomLevel: Double,
    distanceToShore: Double?,
    markers: List<UserMarker>,
    trackIds: List<String>,
    trackViewModel: TrackViewModel,
    /** Bumped at every gesture down: the movement gate takes its zero at each gesture's start. */
    gestureId: Int,
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

    // ── Motion: the raw centre, the radius, and the tick every consumer keys on ──
    val anchor = remember { mutableStateOf<LatLng?>(null) }
    val radiusMeters = remember { mutableStateOf(0.0) }
    val motionTick = remember { mutableIntStateOf(0) }
    // The movement gate's own zero: the anchor's geo point at the start of the running gesture and
    // where that point sat on screen then. Null until a gesture has actually moved the map, which is
    // what keeps arming from opening the gate: the seed below carries no gesture at all, and the
    // boat shifting the centre offset is not a touch either (plan §5).
    val armOrigin = remember { mutableStateOf<InspectArmOrigin?>(null) }
    val shoreState = rememberUpdatedState(distanceToShore)
    val offsetState = rememberUpdatedState(centerOffsetPx)
    val gestureState = rememberUpdatedState(gestureId)
    DisposableEffect(mapView, armed) {
        val mv = mapView
        if (mv == null || !armed) {
            armOrigin.value = null
            onDispose { }
        } else {
            // The gesture this session has already seen: a change means a fresh down, and the gate's
            // zero is taken then — never at arming, where no touch is running.
            var seenGesture = gestureState.value
            // One motion handler for pan and zoom alike: both reset the trigger clock, and the
            // anchor is re-read from the raw projection on every one of them.
            fun refreshMotion() {
                if (gestureState.value != seenGesture) {
                    seenGesture = gestureState.value
                    armOrigin.value = armOriginOf(mv, inspectAnchor(mv, offsetState.value))
                }
                anchor.value = inspectAnchor(mv, offsetState.value)
                radiusMeters.value = inspectRadiusFor(
                    zoomLevel = mv.zoomLevelDouble,
                    anchorLat = anchor.value?.latitude ?: 0.0,
                    viewportMinDp = viewportMinDp,
                    distanceToShore = shoreState.value,
                    pxPerDp = mv.resources.displayMetrics.density
                )
                motionTick.intValue++
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
            // Seed immediately: arming must sweep before the user touches anything. The seed carries
            // no gesture, so it leaves the gate shut and cannot pick.
            refreshMotion()
            onDispose { mv.removeMapListener(listener) }
        }
    }

    // ── Sweep: nearest inside the radius, hysteresis applied to the winner ───
    val winner = remember { mutableStateOf<InspectRank?>(null) }
    val hysteresisPct = AppConfig.uiMapInspectHysteresisPct
    LaunchedEffect(armed, warm) {
        if (!armed || warm.isEmpty()) {
            winner.value = null
            onSweep(null)
            return@LaunchedEffect
        }
        snapshotFlow { motionTick.intValue }
            .conflate()
            .collectLatest {
                val challenger = withContext(Dispatchers.Default) {
                    val a = anchor.value ?: return@withContext null
                    InspectRanking.nearest(a, warm, radiusMeters.value)
                }
                winner.value = InspectRanking.winner(winner.value, challenger, radiusMeters.value, hysteresisPct)
                onSweep(winner.value)
            }
    }

    // ── Trigger clock: the dwell after the last motion, once per committed id ──
    val dwellMs = AppConfig.uiMapInspectDwellMs
    val lastPickedId = remember { mutableStateOf<String?>(null) }
    // The no-re-fire guard is per armed session: the pick's own camera move is a pan event, so
    // without it the clock would reset and open the same item again, while a fresh session must stay
    // free to pick the item the previous one picked.
    LaunchedEffect(armed) { if (armed) lastPickedId.value = null }
    LaunchedEffect(armed, winner.value?.id, motionTick.intValue, gestureId) {
        if (!armed) return@LaunchedEffect
        val candidate = winner.value ?: return@LaunchedEffect
        // The movement gate is read here, in the clock's own body, rather than in the seeding: the
        // seed re-arms this effect through `winner?.id`, so a guard placed in the seeding would be
        // bypassed the moment the sweep published a candidate under the anchor.
        if (!armOriginReached(armOrigin.value, mapView, AppConfig.uiMapInspectArmMoveDp)) return@LaunchedEffect
        if (candidate.id == lastPickedId.value) return@LaunchedEffect
        delay(dwellMs)
        lastPickedId.value = candidate.id
        // The ladder is a single bounded pass over the same warm geometry, with the anchor
        // snapshotted at this moment, so the sequence is reproducible and never re-ranks later.
        val ladder = withContext(Dispatchers.Default) {
            val a = anchor.value ?: return@withContext emptyList()
            InspectRanking.rank(a, warm)
        }
        onPick(candidate, ladder)
    }

    // ── The mode's own overlay: one line, swapped in one non-suspending block ──
    LaunchedEffect(
        armed, winner.value?.id, winner.value?.kind, warm,
        rebuildGeneration, highlightedTrackId
    ) {
        val mv = mapView ?: return@LaunchedEffect
        val candidate = winner.value
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

// ─────────────────────────────────────────────────────────────────────────────
// The movement gate's two device-facing halves (plan §5): read the projection, then compare
// ─────────────────────────────────────────────────────────────────────────────

/** The gesture's own zero: the anchor's geo point at its start, and where that point sat on screen. */
private data class InspectArmOrigin(val anchor: LatLng, val screenX: Float, val screenY: Float)

/** [InspectArmOrigin] for the anchor as it stands, or null when the map cannot project it yet. */
private fun armOriginOf(mapView: MapView, anchor: LatLng?): InspectArmOrigin? {
    val geo = anchor ?: return null
    val projection = mapView.projection ?: return null
    val px = projection.toPixels(GeoPoint(geo.latitude, geo.longitude), null) ?: return null
    return InspectArmOrigin(geo, px.x.toFloat(), px.y.toFloat())
}

/**
 * Whether the running gesture has opened the movement gate: the anchor's **own point**, projected
 * again with the projection as it stands, measured against where it sat when the gesture started.
 *
 * Only the projection call is device-facing here — the comparison is [inspectArmGateOpen], pure and
 * unit-tested beside the ranking. An origin of null means no gesture has moved the map since the arm,
 * so the gate is shut and the arming seed cannot pick.
 */
private fun armOriginReached(origin: InspectArmOrigin?, mapView: MapView?, minMoveDp: Float): Boolean {
    val mv = mapView ?: return false
    val from = origin ?: return false
    val projection = mv.projection ?: return false
    val now = projection.toPixels(GeoPoint(from.anchor.latitude, from.anchor.longitude), null) ?: return false
    return inspectArmGateOpen(
        downX = from.screenX,
        downY = from.screenY,
        nowX = now.x.toFloat(),
        nowY = now.y.toFloat(),
        density = mv.resources.displayMetrics.density,
        minMoveDp = minMoveDp
    )
}

/**
 * The ring's radius in metres for an anchor and a zoom — the one place the dp derivation and the
 * metres conversion are joined, so the ring the user sees and the gate the sweep applies are the
 * same number.
 */
internal fun inspectRadiusFor(
    zoomLevel: Double,
    anchorLat: Double,
    viewportMinDp: Float,
    distanceToShore: Double?,
    pxPerDp: Float
): Double {
    val radiusDp = inspectRadiusDp(
        zoomLevel = zoomLevel,
        distMultiplier = inspectDistMultiplier(distanceToShore),
        viewportMinDp = viewportMinDp,
        factor = AppConfig.uiMapInspectRadiusFactor,
        minDp = AppConfig.uiMapInspectRadiusMinDp,
        maxViewportPct = AppConfig.uiMapInspectRadiusMaxViewportPct
    )
    return inspectRadiusMeters(radiusDp, pxPerDp, anchorLat, zoomLevel)
}
