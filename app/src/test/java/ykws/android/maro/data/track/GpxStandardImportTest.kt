package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import ykws.android.maro.data.model.ImportMode
import java.time.Instant

/**
 * A foreign GPX — no `<maro:data>` blob — read through the standard `<trkpt>` path: the point
 * times, speeds and courses must reach the track, and the stats must be derived from the points
 * rather than left at zero.
 */
@Ignore("The standard-GPX path needs an XmlPullParser implementation on the JVM (kxml2 as a test dependency); the mockable android.jar has none. A dependency is the user's call.")
class GpxStandardImportTest {

    private val firstTime = "2026-09-06T10:00:00Z"
    private val secondTime = "2026-09-06T10:00:10Z"

    private val foreignGpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.0" creator="elsewhere" xmlns="http://www.topografix.com/GPX/1/0">
          <trk>
            <name>Foreign trip</name>
            <cmt>Recorded by another app</cmt>
            <trkseg>
              <trkpt lat="43.5" lon="7.2">
                <time>$firstTime</time>
                <speed>3.5</speed>
                <course>90.0</course>
              </trkpt>
              <trkpt lat="43.5" lon="7.201">
                <time>$secondTime</time>
                <speed>4.0</speed>
                <course>95.0</course>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    private fun importForeign() = GpxImporter.import(
        foreignGpx.toByteArray(),
        "gpx",
        emptySet(),
        emptySet(),
        emptyMap(),
        ImportMode.IMPORT_NEW
    )

    @Test
    fun `a foreign trkpt keeps its time, speed and course`() {
        val batch = importForeign()

        assertEquals(1, batch.imported)
        val track = batch.tracks.single()
        assertEquals(2, track.trackPoints.size)

        val first = track.trackPoints[0]
        val second = track.trackPoints[1]
        assertEquals(0L, first.timeOffsetMs)
        assertEquals(10_000L, second.timeOffsetMs)
        assertEquals(3.5f, first.speedMps)
        assertEquals(90.0f, first.bearingDeg)
        assertEquals(4.0f, second.speedMps)
        assertEquals(95.0f, second.bearingDeg)
    }

    @Test
    fun `a foreign track is dated by its first point, not by the import`() {
        val track = importForeign().tracks.single()

        assertEquals(Instant.parse(firstTime).toEpochMilli(), track.startTimeMs)
        assertEquals(Instant.parse(secondTime).toEpochMilli(), track.endTimeMs)
    }

    @Test
    fun `a foreign track carries its name and comment`() {
        val track = importForeign().tracks.single()

        assertEquals("Foreign trip", track.name)
        assertEquals("Recorded by another app", track.comment)
    }

    @Test
    fun `a foreign track derives its stats from the points`() {
        val track = importForeign().tracks.single()

        assertTrue("distance is derived, not left at zero", track.distanceNm > 0f)
        assertTrue("average speed is derived from the point speeds", track.averageSpeedMps > 0f)
        assertEquals(track.endTimeMs, track.lastPointTimeMs)
    }
}
