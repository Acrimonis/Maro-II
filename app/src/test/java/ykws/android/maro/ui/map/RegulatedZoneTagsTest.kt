package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.RegulatedZone
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZoneType
import ykws.android.maro.data.regulation.RegulationMetadata
import ykws.android.maro.data.regulation.ZoneDisplayCategory

/**
 * Unit tests for [regulatedZoneTags] — the one derivation the bottom-left tag stack, the zone info text
 * and the bottom band's banner clearance all read.
 *
 * The contract they lean on: an empty list is the single answer "no tag is drawn", which is what frees
 * the tag column's width for the banner ([`bannerStartInset`][bannerStartInset]); a tag carries the zone
 * it came from, except the 300 m band's injected entry, which has none; and the order is
 * [CATEGORY_PRIORITY]'s, most restrictive first.
 *
 * Pure — no Compose, no ViewModel, no device: the function takes a set, a point and a band flag, and
 * returns the list.
 */
class RegulatedZoneTagsTest {

    /** A ~220 m square around [INSIDE] — the polygon the marker points are tested against. */
    private val ring = listOf(
        LatLng(43.5490, 7.0990),
        LatLng(43.5490, 7.1010),
        LatLng(43.5510, 7.1010),
        LatLng(43.5510, 7.0990),
        LatLng(43.5490, 7.0990)
    )

    private val inside = LatLng(43.5500, 7.1000)
    private val outside = LatLng(43.6000, 7.2000)

    private fun zone(
        type: RegulatedZoneType,
        speedKn: Double? = null,
        name: String = "zone"
    ) = RegulatedZone(
        outerRing = ring,
        zoneType = type,
        speedLimitKn = speedKn,
        name = name
    )

    private fun setOf(vararg zones: RegulatedZone) = RegulatedZoneSet(
        zones = zones.toList(),
        metadata = RegulationMetadata(
            regionId = "nice-menton",
            fetchTimestampMs = 0L,
            sourceCount = 1,
            totalZones = zones.size
        )
    )

    @Test
    fun `no set and an empty set both draw nothing`() {
        assertTrue(regulatedZoneTags(null, null, false).isEmpty())
        assertTrue(regulatedZoneTags(setOf(), null, false).isEmpty())
    }

    @Test
    fun `a speed limit zone yields one tag carrying its own zone and speed`() {
        val speed = zone(RegulatedZoneType.SPEED_LIMIT, speedKn = 10.0)

        val tags = regulatedZoneTags(setOf(speed), null, false)

        assertEquals(1, tags.size)
        assertEquals(ZoneDisplayCategory.SPEED_LIMIT, tags[0].category)
        assertEquals(10.0, tags[0].speedKn!!, 0.0)
        assertSame(speed, tags[0].zone)
    }

    @Test
    fun `a category without a speed still yields its tag`() {
        val mooring = zone(RegulatedZoneType.MOORING)

        val tags = regulatedZoneTags(setOf(mooring), null, false)

        assertEquals(listOf(ZoneDisplayCategory.MOORING), tags.map { it.category })
        assertNull(tags[0].speedKn)
    }

    @Test
    fun `the marker only sees the zones that contain it`() {
        val zones = setOf(zone(RegulatedZoneType.SPEED_LIMIT, speedKn = 5.0))

        assertEquals(1, regulatedZoneTags(zones, inside, false).size)
        assertTrue(regulatedZoneTags(zones, outside, false).isEmpty())
    }

    @Test
    fun `the same pair from two zones collapses to one tag`() {
        val zones = setOf(
            zone(RegulatedZoneType.SPEED_LIMIT, speedKn = 5.0, name = "a"),
            zone(RegulatedZoneType.SPEED_LIMIT, speedKn = 5.0, name = "b")
        )

        val tags = regulatedZoneTags(zones, null, false)

        assertEquals(1, tags.size)
        assertEquals(5.0, tags[0].speedKn!!, 0.0)
    }

    @Test
    fun `the tags follow the priority table, most restrictive first`() {
        val zones = setOf(
            zone(RegulatedZoneType.MOORING, name = "mooring"),
            zone(RegulatedZoneType.ANCHORING_PROHIBITED, name = "anchoring")
        )

        val tags = regulatedZoneTags(zones, null, false)

        assertEquals(
            listOf(ZoneDisplayCategory.NO_ANCHOR, ZoneDisplayCategory.MOORING),
            tags.map { it.category }
        )
        assertTrue(CATEGORY_PRIORITY[tags[0].category]!! < CATEGORY_PRIORITY[tags[1].category]!!)
    }

    @Test
    fun `inside the 300 m band the injected entry replaces the regulated speed limits`() {
        val regulated = zone(RegulatedZoneType.SPEED_LIMIT, speedKn = 10.0)

        val tags = regulatedZoneTags(setOf(regulated), null, inZone300 = true)

        assertEquals(1, tags.size)
        assertEquals(ZoneDisplayCategory.SPEED_LIMIT, tags[0].category)
        assertEquals(AppConfig.zoneRegulatorySpeedKn.toDouble(), tags[0].speedKn!!, 0.0)
        assertNull("the band's entry has no zone of its own", tags[0].zone)
    }
}
