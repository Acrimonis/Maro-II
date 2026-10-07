package ykws.android.maro.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.data.model.ListFilter
import java.io.File

/**
 * The four route list settings (`routeListSort`, `routeListFilter`, `routeMapFilter`, `routeFilterLinked`)
 * and the four pinned-route appearance settings (S1, S9): every value defaults from its own home, every
 * value round-trips through SharedPreferences, and a persisted retired Kind axis is stripped on load.
 */
class RouteListSettingsTest {

    private fun settingsFrom(seed: Map<String, Any> = emptyMap()): AppSettings =
        SettingsManager(RouteSeededContext(seed)).settings.value

    @Test
    fun theRouteSettingsOpenOnTheirEmptyDefaults() {
        val s = settingsFrom()

        assertEquals(ykws.android.maro.data.model.ListSortState(), s.routeListSort)
        assertEquals(ListFilter(), s.routeListFilter)
        assertEquals(ListFilter(), s.routeMapFilter)
        assertTrue("the routes list and map filters start linked", s.routeFilterLinked)
    }

    @Test
    fun thePinnedRouteAppearanceDefaultsSeededFromTheFile() {
        val s = settingsFrom()
        val config = ykws.android.maro.config.AppConfig

        assertEquals(config.routePinnedFadeFrom, s.trackingTransparencyPinnedRouteNewest)
        assertEquals(config.routePinnedFadeTo, s.trackingTransparencyPinnedRouteOldest)
        assertEquals(config.routePinnedColourFrom, s.trackingColorPinnedRouteFrom)
        assertEquals(config.routePinnedColourTo, s.trackingColorPinnedRouteTo)
        // The seed reads apart from the pinned track's amber, which is the point of a pair of its own (D8).
        assertFalse(s.trackingColorPinnedRouteFrom == config.trackColourPinnedFrom)
    }

    @Test
    fun theEightRouteValuesRoundTripThroughPrefs() {
        val ctx = RouteSeededContext(emptyMap())
        val sort = ykws.android.maro.data.model.ListSortState(
            field = ykws.android.maro.data.model.ListSortField.CREATED,
            customFieldKey = "distanceNm",
            descending = false
        )
        val listFilter = ListFilter(mapOf("pinned" to "PINNED"))
        val mapFilter = ListFilter(mapOf("position" to "WATER"))

        SettingsManager(ctx).update {
            it.copy(
                routeListSort = sort,
                routeListFilter = listFilter,
                routeMapFilter = mapFilter,
                routeFilterLinked = false,
                trackingTransparencyPinnedRouteNewest = 35,
                trackingTransparencyPinnedRouteOldest = 70,
                trackingColorPinnedRouteFrom = 0xFF102030.toInt(),
                trackingColorPinnedRouteTo = 0xFF405060.toInt()
            )
        }

        val reloaded = SettingsManager(ctx).settings.value
        assertEquals(sort, reloaded.routeListSort)
        assertEquals(listFilter, reloaded.routeListFilter)
        assertEquals(mapFilter, reloaded.routeMapFilter)
        assertFalse(reloaded.routeFilterLinked)
        assertEquals(35, reloaded.trackingTransparencyPinnedRouteNewest)
        assertEquals(70, reloaded.trackingTransparencyPinnedRouteOldest)
        assertEquals(0xFF102030.toInt(), reloaded.trackingColorPinnedRouteFrom)
        assertEquals(0xFF405060.toInt(), reloaded.trackingColorPinnedRouteTo)
    }

    @Test
    fun aPersistedKindAxisIsStrippedOnLoadFromBothTrackFilters() {
        // An install that persisted `route=…` while the Kind axis existed must not keep filtering a kind
        // whose list is now kind-locked; the strip runs on load for both track filters (S1, D2).
        val s = settingsFrom(
            mapOf(
                "track_list_filter" to "route=ROUTES;pinned=PINNED",
                "track_map_filter" to "route=TRACKS"
            )
        )

        assertFalse(s.trackListFilter.axes.containsKey("route"))
        assertEquals("PINNED", s.trackListFilter.axes["pinned"])
        assertTrue(s.trackMapFilter.axes.isEmpty())
    }

    @Test
    fun thePinnedRouteLadderIsClampedOnRead() {
        val over = settingsFrom(
            mapOf(
                "tracking_transparency_pinned_route_newest" to 150,
                "tracking_transparency_pinned_route_oldest" to -4
            )
        )

        assertEquals(100, over.trackingTransparencyPinnedRouteNewest)
        assertEquals(0, over.trackingTransparencyPinnedRouteOldest)
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences], so a [SettingsManager] can be
 * exercised from a known starting state and re-created over the same store to prove the round-trip —
 * the seeded-prefs pattern the appearance tests introduced, named apart from their copies.
 */
private class RouteSeededContext(
    seed: Map<String, Any>
) : ContextWrapper(null) {
    private val prefs = RouteSeededSharedPreferences(seed)

    override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"), "routeSeededCtx")
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun getPackageName(): String = "ykws.android.maro.test"
}

private class RouteSeededSharedPreferences(seed: Map<String, Any>) : SharedPreferences {
    private val store = seed.toMutableMap()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = RouteSeededEditor(store)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

private class RouteSeededEditor(private val store: MutableMap<String, Any>) : SharedPreferences.Editor {
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
        pending.clear()
        removed.clear()
    }
}
