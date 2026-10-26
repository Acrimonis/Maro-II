package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/** The two unboxed tables the walk reads per step and per mark: a `Long → Int` map and a point → double map. */
class RoutePrimitiveMapsTest {

    @Test
    fun theSlotMapHoldsEveryKeyItWasGivenAndNoOther() {
        val map = LongIntMap(4)
        val keys = (-5_000L..5_000L step 7).toList() + listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L)
        for ((i, key) in keys.withIndex()) map.putIfAbsent(key, i)
        assertEquals(keys.size, map.size)
        for ((i, key) in keys.withIndex()) assertEquals("key $key", i, map[key])
        assertEquals("a key never stored answers -1", -1, map[123_456_789L])
        assertFalse(map.containsKey(1L))
        map.putIfAbsent(keys[3], 99)
        assertEquals("a key already held keeps its first value", 3, map[keys[3]])
    }

    @Test
    fun thePointMapKeysOnTheExactCoordinates() {
        val map = PointDoubleMap()
        val points = (0 until 3_000).map { LatLng(43.5 + it * 1e-6, 7.0 - it * 1e-6) }
        for ((i, p) in points.withIndex()) map[p] = i.toDouble()
        for ((i, p) in points.withIndex()) assertEquals(i.toDouble(), map[p], 0.0)
        assertTrue("a point never stored answers NaN", map[LatLng(1.0, 1.0)].isNaN())
        map[LatLng(0.0, 0.0)] = 1.0
        assertTrue("-0.0 is not 0.0, as LatLng.equals reads them", map[LatLng(-0.0, 0.0)].isNaN())
        map[points[7]] = 42.0
        assertEquals("a stored point is overwritten in place", 42.0, map[points[7]], 0.0)
        map.clear()
        assertTrue(map[points[0]].isNaN())
    }
}
