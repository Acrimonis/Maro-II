package ykws.android.maro.data.track

import kotlinx.coroutines.flow.MutableSharedFlow
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.WhereAmIResult

/**
 * Process-scoped bridge so the service-owned [TrackRecorder] can reach the
 * UI-owned marker matching engine ([whereAmI], [idleCapture]) and observe
 * marker mutations ([markerChanges]) without holding an Activity reference.
 *
 * MapScreen registers its implementations at composition time; the recorder
 * reads them lazily at invocation time, so the wiring survives Activity
 * recreation while the foreground service keeps running.
 */
object WhereAmIProvider {
    @Volatile
    var whereAmI: ((LatLng) -> WhereAmIResult)? = null

    @Volatile
    var idleCapture: (suspend (LatLng) -> IdleCaptureResult)? = null

    val markerChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
