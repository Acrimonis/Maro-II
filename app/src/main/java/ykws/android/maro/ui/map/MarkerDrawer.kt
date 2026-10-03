package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
import ykws.android.maro.ui.components.DrawerScaffold

// ─────────────────────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The inspect cursor's own Prev/Next, handed to the marker card while it was opened by an inspect
 * pick: the ends of the frozen ladder and what a press on either does. The card reads these rather
 * than the ViewModel's own marker walk, because the ladder mixes both types and the cursor above the
 * drawers is its one author.
 */
internal data class InspectWalk(
    val atFirst: Boolean,
    val atLast: Boolean,
    /**
     * True while an inspect open is in flight and this card is the predecessor held for it (plan §5):
     * the whole step surface stands down for that window — the two walk arms, Back, and this card's own
     * Delete and Edit, because each of them would interleave with the open about to land on this very
     * slot.
     */
    val held: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit
)

/**
 * Pure-content drawer for marker viewing and match results.
 *
 * Animation and shadow are provided by [OverlayLayer].
 * Creating/Editing is now handled by [WizardDrawer].
 *
 * @param viewModel     The [MarkersViewModel] driving the drawer state.
 * @param isLandscape   Whether the device is in landscape orientation.
 * @param onClose       Called when the drawer is dismissed.
 * @param boatPosition  Current boat position for distance-to-boat display.
 * @param onWizardEntry R1: the wizard takes the dashboard slot, so the track dashboard closes first.
 *                      The marker half needs no close: the wizard replaces the Viewing content inside
 *                      the same [MarkerDrawerState], so the marker being edited is never closed.
 */
@Composable
internal fun MarkerDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onClose: () -> Unit,
    boatPosition: LatLng? = null,
    onRequestDelete: (String, String) -> Unit = { _, _ -> },
    trackTitleLookup: (String) -> String? = { null },
    onOpenMarkerTrack: (String) -> Unit = {},
    onWizardEntry: () -> Unit = {},
    /**
     * The panel's floor in portrait: the base every bottom dashboard is held at (R2). Stated at each
     * call rather than defaulting to a collapsing `0.dp` (G4) — the portrait caller passes
     * `dashboardBaseHeight`, while the landscape caller passes `0.dp` explicitly, its non-wrap frame
     * ignoring the floor.
     */
    minPanelHeight: Dp,
    /** Optional report of the open panel's measured height in portrait (Phase 2). */
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    /**
     * The portrait frame's own ceiling (F5) — the band cap the map leaves, under which a taller
     * card's body scrolls instead of covering the map strip. Null keeps the full-screen ceiling;
     * landscape ignores it, its frame being untouched.
     */
    panelMaxHeight: Dp? = null,
    /**
     * The inspect cursor's own Prev/Next, non-null exactly while this card was opened by an inspect
     * pick: the merged ladder's walk then replaces the marker walk, ends and taps alike.
     */
    walk: InspectWalk? = null
) {
    val drawerState by viewModel.drawerState.collectAsState()
    val isOpen = drawerState !is MarkerDrawerState.Hidden

    // Every portrait bottom panel is square at the top; landscape keeps its own right-edge shape.
    val panelShape = if (isLandscape) RoundedCornerShape(bottomStart = 16.dp)
        else RoundedCornerShape(0.dp)

    // Back handler when drawer is open — registered before content so the card's
    // edit-revert BackHandler (composed later) wins while editing.
    if (isOpen) {
        BackHandler { onClose() }
    }

    when (drawerState) {
        is MarkerDrawerState.Viewing -> ViewingContent(viewModel, onClose, boatPosition, panelShape, onRequestDelete, isLandscape, trackTitleLookup, onOpenMarkerTrack, onWizardEntry, minPanelHeight, onMeasuredHeight, panelMaxHeight, walk)
        is MarkerDrawerState.MatchResult -> MatchResultContent(viewModel, onClose, boatPosition, panelShape, isLandscape, minPanelHeight, onMeasuredHeight, panelMaxHeight)
        else -> { /* Creating/Editing handled by WizardDrawer */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewing content — card layout redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewingContent(
    viewModel: MarkersViewModel,
    onClose: () -> Unit,
    boatPosition: LatLng? = null,
    shape: Shape,
    onRequestDelete: (String, String) -> Unit = { _, _ -> },
    isLandscape: Boolean,
    trackTitleLookup: (String) -> String? = { null },
    onOpenMarkerTrack: (String) -> Unit = {},
    onWizardEntry: () -> Unit = {},
    minPanelHeight: Dp,
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    panelMaxHeight: Dp? = null,
    walk: InspectWalk? = null
) {
    val markers by viewModel.markers.collectAsState()
    val mapMarkers by viewModel.mapMarkers.collectAsState()
    val allMarkers by viewModel.allMarkers.collectAsState()
    val selectedIds by viewModel.selectedMarkerIds.collectAsState()
    val selectedIndex by viewModel.selectedMarkerIndex.collectAsState()

    // The card resolves in the world it reads (plan §2, §3): which source reads which collection is
    // [cardWalkWorld]'s one home, shared with the state layer's own resolve-and-close, so the two
    // cannot drift apart. A map-tapped marker reads the map's own source of truth, so a one-item card
    // stands through a write that leaves the map filter — which is what let it render as not-found.
    val world: List<UserMarker> = cardWalkWorld(
        source = viewModel.drawerSource,
        listWorld = markers,
        mapWorld = mapMarkers,
        mapSourceWorld = allMarkers
    )
    val currentId = selectedIds.getOrNull(selectedIndex)
    val marker = currentId?.let { id -> world.find { it.id == id } }

    // The predecessor held for an in-flight inspect open: this card's own Delete stands down with the
    // walk, because the advance it triggers would interleave with the open about to land here (§5).
    val held = walk?.held == true
    val disabledAlpha = 0.35f

    val deleteAction: @Composable () -> Unit = {
        if (marker != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { if (!held) onRequestDelete(marker.id, marker.name) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = ButtonColors.icon.copy(alpha = if (held) disabledAlpha else 1f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // The pills' own ends, and whether they are drawn at all, are [cardStepEnds]'s one home (plan §2),
    // so a menu-opened card greys its ends exactly as the panel's does and a one-item walk draws no
    // bars.
    val stepEnds = cardStepEnds(
        source = viewModel.drawerSource,
        selectedCount = selectedIds.size,
        selectedIndex = selectedIndex,
        walkAtFirst = walk?.atFirst,
        walkAtLast = walk?.atLast
    )

    val footerContent: @Composable () -> Unit = {
        if (stepEnds.shows) {
            MarkerPrevNext(viewModel, walk, stepEnds)
        }
    }

    DrawerScaffold(
        title = marker?.name ?: stringResource(R.string.marker_title_fallback),
        onClose = onClose,
        headerHorizontalPadding = 12.dp,
        scrollable = true,
        suppressOverscrollWhenFits = true,
        // Wrap-content only in portrait (bottom panel floors at minPanelHeight so it never
        // shrinks below the dashboard). Landscape uses the non-wrap full-height branch so the
        // drawer covers the entire left dashboard column (top-to-bottom).
        wrapContent = !isLandscape,
        wrapContentMinHeight = if (isLandscape) 0.dp else minPanelHeight,
        onMeasuredHeight = onMeasuredHeight,
        wrapContentMaxHeight = panelMaxHeight,
        // Landscape (non-wrap): bottom-align the card above the prev/next footer, mirroring the
        // track drawer. Ignored in portrait wrap mode (whole panel is already bottom-aligned).
        bottomAnchoredContent = true,
        statusBarsInset = isLandscape,
        shape = shape,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
        headerActions = {
            deleteAction()
        },
        footer = { footerContent() }
    ) {
        MarkerDetailContent(
            marker = marker,
            boatPosition = boatPosition,
            trackTitleLookup = trackTitleLookup,
            onOpenMarkerTrack = onOpenMarkerTrack,
            onWizardEntry = onWizardEntry,
            viewModel = viewModel,
            held = held
        )
    }
}

/**
 * The marker detail drawer's content stack: the optional distance-to-boat line plus the
 * [MarkerCardContent] (with its belongs-to-track row).
 */
@Composable
private fun MarkerDetailContent(
    marker: UserMarker?,
    boatPosition: LatLng?,
    trackTitleLookup: (String) -> String?,
    onOpenMarkerTrack: (String) -> Unit,
    onWizardEntry: () -> Unit,
    viewModel: MarkersViewModel,
    /** True while an inspect open holds this card as its predecessor — the Edit stands down with it. */
    held: Boolean = false
) {
    // The card draws nothing for a marker it cannot resolve (plan §1, §2): the state layer closes a
    // live card before this is reached, so what is left here is the frame between that close and the
    // next recomposition — no crash, and no user-facing text. The branch that used to render the
    // not-found string, and the string itself, went with the rule.
    if (marker == null) return

    // Direction + distance (if boatPosition available)
    if (boatPosition != null) {
        val markerPos = when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> g.position
            is MarkerGeometry.Circle -> g.center
            is MarkerGeometry.Corridor -> g.p1
        }
        val bearing = SpatialOperations.initialBearing(boatPosition, markerPos)
        val distM = SpatialOperations.haversine(markerPos, boatPosition)
        val dir = stringResource(cardinalDirectionRes(bearing))
        val distStr = if (distM < 1000.0) stringResource(R.string.settings_value_meters, distM.toInt())
            else stringResource(R.string.dash_value_km, distM / 1000.0)
        Text(
            text = stringResource(R.string.marker_direction_of_boat_fmt, dir, distStr),
            color = ComposeColor(AppConfig.uiTextMuted),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(6.dp))
    }

    MarkerCardContent(
        marker = marker,
        trackTitle = marker.trackId?.let(trackTitleLookup),
        onOpenTrack = marker.trackId?.let { tid -> { onOpenMarkerTrack(tid) } },
        onTap = {},
        onEdit = {
            // R1: the wizard takes this dashboard's slot — only the track dashboard closes. The
            // marker half is left alone: the wizard replaces the Viewing content inside the same
            // MarkerDrawerState, so the card is never closed out from under the edit.
            // While an inspect open holds this card, the wizard stands down with everything else:
            // its disarm would clear the very hand-off holding this card for the successor (§5).
            if (!held) {
                onWizardEntry()
                // The door is named (plan §4): card entry, so the editor resolves the marker in
                // this card's world and hands the card back on a save and on a cancel alike.
                viewModel.startWizard(marker.id, WizardDoor.CARD)
            }
        },
        onSetIcon = { id, icon -> viewModel.setMarkerIcon(id, icon) },
        onSetPin = { id, pinned -> viewModel.setMarkerPinned(id, pinned) },
        onUpdateText = { name, desc -> viewModel.updateMarkerText(marker.id, name, desc) },
        onLongPress = null,
        showChevron = false
    )

    Spacer(Modifier.height(4.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchResultContent(
    viewModel: MarkersViewModel,
    onClose: () -> Unit,
    boatPosition: LatLng? = null,
    shape: Shape,
    isLandscape: Boolean,
    minPanelHeight: Dp,
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    panelMaxHeight: Dp? = null
) {
    val result by viewModel.matchResult.collectAsState()

    DrawerScaffold(
        title = stringResource(R.string.where_am_i_title),
        onClose = onClose,
        headerHorizontalPadding = 12.dp,
        scrollable = true,
        // Portrait wraps and floors at the base, on the same path its sibling detail cards use, so
        // the caller's slot carries no height of its own (R1, R2). Landscape keeps its own face.
        wrapContent = !isLandscape,
        wrapContentMinHeight = if (isLandscape) 0.dp else minPanelHeight,
        bottomAnchoredContent = !isLandscape,
        onMeasuredHeight = onMeasuredHeight,
        wrapContentMaxHeight = panelMaxHeight,
        statusBarsInset = isLandscape,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        val matches = result?.allMatches ?: emptyList()
        if (matches.isEmpty()) {
            Text(
                text = stringResource(R.string.where_am_i_none),
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                matches.forEach { match ->
                    MatchRow(match, boatPosition)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Previous/Next navigation pills — shared by the landscape body and the pinned portrait footer.
 *
 * The walk is a callback rather than a ViewModel read ([walk]) because an inspect-opened card steps
 * the merged distance ladder, which the inspect cursor above both drawers owns; null leaves the
 * card on its own world, so a list- or map-opened card walks exactly as it always did.
 *
 * Which ends grey is [cardStepEnds]'s one home (plan §2): every door of the item's-list kind — the
 * panel's and the menu chevron's alike — greys the index it lands on, so a menu card at either end
 * shows the 35 % face and loses its click exactly as the panel's does.
 */
@Composable
private fun MarkerPrevNext(
    viewModel: MarkersViewModel,
    walk: InspectWalk? = null,
    ends: CardStepEnds
) {
    Spacer(Modifier.height(10.dp))
    val accentBg = ComposeColor(AppConfig.uiAccent)
    val accentFg = ComposeColor(AppConfig.uiTextPrimary)
    val disabledAlpha = 0.35f
    val isAtFirst = ends.atFirst
    val isAtLast = ends.atLast
    val onPrev: () -> Unit = walk?.onPrev ?: viewModel::viewPreviousMarker
    val onNext: () -> Unit = walk?.onNext ?: viewModel::viewNextMarker

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(accentBg.copy(alpha = if (isAtFirst) disabledAlpha else 1f))
                .then(
                    if (!isAtFirst) Modifier.clickable { onPrev() }
                    else Modifier
                )
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.action_previous),
                color = accentFg.copy(alpha = if (isAtFirst) disabledAlpha else 1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(accentBg.copy(alpha = if (isAtLast) disabledAlpha else 1f))
                .then(
                    if (!isAtLast) Modifier.clickable { onNext() }
                    else Modifier
                )
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.action_next),
                color = accentFg.copy(alpha = if (isAtLast) disabledAlpha else 1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Match result row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MatchRow(match: WhereAmIMatch, boatPosition: LatLng?) {
    // The one match-marker rule (plan §3), shared with the card's own world and the recording's
    // snapshot — never a `when` of its own at each of the three readers.
    val marker = match.matchedMarker()
    val icon = marker.icon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
    ) {
        // Left-edge color accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(ComposeColor(MarkerColors.of(marker.colorIndex)))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Direction + distance (LineOfSightMatch only)
            if (match is WhereAmIMatch.LineOfSightMatch) {
                val dir = stringResource(cardinalDirectionRes(match.bearingDeg))
                val distStr = if (boatPosition != null) {
                    val dist = geometricDistanceToZone(boatPosition, marker.geometry)
                    if (dist < 1000.0) stringResource(R.string.settings_value_meters, dist.toInt())
                    else stringResource(R.string.dash_value_km, dist / 1000.0)
                } else null
                val text = if (distStr != null)
                    stringResource(R.string.marker_direction_of_boat_fmt, dir, distStr) else dir
                Text(
                    text = text,
                    color = ComposeColor(AppConfig.uiTextMuted),
                    fontSize = 11.sp
                )
            }
            // Name + icon row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = marker.name,
                    color = ComposeColor(AppConfig.uiTextPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (icon != null) {
                    Text(
                        text = icon,
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** Straight-line (flight-of-bird) distance from [boat] to the nearest edge of [geometry]. */
private fun geometricDistanceToZone(boat: LatLng, geometry: MarkerGeometry): Double {
    return when (geometry) {
        is MarkerGeometry.Pin -> SpatialOperations.haversine(boat, geometry.position)
        is MarkerGeometry.Circle -> {
            val distToCenter = SpatialOperations.haversine(boat, geometry.center)
            (distToCenter - geometry.radiusM).coerceAtLeast(0.0)
        }
        is MarkerGeometry.Corridor -> {
            val dSeg = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
            (dSeg - geometry.widthM / 2.0).coerceAtLeast(0.0)
        }
    }
}

/** Resource id of the cardinal direction (N, NE, E, SE, S, SW, W, NW) for a bearing in degrees. */
private fun cardinalDirectionRes(bearingDeg: Double): Int {
    val normalized = ((bearingDeg % 360) + 360) % 360
    return when {
        normalized < 22.5 || normalized >= 337.5 -> R.string.cardinal_n
        normalized < 67.5 -> R.string.cardinal_ne
        normalized < 112.5 -> R.string.cardinal_e
        normalized < 157.5 -> R.string.cardinal_se
        normalized < 202.5 -> R.string.cardinal_s
        normalized < 247.5 -> R.string.cardinal_sw
        normalized < 292.5 -> R.string.cardinal_w
        else -> R.string.cardinal_nw
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color picker — 4×4 swatch grid from MarkerColors.all
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkerColorPickerDialog(
    currentColorIndex: Int?,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MarkerColors.all

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.marker_color_title)) },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colors.size) { index ->
                        val color = colors[index]
                        val isSelected = index == currentColorIndex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ComposeColor(color))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) ComposeColor(AppConfig.uiAccent)
                                        else ComposeColor(AppConfig.uiDividerColor),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onColorSelected(index) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
