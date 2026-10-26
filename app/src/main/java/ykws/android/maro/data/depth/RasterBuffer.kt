package ykws.android.maro.data.depth

import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel

/** The existing big-endian RawBuf format, transferred in bounded chunks. */
internal object RasterBuffer {
    data class Pixels(val width: Int, val height: Int, val values: IntArray)

    /** Original single-buffer read retained when the complete raster fits the transfer bound. */
    fun readSmall(channel: ReadableByteChannel, fileSize: Long, capacity: Int, checkActive: () -> Unit): Pixels {
        require(fileSize in 12L..(8L + capacity * 4L)) { "Raster exceeds transfer bound" }
        checkActive()
        val bytes = ByteBuffer.allocateDirect(fileSize.toInt())
        readFully(channel, bytes, checkActive)
        bytes.flip()
        val width = bytes.int
        val height = bytes.int
        val count = count(width, height)
        require(fileSize == 8L + count * 4L) { "Invalid raster length" }
        checkActive()
        val values = IntArray(count)
        bytes.asIntBuffer().get(values)
        checkActive()
        return Pixels(width, height, values)
    }

    fun writeFile(file: File, width: Int, height: Int, capacity: Int, checkActive: () -> Unit,
                  pixels: (offset: Int, buffer: IntArray, count: Int) -> Unit) {
        checkActive()
        file.parentFile?.mkdirs()
        val temporary = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).channel.use { write(it, width, height, capacity, checkActive, pixels) }
            checkActive()
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun write(channel: WritableByteChannel, width: Int, height: Int, capacity: Int,
              checkActive: () -> Unit, pixels: (offset: Int, buffer: IntArray, count: Int) -> Unit) {
        val count = count(width, height)
        checkActive()
        val size = minOf(count, capacity.coerceAtLeast(1))
        val data = IntArray(size)
        val bytes = ByteBuffer.allocateDirect(8 + size * 4)
        var offset = 0
        while (offset < count) {
            checkActive()
            val length = minOf(size, count - offset)
            pixels(offset, data, length)
            bytes.clear()
            if (offset == 0) { bytes.putInt(width); bytes.putInt(height) }
            val headerSize = bytes.position()
            bytes.asIntBuffer().put(data, 0, length)
            bytes.position(0).limit(headerSize + length * 4)
            writeFully(channel, bytes, checkActive)
            offset += length
        }
    }

    fun read(channel: ReadableByteChannel, fileSize: Long, capacity: Int, checkActive: () -> Unit,
             create: (width: Int, height: Int) -> Unit,
             pixels: (offset: Int, buffer: IntArray, count: Int) -> Unit) {
        checkActive()
        require(fileSize >= 12) { "Invalid raster length" }
        val firstSize = minOf(fileSize, 8L + capacity.coerceAtLeast(1) * 4L).toInt()
        val bytes = ByteBuffer.allocateDirect(firstSize)
        readFully(channel, bytes, checkActive)
        bytes.flip()
        val width = bytes.int
        val height = bytes.int
        val count = count(width, height)
        require(fileSize == 8L + count * 4L) { "Invalid raster length" }
        checkActive()
        create(width, height)
        val size = minOf(count, capacity.coerceAtLeast(1))
        val data = IntArray(size)
        var offset = 0
        while (offset < count) {
            checkActive()
            val length = minOf(size, count - offset)
            if (offset != 0) {
                bytes.clear().limit(length * 4)
                readFully(channel, bytes, checkActive)
                bytes.flip()
            }
            bytes.asIntBuffer().get(data, 0, length)
            pixels(offset, data, length)
            offset += length
        }
        checkActive()
    }

    private fun count(width: Int, height: Int): Int {
        require(width > 0 && height > 0 && width.toLong() * height <= Int.MAX_VALUE / 4) { "Invalid raster dimensions" }
        return width * height
    }

    private fun readFully(channel: ReadableByteChannel, bytes: ByteBuffer, checkActive: () -> Unit) {
        while (bytes.hasRemaining()) {
            checkActive()
            if (channel.read(bytes) < 0) throw EOFException("Truncated raster")
        }
    }

    private fun writeFully(channel: WritableByteChannel, bytes: ByteBuffer, checkActive: () -> Unit) {
        while (bytes.hasRemaining()) { checkActive(); channel.write(bytes) }
    }
}

/** Linear chunks mapped to rectangular Bitmap API calls without changing pixel order. */
internal inline fun rasterPixelSpans(width: Int, offset: Int, count: Int,
                                    consume: (arrayOffset: Int, x: Int, y: Int, width: Int, height: Int) -> Unit) {
    var consumed = 0
    while (consumed < count) {
        val position = offset + consumed
        val x = position % width
        val remaining = count - consumed
        val spanWidth = minOf(width - x, remaining)
        val rows = if (x == 0 && spanWidth == width) remaining / width else 1
        consume(consumed, x, position / width, spanWidth, rows)
        consumed += spanWidth * rows
    }
}
