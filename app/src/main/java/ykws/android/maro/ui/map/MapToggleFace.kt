package ykws.android.maro.ui.map

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

// ─────────────────────────────────────────────────────────────────────────────
// The toggle squares' colour normalization — one pure resolution home
//
// Every square of the map's top-left toggle row rides one constant five-colour set, each read from a
// semantic token:
//
//   pale   semantic.inactive   the square is off
//   amber  semantic.caution    the square is still getting its data
//   blue   semantic.info       the square is on and doing what it is for (nominal, by house taste)
//   green  semantic.compliant  the square is on and standing by
//   red    semantic.danger     the thing the square needs is not there
//
// The two channels: the tile's fill says what the square is doing; the pulse mark says what its data is
// worth. The resolvers below are their one home — pure functions of a state and the palette, with no
// Compose call inside, so the per-state test reaches them directly. The composables in `MapSurface`,
// `MapControls`, `TrackStatusIcon`, `InspectMode` and `RouteOverlay` only paint what a resolver returns.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One resolved square of the row — the state colour the normalization separates, plus the mark's beat.
 *
 * [fill] is the state colour the square's tile lays over the family's own white base
 * (`ui.map.surface.inactive`), or null when the square is off and so wears that base alone with its
 * glyph dimmed. [dot] is the colour of the square's pulse mark, or null when it wears none — the off
 * square, the recenter action and the land/water reading. [dotPulseMs] is the mark's own period, or
 * null for the shared default beat; the loading arm is its only writer, and it names that shared beat
 * rather than a second one, so every pulse in the app beats as one.
 *
 * All are plain ints rather than Compose colours, which is what keeps every resolver here pure and
 * JVM-testable.
 */
internal data class TopToggleFace(
    val fill: Int?,
    val dot: Int?,
    val dotPulseMs: Int? = null
) {
    companion object {
        /** The off square: the shared pale base alone, its glyph dimmed, no mark. */
        val OFF: TopToggleFace = TopToggleFace(fill = null, dot = null)
    }
}

/**
 * GPS — the square's seven states.
 *
 * DEMO is off. ACQUIRING and WEAK wear the acquiring amber with a red mark, no fix held. ESTIMATING
 * (dead reckoning) wears the same amber with the same red mark, a guess being neither real nor
 * complete. HEALTHY and IDLE both wear the nominal blue with a green mark — the receiver's eased
 * cadence is no part of a held fix, so the GPS square has **no idle face**. STALE loses the fix and
 * wears the hazard red with a red mark.
 */
internal fun gpsFace(state: GpsIconState): TopToggleFace = when (state) {
    GpsIconState.DEMO -> TopToggleFace.OFF
    GpsIconState.ACQUIRING, GpsIconState.WEAK ->
        TopToggleFace(AppConfig.statusGpsAcquiring, AppConfig.semanticDanger)
    GpsIconState.HEALTHY ->
        TopToggleFace(AppConfig.statusGpsHealthy, AppConfig.semanticCompliant)
    GpsIconState.IDLE ->
        TopToggleFace(AppConfig.statusGpsIdle, AppConfig.semanticCompliant)
    GpsIconState.STALE ->
        TopToggleFace(AppConfig.statusGpsStale, AppConfig.semanticDanger)
    GpsIconState.ESTIMATING ->
        TopToggleFace(AppConfig.statusGpsEstimating, AppConfig.semanticDanger)
}

/**
 * Tracking — off, then two on-phases told apart by the fill: recording (the boat is moving) wears the
 * nominal blue, standing by (the boat is still) wears the green. Both carry the complete-data green
 * mark, the square's one place where the fill and the dot are the same hue for two different reasons.
 */
internal fun trackingFace(recorderState: TrackRecorderUiState): TopToggleFace =
    when (recorderState.state) {
        TrackRecorderState.OFF -> TopToggleFace.OFF
        TrackRecorderState.ON -> TopToggleFace(
            fill = if (recorderState.isMoving) AppConfig.statusTrackingRecording
            else AppConfig.statusTrackingIdle,
            dot = AppConfig.semanticCompliant
        )
    }

/**
 * Land/water — a **reading** rather than a mode, so it is never off and never dotted: the nominal blue
 * over water, the hazard red over land. The missing mark is what tells it from a control at a glance.
 */
internal fun earthWaterFace(isWater: Boolean): TopToggleFace =
    TopToggleFace(
        fill = if (isWater) AppConfig.statusEarthWaterWater else AppConfig.statusEarthWaterLand,
        dot = null
    )

/** Inspect — off, or armed: the app accent with the complete-data green mark. */
internal fun inspectFace(armed: Boolean): TopToggleFace =
    if (armed) TopToggleFace(AppConfig.uiAccent, AppConfig.semanticCompliant)
    else TopToggleFace.OFF

/**
 * Route — off, then the acquiring amber while the search has not answered (its mark amber, a
 * provisional line being partial) and the nominal blue once the line is followed (its mark green, the
 * data complete). The square never reads the user's line colour, which stays on the line alone.
 *
 * **The loading arm.** While the background warm-up is building tiles ([tileStoreBusy], the holder's own
 * busy window), the square's mark turns white ([`AppConfig.uiMapPulseDotLoading`]) and beats at the
 * app's one shared period ([`MAP_PULSE_DEFAULT_MS`]) — a loading tell for the warm-up's own work, held a
 * grace after its last tile so successive tiles read as a steady window rather than a flicker. The mark
 * is dark while the walk waits at its gate, and a route computing over the store never lights it. It
 * touches the mark's colour and beat only: the fill is resolved exactly as the arms below, so the square
 * still says what the mode is doing, and the mark appears on an otherwise-off square because the tell is
 * about the warm-up running, not about the mode being on.
 */
internal fun routeFace(
    armed: Boolean,
    following: Boolean,
    searching: Boolean,
    tileStoreBusy: Boolean = false
): TopToggleFace {
    val face = when {
        !armed -> TopToggleFace.OFF
        following -> TopToggleFace(AppConfig.routeNavigateColor, AppConfig.semanticCompliant)
        else -> TopToggleFace(
            fill = AppConfig.semanticCaution,
            dot = if (searching) AppConfig.semanticCaution else AppConfig.semanticCompliant
        )
    }
    if (!tileStoreBusy) return face
    return face.copy(
        dot = AppConfig.uiMapPulseDotLoading,
        dotPulseMs = MAP_PULSE_DEFAULT_MS
    )
}

/** Lock — off, or locked: the nominal blue with the complete-data green mark. */
internal fun lockFace(locked: Boolean): TopToggleFace =
    if (locked) TopToggleFace(AppConfig.statusLockOn, AppConfig.semanticCompliant)
    else TopToggleFace.OFF

/** Recenter — an action rather than a mode, so it wears the nominal blue and no mark. */
internal fun recenterFace(): TopToggleFace = TopToggleFace(AppConfig.uiAccent, null)
