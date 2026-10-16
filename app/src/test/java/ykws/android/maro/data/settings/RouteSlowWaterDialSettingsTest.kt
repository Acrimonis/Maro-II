package ykws.android.maro.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.config.VerdictDial
import java.io.File

/**
 * **The verdict's dial's stored stop** — the one value the crown's weight is read from. It round-trips
 * through SharedPreferences and clamps on read like every other bounded slider, a pre-dial store's
 * aversion cursor maps onto the new stops so an upgrade lands where the old bridge read, and a stored
 * **0** reaches the engine's own weight as a plain zero rather than the sentinel.
 */
class RouteSlowWaterDialSettingsTest {

    private fun settingsFrom(seed: Map<String, Any> = emptyMap()): AppSettings =
        SettingsManager(SlowWaterSeededContext(seed)).settings.value

    @Test
    fun theDialStopRoundTripsThroughPrefsAndClampsOnRead() {
        val ctx = SlowWaterSeededContext(emptyMap())
        SettingsManager(ctx).update { it.copy(routeSlowWaterStop = 3) }

        val reloaded = SettingsManager(ctx).settings.value
        assertEquals("the chosen stop round-trips", 3, reloaded.routeSlowWaterStop)
        assertEquals("and reads as its own weight", 3.0, reloaded.verdictWeight!!, 1e-9)

        assertEquals(
            "a stored stop above the track clamps to its last stop",
            VerdictDial.stops.lastIndex,
            settingsFrom(mapOf("route_slow_water_stop" to 99)).routeSlowWaterStop
        )
        assertEquals(
            "and one below it clamps to the first",
            0,
            settingsFrom(mapOf("route_slow_water_stop" to -4)).routeSlowWaterStop
        )
    }

    @Test
    fun theLegacyCursorMapsOntoTheNewStops() {
        // The retired cursor spread evenly over five stops and shifted one place along the six-stop
        // track, so an upgrade lands on the stop the old bridge would have read (SettingsManager).
        assertEquals(
            "the cursor's bottom lands on 1x",
            VerdictDial.ONE.index,
            settingsFrom(mapOf("route_slow_water_aversion" to 0f)).routeSlowWaterStop
        )
        assertEquals(
            "its middle lands on 3x",
            VerdictDial.THREE.index,
            settingsFrom(mapOf("route_slow_water_aversion" to 2.5f)).routeSlowWaterStop
        )
        assertEquals(
            "and its top lands on the sentinel",
            VerdictDial.IGNORE_TIME.index,
            settingsFrom(mapOf("route_slow_water_aversion" to 5f)).routeSlowWaterStop
        )
    }

    @Test
    fun aStoredZeroStopReachesTheEnginesOwnWeight() {
        val s = settingsFrom(mapOf("route_slow_water_stop" to 0))
        assertEquals("the stored stop is the dial's first", 0, s.routeSlowWaterStop)
        assertEquals(
            "and the engine's own weight is a plain zero, not the sentinel",
            0.0,
            s.verdictWeight!!,
            1e-9
        )
        assertNull(
            "while the top stop alone carries no weight at all",
            settingsFrom(mapOf("route_slow_water_stop" to VerdictDial.IGNORE_TIME.index)).verdictWeight
        )
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences], so a [SettingsManager] can be
 * exercised from a known starting state and re-created over the same store to prove the round-trip —
 * the seeded-prefs pattern the settings tests share, named apart from their copies.
 */
private class SlowWaterSeededContext(
    seed: Map<String, Any>
) : ContextWrapper(null) {
    private val prefs = SlowWaterSeededSharedPreferences(seed)

    override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"), "slowWaterSeededCtx")
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun getPackageName(): String = "ykws.android.maro.test"
}

private class SlowWaterSeededSharedPreferences(seed: Map<String, Any>) : SharedPreferences {
    private val store = seed.toMutableMap()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? =
        store[key] as? MutableSet<String> ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = SlowWaterSeededEditor(store)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

private class SlowWaterSeededEditor(private val store: MutableMap<String, Any>) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()

    override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
    override fun remove(key: String): SharedPreferences.Editor = apply { removed.add(key) }
    override fun clear(): SharedPreferences.Editor = apply { store.clear() }
    override fun commit(): Boolean { applyPending(); return true }
    override fun apply() { applyPending() }

    private fun applyPending() {
        removed.forEach { store.remove(it) }
        pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
    }
}
