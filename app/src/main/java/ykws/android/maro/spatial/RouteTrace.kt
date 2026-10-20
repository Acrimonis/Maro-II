package ykws.android.maro.spatial

import android.util.Log

/**
 * **The route trace's one gate** — the `MaroRoute` tag and its level, read once and remembered, shared by
 * the engine and the route mode's view model so the two never disagree about whether the trace is on.
 *
 * The read is taken through [runCatching] because a plain JVM unit test carries the mockable
 * `android.util.Log`, whose methods throw: there the gate answers `false` and the trace stays inert. On the
 * device it is the ordinary level, set with `adb shell setprop log.tag.MaroRoute INFO`.
 */
internal object RouteTrace {

    const val TAG = "MaroRoute"

    val enabled: Boolean by lazy {
        runCatching { Log.isLoggable(TAG, Log.INFO) }.getOrDefault(false)
    }

    /** Writes the line [message] builds — built only where the gate is open. */
    inline fun line(message: () -> String) {
        if (enabled) Log.i(TAG, message())
    }
}
