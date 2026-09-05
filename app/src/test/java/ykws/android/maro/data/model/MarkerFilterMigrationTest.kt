package ykws.android.maro.data.model

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.settings.SettingsManager
import java.io.File

/**
 * Unit tests for the marker icon/pin decoupling:
 *  - [UserMarker.matchesFilter] on the renamed `icon` axis and the new `pinned` axis.
 *  - The [SettingsManager] v7 migration that rewrites the persisted `markerListFilter`
 *    from the old `pinned=PINNED/UNPINNED` axis onto the new `icon=WITH_ICON/WITHOUT_ICON` axis.
 */
class MarkerFilterMigrationTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun marker(
        id: String,
        icon: String? = null,
        pinned: Boolean = false,
        origin: MarkerOrigin = MarkerOrigin.USER
    ): UserMarker = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(43.0, 7.0)),
        icon = icon,
        pinned = pinned,
        origin = origin
    )

    private fun filter(vararg entries: Pair<String, String>): ListFilter =
        ListFilter(entries.toMap())

    // ── UserMarker.matchesFilter: icon axis ─────────────────────────────────

    @Test
    fun `icon axis WITH_ICON matches only markers carrying an icon`() {
        val withIcon = marker("a", icon = "\uD83D\uDCCD")
        val withoutIcon = marker("b", icon = null)
        val f = filter("icon" to "WITH_ICON")

        assertTrue(withIcon.matchesFilter(f))
        assertFalse(withoutIcon.matchesFilter(f))
    }

    @Test
    fun `icon axis WITHOUT_ICON matches only markers without an icon`() {
        val withIcon = marker("a", icon = "\uD83D\uDCCD")
        val withoutIcon = marker("b", icon = null)
        val f = filter("icon" to "WITHOUT_ICON")

        assertFalse(withIcon.matchesFilter(f))
        assertTrue(withoutIcon.matchesFilter(f))
    }

    @Test
    fun `icon axis ALL matches regardless of icon`() {
        val withIcon = marker("a", icon = "\uD83D\uDCCD")
        val withoutIcon = marker("b", icon = null)
        val f = filter("icon" to "ALL")

        assertTrue(withIcon.matchesFilter(f))
        assertTrue(withoutIcon.matchesFilter(f))
    }

    // ── UserMarker.matchesFilter: pinned axis ───────────────────────────────

    @Test
    fun `pinned axis PINNED matches only pinned markers`() {
        val pinned = marker("a", pinned = true)
        val unpinned = marker("b", pinned = false)
        val f = filter("pinned" to "PINNED")

        assertTrue(pinned.matchesFilter(f))
        assertFalse(unpinned.matchesFilter(f))
    }

    @Test
    fun `pinned axis UNPINNED matches only unpinned markers`() {
        val pinned = marker("a", pinned = true)
        val unpinned = marker("b", pinned = false)
        val f = filter("pinned" to "UNPINNED")

        assertFalse(pinned.matchesFilter(f))
        assertTrue(unpinned.matchesFilter(f))
    }

    @Test
    fun `pinned axis ALL matches regardless of pin state`() {
        val pinned = marker("a", pinned = true)
        val unpinned = marker("b", pinned = false)
        val f = filter("pinned" to "ALL")

        assertTrue(pinned.matchesFilter(f))
        assertTrue(unpinned.matchesFilter(f))
    }

    @Test
    fun `icon and pinned axes are independent`() {
        // A marker can carry an icon but be unpinned, and vice versa.
        val iconOnly = marker("a", icon = "\uD83D\uDCCD", pinned = false)
        val pinnedOnly = marker("b", icon = null, pinned = true)

        assertTrue(iconOnly.matchesFilter(filter("icon" to "WITH_ICON", "pinned" to "UNPINNED")))
        assertFalse(iconOnly.matchesFilter(filter("icon" to "WITH_ICON", "pinned" to "PINNED")))
        assertTrue(pinnedOnly.matchesFilter(filter("icon" to "WITHOUT_ICON", "pinned" to "PINNED")))
        assertFalse(pinnedOnly.matchesFilter(filter("icon" to "WITH_ICON", "pinned" to "PINNED")))
    }

    // ── SettingsManager v7 migration ────────────────────────────────────────

    @Test
    fun `v7 migration rewrites markerListFilter pinned axis to icon axis`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 6,
                "marker_list_filter" to "pinned=PINNED;geometry=PINS"
            )
        )
        val settings = SettingsManager(ctx).settings.value

        // Old PINNED -> WITH_ICON; geometry axis untouched.
        assertEquals("WITH_ICON", settings.markerListFilter.axes["icon"])
        assertEquals("PINS", settings.markerListFilter.axes["geometry"])
        assertNull(settings.markerListFilter.axes["pinned"])
    }

    @Test
    fun `v7 migration rewrites UNPINNED to WITHOUT_ICON`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 6,
                "marker_list_filter" to "pinned=UNPINNED"
            )
        )
        val settings = SettingsManager(ctx).settings.value

        assertEquals("WITHOUT_ICON", settings.markerListFilter.axes["icon"])
        assertNull(settings.markerListFilter.axes["pinned"])
    }

    @Test
    fun `v7 migration leaves already-icon filter untouched`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 6,
                "marker_list_filter" to "icon=WITH_ICON;geometry=CIRCLES"
            )
        )
        val settings = SettingsManager(ctx).settings.value

        assertEquals("WITH_ICON", settings.markerListFilter.axes["icon"])
        assertEquals("CIRCLES", settings.markerListFilter.axes["geometry"])
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences] so the
 * [SettingsManager] version migration can be exercised from a known starting state.
 */
private class SeededContext(
    seed: Map<String, Any>
) : ContextWrapper(null) {
    private val prefs = SeededSharedPreferences(seed)

    override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"), "seededCtx")
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun getPackageName(): String = "ykws.android.maro.test"
}

private class SeededSharedPreferences(seed: Map<String, Any>) : SharedPreferences {
    private val store = seed.toMutableMap()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = SeededEditor(store)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

private class SeededEditor(private val store: MutableMap<String, Any>) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()

    override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putStringSet(key: String, value: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
    override fun remove(key: String): SharedPreferences.Editor = apply { removed.add(key) }
    override fun clear(): SharedPreferences.Editor = apply { pending.clear(); removed.clear(); store.clear() }
    override fun commit(): Boolean { apply(); return true }
    override fun apply() {
        removed.forEach { store.remove(it) }
        removed.clear()
        pending.forEach { (k, v) -> if (v != null) store[k] = v else store.remove(k) }
        pending.clear()
    }
}
