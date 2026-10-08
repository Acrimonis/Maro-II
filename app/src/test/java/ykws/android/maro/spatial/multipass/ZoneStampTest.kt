package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import java.util.concurrent.atomic.AtomicReference

/**
 * **The zone stamp — the narrowest world member P4.2 needs.**
 *
 * The zones are a runtime list with no generation marker of their own, so a fine tile keyed on a set of
 * zones could keep a stale limit after the list is rebuilt. [MultipassWorld.zoneGenerationStamp] answers
 * a **content** stamp over that list; these tests pin the three facts the design rests on: a rebuilt list
 * moves it, a re-read of the same list does not, and the live adapter answers it over the very list it
 * hands `speedZonesIn`.
 */
class ZoneStampTest {

    private fun zone(limitKn: Double) = SpeedZone(
        id = "z1", name = "Cap", speedLimitKn = limitKn,
        outerRing = listOf(LatLng(43.510, 7.020), LatLng(43.510, 7.030), LatLng(43.515, 7.030))
    )

    @Test
    fun aRebuiltZoneListMovesTheContentStamp() {
        val first = listOf(zone(4.0))
        val rebuilt = listOf(zone(6.0))
        assertNotEquals(zoneContentStamp(first), zoneContentStamp(rebuilt))

        // A new zone, and a moved ring, move it too — the whole data class, not just the limit.
        assertNotEquals(zoneContentStamp(first), zoneContentStamp(first + zone(8.0)))
        val movedRing = SpeedZone("z1", "Cap", 4.0, listOf(LatLng(43.500, 7.020), LatLng(43.500, 7.030)))
        assertNotEquals(zoneContentStamp(first), zoneContentStamp(listOf(movedRing)))
    }

    @Test
    fun aReReadOfTheSameListDoesNotMoveTheContentStamp() {
        val zones = listOf(zone(4.0))
        val first = zoneContentStamp(zones)
        assertEquals("a content hash is stable across reads of the same list", first, zoneContentStamp(zones))
        assertEquals("nor does a copy of it move it", first, zoneContentStamp(zones.toList()))
    }

    @Test
    fun theLiveAdapterAnswersTheContentStampOverItsOwnProvider() {
        val provider = AtomicReference(listOf(zone(4.0)))
        val world = LiveMultipassWorld(
            coastline = CoastlineRepository(),
            depth = DepthRepository(),
            zonesProvider = { provider.get() }
        )
        val before = world.zoneGenerationStamp
        assertEquals(zoneContentStamp(provider.get()), before)
        assertEquals("a re-read does not move it", before, world.zoneGenerationStamp)

        provider.set(listOf(zone(6.0)))
        assertNotEquals("a rebuilt list moves it", before, world.zoneGenerationStamp)
    }
}
