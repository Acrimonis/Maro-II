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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// The route confirmation, hosted by the dashboard slot
//
// While the mode is armed the dashboard's own slot shows this instead of the indicator grid: the
// details of the aimed route, the pin the save would start with, and the four outcomes. Nothing
// floats over the map — the panel rides in the slot the eye is already on, and every gesture outside
// it still reaches the map the aim is being dragged across.
//
// It reuses what already exists rather than re-drawing it: [ConfirmActionButton] for the outcomes
// and [RoutePinOption] for the pin, so the styling and the tick each keep one home.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The route's confirmation panel, for the dashboard slot.
 *
 * The draft is the whole input: [onRoute], [onSaveRoute], [onSaveOnly] and [onCancel] are the four
 * outcomes, and a draft with nothing resolved offers **only the way out**, because there is no route
 * to lock and none to save. The pin is this panel's own state — the initial value the save takes —
 * while every outcome is a button, which is the epic's split.
 *
 * Renders nothing at all when the machine is not drafting: the slot belongs to the dashboard
 * whenever no route is being aimed.
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    onRoute: (RoutePlan) -> Unit,
    onSaveRoute: (RoutePlan, Boolean) -> Unit,
    onSaveOnly: (RoutePlan, Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state as? RouteState.Draft ?: return
    val plan = draft.plan
    val context = LocalContext.current
    var pinned by remember { mutableStateOf(false) }

    val cancel = ConfirmAction(
        label = stringResource(R.string.action_cancel),
        role = ConfirmActionRole.SECONDARY,
        onClick = onCancel
    )

    Column(
        modifier = modifier
            .background(Color(AppConfig.uiDashboardBackground))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.route_dialog_title),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = plan?.let { routeDetailText(context, draft.start, it) }
                ?: stringResource(
                    // Three states read as one null plan and mean different things: nothing asked yet, a
                    // search in flight, and an aim the water has no route to. Only the last is a refusal,
                    // and the middle one is what tells a slow search apart from an aim with no answer —
                    // the feedback that matters now that a drag is served one search at a time.
                    when {
                        draft.searching -> R.string.route_searching
                        draft.asked -> R.string.route_no_route
                        else -> R.string.route_aim_hint
                    }
                ),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )

        if (plan == null) {
            ConfirmActionButton(action = cancel, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        RoutePinOption(pinned = pinned, onPinnedChange = { pinned = it })

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
                    onClick = { onRoute(plan) }
                ),
                modifier = Modifier.weight(1f)
            )
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_save_route),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = { onSaveRoute(plan, pinned) }
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
                    onClick = { onSaveOnly(plan, pinned) }
                ),
                modifier = Modifier.weight(1f)
            )
            ConfirmActionButton(action = cancel, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The panel's detail lines: the frozen start, where the route really ends, and what it costs.
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
