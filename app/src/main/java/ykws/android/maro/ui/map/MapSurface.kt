package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

/**
 * One resolved surface face: the fill a surface paints and the weight its content carries.
 *
 * The face is what keeps the state logic where it was — each control decides its own fill and hands it
 * over, so this file only paints. Nothing here reads a status colour: the GPS table, the tracking dot and
 * the water/land reading stay the controls' own business.
 */
internal data class MapSurfaceFace(
    val fill: Color,
    val contentAlpha: Float
)

/**
 * The shared fill at its own weight with the content left whole — what both overlay cards paint. Their
 * text is content, not a glyph carrying a state, so it is never dimmed.
 */
internal fun mapSurfaceFace(): MapSurfaceFace =
    MapSurfaceFace(fill = Color(AppConfig.uiMapSurfaceInactive), contentAlpha = 1f)

/**
 * An inactive control face: the shared fill painted whole, since its weight is already in the token, and
 * the control's own content dimmed to the surface's inactive content alpha — GPS DEMO, tracking OFF,
 * lock OFF, the legend's collapsed square.
 */
internal fun mapSurfaceFaceInactive(): MapSurfaceFace =
    MapSurfaceFace(
        fill = Color(AppConfig.uiMapSurfaceInactive),
        contentAlpha = AppConfig.uiMapSurfaceInactiveContentAlpha
    )

/**
 * An active control face: its own state colour at the surface's active alpha, content whole. The colour
 * is the control's (a status tint, the app accent); the weight is the surface's.
 */
internal fun mapSurfaceFaceActive(color: Color): MapSurfaceFace =
    MapSurfaceFace(fill = color.copy(alpha = AppConfig.uiMapSurfaceActiveAlpha), contentAlpha = 1f)

/**
 * The surface family's one background-painting path: it paints the shared fill whole, clips the shared
 * corner, draws the shared border and applies the shared padding, all from the `ui.map.surface.*` block.
 *
 * The family it covers is the row's status squares and the recenter square (through [MapToggleSquare]),
 * the collapsed legend square and both overlay cards. It is not a claim about every box on the map:
 * `ZoomButton`, `LockBanner`, `MapStatusBanner` and the regulated-zone icon stack paint their own faces
 * and stay outside it.
 *
 * The inactive fade lands on the **content**, never on the box: `Modifier.alpha` sits on the inner
 * wrapper, so an inactive face still paints its fill at the property's own weight and only the glyph
 * inside it dims (D2, map-surface normalization). That is why the fade is applied here rather than left to
 * each caller's modifier order.
 *
 * [onClick] and [contentDescription] are parameters rather than caller modifiers because the tap belongs
 * *inside* the clip: the ripple then follows the shared corner, as it did before this path existed. A
 * modifier passed by the caller is applied outside the clip and would not.
 *
 * The two are one decision — a labelled tap, or neither — because the semantics arm below needs both: a
 * description with no tap is dropped on the floor, and a tap with no description is an unlabelled
 * clickable (what the row squares that pass a tap alone already are). A caller that wants its square
 * announced passes both.
 */
@Composable
internal fun MapSurface(
    face: MapSurfaceFace,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppConfig.uiMapSurfaceCornerRadius.dp)
    val interaction = when {
        onClick == null -> Modifier
        contentDescription == null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(face.fill)
            .border(AppConfig.uiMapSurfaceBorderWidth.dp, Color(AppConfig.uiMapSurfaceBorderColor), shape)
            .then(interaction)
            .padding(AppConfig.uiMapSurfacePadding.dp),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier.alpha(face.contentAlpha),
            contentAlignment = contentAlignment,
            content = content
        )
    }
}

/**
 * One square of the map's top-left toggle row, layered on [MapSurface]: it adds the row's own size and
 * passes the tap through so the ripple follows the surface's corner.
 *
 * The glyph's size is not this composable's: each caller draws its own emoji at [TOP_TOGGLE_ICON_SIZE],
 * and that one accessor is read six times — the five row squares and `MapScreen`'s collapsed legend
 * square — which is what keeps `ui.map.toggle.icon.size` single. Nothing is added in here.
 *
 * The content box fills the padded area by construction, because a square's size is fixed: the wrapper
 * below takes the padding's own box whatever the glyph's measured one turns out to be, so an alignment
 * like `Alignment.TopEnd` lands on the square's corner inset by `ui.map.surface.padding` alone, at any
 * emoji metric and any font scale. That is what the tracking dot stands on. A wrap-sized surface — either
 * overlay card — keeps hugging its content, and only this fixed square fills.
 *
 * The face arrives already resolved, and it alone decides whether the square is active and whether its
 * glyph is dimmed — there is no branch in here.
 */
@Composable
internal fun MapToggleSquare(
    face: MapSurfaceFace,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    MapSurface(
        face = face,
        modifier = modifier.size(TOP_TOGGLE_SQUARE),
        onClick = onClick,
        contentDescription = contentDescription
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
