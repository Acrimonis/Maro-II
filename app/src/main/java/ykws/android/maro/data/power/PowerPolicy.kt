package ykws.android.maro.data.power

/**
 * Immutable snapshot of every input one [PowerPolicy] evaluation needs.
 *
 * Time is expressed as **ages**, not timestamps, so the policy stays framework-free and
 * deterministic: the caller owns the clock (`SystemClock.elapsedRealtime()`) and passes elapsed
 * milliseconds. That is what makes the whole policy testable on the JVM.
 *
 * @property keepScreenOn          the user's master "don't lock the phone" setting.
 * @property movementGateEnabled   when false the screen is held whenever the app is in front —
 *                                 the behaviour that shipped before this feature. When true, the
 *                                 movement gate and the interaction grace decide instead.
 * @property speedKn               last known speed over ground in knots, or null when no speed has
 *                                 ever been reported since the app started.
 * @property fixAgeMs              age of the last real speed reading (ms).
 * @property lastTouchAgeMs        age of the last user touch (ms).
 * @property thresholdKn           speed above which the boat counts as moving.
 * @property graceMs               how long either movement or a touch keeps holding the screen.
 * @property stalenessBoundMs      age beyond which the last speed reading is no longer trusted.
 * @property recording             true while a track is recording — the keep-alive floor.
 * @property passiveCaptureEnabled reserved: passive idle-marker capture (phase 4a).
 * @property zoneLatched           reserved: safety-zone keep-alive latch (phase 4b).
 */
data class PowerInputs(
    val keepScreenOn: Boolean,
    val movementGateEnabled: Boolean,
    val speedKn: Float?,
    val fixAgeMs: Long,
    val lastTouchAgeMs: Long,
    val thresholdKn: Float,
    val graceMs: Long,
    val stalenessBoundMs: Long,
    val recording: Boolean,
    val passiveCaptureEnabled: Boolean = false,
    val zoneLatched: Boolean = false,
)

/**
 * Outcome of one [PowerPolicy] evaluation.
 *
 * @property screenOn  whether `FLAG_KEEP_SCREEN_ON` should be held on the window right now.
 * @property keepAlive whether the app still has a reason to stay alive in the background. The
 *                     battery-exemption advisory is deliberately **not** part of this type — it is
 *                     owned by `PowerKeeper`, not a property of the power state.
 */
data class PowerState(
    val screenOn: Boolean,
    val keepAlive: Boolean,
) {
    companion object {
        val OFF = PowerState(screenOn = false, keepAlive = false)
    }
}

/**
 * Framework-free power decision — the pure half of the power-management feature
 * (`xTrack/Performance/260912_FEAT_PLN_Performance_power-management-centralization.md` §4.1).
 *
 * No Android imports, no state, no clock: callers pass a [PowerInputs] snapshot and get a
 * [PowerState] back, exactly like [`ykws.android.maro.data.location.AdaptiveGpsPolicy`] folds fixes
 * into a cadence. Being stateless means every rule below is directly unit-testable.
 *
 * Two channels are decided independently:
 *
 * - **Screen channel** — held while the boat is moving, or within [PowerInputs.graceMs] of the last
 *   touch. Either condition holds it; both must lapse before it releases. Releasing does not lock
 *   the screen, it stops *preventing* the lock, so the device's own timeout takes over.
 * - **Keep-alive channel** — the service side. Recording is the floor; the remaining reasons are
 *   reserved for later phases.
 *
 * Unknown versus lost speed are deliberately different:
 * - **Never had a fix** → moving, so the screen is held. Transient, self-corrects within seconds.
 * - **Fix gone stale** → not moving. Motion cannot be proven, and holding forever is the worse
 *   failure; a touch re-holds the screen immediately.
 */
class PowerPolicy {

    fun evaluate(inputs: PowerInputs): PowerState {
        // Never had a fix → optimistically moving (transient). Stale fix → cannot prove motion.
        val moving = when {
            inputs.speedKn == null -> true
            inputs.fixAgeMs > inputs.stalenessBoundMs -> false
            else -> inputs.speedKn > inputs.thresholdKn
        }

        val held = if (inputs.movementGateEnabled) {
            moving || inputs.lastTouchAgeMs <= inputs.graceMs
        } else {
            // Additive semantics: gate off means today's behaviour — hold while the app is in front.
            true
        }

        return PowerState(
            screenOn = inputs.keepScreenOn && held,
            keepAlive = inputs.recording || inputs.passiveCaptureEnabled || inputs.zoneLatched,
        )
    }

    // Deliberately no companion constants: every number this policy needs arrives through
    // [PowerInputs]. The bounds, defaults and staleness bound live in `maro.properties` (surfaced as
    // typed `AppConfig` accessors) and the user's choices live in `SettingsManager` — so the policy
    // stays free of both.
}
