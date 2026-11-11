package ykws.android.maro.data.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureCacheTest {

    private val hour = 3_600_000L
    private fun newCache() = TemperatureCache { source ->
        when (source) {
            TemperatureSource.AIR -> hour
            TemperatureSource.WATER -> 12 * hour
        }
    }

    private fun sourceOf(key: String) = when (key.substringBefore(':')) {
        "air" -> TemperatureSource.AIR
        "water" -> TemperatureSource.WATER
        else -> null
    }

    @Test
    fun `an entry is fresh inside its source expiry and expired after it`() {
        val cache = newCache()
        val entry = CacheEntry(18.0, fetchedAtMs = 1_000L)
        assertTrue(cache.isFresh(TemperatureSource.AIR, entry, 1_000L + hour - 1))
        assertFalse(cache.isFresh(TemperatureSource.AIR, entry, 1_000L + hour))
        assertTrue(cache.isFresh(TemperatureSource.WATER, entry, 1_000L + hour))
    }

    @Test
    fun `a clock that went backwards expires the entry`() {
        val entry = CacheEntry(18.0, fetchedAtMs = 10_000L)
        assertFalse(newCache().isFresh(TemperatureSource.AIR, entry, 5_000L))
    }

    @Test
    fun `invalidateAll marks values expired and drops empty answers`() {
        val cache = newCache()
        cache.put("air:A:0:0", CacheEntry(18.0, 1_000L))
        cache.put("water:A:0:0", CacheEntry(null, 1_000L))

        cache.invalidateAll()

        val kept = cache.get("air:A:0:0")
        assertNotNull(kept)
        assertEquals(18.0, kept!!.valueC!!, 0.0)
        assertFalse(cache.isFresh(TemperatureSource.AIR, kept, 2_000L))
        assertNull(cache.get("water:A:0:0"))
    }

    @Test
    fun `a stored cache comes back under the same signature`() {
        val cache = newCache()
        cache.put("air:A:0:0", CacheEntry(18.0, 1_000L))
        cache.put("water:B:1:2", CacheEntry(null, 1_000L))
        val text = cache.encode("sig")

        val restored = newCache()
        restored.restore(text, "sig", 2_000L, ::sourceOf)

        assertEquals(2, restored.size)
        assertNull(restored.get("water:B:1:2")!!.valueC)
    }

    @Test
    fun `a stored cache is discarded when the signature changed`() {
        val cache = newCache()
        cache.put("air:A:0:0", CacheEntry(18.0, 1_000L))
        val text = cache.encode("sig-1km")

        val restored = newCache()
        restored.restore(text, "sig-2km", 2_000L, ::sourceOf)

        assertEquals(0, restored.size)
    }

    @Test
    fun `entries past their expiry are not restored`() {
        val cache = newCache()
        cache.put("air:A:0:0", CacheEntry(18.0, 1_000L))
        cache.put("water:A:0:0", CacheEntry(17.0, 1_000L))
        val text = cache.encode("sig")

        val restored = newCache()
        restored.restore(text, "sig", 1_000L + 2 * hour, ::sourceOf)

        assertNull(restored.get("air:A:0:0"))
        assertNotNull(restored.get("water:A:0:0"))
    }

    @Test
    fun `unreadable text leaves the cache empty`() {
        val restored = newCache()
        restored.restore("not json", "sig", 0L, ::sourceOf)
        assertEquals(0, restored.size)
    }
}
