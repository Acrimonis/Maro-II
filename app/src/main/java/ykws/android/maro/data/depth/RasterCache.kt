
package ykws.android.maro.data.depth
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthGrid

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException

/**
 * RawBuf disk cache for raster [Bitmap] outputs (depth colour map, low-depth warning overlay).
 *
 * Persists pixels through bounded raw buffers and file channels, avoiding image encode/decode
 * overhead. Two ~27 MB rasters read back in ~105 ms total vs ~6 s cold rebuild.
 *
 * Cache files live in [Context.cacheDir]/raster/ — cleared by the OS when storage runs low,
 * or explicitly via [evict].
 */
object RasterCache {

    private const val TAG = "RasterCache"
    private const val DIR_NAME = "raster"

    /** Pipeline steps — the first two (GRID, ISOBATH) are in-memory only; the last two are cached to disk. */
    enum class Step {
        GRID,
        ISOBATH,
        DEPTH_COLOUR,
        LOW_DEPTH_WARNING
    }

    /**
     * Compound cache key: any change in these values invalidates the cache.
     * Hash-code based filename avoids embedding special characters.
     *
     * [colorsHash] is the hash of the colours **this step** is painted from, so a change to one
     * raster's palette leaves the other's cache file alone. Build a key through [keyFor] rather
     * than by hand, and keep a new colour on the step it belongs to in `AppConfig`.
     *
     * [coastStamp] identifies the coastline the warning overlay classified water with (its fetch
     * timestamp), so a replaced coastline over an unchanged grid misses the stale overlay. It is `0`
     * on the colour map, whose pixels never consult the coastline.
     */
    data class Key(
        val gridTimestampMs: Long,
        val emodnetCutoffM: Float,
        val lowDepthCrashDepthM: Float,
        val lowDepthStartWarningM: Float,
        val colorsHash: Int,
        val coastStamp: Long = 0L
    ) {
        /**
         * The file name is this hash. The five original fields fold as the generated data-class hash
         * did, and [coastStamp] folds in only when set, so a key without one — every colour-map key —
         * still names the file it named before the stamp existed.
         */
        override fun hashCode(): Int {
            var h = gridTimestampMs.hashCode()
            h = 31 * h + emodnetCutoffM.hashCode()
            h = 31 * h + lowDepthCrashDepthM.hashCode()
            h = 31 * h + lowDepthStartWarningM.hashCode()
            h = 31 * h + colorsHash
            return if (coastStamp == 0L) h else 31 * h + coastStamp.hashCode()
        }
    }

    /**
     * The key for one raster [step] over [grid]. The two depth thresholds are fields because the
     * warning overlay grades its alpha by them — inert for the colour map, carried for both so the
     * two steps keep one shape. [coastStamp] (0 when no coastline is loaded) reaches the key of the
     * warning overlay only.
     */
    fun keyFor(
        step: Step,
        grid: DepthGrid,
        emodnetCutoffM: Float,
        lowDepthCrashDepthM: Float,
        lowDepthStartWarningM: Float,
        coastStamp: Long
    ): Key = Key(
        gridTimestampMs = grid.metadata.fetchTimestampMs,
        emodnetCutoffM = emodnetCutoffM,
        lowDepthCrashDepthM = lowDepthCrashDepthM,
        lowDepthStartWarningM = lowDepthStartWarningM,
        colorsHash = if (step == Step.DEPTH_COLOUR) AppConfig.depthRasterColorsHash
        else AppConfig.lowDepthRasterColorsHash,
        coastStamp = if (step == Step.LOW_DEPTH_WARNING) coastStamp else 0L
    )

    // ── Public API ──────────────────────────────────────────────────────────

    fun has(context: Context, step: Step, key: Key): Boolean =
        if (step == Step.GRID || step == Step.ISOBATH) true // in-memory only, always "present"
        else cacheFile(context, step, key).exists()

    /** Writes the same RawBuf layout with bounded transfer arrays and atomic publication. */
    fun write(context: Context, step: Step, key: Key, bitmap: Bitmap, checkActive: () -> Unit = {}) {
        if (step == Step.GRID || step == Step.ISOBATH) return
        checkActive()
        val file = cacheFile(context, step, key)
        RasterBuffer.writeFile(file, bitmap.width, bitmap.height, AppConfig.rasterBufferPixels, checkActive) { offset, data, count ->
            transferPixels(bitmap, offset, data, count, writingBitmap = false)
        }
    }

    /** Reads complete validated chunks; cancellation never evicts a valid cache entry. */
    fun read(context: Context, step: Step, key: Key, checkActive: () -> Unit = {}): Bitmap? {
        if (step == Step.GRID || step == Step.ISOBATH) return null
        checkActive()
        val file = cacheFile(context, step, key)
        if (!file.exists()) return null
        return try {
            var bitmap: Bitmap? = null
            FileInputStream(file).channel.use { channel ->
                if (channel.size() <= 8L + AppConfig.rasterBufferPixels * 4L) {
                    val small = RasterBuffer.readSmall(channel, channel.size(), AppConfig.rasterBufferPixels, checkActive)
                    checkActive()
                    bitmap = Bitmap.createBitmap(small.values, small.width, small.height, Bitmap.Config.ARGB_8888)
                } else RasterBuffer.read(channel, channel.size(), AppConfig.rasterBufferPixels, checkActive,
                    create = { width, height -> bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) },
                    pixels = { offset, data, count -> transferPixels(bitmap!!, offset, data, count, writingBitmap = true) })
            }
            checkActive()
            bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "${step.name} cache read failed, evicting", e)
            file.delete()
            null
        }
    }

    /** Transfer whole rows together, with at most one partial row at each end of a chunk. */
    private fun transferPixels(bitmap: Bitmap, offset: Int, data: IntArray, count: Int, writingBitmap: Boolean) {
        rasterPixelSpans(bitmap.width, offset, count) { arrayOffset, x, y, width, height ->
            if (writingBitmap) bitmap.setPixels(data, arrayOffset, width, x, y, width, height)
            else bitmap.getPixels(data, arrayOffset, width, x, y, width, height)
        }
    }

    /** Delete all cached files for a given step (e.g. when settings change). */
    fun evict(context: Context, step: Step) {
        val dir = cacheDir(context)
        if (!dir.exists()) return
        dir.listFiles()?.filter { it.name.startsWith(step.name) }?.forEach { it.delete() }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun cacheFile(context: Context, step: Step, key: Key): File =
        File(cacheDir(context), "${step.name}_${key.hashCode()}.buf")

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, DIR_NAME)
}
