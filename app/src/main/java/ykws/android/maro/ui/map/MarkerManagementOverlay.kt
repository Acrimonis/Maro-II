package ykws.android.maro.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CustomSortField
import ykws.android.maro.data.model.FilterAxisSpec
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.markerFilterAxes
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.ui.components.ListOverlayScaffold

/**
 * Full-screen overlay displaying a LazyColumn of user marker cards with
 * swipe-to-delete. Delegates structure to [ListOverlayScaffold].
 *
 * @param markers              List of all user markers.
 * @param onAction             Single callback for all list actions.
 * @param onCreateFirst        Called when user taps "Create First Marker" from empty state.
 * @param onDismiss            Called to close the management page.
 * @param onSetIcon            Called to set/change marker icon.
 * @param sortOrder            Current sort order from settings.
 * @param onSortOrderChange    Called when user picks a new sort order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarkerManagementOverlay(
    markers: List<UserMarker>,
    onAction: (ykws.android.maro.data.model.ListAction) -> Unit,
    onCreateFirst: () -> Unit,
    onDismiss: () -> Unit,
    onSetIcon: (String, String?) -> Unit = { _, _ -> },
    sortState: ykws.android.maro.data.model.ListSortState,
    onSortStateChange: (ykws.android.maro.data.model.ListSortState) -> Unit,
    filterState: ListFilter = ListFilter(),
    onFilterChange: (ListFilter) -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val markerCustomSortFields = remember {
        listOf(
            CustomSortField("origin", R.string.sort_custom_origin)
        )
    }

    ListOverlayScaffold(
        items = markers,
        title = "Markers \u00B7 ${markers.size}",
        sectionLabel = "YOUR MARKERS",
        sortState = sortState,
        onSortStateChange = onSortStateChange,
        customSortFields = markerCustomSortFields,
        filterAxes = markerFilterAxes(),
        filterState = filterState,
        onFilterChange = onFilterChange,
        onReset = onReset,
        accentColors = { list -> list.associate { it.id to Color(ykws.android.maro.ui.map.MarkerColors.of(it.colorIndex)) } },
        cardContent = { marker ->
            MarkerCardContent(
                marker = marker,
                onTap = { onAction(ykws.android.maro.data.model.ListAction.SelectItem(marker.id)) },
                onEdit = { onAction(ykws.android.maro.data.model.ListAction.EditItem(marker.id)) },
                onSetIcon = onSetIcon
            )
        },
        emptyState = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CenteredPinIcon(sizeDp = 64)
                Spacer(modifier = Modifier.height(24.dp))
                Text("No markers yet", color = Color(AppConfig.uiSettingsTextMuted), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCreateFirst,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(AppConfig.buttonActionBgColor)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create First Marker", color = Color(AppConfig.buttonActionIconColor), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        onAction = onAction,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Marker card content — matching track list item pattern
// ─────────────────────────────────────────────────────────────────────────────

private val MARKER_CARD_RADIUS = 12.dp
private val MARKER_ACCENT_BAR_WIDTH = 4.dp
private val MARKER_CONTENT_PAD_H = 8.dp
private val MARKER_CONTENT_PAD_V = 4.dp
private val MARKER_HEADER_FONT_SIZE = 11.sp
private val MARKER_TITLE_FONT_SIZE = 15.sp
private val MARKER_GEOMETRY_FONT_SIZE = 14.sp
private val MARKER_DESC_FONT_SIZE = 13.sp

@Composable
private fun MarkerCardContent(
    marker: UserMarker,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onSetIcon: (String, String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(MARKER_CARD_RADIUS))
            .background(Color(AppConfig.uiCardBackground))
    ) {
        Box(
            modifier = Modifier
                .width(MARKER_ACCENT_BAR_WIDTH)
                .fillMaxHeight()
                .background(Color(MarkerColors.of(marker.colorIndex)))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MARKER_CONTENT_PAD_H, vertical = MARKER_CONTENT_PAD_V)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = coordinateHeader(marker),
                    color = Color(AppConfig.uiSettingsTextMuted),
                    fontSize = MARKER_HEADER_FONT_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    var showIconPicker by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showIconPicker = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (marker.icon != null) {
                            Text(marker.icon!!, fontSize = 20.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.LocationOff,
                                contentDescription = "Set icon",
                                tint = ButtonColors.icon,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    if (showIconPicker) {
                        IconPickerDialog(
                            currentIcon = marker.icon,
                            onIconSelected = { icon ->
                                onSetIcon(marker.id, icon)
                                showIconPicker = false
                            },
                            onDismiss = { showIconPicker = false }
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = ButtonColors.icon,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
            Spacer(Modifier.height(2.dp))

            Text(
                text = marker.name,
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = MARKER_TITLE_FONT_SIZE,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = markerFormatText(marker),
                color = Color(AppConfig.uiSettingsTextPrimary),
                fontSize = MARKER_GEOMETRY_FONT_SIZE,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (marker.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = marker.description,
                    color = Color(AppConfig.uiSettingsTextMuted),
                    fontSize = MARKER_DESC_FONT_SIZE,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun coordinateHeader(marker: UserMarker): String {
    fun fmt(ll: ykws.android.maro.data.model.LatLng) =
        "%.4f, %.4f".format(ll.latitude, ll.longitude)
    return when (val g = marker.geometry) {
        is MarkerGeometry.Pin -> "[${fmt(g.position)}]"
        is MarkerGeometry.Circle -> "[${fmt(g.center)}]"
        is MarkerGeometry.Corridor -> "[${fmt(g.p1)}] \u2192 [${fmt(g.p2)}]"
    }
}

private fun markerFormatText(marker: UserMarker): String {
    val icon = MarkerGeometry.iconFor(marker.geometry)
    val proximityM = marker.proximityOverrideM
        ?: when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> AppConfig.markerProximityPinM
            is MarkerGeometry.Circle -> g.radiusM * AppConfig.markerProximityZoneMultiplier
            is MarkerGeometry.Corridor -> g.widthM * AppConfig.markerProximityZoneMultiplier
        }
    val prox = "${proximityM.toLong()}m prox"
    return when (marker.geometry) {
        is MarkerGeometry.Pin -> "$icon - $prox"
        is MarkerGeometry.Circle -> {
            val r = "${marker.geometry.radiusM.toLong()}m r"
            "$icon - $r - $prox"
        }
        is MarkerGeometry.Corridor -> {
            val w = "${marker.geometry.widthM.toLong()}m w"
            "$icon - $w - $prox"
        }
    }
}

@Composable
private fun CenteredPinIcon(sizeDp: Int) {
    val density = LocalDensity.current
    val color = Color(AppConfig.uiSettingsTextMuted).copy(alpha = 0.4f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(sizeDp.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val strokeW = w * 0.10f
        val pinRadius = w * 0.25f
        val pinCenterY = h * 0.45f
        drawCircle(
            color = color,
            radius = pinRadius,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY),
            style = Stroke(width = strokeW)
        )
        val tipY = h * 0.95f
        val pointLeft = cx - pinRadius * 0.7f
        val pointRight = cx + pinRadius * 0.7f
        val pointTop = pinCenterY + pinRadius * 0.9f
        val path = Path().apply {
            moveTo(pointLeft, pointTop)
            lineTo(cx, tipY)
            lineTo(pointRight, pointTop)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = strokeW))
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = pinRadius * 0.35f,
            center = androidx.compose.ui.geometry.Offset(cx, pinCenterY)
        )
    }
}
