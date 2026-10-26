package ykws.android.maro.data.track

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class RecordingPointsTest {
    private fun point(i: Int) = TrackPoint(
        lat = 43.5 + i * 0.00001, lon = 7.0, timeOffsetMs = i * 1_000L,
        type = if (i % 37 == 0) PointType.GAP else PointType.NORMAL
    )

    @Test fun snapshotsRemainExactAcrossChunksAndResume() {
        for (chunkSize in listOf(1, 2, 7, 256)) {
            val seed = (0 until 19).map(::point).toMutableList()
            val builder = RecordingPoints(chunkSize, seed)
            var reference: List<TrackPoint> = seed.toList()
            val saved = mutableListOf(builder.snapshot() to reference)
            seed.clear() // The resumed caller does not own the builder's chunks.
            for (i in 19 until 1_100) {
                reference = reference + point(i) // Original recorder path, retained as the oracle.
                val snapshot = builder.append(point(i))
                assertEquals(reference, snapshot)
                assertEquals(reference.asReversed(), snapshot.asReversed())
                if (i % 31 == 0) saved += snapshot to reference
            }
            saved.forEach { (snapshot, expected) ->
                assertEquals(expected, snapshot)
                val track = Track("trip", "trip", startTimeMs = 0, trackPoints = expected)
                assertArrayEquals(ProtoBuf.encodeToByteArray(track),
                    ProtoBuf.encodeToByteArray(track.copy(trackPoints = snapshot)))
            }
        }
    }

    @Test fun snapshotsEnforceListBoundsAndDoNotExposeTheMutableTail() {
        val builder = RecordingPoints(2)
        val empty = builder.snapshot()
        val one = builder.append(point(1))
        val two = builder.append(point(2))
        builder.append(point(3))
        assertTrue(empty.isEmpty())
        assertEquals(listOf(point(1)), one)
        assertEquals(listOf(point(1), point(2)), two)
        for (index in listOf(-1, 2)) {
            try { two[index]; fail("out-of-range read") } catch (_: IndexOutOfBoundsException) { }
        }
    }
}
