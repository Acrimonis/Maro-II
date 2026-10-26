package ykws.android.maro.data.track

import java.util.RandomAccess

/** Lane-owned builder. Published lists share sealed chunks and own a copy of the bounded tail. */
internal class RecordingPoints(private val chunkSize: Int, initial: List<TrackPoint> = emptyList()) {
    init { require(chunkSize > 0) }

    private var chunks: List<List<TrackPoint>> = initial.chunked(chunkSize).let {
        if (initial.size % chunkSize == 0) it else it.dropLast(1)
    }
    private val tail = ArrayList<TrackPoint>(chunkSize).apply {
        addAll(initial.takeLast(initial.size % chunkSize))
    }

    fun append(point: TrackPoint): List<TrackPoint> {
        tail.add(point)
        if (tail.size == chunkSize) {
            chunks = chunks + listOf(tail.toList())
            tail.clear()
        }
        return snapshot()
    }

    fun snapshot(): List<TrackPoint> = if (chunks.isEmpty()) tail.toList()
        else Snapshot(chunks, tail.toList(), chunkSize)

    private class Snapshot(
        private val chunks: List<List<TrackPoint>>,
        private val tail: List<TrackPoint>,
        private val chunkSize: Int
    ) : AbstractList<TrackPoint>(), RandomAccess {
        override val size: Int = chunks.size * chunkSize + tail.size

        override fun get(index: Int): TrackPoint {
            if (index < 0 || index >= size) throw IndexOutOfBoundsException("index=$index size=$size")
            val chunk = index / chunkSize
            return if (chunk < chunks.size) chunks[chunk][index % chunkSize]
                else tail[index % chunkSize]
        }
    }
}
