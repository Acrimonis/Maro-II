package ykws.android.maro.data.coastline

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.BandPolygon
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineMetadata
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data

/** Round-trip tests for the Zone300 band through the protobuf serializer. */
class CoastlineSerializerZone300Test {

    private fun sampleData(zone: Zone300Data?): CoastlineData {
        val mainland = CoastlineSegment(
            osmWayId = 1L,
            points = listOf(
                CoastlinePoint(43.50f, 7.00f),
                CoastlinePoint(43.50f, 7.02f)
            ),
            isMainland = true,
            isClosed = false
        )
        return CoastlineData(
            mainland = mainland,
            islands = emptyList(),
            metadata = CoastlineMetadata(source = "test", pointCount = 2, meanSpacingM = 0.0, projectionRefLat = 43.5),
            regionId = "test",
            boundingBox = BoundingBox(43.49, 43.51, 7.00, 7.02),
            zone300 = zone
        )
    }

    @Test
    fun `zone300 round-trips through protobuf`() {
        val zone = Zone300Data(
            fillPolygons = listOf(
                BandPolygon(
                    outer = listOf(LatLng(43.50, 7.00), LatLng(43.50, 7.01), LatLng(43.49, 7.01)),
                    holes = listOf(listOf(LatLng(43.499, 7.004), LatLng(43.499, 7.006), LatLng(43.498, 7.006)))
                )
            ),
            seawardLines = listOf(listOf(LatLng(43.49, 7.00), LatLng(43.49, 7.01))),
            gridCellM = 15.0,
            bandM = 300.0
        )

        val back = CoastlineSerializer.deserialize(CoastlineSerializer.serialize(sampleData(zone)))

        val z = back.zone300
        assertNotNull("zone300 should survive round-trip", z)
        requireNotNull(z)
        assertEquals(15.0, z.gridCellM, 1e-9)
        assertEquals(300.0, z.bandM, 1e-9)
        assertEquals(1, z.fillPolygons.size)
        assertEquals(3, z.fillPolygons[0].outer.size)
        assertEquals(1, z.fillPolygons[0].holes.size)
        assertEquals(3, z.fillPolygons[0].holes[0].size)
        assertEquals(1, z.seawardLines.size)
        assertEquals(2, z.seawardLines[0].size)
        // coordinates survive at float precision
        assertEquals(43.50, z.fillPolygons[0].outer[0].latitude, 1e-4)
        assertEquals(7.006, z.fillPolygons[0].holes[0][2].longitude, 1e-4)
    }

    @Test
    fun `null zone300 round-trips to null (backward compatible)`() {
        val back = CoastlineSerializer.deserialize(CoastlineSerializer.serialize(sampleData(null)))
        assertNull(back.zone300)
    }
}
