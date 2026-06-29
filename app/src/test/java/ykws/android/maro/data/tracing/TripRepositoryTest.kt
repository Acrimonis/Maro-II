package ykws.android.maro.data.tracing

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Unit tests for [TripRepository] — file-based protobuf CRUD.
 *
 * Uses a real temp directory so file I/O and proto round-trips are
 * validated end-to-end.
 */
class TripRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repo: TripRepository

    @Before
    fun setUp() {
        tempDir = createTempDir("tripRepoTest")
        repo = TripRepository(StubContext(tempDir))
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── Save ────────────────────────────────────────────────────────────────

    @Test
    fun `save creates binary file on disk`() = runTest {
        val trip = createTrip()
        val result = repo.save(trip)

        assertTrue(result.isSuccess)

        val file = File(tempDir, "trips/${trip.id}.trip")
        assertTrue("Trip file should exist", file.exists())
        assertTrue("Trip file should have data", file.length() > 0)
    }

    // ── Load ────────────────────────────────────────────────────────────────

    @Test
    fun `load returns saved trip with all fields intact`() = runTest {
        val original = createTrip(
            name = "Round-trip Test",
            tracePoints = listOf(
                TracePoint(lat = 43.5283, lon = 7.0450, speedMps = 2.5f, bearingDeg = 90f, timeOffsetSec = 0),
                TracePoint(lat = 43.5300, lon = 7.0500, speedMps = 3.0f, bearingDeg = 45f, timeOffsetSec = 60)
            )
        )

        repo.save(original)
        val loaded = repo.load(original.id).getOrThrow()

        assertEquals(original.id, loaded.id)
        assertEquals(original.name, loaded.name)
        assertEquals(original.description, loaded.description)
        assertEquals(original.startTimeMs, loaded.startTimeMs)
        assertEquals(original.endTimeMs, loaded.endTimeMs)
        assertEquals(original.pausedDurationSec, loaded.pausedDurationSec)
        assertEquals(original.fastestSpeedMps, loaded.fastestSpeedMps, 1e-6f)
        assertEquals(original.trackColorArgb, loaded.trackColorArgb)
        assertEquals(original.tracePoints.size, loaded.tracePoints.size)
        assertEquals(original.tracePoints[0].lat, loaded.tracePoints[0].lat, 1e-10)
        assertEquals(original.tracePoints[1].lon, loaded.tracePoints[1].lon, 1e-10)
    }

    @Test
    fun `load returns failure for non-existent trip`() = runTest {
        val result = repo.load("nonexistent-id")
        assertTrue(result.isFailure)
    }

    // ── List ────────────────────────────────────────────────────────────────

    @Test
    fun `list returns empty when no trips saved`() = runTest {
        val summaries = repo.list().getOrThrow()
        assertTrue(summaries.isEmpty())
    }

    @Test
    fun `list returns all saved trips as summaries sorted by startTimeMs desc`() = runTest {
        val trip1 = createTrip(startTimeMs = 1_000_000_000_000L, name = "Oldest")
        val trip2 = createTrip(startTimeMs = 2_000_000_000_000L, name = "Newest")
        val trip3 = createTrip(startTimeMs = 1_500_000_000_000L, name = "Middle")

        repo.save(trip1)
        repo.save(trip2)
        repo.save(trip3)

        val summaries = repo.list().getOrThrow()
        assertEquals(3, summaries.size)
        assertEquals("Newest", summaries[0].name)
        assertEquals("Middle", summaries[1].name)
        assertEquals("Oldest", summaries[2].name)
    }

    @Test
    fun `list summary has correct point count`() = runTest {
        val points = (0 until 5).map { i ->
            TracePoint(lat = 43.5 + i * 0.001, lon = 7.0 + i * 0.001, timeOffsetSec = i * 10)
        }
        val trip = createTrip(tracePoints = points)
        repo.save(trip)

        val summaries = repo.list().getOrThrow()
        assertEquals(1, summaries.size)
        assertEquals(5, summaries[0].pointCount)
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes trip file from disk`() = runTest {
        val trip = createTrip()
        repo.save(trip)

        val file = File(tempDir, "trips/${trip.id}.trip")
        assertTrue(file.exists())

        repo.delete(trip.id)
        assertFalse("Trip file should be removed after delete", file.exists())
    }

    @Test
    fun `delete non-existent trip returns success`() = runTest {
        val result = repo.delete("nonexistent-id")
        assertTrue(result.isSuccess)
    }

    // ── Protobuf round-trip integrity ───────────────────────────────────────

    @Test
    fun `protobuf round-trip preserves large tracePoints list`() = runTest {
        val points = (0 until 50).map { i ->
            TracePoint(
                lat = 43.5 + i * 0.002,
                lon = 7.0 + i * 0.001,
                speedMps = (i % 10).toFloat(),
                bearingDeg = (i * 7 % 360).toFloat(),
                timeOffsetSec = i * 5
            )
        }
        val original = createTrip(tracePoints = points)
        repo.save(original)
        val restored = repo.load(original.id).getOrThrow()

        assertEquals(50, restored.tracePoints.size)
        for (i in 0 until 50) {
            assertEquals(points[i].lat, restored.tracePoints[i].lat, 1e-10)
            assertEquals(points[i].lon, restored.tracePoints[i].lon, 1e-10)
            assertEquals(points[i].speedMps, restored.tracePoints[i].speedMps)
            assertEquals(points[i].bearingDeg, restored.tracePoints[i].bearingDeg)
            assertEquals(points[i].timeOffsetSec, restored.tracePoints[i].timeOffsetSec)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun createTrip(
        name: String = "Test Trip",
        startTimeMs: Long = 1_700_000_000_000L,
        endTimeMs: Long? = 1_700_000_360_000L,
        tracePoints: List<TracePoint> = emptyList()
    ): Trip = Trip(
        id = UUID.randomUUID().toString(),
        name = name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        tracePoints = tracePoints
    )
}
