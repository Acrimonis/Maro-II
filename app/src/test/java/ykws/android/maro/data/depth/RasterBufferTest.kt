package ykws.android.maro.data.depth

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

class RasterBufferTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cancelledReplacementKeepsPreviousEntryAndRemovesTemporaryFile() {
        val file = File(temp.root, "raster.buf")
        file.writeText("previous entry")
        var chunks = 0
        try {
            RasterBuffer.writeFile(file, 100, 100, 32, {}) { _, _, _ ->
                if (++chunks == 3) throw CancellationException()
            }
            fail()
        } catch (_: CancellationException) { }
        assertEquals("previous entry", file.readText())
        assertEquals(listOf("raster.buf"), temp.root.list()!!.toList())
    }

    @Test fun chunksMatchLegacyBytesAndRoundTripAcrossPartialTransfers() {
        val width = 13
        val height = 17
        val source = IntArray(width * height) { it * 192839 - 7 }
        val expected = ByteBuffer.allocate(8 + source.size * 4).apply {
            putInt(width); putInt(height); asIntBuffer().put(source)
        }.array()
        for (capacity in listOf(1, 7, 13, 33, 1000)) {
            val bytes = ByteArrayOutputStream()
            val channel = Channels.newChannel(bytes)
            val partial = object : WritableByteChannel by channel {
                override fun write(src: ByteBuffer): Int {
                    val old = src.limit(); src.limit(minOf(old, src.position() + 3))
                    return try { channel.write(src) } finally { src.limit(old) }
                }
            }
            RasterBuffer.write(partial, width, height, capacity, {}) { off, buf, len -> source.copyInto(buf, 0, off, off + len) }
            assertArrayEquals(expected, bytes.toByteArray())
            val input = Channels.newChannel(ByteArrayInputStream(expected))
            val partialInput = object : ReadableByteChannel by input {
                override fun read(dst: ByteBuffer): Int {
                    val old = dst.limit(); dst.limit(minOf(old, dst.position() + 5))
                    return try { input.read(dst) } finally { dst.limit(old) }
                }
            }
            val actual = IntArray(source.size)
            RasterBuffer.read(partialInput, expected.size.toLong(), capacity, {}, { w, h -> assertEquals(width, w); assertEquals(height, h) }) {
                off, buf, len -> buf.copyInto(actual, off, 0, len)
            }
            assertArrayEquals(source, actual)
            val small = RasterBuffer.readSmall(Channels.newChannel(ByteArrayInputStream(expected)), expected.size.toLong(), source.size, {})
            assertArrayEquals(source, small.values)
            assertEquals(width, small.width)
            assertEquals(height, small.height)
        }
    }

    @Test fun spansCoverEveryPixelOnceWithoutReordering() {
        for (width in 1..17) for (offset in 0..38) for (count in 1..70) {
            val actual = mutableListOf<Int>()
            rasterPixelSpans(width, offset, count) { arrayOffset, x, y, w, h ->
                assertEquals(actual.size, arrayOffset)
                repeat(h) { row -> repeat(w) { col -> actual += (y + row) * width + x + col } }
            }
            assertEquals((offset until offset + count).toList(), actual)
        }
    }

    @Test fun corruptDimensionsAndTruncationFailBeforePublishingPixels() {
        for ((w, h) in listOf(0 to 1, -1 to 2, Int.MAX_VALUE to Int.MAX_VALUE, 10 to 10)) {
            val bytes = ByteBuffer.allocate(8).putInt(w).putInt(h).array()
            var created = false
            try {
                RasterBuffer.read(Channels.newChannel(ByteArrayInputStream(bytes)), 8L, 16, {}, { _, _ -> created = true }) { _, _, _ -> fail() }
                fail("invalid file accepted")
            } catch (_: IllegalArgumentException) { }
            assertFalse(created)
        }
    }

    @Test fun cancellationStopsTransferAtABoundedChunk() {
        var checks = 0
        var supplied = 0
        try {
            RasterBuffer.write(Channels.newChannel(ByteArrayOutputStream()), 100, 100, 16,
                { if (++checks == 8) throw CancellationException() }) { _, _, count -> supplied += count }
            fail()
        } catch (_: CancellationException) { }
        assertTrue(supplied in 1..48)
    }
}
