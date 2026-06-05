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
import java.io.File

/**
 * Single source of truth for depth data. Mirrors `CoastlineRepository`: cache-aside load
 * (Protobuf binary in `filesDir/depth/{regionId}.bin`), reactive [StateFlow] state +
 * progress, and a derived [DepthRenderModel] (isobaths) built off the main thread.
 *
 * The shallow (Litto3D) tier is injected as [preloadedShallow] (task: preloaded lane).
 */
class DepthRepository(
    private val generator: DepthGenerator = DepthGenerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var cacheDir: File? = null

    private val _state = MutableStateFlow<DepthState>(DepthState.Idle)
    val state: StateFlow<DepthState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(GenerationProgress("", 0))
    val progress: StateFlow<GenerationProgress> = _progress.asStateFlow()

    private var grid: DepthGrid? = null
    private var renderModel: DepthRenderModel? = null

    // ── Cache management ──────────────────────────────────────────────────────

    fun setCacheDir(context: Context) {
        cacheDir = File(context.filesDir, "depth")
        cacheDir?.mkdirs()
    }

    private fun cacheFile(regionId: String): File? = cacheDir?.resolve("$regionId.bin")

    private fun readFromCache(regionId: String): DepthGrid? {
        val file = cacheFile(regionId) ?: return null
        if (!file.exists()) return null
        return try {
            DepthSerializer.deserialize(file.readBytes())
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun writeToCache(regionId: String, data: DepthGrid) {
        val file = cacheFile(regionId) ?: return
        try {
            file.writeBytes(DepthSerializer.serialize(data))
        } catch (_: Exception) {
            // Non-critical — cache is a convenience.
        }
    }

    private fun deleteCacheFile(regionId: String) {
        cacheFile(regionId)?.delete()
    }

    // ── Load / Refresh (cache-aside) ──────────────────────────────────────────

    /**
     * Loads depth for a region: cache hit → ready immediately; miss → fetch + merge +
     * validate (one-time lazy, on first map init), persist, then ready.
     */
    suspend fun loadDepth(
        regionId: String = DepthConstants.REGION_ID,
        preloadedShallow: DepthGrid? = null
    ) {
        _state.value = DepthState.Loading
        _progress.value = GenerationProgress("", 0)

        val cached = withContext(ioDispatcher) { readFromCache(regionId) }
        if (cached != null) {
            setReady(cached)
            _progress.value = GenerationProgress("Terminé (cache)", 100)
            return
        }

        try {
            val result = generator.generate(
                regionId = regionId,
                preloadedShallow = preloadedShallow
            ) { phase, pct -> _progress.value = GenerationProgress(phase, pct) }
            withContext(ioDispatcher) { writeToCache(regionId, result) }
            setReady(result)
            _progress.value = GenerationProgress("Terminé", 100)
        } catch (e: Exception) {
            _state.value = DepthState.Error(e.message ?: "Erreur lors du chargement des profondeurs.")
        }
    }

    /** Force a fresh fetch (deletes the cache first). */
    suspend fun refreshDepth(
        regionId: String = DepthConstants.REGION_ID,
        preloadedShallow: DepthGrid? = null
    ) {
        withContext(ioDispatcher) { deleteCacheFile(regionId) }
        loadDepth(regionId, preloadedShallow)
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
