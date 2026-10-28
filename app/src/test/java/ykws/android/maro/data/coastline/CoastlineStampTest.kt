package ykws.android.maro.data.coastline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineMetadata
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.Zone300Data

/**
 * **The coastline's content stamp is a value of the coast's points, not of the file or the object.**
 *
 * The route engine keys its on-disk fine tiles on the stamp, so it must answer one number on every launch
 * over the same data: the bundled asset and the cache copy the repository writes from it — re-encoded by
 * the serializer, the 300 m band added — are one coast. An identity hash answered a new number every
 * process and left the store write-only; these cases pin the three facts the key now rests on.
 */
class CoastlineStampTest {

    private fun point(lat: Double, lon: Double) = CoastlinePoint(lat.toFloat(), lon.toFloat())

    private fun data(shift: Double = 0.0) = CoastlineData(
        mainland = CoastlineSegment(
            osmWayId = 1L,
            points = listOf(point(43.52, 7.00 + shift), point(43.52, 7.03 + shift), point(43.52, 7.06 + shift)),
            isMainland = true
        ),
        islands = listOf(
            CoastlineSegment(
                osmWayId = 2L,
                points = listOf(point(43.50, 7.01), point(43.50, 7.02), point(43.51, 7.02), point(43.50, 7.01)),
                isClosed = true
            )
        ),
        metadata = CoastlineMetadata(source = "test", pointCount = 7, meanSpacingM = 10.0, fetchTimestampMs = 1L),
        regionId = "test",
        boundingBox = BoundingBox(43.0, 44.0, 6.0, 8.0)
    )

    @Test
    fun theSameDataAnswersOneStampAndOtherPointsAnother() {
        val base = data()
        assertEquals("a stamp is a value of the points: read twice, it is one number", coastlineContentStamp(base), coastlineContentStamp(data()))
        assertNotEquals("a coast with other points is another stamp", coastlineContentStamp(base), coastlineContentStamp(data(shift = 0.001)))
    }

    @Test
    fun theReEncodedCacheCopyWithTheBandAddedAnswersTheAssetsStamp() {
        val asset = data()
        // The repository writes its cache from the loaded data with the band attached, through the serializer;
        // the next launch reads that copy. Its stamp must be the asset's, or every tile rebuilds once more.
        val cached = CoastlineSerializer.deserialize(
            CoastlineSerializer.serialize(
                asset.copy(zone300 = Zone300Data(fillPolygons = emptyList(), seawardLines = emptyList(), gridCellM = 10.0))
            )
        )
        assertEquals(coastlineContentStamp(asset), coastlineContentStamp(cached))
    }

    @Test
    fun aRepositoryWithNoCoastAnswersZero() {
        assertEquals(0L, CoastlineRepository().coastlineStamp)
    }
}
