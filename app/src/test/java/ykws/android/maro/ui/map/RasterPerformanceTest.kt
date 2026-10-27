package ykws.android.maro.ui.map

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.RasterBuffer
import ykws.android.maro.data.model.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class RasterPerformanceTest {
    @get:Rule val temp = TemporaryFolder()
    @Volatile private var sink = 0

    private fun grid(rows: Int, cols: Int): DepthGrid = DepthGrid("test", BoundingBox(43.0, 44.0, 7.0, 8.0),
        rows, cols, .001, .001, DepthDatum.LAT,
        FloatArray(rows * cols) { when(it % 7) { 0 -> Float.NaN; 1 -> -1f; else -> (it % 31) / 10f } },
        ByteArray(rows * cols) { (it % 4).toByte() }, ByteArray(rows * cols) { 90 },
        DepthMetadata("synthetic", 0, 10.0, rows * cols, 0, 0f, 3f))

    private fun reference(g: DepthGrid, warning: Boolean, water: (Double, Double) -> Boolean): IntArray =
        if (warning) ReferenceLowDepthWarningBitmap.pixels(g,.5f,1.5f,water,2f)
        else ReferenceDepthBitmap.pixels(g,2f)

    @Test fun completedPixelsMatchReferenceForBothBuilders() {
        val g = grid(73, 91)
        val water: (Double, Double) -> Boolean = { lat, lon -> lat + lon > 50.02 }
        assertArrayEquals(reference(g, false, water), DepthBitmap.pixels(g, 2f))
        assertArrayEquals(reference(g, true, water), LowDepthWarningBitmap.pixels(g, .5f, 1.5f, water, 2f))
    }

    @Test fun cancellationSkipsRemainingRowsAndFinalProgress() {
        val g = grid(200, 300)
        for (warning in listOf(false, true)) {
            var checks = 0
            var completed = false
            val check = { if (++checks == 12) throw CancellationException() }
            val progress: (Int) -> Unit = { if (it == 100) completed = true }
            try {
                if (warning) LowDepthWarningBitmap.pixels(g, onProgress = progress, checkActive = check)
                else DepthBitmap.pixels(g, onProgress = progress, checkActive = check)
                fail()
            } catch (_: CancellationException) { }
            assertEquals(12, checks)
            assertFalse(completed)
        }
    }

    @Test fun measureRasterAndRawBufferWorkloads() {
        assumeTrue(System.getenv("MARO_RASTER_PERF") == "1")
        for (side in listOf(256, 2048)) {
            val g = grid(side, side)
            for (warning in listOf(false, true)) repeat(24) { round ->
                for (candidate in if (round % 2 == 0) listOf(false,true) else listOf(true,false)) {
                    var reads = 0
                    val water: (Double, Double) -> Boolean = { _, _ -> reads++; true }
                    var checks = 0
                    val start = System.nanoTime()
                    val pixels = if (!candidate) reference(g, warning, water)
                    else if (warning) LowDepthWarningBitmap.pixels(g,.5f,1.5f,water,2f,checkActive = { checks++ })
                    else DepthBitmap.pixels(g,2f,checkActive = { checks++ })
                    val elapsed = System.nanoTime()-start
                    sink = pixels[pixels.size/2]
                    println("RASTER-PERF side=$side warning=$warning candidate=$candidate round=$round ns=$elapsed reads=$reads checks=$checks")
                }
            }
            repeat(24) { round ->
                for (candidate in if (round % 2 == 0) listOf(false,true) else listOf(true,false)) {
                    var reads = 0
                    var checks = 0
                    val water: (Double, Double) -> Boolean = { _, _ -> reads++; true }
                    val start = System.nanoTime()
                    try {
                        if (candidate) LowDepthWarningBitmap.pixels(g,.5f,1.5f,water,2f,
                            checkActive = { if (++checks == 12) throw CancellationException() })
                        else reference(g,true,water)
                    } catch (_: CancellationException) { }
                    println("CANCEL-PERF side=$side candidate=$candidate round=$round ns=${System.nanoTime()-start} reads=$reads")
                }
            }
            val pixels = IntArray(side * side) { it * 739 }
            repeat(24) { round ->
                for (candidate in if (round % 2 == 0) listOf(false,true) else listOf(true,false)) {
                    val file = File(temp.root,"$side-$candidate.buf")
                    val start = System.nanoTime()
                    val result: IntArray
                    if (!candidate) {
                        val extracted = pixels.copyOf() // Original Bitmap.getPixels extraction before writing.
                        val bytes = ByteBuffer.allocateDirect(8+pixels.size*4).apply { putInt(side); putInt(side); asIntBuffer().put(extracted); position(capacity()); flip() }
                        FileOutputStream(file).channel.use { while(bytes.hasRemaining()) it.write(bytes) }
                        val read = ByteBuffer.allocateDirect(8+pixels.size*4)
                        FileInputStream(file).channel.use { while(read.hasRemaining()) it.read(read) }
                        read.flip(); read.int; read.int
                        result = IntArray(pixels.size); read.asIntBuffer().get(result)
                    } else {
                        FileOutputStream(file).channel.use { ch -> RasterBuffer.write(ch,side,side,AppConfig.rasterBufferPixels,{}) { off,buf,n -> pixels.copyInto(buf,0,off,off+n) } }
                        result = FileInputStream(file).channel.use { ch ->
                            if (pixels.size <= AppConfig.rasterBufferPixels) RasterBuffer.readSmall(ch,ch.size(),AppConfig.rasterBufferPixels,{}).values
                            else IntArray(pixels.size).also { target ->
                                RasterBuffer.read(ch,ch.size(),AppConfig.rasterBufferPixels,{}, { _, _ -> }) { off,buf,n -> buf.copyInto(target,off,0,n) }
                            }
                        }
                    }
                    val elapsed = System.nanoTime()-start
                    assertArrayEquals(pixels,result)
                    println("BUFFER-PERF side=$side candidate=$candidate round=$round ns=$elapsed")
                }
            }
        }
    }
}
