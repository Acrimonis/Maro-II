package ykws.android.maro.data.power

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackRecordingService

/**
 * Android-side owner of the power decision — the imperative half of the feature
 * (`xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` §4.2).
 *
 * It gathers the inputs, delegates the actual decision to the framework-free [PowerPolicy], and
 * publishes the result as [state]. It deliberately does **not** touch the window: `FLAG_KEEP_SCREEN_ON`
 * needs an Activity, so the Activity remains a thin applier that only flips the flag on change.
 *
 * Scope in phase 1 is the **screen channel**. Own
 * - [PowerPolicy] — pure decision
 * - [applySettings] — the user-facing settings
 * - [onSpeed] / [onTouch] — pushed runtime inputs
 * - [isExemptFromBatteryOptimizations] — the exemption query's single home
 * - [start] — the recording observer and the grace ticker
 *
 * ### Input wiring
 * The UI position pipeline is the speed source, pushed in through [onSpeed], so the keeper adds no
 * GPS subscription of its own and `data/power/` never depends on `ui/`. A null push (the source has
 * no speed right now) is **ignored**: the last known speed is retained and loss of fixes is handled
 * by the staleness bound instead, which keeps a brief dropout from dropping the hold.
 *
 * ### Freshness contract (do not weaken this)
 * A push is **not** a reading. The source re-publishes its cached speed whenever unrelated state
 * changes, so the caller states whether the reading is new ([SpeedFreshness]) and only a new reading
 * ages from zero. Inferring freshness from call arrival — the original implementation — let a stale
 * cached speed re-stamp itself indefinitely and hold the screen on forever.
 */
class PowerKeeper(private val context: Context) {

    private val policy = PowerPolicy()

    private val _state = MutableStateFlow(PowerState.OFF)

    /** Current decision. Collect it; do not mutate. */
    val state: StateFlow<PowerState> = _state.asStateFlow()

    // ── Inputs ───────────────────────────────────────────────────────────────
    private var keepScreenOn = false
    private var movementGateEnabled = false
    private var thresholdKn = AppConfig.powerScreenMovementThresholdKn
    private var graceMs = AppConfig.powerScreenGraceDefaultMinutes * 60_000L

    /** Null until a real speed has ever been reported. */
    private var speedKn: Float? = null

    /** When the last *genuinely new* speed reading arrived — see the freshness contract above. */
    private val freshness = SpeedFreshness()
    private var lastTouchMs = SystemClock.elapsedRealtime()
    private var recording = false

    /**
     * Push the persisted settings. Called on every settings change by the host.
     */
    fun applySettings(settings: AppSettings) {
        keepScreenOn = settings.keepScreenOn
        movementGateEnabled = settings.keepScreenOnMovementGate
        thresholdKn = settings.keepScreenOnSpeedThresholdKn
        graceMs = settings.keepScreenOnGraceMinutes
            .coerceIn(AppConfig.powerScreenGraceMinMinutes, AppConfig.powerScreenGraceMaxMinutes) * 60_000L
        recompute()
    }

    /**
     * Push the latest speed over ground. Ignored when null — see the class KDoc.
     *
     * @param isNewReading true only when the source knows this is a new reading; re-publishing a
     *   cached value must pass false, or a stale speed will never age out. The parameter is
     *   deliberately required so a caller cannot fall back to inferring freshness from the call.
     */
    fun onSpeed(speedKn: Float?, isNewReading: Boolean) {
        if (speedKn == null) return
        this.speedKn = speedKn
        freshness.onReading(SystemClock.elapsedRealtime(), isNewReading)
        recompute()
    }

    /**
     * Record user interaction. Called from the Activity's `dispatchTouchEvent`, which is the only
     * place that sees touches handled by the osmdroid `MapView` as well as by Compose.
     *
     * Deliberately cheap: it stores a timestamp and only re-evaluates when the screen is currently
     * *not* held, so a touch cannot become a per-event recomposition of the whole tree.
     */
    fun onTouch() {
        lastTouchMs = SystemClock.elapsedRealtime()
        if (!_state.value.screenOn) recompute()
    }

    /**
     * Whether the app is already exempt from battery optimization. Returns `true` when the service
     * cannot be reached, so a failure to query never turns into a nagging prompt.
     */
    fun isExemptFromBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Begin observing the recording floor and start the grace ticker.
     *
     * The ticker exists because the grace period expires with **no** input change: without it the
     * screen would stay held until the next fix or touch. It re-evaluates only while the gate is
     * actually holding the screen, and [MutableStateFlow] suppresses equal values, so a steady
     * state costs nothing downstream.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            TrackRecordingService.isRecording.collect { isRecording ->
                recording = isRecording
                recompute()
            }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (keepScreenOn && movementGateEnabled) recompute()
            }
        }
    }

    private fun recompute() {
        val now = SystemClock.elapsedRealtime()
        _state.value = policy.evaluate(
            PowerInputs(
                keepScreenOn = keepScreenOn,
                movementGateEnabled = movementGateEnabled,
                speedKn = speedKn,
                // No genuine reading yet → "cannot prove freshness", which the policy resolves as
                // stale (unless the speed itself is still unknown, which holds instead).
                fixAgeMs = freshness.ageMs(now) ?: Long.MAX_VALUE,
                lastTouchAgeMs = now - lastTouchMs,
                thresholdKn = thresholdKn,
                graceMs = graceMs,
                stalenessBoundMs = AppConfig.powerScreenStalenessBoundMs,
                recording = recording,
            )
        )
    }

    private companion object {
        /** Grace-ticker period. Coarse on purpose: the release only needs to be roughly on time. */
        const val TICK_MS = 5_000L
    }
}
