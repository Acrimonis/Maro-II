package ykws.android.maro.spatial.mesh

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.data.route.RouteMeshSerializer
import java.io.IOException

/**
 * Owns the navigation mesh: one stream-deserialize on [Dispatchers.Default], into flat arrays the
 * repository keeps for its life.
 *
 * This is the only file that opens the mesh, exactly as the depth and coastline repositories are the
 * only files that open theirs, and it decodes once rather than per search — the load is the
 * expensive half (a few MB of protobuf), the search is the cheap one.
 *
 * It answers with the mesh or with null and reports **no readiness of its own**: what the app knows
 * about the mesh is [`MeshRouteEngine`]'s readiness, which is what keeps the feature's gate and the
 * asset from being two statements of the same fact. What it does hold is the decoded mesh, behind a
 * **single-flight** guard — see [loadIfNeeded].
 */
internal class RouteMeshRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    @Volatile
    private var loaded: RouteMeshArrays? = null

    /**
     * Serialises the decode, so the load is single-flight: the initial preparation and the
     * readiness retry can overlap, and without this both would decode the same mesh.
     */
    private val loadMutex = Mutex()

    /** @return the decoded mesh, or null while it has not been loaded. */
    val mesh: RouteMeshArrays? get() = loaded

    /**
     * Decodes the mesh if it is not already held, and **only once even when called twice at once**.
     *
     * The second caller waits for the first and then finds the mesh decoded, so the stream-deserialize
     * of a few MB of protobuf happens once rather than once per caller. A load that failed is not
     * cached as a fact: the next call tries again, which is what makes a missing asset a state to
     * report rather than a state to remember.
     *
     * @return the mesh, or null when the asset is missing — a region that was never baked is a
     *         state to report, not a failure to throw.
     */
    suspend fun loadIfNeeded(): RouteMeshArrays? {
        loaded?.let { return it }
        return loadMutex.withLock {
            // Re-read under the lock: whoever waited for it may find the mesh already decoded.
            loaded ?: withContext(dispatcher) { decode() }?.also { loaded = it }
        }
    }

    private fun decode(): RouteMeshArrays? = try {
        context.assets.open(ASSET_PATH).use { RouteMeshSerializer.deserialize(it) }
    } catch (_: IOException) {
        null
    }

    companion object {
        /**
         * The mesh asset the bake writes, named for the region so two corridors can coexist and a
         * region change invalidates the mesh the way it invalidates the other baked caches.
         */
        val ASSET_PATH: String = "route/" + BuildConfig.REGION_ID + ".bin"
    }
}
