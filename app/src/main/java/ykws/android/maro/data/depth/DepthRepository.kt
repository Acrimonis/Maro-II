package ykws.android.maro.data.depth

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthRenderModel
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.GenerationProgress

/**
 * Single source of truth for depth data. The app is a **pure consumer**: it loads the prebaked,
 * fully-cooked depth grid (gathered + merged + validated on the computer by `DepthPrebakeTest`)
 * from bundled assets. No on-device gathering or processing — see
 * docs/MARO_ARCHITECTURE.md § Data Gathering & Processing Lifecycle.
 *
 * Render geometry (isobaths) is derived from the loaded grid at load time on `Dispatchers.Default`
 * (a draw step, not data generation).
 */
class DepthRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var assets: android.content.res.AssetManager? = null

    private val _state = MutableStateFlow<DepthState>(DepthState.Idle)
    val state: StateFlow<DepthState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(GenerationProgress("", 0))
    val progress: StateFlow<GenerationProgress> = _progress.asStateFlow()

    private var grid: DepthGrid? = null
    private var renderModel: DepthRenderModel? = null

    /** Capture the asset manager for loading the bundled prebaked grid. (Name kept for callers.) */
    fun setCacheDir(context: Context) {
        assets = context.assets
    }

    /** Loads the bundled prebaked depth grid from `assets/depth/<regionId>.bin`. */
    suspend fun loadDepth(regionId: String = DepthConstants.REGION_ID) {
        _state.value = DepthState.Loading
        _progress.value = GenerationProgress("", 0)
        val loaded = withContext(ioDispatcher) { readBundled(regionId) }
        if (loaded != null) {
            setReady(loaded)
            _progress.value = GenerationProgress("Terminé", 100)
        } else {
            _state.value = DepthState.Error("Aucune donnée de profondeur préchargée (lancer le prebake).")
        }
    }

    /** Re-reads the bundled prebaked grid (no on-device generation). */
    suspend fun refreshDepth(regionId: String = DepthConstants.REGION_ID) = loadDepth(regionId)

    private fun readBundled(regionId: String): DepthGrid? = try {
        assets?.open("depth/$regionId.bin")?.use { DepthSerializer.deserialize(it.readBytes()) }
    } catch (_: Exception) {
        null
    }

    private suspend fun setReady(g: DepthGrid) {
        grid = g
        renderModel = withContext(Dispatchers.Default) {
            DepthRenderModel(isobaths = DepthIsobaths.build(g), bitmapReady = false)
        }
        _state.value = DepthState.Ready(g)
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun depthAt(lat: Double, lon: Double): DepthSample = grid?.depthAt(lat, lon) ?: DepthSample.NONE
    fun getGrid(): DepthGrid? = grid
    fun getRenderModel(): DepthRenderModel? = renderModel
    fun isLoaded(): Boolean = grid != null
}
