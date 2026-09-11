package ykws.android.maro.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.map.DrawerSlot
import ykws.android.maro.ui.map.SlideDirection

/** Visual role of a [ConfirmAction]. */
enum class ConfirmActionRole { PRIMARY, SECONDARY, DANGER }

/**
 * One action of a [ConfirmDialog]: a full-width stacked button.
 *
 * @param label   Button label (localized by the caller).
 * @param role    [ConfirmActionRole.PRIMARY] = accent filled, [ConfirmActionRole.DANGER] =
 *                danger filled, [ConfirmActionRole.SECONDARY] = outlined accent label.
 * @param onClick Fired on tap. The caller owns every side effect.
 */
data class ConfirmAction(
    val label: String,
    val role: ConfirmActionRole = ConfirmActionRole.PRIMARY,
    val onClick: () -> Unit
)

/**
 * Animation duration (ms) for the confirmation dialog panel's entrance and exit slide. The scrim is
 * a hard on/off toggle and does not share this window — the ladder scrim yields to it while the
 * dialog is visible, so the two dim layers never stack.
 */
internal const val ConfirmDialogAnimMs = 450

/**
 * A confirmation dialog described as data — title, message, optional content slot and actions — so
 * a surface whose own overlay would be clipped to its host panel (a list drawer) can hand the
 * dialog up to the overlay ladder and have it rendered over the drawers and the map.
 *
 * [options] and the [actions] lambdas are composed/run at the ladder level, but are captured where
 * the dialog's state lives, so confirming still drives the originating surface (e.g. exits that
 * list's multiselect mode).
 */
data class ConfirmRequest(
    val title: String,
    val message: String? = null,
    val options: (@Composable ColumnScope.() -> Unit)? = null,
    val actions: List<ConfirmAction>
)

/**
 * Ladder-level sink for [ConfirmRequest]s raised by drawer-hosted surfaces. The screen owns one,
 * provides it through [LocalConfirmDialogHost] and paints it with [ConfirmRequestHost] above the
 * drawers. [show] replaces any pending request; [dismiss] closes the current one.
 */
class ConfirmDialogHostState {
    var request by mutableStateOf<ConfirmRequest?>(null)
        private set

    fun show(request: ConfirmRequest) {
        this.request = request
    }

    fun dismiss() {
        request = null
    }
}

/** Provided by the screen; null when no ladder-level host is installed. */
val LocalConfirmDialogHost = compositionLocalOf<ConfirmDialogHostState?> { null }

/**
 * Paints the [ConfirmRequest] currently held by [state] on the overlay ladder. The last request is
 * retained after [ConfirmDialogHostState.dismiss] so the panel can animate out.
 */
@Composable
fun ConfirmRequestHost(state: ConfirmDialogHostState) {
    val target = state.request
    var retained by remember { mutableStateOf<ConfirmRequest?>(null) }
    LaunchedEffect(target) {
        if (target != null) retained = target
    }
    retained?.let { request ->
        ConfirmDialog(
            title = request.title,
            visible = target != null,
            onDismiss = { state.dismiss() },
            message = request.message,
            options = request.options,
            actions = request.actions
        )
    }
}

/**
 * Canonical confirmation dialog — replaces `AlertDialog` confirmations and `ConfirmSheet`.
 *
 * Rendered on the app's own overlay ladder (no framework sheet, no platform dialog window):
 * a bottom-anchored panel that slides up and fades in, over its own full-screen scrim.
 *
 * Dim: owned by this component. A full-screen `ui.scrim.alpha` layer is painted beneath the panel
 * as a hard on/off toggle (no fade), so the whole screen — drawers and map included — stays dimmed
 * and touch-blocked while the dialog is visible, even when the dialog is painted by the ladder above
 * a clipped drawer. The ladder scrim yields to it while visible, so the dims never stack.
 *
 * Layout, top to bottom: title (18 sp bold) → message (14 sp `uiTextPrimary`) → [options] →
 * 0.5 dp `uiDividerColor` divider → [actions] stacked full width, 8 dp apart.
 *
 * Geometry: the panel width equals the device's portrait width — `min(maxWidth, maxHeight)` —
 * in both orientations, unconditionally; it is horizontally centred and bottom-anchored flush with
 * the bottom edge (rounded top corners, square bottom), the navigation-bar inset is applied inside
 * the panel, its height wraps its content and scrolls when taller than the available space
 * (accounting for the IME and the navigation-bar inset).
 *
 * Dismissal: the scrim tap, system back and every action that should abort run [onDismiss].
 * [onDismiss] may carry a side effect (recovery saves the checkpoint). The component adds no
 * implicit Cancel — a Cancel is just another [ConfirmAction] supplied by the caller, last.
 *
 * @param title    Dialog title.
 * @param visible  Whether the dialog is shown; the host keeps the composable mounted so the
 *                 exit animation can play.
 * @param onDismiss Runs on scrim tap, back press and any caller-supplied Cancel action.
 * @param message  Optional body text.
 * @param options  Optional content slot (checkbox rows, text field).
 * @param actions  Caller-provided actions rendered in order.
 */
@Composable
fun ConfirmDialog(
    title: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    message: String? = null,
    options: (@Composable ColumnScope.() -> Unit)? = null,
    actions: List<ConfirmAction>
) {
    val density = LocalDensity.current
    val imeHeightDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val navBarDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

    BackHandler(enabled = visible) { onDismiss() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Portrait width of the device — identical in both orientations, no cap token.
        val dialogWidth = min(maxWidth, maxHeight)
        // Wrap-and-scroll cap: full height minus the IME (the panel is offset up by it), the
        // navigation-bar inset (applied inside the panel) and a 16dp top breathing margin.
        val maxPanelHeight = (maxHeight - navBarDp - imeHeightDp - 16.dp)
            .coerceAtLeast(96.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            // ── Scrim (full-screen, beneath the panel) ────────────────────────
            // Owned by the dialog so the dim still covers the whole screen when the dialog is
            // painted above a clipped drawer. A hard on/off toggle (no fade) — the ladder scrim
            // yields to it while visible, so the two dim layers never stack.
            if (visible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = AppConfig.uiScrimAlpha))
                        .clickable(interactionSource = null, indication = null) { onDismiss() }
                )
            }

            // ── Panel (slide up + fade via the bottom slot) ───────────────────
            DrawerSlot(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(dialogWidth)
                    .offset(y = 0.dp - imeHeightDp),
                slideDirection = SlideDirection.FROM_BOTTOM,
                durationMs = ConfirmDialogAnimMs
            ) {
                Surface(
                    color = Color(AppConfig.uiBackground),
                    // Rounded top only: a flush panel with rounded bottom corners would
                    // show the scrim through the notches.
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Accent outline drawn as an explicit open path: up the left side,
                        // around the top-left radius, across the top, around the top-right
                        // radius, back down the right side. The bottom edge is left open —
                        // the panel is flush with the screen bottom and must keep reading as
                        // a pull-up drawer (`Surface(border = …)` would trace all four sides
                        // and close the shape at the bottom).
                        .drawBehind {
                            val radius = 16.dp.toPx()
                            val outline = Path().apply {
                                moveTo(0f, size.height)
                                lineTo(0f, radius)
                                arcTo(Rect(0f, 0f, 2 * radius, 2 * radius), 180f, 90f, false)
                                lineTo(size.width - radius, 0f)
                                arcTo(
                                    Rect(size.width - 2 * radius, 0f, size.width, 2 * radius),
                                    270f,
                                    90f,
                                    false
                                )
                                lineTo(size.width, size.height)
                            }
                            drawPath(
                                path = outline,
                                color = Color(AppConfig.uiAccent),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPanelHeight)
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color(AppConfig.uiTextPrimary),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { heading() }
                        )
                        if (message != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = message,
                                color = Color(AppConfig.uiTextPrimary),
                                fontSize = 14.sp
                            )
                        }
                        if (options != null) {
                            Spacer(Modifier.height(12.dp))
                            options()
                            Spacer(Modifier.height(4.dp))
                        } else {
                            Spacer(Modifier.height(16.dp))
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(AppConfig.uiDividerColor)
                        )
                        Spacer(Modifier.height(12.dp))
                        actions.forEachIndexed { index, action ->
                            if (index > 0) Spacer(Modifier.height(8.dp))
                            ConfirmActionButton(action)
                        }
                    }
                }
            }
        }
    }
}

/** One stacked full-width action button, styled by its [ConfirmActionRole]. */
@Composable
private fun ConfirmActionButton(action: ConfirmAction) {
    val shape = RoundedCornerShape(12.dp)
    when (action.role) {
        ConfirmActionRole.PRIMARY -> Button(
            onClick = action.onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(AppConfig.uiAccent)),
            shape = shape
        ) {
            Text(action.label, color = Color.White, fontWeight = FontWeight.Bold)
        }
        ConfirmActionRole.DANGER -> Button(
            onClick = action.onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(AppConfig.semanticDanger)),
            shape = shape
        ) {
            Text(action.label, color = Color.White, fontWeight = FontWeight.Bold)
        }
        ConfirmActionRole.SECONDARY -> OutlinedButton(
            onClick = action.onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape
        ) {
            Text(action.label, color = Color(AppConfig.uiAccent))
        }
    }
}
