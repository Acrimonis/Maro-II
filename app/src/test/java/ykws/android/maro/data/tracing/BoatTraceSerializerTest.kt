package ykws.android.maro.data.tracing

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [TracePoint], [Trip], and [TripSummary] — validates Protobuf round-trip.
 */
@OptIn(ExperimentalSerializationApi::class)
class BoatTraceSerializerTest {

    private val protoBuf = ProtoBuf

    // ── TracePoint tests ─────────────────────────────────────────────────────

    @Test
    fun `TracePoint roundtrip preserves all fields`() {
        val original = TracePoint(
            lat = 43.5283,
            lon = 7.0450,
            speedMps = 2.5f,
            bearingDeg = 180f,
            timeOffsetSec = 120
        )

        val bytes = protoBuf.encodeToByteArray(TracePoint.serializer(), original)
        val restored = protoBuf.decodeFromByteArray(TracePoint.serializer(), bytes)

        assertEquals(original.lat, restored.lat, 1e-10)
        assertEquals(original.lon, restored.lon, 1e-10)
        assertEquals(original.speedMps, restored.speedMps)
        assertEquals(original.bearingDeg, restored.bearingDeg)
        assertEquals(original.timeOffsetSec, restored.timeOffsetSec)
    }

    @Test
    fun `TracePoint roundtrip with null optional fields`() {
        val original = TracePoint(
            lat = 43.5,
            lon = 7.0,
            speedMps = null,
            bearingDeg = null,
            timeOffsetSec = 0
        )

        val bytes = protoBuf.encodeToByteArray(TracePoint.serializer(), original)
        val restored = protoBuf.decodeFromByteArray(TracePoint.serializer(), bytes)

        assertNull(restored.speedMps)
        assertNull(restored.bearingDeg)
        assertEquals(0, restored.timeOffsetSec)
    }

    // ── Trip tests ───────────────────────────────────────────────────────────

    @Test
    fun `Trip roundtrip preserves all fields`() {
        val tracePoints = listOf(
            TracePoint(lat = 43.5283, lon = 7.0450, speedMps = 1.0f, bearingDeg = 90f, timeOffsetSec = 0),
            TracePoint(lat = 43.5300, lon = 7.0500, speedMps = 2.5f, bearingDeg = 45f, timeOffsetSec = 60),
            TracePoint(lat = 43.5350, lon = 7.0600, speedMps = 3.0f, bearingDeg = 30f, timeOffsetSec = 120)
        )
        val original = Trip(
            id = UUID.randomUUID().toString(),
            name = "Test Trip",
            description = "A short test voyage",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_360_000L,
            pausedDurationSec = 30,
            trackColorArgb = 0xFFFF5722.toInt(),
            tracePoints = tracePoints,
            fastestSpeedMps = 3.0f
        )

        val bytes = protoBuf.encodeToByteArray(Trip.serializer(), original)
        val restored = protoBuf.decodeFromByteArray(Trip.serializer(), bytes)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.description, restored.description)
        assertEquals(original.startTimeMs, restored.startTimeMs)
        assertNotNull(restored.endTimeMs)
        assertEquals(original.endTimeMs, restored.endTimeMs)
        assertEquals(original.pausedDurationSec, restored.pausedDurationSec)
        assertEquals(original.trackColorArgb, restored.trackColorArgb)
        assertEquals(original.fastestSpeedMps, restored.fastestSpeedMps, 1e-6f)
        assertEquals(original.tracePoints.size, restored.tracePoints.size)
        assertEquals(original.tracePoints[0].lat, restored.tracePoints[0].lat, 1e-10)
        assertEquals(original.tracePoints[1].lon, restored.tracePoints[1].lon, 1e-10)
        assertEquals(original.tracePoints[2].speedMps, restored.tracePoints[2].speedMps)
    }

    @Test
    fun `Trip roundtrip with null endTime and empty tracePoints`() {
        val original = Trip(
            id = UUID.randomUUID().toString(),
            name = "Ongoing Trip",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = null,
            tracePoints = emptyList()
        )

        val bytes = protoBuf.encodeToByteArray(Trip.serializer(), original)
        val restored = protoBuf.decodeFromByteArray(Trip.serializer(), bytes)

        assertNull(restored.endTimeMs)
        assertEquals(0, restored.tracePoints.size)
        assertEquals(0f, restored.fastestSpeedMps, 1e-6f)
    }

    @Test
    fun `Trip roundtrip with large tracePoints list`() {
        val tracePoints = (0 until 100).map { i ->
            TracePoint(
                lat = 43.5 + i * 0.001,
                lon = 7.0 + i * 0.001,
                speedMps = (i % 10).toFloat(),
                bearingDeg = (i * 10 % 360).toFloat(),
                timeOffsetSec = i * 10
            )
        }
        val original = Trip(
            id = UUID.randomUUID().toString(),
            name = "Large Trip",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = tracePoints
        )

        val bytes = protoBuf.encodeToByteArray(Trip.serializer(), original)
        val restored = protoBuf.decodeFromByteArray(Trip.serializer(), bytes)

        assertEquals(100, restored.tracePoints.size)
        for (i in 0 until 100) {
            assertEquals(tracePoints[i].lat, restored.tracePoints[i].lat, 1e-10)
            assertEquals(tracePoints[i].lon, restored.tracePoints[i].lon, 1e-10)
            assertEquals(tracePoints[i].speedMps, restored.tracePoints[i].speedMps)
            assertEquals(tracePoints[i].bearingDeg, restored.tracePoints[i].bearingDeg)
            assertEquals(tracePoints[i].timeOffsetSec, restored.tracePoints[i].timeOffsetSec)
        }
    }

    // ── TripSummary tests ────────────────────────────────────────────────────

    @Test
    fun `TripSummary is constructable with all fields`() {
        val summary = TripSummary(
            id = UUID.randomUUID().toString(),
            name = "Summary Trip",
            description = "A nice voyage",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_360_000L,
            pointCount = 42,
            trackColorArgb = 0xFF2196F3.toInt(),
            pausedDurationSec = 10,
            fastestSpeedMps = 4.5f
        )

        assertEquals(42, summary.pointCount)
        assertEquals("A nice voyage", summary.description)
        assertEquals(10, summary.pausedDurationSec)
        assertEquals(4.5f, summary.fastestSpeedMps, 1e-6f)
    }

    @Test
    fun `TripSummary navigatingDurationMs computes correctly`() {
        val summary = TripSummary(
            id = "test-id",
            name = "Test",
            startTimeMs = 1_000_000_000_000L,
            endTimeMs = 1_000_003_600_000L, // 3600 s = 1 hour
            pointCount = 10,
            trackColorArgb = 0xFF2196F3.toInt(),
            pausedDurationSec = 600 // 10 min paused
        )

        // navigatingDurationMs = (3_600_000 - 0) - (600 * 1000) = 3_000_000 ms
        assertEquals(3_000_000L, summary.navigatingDurationMs)
    }

    @Test
    fun `TripSummary navigatingDurationMs is zero when endTimeMs is null`() {
        val summary = TripSummary(
            id = "test-id",
            name = "Ongoing",
            startTimeMs = 1_000_000_000_000L,
            endTimeMs = null,
            pointCount = 5,
            trackColorArgb = 0xFF2196F3.toInt()
        )

        assertEquals(0L, summary.navigatingDurationMs)
    }
}
