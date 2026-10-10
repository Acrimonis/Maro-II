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
 * One resolved surface face: the fill a surface paints, the weight its content carries, and the mark it
 * wears.
 *
 * The face is what keeps the state logic where it was — each control decides its own fill, hands it over,
 * and a mode square's data mark rides with it as [dot] — so this file only paints. Nothing here reads a
 * status colour: the GPS table, the tracking dot and the water/land reading stay the controls' own
 * business. [dot] is non-null only for the mode squares that wear a mark; the off square, the recenter
 * action, the reading squares and every overlay card carry none.
 */
internal data class MapSurfaceFace(
    val fill: Color,
    val contentAlpha: Float,
    val dot: Color?
)

/**
 * The shared base with nothing over it and the content left whole — what both overlay cards paint. Their
 * text is content, not a glyph carrying a state, so it is never dimmed. `MapSurface` itself paints the
 * base (`ui.map.surface.inactive`); this face adds no colour of its own.
 */
internal fun mapSurfaceFace(): MapSurfaceFace =
    MapSurfaceFace(fill = Color.Transparent, contentAlpha = 1f, dot = null)

/**
 * An inactive control face: the base alone over the family's own white (which `MapSurface` paints under
 * every face), with the control's content dimmed to the surface's inactive content alpha — GPS DEMO,
 * tracking OFF, lock OFF, the legend's collapsed square. It adds no colour over the base.
 */
internal fun mapSurfaceFaceInactive(): MapSurfaceFace =
    MapSurfaceFace(
        fill = Color.Transparent,
        contentAlpha = AppConfig.uiMapSurfaceInactiveContentAlpha,
        dot = null
    )

/**
 * An active control face: its own state colour at the surface's active alpha, content whole. The colour
 * is the control's (a status tint, the app accent); the weight is the surface's and, laid over the base
 * `MapSurface` paints beneath it, the tile never becomes a window on the map.
 */
internal fun mapSurfaceFaceActive(color: Color): MapSurfaceFace =
    MapSurfaceFace(
        fill = color.copy(alpha = AppConfig.uiMapSurfaceActiveAlpha),
        contentAlpha = 1f,
        dot = null
    )

/**
 * The tile a resolved [TopToggleFace] paints, and the mark it wears: the surface's active face when the
 * square is on, the shared inactive face — the base alone, glyph dimmed — when it is off, with the
 * face's own dot carried onto the surface so [MapToggleSquare] can paint the mark from the square itself.
 */
internal fun TopToggleFace.toSurfaceFace(): MapSurfaceFace =
    (fill?.let { mapSurfaceFaceActive(Color(it)) } ?: mapSurfaceFaceInactive())
        .copy(dot = dot?.let { Color(it) })

/**
 * The surface family's one background-painting path: it paints the family's white base, clips the shared
 * corner, draws the shared border and applies the shared padding, all from the `ui.map.surface.*` block.
 * On an active square it lays the face's own state colour over that base, so a tile is a tile whatever
 * lies beneath it.
 *
 * The family it covers is the row's status squares and the recenter square (through [MapToggleSquare]),
 * the collapsed legend square, the bottom-left regulated-zone tag column and both overlay cards. Two boxes
 * outside it paint faces of their own: `ZoomButton`, on its own circle, and the bottom band's banner
 * family through `MapBanner`, which holds the pill's and the two cards' skin itself
 * (`docs/ui-component-guidelines.md` §5.7).
 *
 * The white base lands under every face and the inactive fade lands on the **content**, never on the
 * box: the base paints `ui.map.surface.inactive` whole, an active face lays its state colour over it at
 * `ui.map.surface.active.alpha`, and `Modifier.alpha` sits on the inner wrapper so only the glyph inside
 * dims (D2, map-surface normalization). That is why the fade is applied here rather than left to each
 * caller's modifier order.
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
            .background(Color(AppConfig.uiMapSurfaceInactive))
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
 * One fixed-size square of the map chrome, layered on [MapSurface]: it adds the family's own square side
 * and passes the tap through so the ripple follows the surface's corner — a caller with no tap, like a
 * zone tag, simply omits it.
 *
 * The glyph's size is not this composable's: each caller draws its own emoji at [TOP_TOGGLE_ICON_SIZE],
 * and that one accessor is read seven times — the five row squares, `MapScreen`'s collapsed legend square
 * and the regulated-zone tag column — which is what keeps `ui.map.toggle.icon.size` single. Nothing is
 * added in here.
 *
 * The content box fills the padded area by construction, because a square's size is fixed: the wrapper
 * below takes the padding's own box whatever the glyph's measured one turns out to be, so the glyph stays
 * centred at any emoji metric and any font scale. A wrap-sized surface — either overlay card — keeps
 * hugging its content, and only this fixed square fills.
 *
 * The square also paints its own data mark: when the face carries a [MapSurfaceFace.dot], the disc is
 * drawn from this Box — not from the padded content box the glyph sits in — aligned `TopEnd` and inset by
 * [MAP_PULSE_DOT_INSET] on both axes, so the mark sits against the square's own corner rather than the
 * padded box's.
 *
 * The face arrives already resolved, and it alone decides whether the square is active, whether its glyph
 * is dimmed and whether it wears a mark — there is no branch in here.
 */
@Composable
internal fun MapToggleSquare(
    face: MapSurfaceFace,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.size(TOP_TOGGLE_SQUARE)) {
        MapSurface(
            face = face,
            modifier = Modifier.fillMaxSize(),
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
        // The mark is the square's own: drawn from the square, against its edge, inset by the mark's own
        // ratio of the disc on both the top and the right, so it no longer rides the padded content box
        // the glyph sits in.
        face.dot?.let { dotColor ->
            MapPulseDot(
                color = dotColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(MAP_PULSE_DOT_INSET)
            )
        }
    }
}
