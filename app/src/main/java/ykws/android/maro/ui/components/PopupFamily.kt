package ykws.android.maro.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * The popup family's geometry, §2.10 of `docs/ui-component-guidelines.md`, stated once for every member
 * — the filter popup, the sort popup and the dropdown's menu.
 */
internal const val POPUP_WIDTH_DP = 240
internal const val POPUP_CORNER_DP = 12
internal const val POPUP_SHADOW_DP = 8
internal const val POPUP_BORDER_DP = 1
/** The family's inset, the same all round — a member hands [PopupSurface] its content and nothing else. */
internal const val POPUP_PAD_DP = 12
internal const val POPUP_GROUP_GAP_DP = 4
internal const val POPUP_SECTION_PAD_VERTICAL_DP = 8
internal const val POPUP_ROW_PAD_HORIZONTAL_DP = 16
internal const val POPUP_ROW_PAD_VERTICAL_DP = 2
internal const val POPUP_ROW_FONT_SP = 15
internal const val POPUP_CHECK_BOX_DP = 24
internal const val POPUP_CHECK_FONT_SP = 16
internal const val POPUP_TITLE_FONT_SP = 16
internal const val POPUP_TITLE_PAD_DP = 4
internal const val POPUP_ROW_LABEL_GAP_DP = 8

/** How much of a row's content survives when the row is switched off — the same dim every family uses. */
private const val DISABLED_ALPHA = 0.4f

/**
 * **§2.10's outer surface, the family's one entrance**: `uiBackground` on a 12dp corner behind the accent
 * rim, an 8dp shadow, the given width, and a height bound it scrolls past rather than clipping. Inside is
 * [POPUP_PAD_DP] on all four sides and [POPUP_GROUP_GAP_DP] between groups, so a member passes its
 * sections and nothing else.
 *
 * **`scrollable = false` is for a member that scrolls itself** (2026-09-29): the wheel owns its own snap
 * scroll, and nesting it inside this one would give two same-axis scrollers — two scroll nodes for a
 * reader, and the outer one able to move the wheel under its own centre band.
 */
@Composable
internal fun PopupSurface(
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    width: Dp = POPUP_WIDTH_DP.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(POPUP_CORNER_DP.dp),
        color = Color(AppConfig.uiBackground),
        shadowElevation = POPUP_SHADOW_DP.dp,
        modifier = modifier
            .width(width)
            .border(
                width = POPUP_BORDER_DP.dp,
                color = Color(AppConfig.uiAccent),
                shape = RoundedCornerShape(POPUP_CORNER_DP.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(POPUP_PAD_DP.dp),
            verticalArrangement = Arrangement.spacedBy(POPUP_GROUP_GAP_DP.dp),
            content = content
        )
    }
}

/**
 * The title that names a popup's section, dimmer than the settings `SubSectionHeader` by design (§2.10).
 */
@Composable
internal fun PopupSectionTitle(title: String) {
    Text(
        text = title,
        color = Color(AppConfig.uiDashboardTextMuted),
        fontSize = POPUP_TITLE_FONT_SP.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = POPUP_TITLE_PAD_DP.dp, vertical = POPUP_TITLE_PAD_DP.dp)
    )
}

/**
 * A group of rows inside a popup — the lighter card that sits on [AppConfig.uiBackground].
 */
@Composable
internal fun PopupSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(POPUP_CORNER_DP.dp),
        color = Color(AppConfig.uiCardBackground)
    ) {
        Column(
            modifier = Modifier.padding(vertical = POPUP_SECTION_PAD_VERTICAL_DP.dp),
            content = content
        )
    }
}

/**
 * **The one row every popup draws** — the filter popup, the sort popup and the dropdown's menu alike, so
 * their metrics, their text and the mark on the taken option cannot drift apart.
 *
 * A 24dp box holds the `✓` in `uiAccent` on the selected row only, the label follows at 15sp — Medium, or
 * SemiBold while it is the one in force — and a switched-off row greys to 0.4 alpha. [trailing] is the
 * slot a caller fills when the row carries a control of its own, the sort popup's direction arrow being
 * the case; it is right-aligned by the row's own slack.
 */
@Composable
internal fun PopupRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(
                horizontal = POPUP_ROW_PAD_HORIZONTAL_DP.dp,
                vertical = POPUP_ROW_PAD_VERTICAL_DP.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(POPUP_CHECK_BOX_DP.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "\u2713",
                    color = Color(AppConfig.uiAccent).copy(alpha = contentAlpha),
                    fontSize = POPUP_CHECK_FONT_SP.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(POPUP_ROW_LABEL_GAP_DP.dp))
        Text(
            text = text,
            color = (if (enabled) Color(AppConfig.uiTextPrimary) else Color(AppConfig.uiTextMuted))
                .copy(alpha = contentAlpha),
            fontSize = POPUP_ROW_FONT_SP.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        }
    }
}
