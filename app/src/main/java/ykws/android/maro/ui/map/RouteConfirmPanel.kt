package ykws.android.maro.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// The route's panel, hosted by the dashboard slot — one layout, two phases
//
// While the mode is armed the dashboard's own slot shows this instead of the indicator grid: the aim's
// sentence and the actions of the phase, then the details of the route, with nothing floating over the
// map the aim is being dragged across. **Both phases have their own content** (R16, R17) and the panel
// is the same panel: it branches on the phase rather than the screen holding two of them.
//
// It reuses what already exists rather than re-drawing it: [ConfirmActionButton] for the outcomes and
// [RoutePinOption] for the pin, so the styling and the tick each keep one home.
//
// **The panel never raises a dialog itself.** Leaving the following phase asks first (R23), and the
// panel's own **Exit** is one of the two doors — it asks through [onRequestExit], the screen hosts the
// one dialog, so no second dialog exists for the second door.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The route's panel, for the dashboard slot.
 *
 * @param pinned            the pin state the saves start with — held by the screen, because the exit
 *                          dialog's own save reads it and the dialog is the screen's.
 * @param onRoute           **Route**: follows the aimed route without saving it.
 * @param onSaveRoute       **Save as Track and Route**: writes the track, then follows.
 * @param onSaveOnly        **Save as Track and Exit**: writes the track and ends the mode.
 * @param onCancel          the draft's Cancel — leaves the mode and asks nothing (R23).
 * @param onFreezeResume    **Freeze/Resume** while following.
 * @param onAbortRefresh    **Abort** while a refresh runs.
 * @param onSaveTrack       **Save as Track** while following.
 * @param onRequestExit     **Exit** while following — raises the screen's one exit dialog.
 *
 * Renders nothing at all when the machine is idle: the slot belongs to the dashboard whenever no route
 * is being aimed or followed.
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onSaveOnly: () -> Unit,
    onCancel: () -> Unit,
    onFreezeResume: () -> Unit,
    onAbortRefresh: () -> Unit,
    onSaveTrack: () -> Unit,
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is RouteState.Choosing -> ChoosingPanel(
            choosing = state,
            pinned = pinned,
            onPinnedChange = onPinnedChange,
            onRoute = onRoute,
            onSaveRoute = onSaveRoute,
            onSaveOnly = onSaveOnly,
            onCancel = onCancel,
            modifier = modifier
        )

        is RouteState.Following -> FollowingPanel(
            following = state,
            onFreezeResume = onFreezeResume,
            onAbortRefresh = onAbortRefresh,
            onSaveTrack = onSaveTrack,
            onRequestExit = onRequestExit,
            modifier = modifier
        )

        RouteState.Idle -> return
    }
}

/**
 * **Choosing the destination** (R16): one sentence for the state, the four details once a route has
 * resolved, the pin the save would start with and the four outcomes.
 *
 * A phase with nothing resolved offers **only the way out** — there is no route to lock and none to
 * save — and the sentence is the one its own state deserves: the origin's refusal first (there is no
 * anchor to route from), then the aim's own refusal with the crosshair painted by the map, then
 * "Computing…" or "no route to this aim", and the arming hint while nothing has been asked at all.
 */
@Composable
private fun ChoosingPanel(
    choosing: RouteState.Choosing,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onSaveOnly: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = choosing.plan
    val context = LocalContext.current

    PanelColumn(modifier = modifier) {
        Text(
            text = stringResource(R.string.route_dialog_title),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = plan?.let { routeDetailText(context, choosing.start, it) }
                ?: choosingSentence(choosing),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (plan == null) {
            ConfirmActionButton(action = cancelAction(onCancel), modifier = Modifier.fillMaxWidth())
            return@PanelColumn
        }

        RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)

        // Two rows of two, so the four outcomes fit the slot the dashboard occupies in portrait
        // without the panel having to scroll to be usable.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_route),
                    role = ConfirmActionRole.PRIMARY,
                    onClick = onRoute
                ),
                modifier = Modifier.weight(1f)
            )
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_save_route),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = onSaveRoute
                ),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_save_only),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = onSaveOnly
                ),
                modifier = Modifier.weight(1f)
            )
            ConfirmActionButton(action = cancelAction(onCancel), modifier = Modifier.weight(1f))
        }
    }
}

/**
 * **Following a route** (R17): the phase's sentence, the four details, its **status**, and the three
 * actions — with the first changing while a refresh runs (**Abort**) and while it is frozen
 * (**Resume**), each label naming the action it offers.
 *
 * The status is the one line the dashboard carries while the refresh cycle works (R15): it says the
 * route is current, that a recompute is running, or that the session's freeze is holding the gate's
 * clock still.
 */
@Composable
private fun FollowingPanel(
    following: RouteState.Following,
    onFreezeResume: () -> Unit,
    onAbortRefresh: () -> Unit,
    onSaveTrack: () -> Unit,
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = following.plan
    val context = LocalContext.current
    val refreshing = following.refresh == RouteRefresh.RUNNING

    PanelColumn(modifier = modifier) {
        Text(
            text = stringResource(R.string.route_following_active),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = routeDetailText(context, plan.start, plan),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                when {
                    following.frozen -> R.string.route_status_frozen
                    refreshing -> R.string.route_following_recomputing
                    else -> R.string.route_status_current
                }
            ),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfirmActionButton(
                action = if (refreshing) {
                    ConfirmAction(
                        label = stringResource(R.string.route_action_abort),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onAbortRefresh
                    )
                } else {
                    ConfirmAction(
                        label = stringResource(
                            if (following.frozen) R.string.action_resume
                            else R.string.route_action_freeze
                        ),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onFreezeResume
                    )
                },
                modifier = Modifier.weight(1f)
            )
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_save_track),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = onSaveTrack
                ),
                modifier = Modifier.weight(1f)
            )
        }
        ConfirmActionButton(
            action = ConfirmAction(
                label = stringResource(R.string.route_action_exit),
                role = ConfirmActionRole.PRIMARY,
                onClick = onRequestExit
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The panel's own surface: the dashboard's background, scrolled, on its own gutters. */
@Composable
private fun PanelColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(Color(AppConfig.uiDashboardBackground))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}

/** The one way out of the choosing phase, and it asks nothing (R23). */
@Composable
private fun cancelAction(onCancel: () -> Unit): ConfirmAction = ConfirmAction(
    label = stringResource(R.string.action_cancel),
    role = ConfirmActionRole.SECONDARY,
    onClick = onCancel
)

/**
 * The sentence the choosing phase shows while no route has resolved — the state's own words.
 *
 * A refused **origin** outranks everything (there is no anchor to route from, and it reads as a line
 * about the position rather than on the target, R7); a refused **aim** names its reason with the
 * outcomes staying hidden (R6); then the three readings a null plan can mean — nothing asked yet, a
 * search in flight, and an aim no route answers.
 */
@Composable
private fun choosingSentence(choosing: RouteState.Choosing): String = when {
    choosing.originRefusal != null -> stringResource(
        R.string.route_origin_invalid,
        stringResource(choosing.originRefusal.labelResId)
    )

    choosing.refusal != null -> stringResource(
        R.string.route_destination_invalid,
        stringResource(choosing.refusal.labelResId)
    )

    choosing.searching -> stringResource(R.string.route_searching)
    choosing.asked -> stringResource(R.string.route_no_route)
    else -> stringResource(R.string.route_aim_hint)
}

/**
 * The panel's detail lines: the frozen start, where the route really ends, and what it costs — the
 * four details R24 names, in the order the eye reads them.
 *
 * The time is the plan's own cruise over its own legs — the trip figure's pace belongs to the
 * dashboard's distance cell, not to the panel that locks the route.
 */
private fun routeDetailText(context: Context, start: RoutePoint?, plan: RoutePlan): String {
    val course = plan.remainingFrom(plan.start)
    return buildString {
        start?.let {
            append(context.getString(R.string.route_label_start))
                .append(" : ").append(it.asCoordinates()).append('\n')
        }
        append(context.getString(R.string.route_label_destination))
            .append(" : ").append(plan.destination.asCoordinates())
        if (plan.destinationMoved) {
            append("  (").append(context.getString(R.string.route_destination_moved)).append(')')
        }
        append('\n')
        append(context.getString(R.string.route_label_distance)).append(" : ")
            .append(context.getString(R.string.route_trip_distance_nm, plan.distanceNm))
            .append('\n')
        append(context.getString(R.string.route_label_eta)).append(" : ")
            .append(etaText(context, course.durationSec))
        // **A crossing is said before the route is locked.** The panel is where the boat decides, so a
        // route that has to enter a zone — because no way around exists, or because one end of it
        // stands inside — names the zone it enters rather than presenting the crossing as an ordinary
        // one.
        if (plan.forcedCrossingZoneNames.isNotEmpty()) {
            append('\n')
            append(
                context.getString(
                    R.string.route_forced_crossing,
                    plan.forcedCrossingZoneNames.joinToString(", ")
                )
            )
        }
    }
}

/** A point as the panel prints it — four decimals, the app's own convention for a coordinate. */
private fun RoutePoint.asCoordinates(): String =
    String.format(Locale.US, "%.4f, %.4f", latitude, longitude)

/** A duration as the dashboard's own ETA cells print it: whole seconds under a minute, else m/s. */
private fun etaText(context: Context, seconds: Double): String {
    val whole = seconds.toInt()
    return if (whole < 60) {
        context.getString(R.string.dash_eta_sec, whole)
    } else {
        context.getString(R.string.dash_eta_min_sec, whole / 60, whole % 60)
    }
}
