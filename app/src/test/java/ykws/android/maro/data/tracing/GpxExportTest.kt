package ykws.android.maro.data.tracing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for [Trip.toGpx] — validates GPX 1.1 XML output structure.
 */
class GpxExportTest {

    @Test
    fun `gpx output contains XML declaration and gpx root element`() {
        val gpx = emptyTrip().toGpx()

        assertTrue("Should start with XML declaration", gpx.startsWith("<?xml version=\"1.0\""))
        assertTrue("Should contain gpx element", gpx.contains("<gpx version=\"1.1\""))
        assertTrue("Should contain Maro-II creator", gpx.contains("creator=\"Maro-II\""))
        assertTrue("Should contain GPX namespace", gpx.contains("http://www.topografix.com/GPX/1/1"))
        assertTrue("Should close gpx element", gpx.contains("</gpx>"))
    }

    @Test
    fun `gpx output contains trk with name and desc`() {
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "Afternoon Sail",
            description = "A lovely afternoon on the water",
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_003_600_000L,
            tracePoints = listOf(
                TracePoint(lat = 43.5283, lon = 7.0450, timeOffsetSec = 0)
            )
        )
        val gpx = trip.toGpx()

        assertTrue("Should contain trk element", gpx.contains("<trk>"))
        assertTrue("Should contain name with trip name", gpx.contains("<name>Afternoon Sail</name>"))
        assertTrue("Should contain desc with trip description", gpx.contains("<desc>A lovely afternoon on the water</desc>"))
        assertTrue("Should contain trkseg element", gpx.contains("<trkseg>"))
        assertTrue("Should close trkseg element", gpx.contains("</trkseg>"))
        assertTrue("Should close trk element", gpx.contains("</trk>"))
    }

    @Test
    fun `gpx output contains trkpt with lat lon time speed and course`() {
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "Speed Test",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = listOf(
                TracePoint(
                    lat = 43.5283,
                    lon = 7.0450,
                    speedMps = 3.5f,
                    bearingDeg = 90f,
                    timeOffsetSec = 0
                )
            )
        )
        val gpx = trip.toGpx()

        assertTrue("Should contain trkpt element", gpx.contains("<trkpt lat=\"43.5283\" lon=\"7.0450\">"))
        assertTrue("Should contain time element", gpx.contains("<time>"))
        assertTrue("Should contain speed element", gpx.contains("<speed>3.5</speed>"))
        assertTrue("Should contain course element", gpx.contains("<course>90.0</course>"))
        assertTrue("Should close trkpt element", gpx.contains("</trkpt>"))
    }

    @Test
    fun `gpx output handles multiple trace points`() {
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "Multi-point",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = listOf(
                TracePoint(lat = 43.5283, lon = 7.0450, speedMps = 2.0f, bearingDeg = 45f, timeOffsetSec = 0),
                TracePoint(lat = 43.5300, lon = 7.0500, speedMps = 3.0f, bearingDeg = 90f, timeOffsetSec = 60),
                TracePoint(lat = 43.5350, lon = 7.0600, speedMps = 4.0f, bearingDeg = 135f, timeOffsetSec = 120)
            )
        )
        val gpx = trip.toGpx()

        // Count occurrences of <trkpt
        val trkptCount = gpx.split("<trkpt").size - 1
        assertEquals("Should have 3 trkpt elements", 3, trkptCount)
    }

    @Test
    fun `gpx output escapes XML special characters in name and description`() {
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "Tom & Jerry's <Trip>",
            description = "Coast < 5nm & speed > 20kn",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = emptyList()
        )
        val gpx = trip.toGpx()

        assertTrue("Should escape & in name", gpx.contains("Tom &#38; Jerry's &#60;Trip&#62;"))
        assertTrue("Should escape < and & in description", gpx.contains("Coast &#60; 5nm &#38; speed &#62; 20kn"))
        assertFalse("Should not contain raw &", gpx.contains("Tom & Jerry"))
    }

    @Test
    fun `gpx output omits speed and course when null`() {
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "No Sensors",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = listOf(
                TracePoint(lat = 43.5283, lon = 7.0450, speedMps = null, bearingDeg = null, timeOffsetSec = 0)
            )
        )
        val gpx = trip.toGpx()

        assertFalse("Should not contain speed element", gpx.contains("<speed>"))
        assertFalse("Should not contain course element", gpx.contains("<course>"))
    }

    @Test
    fun `gpx time is in ISO 8601 UTC format`() {
        // startTimeMs = 2023-11-14T08:46:40Z
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            name = "Time Check",
            startTimeMs = 1_700_000_000_000L,
            tracePoints = listOf(
                TracePoint(lat = 43.5283, lon = 7.0450, timeOffsetSec = 0)
            )
        )
        val gpx = trip.toGpx()

        // ISO 8601 pattern: 2023-11-14T08:46:40Z
        assertTrue(
            "Should contain ISO 8601 time",
            gpx.contains("<time>2023-11-14T08:46:40Z</time>")
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun emptyTrip(): Trip = Trip(
        id = UUID.randomUUID().toString(),
        name = "Empty",
        startTimeMs = 1_700_000_000_000L,
        tracePoints = emptyList()
    )
}
