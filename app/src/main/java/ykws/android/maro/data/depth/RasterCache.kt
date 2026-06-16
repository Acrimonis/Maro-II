
package ykws.android.maro.data.depth
import ykws.android.maro.config.AppConfig

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * RawBuf disk cache for raster [Bitmap] outputs (depth colour map, low-depth warning overlay).
 *
 * Persists [IntArray] pixel data directly via [ByteBuffer] → [FileChannel], avoiding encode/decode
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
     * When adding fields, also keep [AppConfig.rasterColorsHash] in sync.
     */
    data class Key(
        val gridTimestampMs: Long,
        val emodnetCutoffM: Float,
        val lowDepthMaxM: Float,
        val lowDepthMinOpacityPct: Int,
        val nodataColor: Int,
        val colorsHash: Int
    )

    // ── Public API ──────────────────────────────────────────────────────────

    fun has(context: Context, step: Step, key: Key): Boolean =
        if (step == Step.GRID || step == Step.ISOBATH) true // in-memory only, always "present"
        else cacheFile(context, step, key).exists()

    /**
     * Writes [pixels] (size w×h, ARGB_8888 layout) to a RawBuf cache file.
     * File format: 8-byte header (w:Int, h:Int) + raw pixel data (w×h×4 bytes).
     * No-op for GRID / ISOBATH steps.
     */
    fun write(context: Context, step: Step, key: Key, pixels: IntArray, w: Int, h: Int) {
        if (step == Step.GRID || step == Step.ISOBATH) return
        val file = cacheFile(context, step, key)
        file.parentFile?.mkdirs()
        val t0 = System.currentTimeMillis()
        val buf = ByteBuffer.allocateDirect(8 + pixels.size * 4)
        buf.putInt(w)
        buf.putInt(h)
        buf.asIntBuffer().put(pixels)
        buf.position(buf.capacity())
        buf.flip()
        FileOutputStream(file).channel.use { it.write(buf) }
        val elapsed = System.currentTimeMillis() - t0
        Log.d(TAG, "${step.name} write: ${elapsed}ms sizeKB=${file.length() / 1024}")
    }

    /**
     * Reads a RawBuf cache file back into a [Bitmap], or returns null on miss / corruption.
     * Returns null for GRID / ISOBATH steps (in-memory only).
     */
    fun read(context: Context, step: Step, key: Key): Bitmap? {
        if (step == Step.GRID || step == Step.ISOBATH) return null
        val file = cacheFile(context, step, key)
        if (!file.exists()) return null
        return try {
            val t0 = System.currentTimeMillis()
            val buf = FileInputStream(file).channel.use { ch ->
                val size = ch.size().toInt()
                val bb = ByteBuffer.allocateDirect(size)
                ch.read(bb)
                bb.flip()
                bb
            }
            val w = buf.getInt()
            val h = buf.getInt()
            val pixels = IntArray(w * h)
            buf.asIntBuffer().get(pixels)
            val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            val elapsed = System.currentTimeMillis() - t0
            Log.d(TAG, "${step.name} read: ${elapsed}ms")
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "${step.name} cache read failed, evicting", e)
            file.delete()
            null
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
