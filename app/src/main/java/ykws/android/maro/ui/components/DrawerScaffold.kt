package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import androidx.compose.ui.graphics.Shape

// ─────────────────────────────────────────────────────────────────────────────
// DrawerHeader — canonical drawer header promoted from MarkerDrawer.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Canonical drawer header used by [DrawerScaffold] and available standalone.
 *
 * Tokens per [docs/ui-drawer-guidelines.md §6]:
 * - Back button: 32dp IconButton, CircleShape, uiSettingsSwitchTrackInactive bg
 * - Back icon: ArrowBack 18dp, uiSettingsTextPrimary tint
 * - Title: 17sp Bold, uiSettingsTextPrimary, maxLines=1, ellipsis overflow
 * - Back→title spacer: 16dp
 */
@Composable
fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = 6.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_close),
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerScaffold — fixed-header + scrollable-body drawer shell
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable drawer scaffold: fixed [DrawerHeader] at top, scrollable (or static)
 * body below.
 *
 * Structure:
 * ```
 * Box(fillMaxSize, clip(shape), background(uiSettingsBackground), modifier)
 *   └─ Column(fillMaxSize)
 *        ├─ DrawerHeader(title, onClose, headerActions, hPad, vPad)  ← FIXED
 *        └─ Box(Modifier.weight(1f).fillMaxWidth())                   ← scroll host
 *             └─ if (scrollable) Column(verticalScroll, contentPadding) { content() }
 *                else Column(contentPadding) { content() }
 * ```
 *
 * @param title                    Header title text.
 * @param onClose                  Back-button / dismiss callback.
 * @param modifier                 Outer Modifier applied to the root Box.
 * @param headerActions            Composable slot in the header Row (right side).
 * @param headerHorizontalPadding  Horizontal padding for the header Row (default 24dp).
 * @param headerVerticalPadding    Vertical padding for the header Row (default 3dp).
 * @param contentPadding           Padding applied around the scrollable content body.
 * @param scrollable               Whether the body scrolls (true) or is static (false).
 * @param statusBarsInset          If true, applies .windowInsetsPadding(statusBars)
 *                                 after the background (for full-screen drawer panels).
 * @param shape                    Clip shape for the root Box (default left-side drawer).
 * @param content                  Body content, in a [ColumnScope].
 */
@Composable
fun DrawerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    statusBarsInset: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val bgModifier = Modifier
        .fillMaxSize()
        .background(ComposeColor(AppConfig.uiSettingsBackground), shape)

    Box(
        modifier = modifier
            .then(bgModifier)
            .then(if (statusBarsInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DrawerHeader(
                title = title,
                onClose = onClose,
                actions = headerActions,
                horizontalPadding = headerHorizontalPadding,
                verticalPadding = headerVerticalPadding
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (scrollable) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(contentPadding),
                        content = content
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        content = content
                    )
                }
            }
        }
    }
}
