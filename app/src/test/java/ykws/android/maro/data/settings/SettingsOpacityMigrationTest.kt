package ykws.android.maro.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Unit tests for the [SettingsManager] v8 migration that converts the track
 * transparency settings (0 = opaque, 100 = invisible) into opacity settings
 * (0 = invisible, 100 = opaque).
 *
 * The migration must:
 *  - write new `tracking_opacity_*` keys with the inverted value (`new = 100 - old`), and
 *  - remove the old `tracking_transparency_*` keys.
 */
class SettingsOpacityMigrationTest {

    @Test
    fun `v8 migration inverts transparency values into opacity keys`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 7,
                "tracking_transparency_newest" to 20,
                "tracking_transparency_oldest" to 80,
                "tracking_transparency_pinned_newest" to 0,
                "tracking_transparency_pinned_oldest" to 20
            )
        )
        val settings = SettingsManager(ctx).settings.value

        // opacity = 100 - transparency
        assertEquals(80, settings.trackingOpacityNewest)
        assertEquals(20, settings.trackingOpacityOldest)
        assertEquals(100, settings.trackingOpacityPinnedNewest)
        assertEquals(80, settings.trackingOpacityPinnedOldest)
    }

    @Test
    fun `v8 migration removes old transparency keys`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 7,
                "tracking_transparency_newest" to 20,
                "tracking_transparency_oldest" to 80,
                "tracking_transparency_pinned_newest" to 0,
                "tracking_transparency_pinned_oldest" to 20
            )
        )
        SettingsManager(ctx)

        assertFalse(ctx.prefs.contains("tracking_transparency_newest"))
        assertFalse(ctx.prefs.contains("tracking_transparency_oldest"))
        assertFalse(ctx.prefs.contains("tracking_transparency_pinned_newest"))
        assertFalse(ctx.prefs.contains("tracking_transparency_pinned_oldest"))
    }

    @Test
    fun `v8 migration writes inverted opacity keys`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 7,
                "tracking_transparency_newest" to 20,
                "tracking_transparency_oldest" to 80,
                "tracking_transparency_pinned_newest" to 0,
                "tracking_transparency_pinned_oldest" to 20
            )
        )
        SettingsManager(ctx)

        assertEquals(80, ctx.prefs.getInt("tracking_opacity_newest", -1))
        assertEquals(20, ctx.prefs.getInt("tracking_opacity_oldest", -1))
        assertEquals(100, ctx.prefs.getInt("tracking_opacity_pinned_newest", -1))
        assertEquals(80, ctx.prefs.getInt("tracking_opacity_pinned_oldest", -1))
    }

    @Test
    fun `v8 migration leaves already-opacity prefs untouched`() {
        val ctx = SeededContext(
            mapOf(
                "prefs_version" to 8,
                "tracking_opacity_newest" to 90,
                "tracking_opacity_oldest" to 30
            )
        )
        val settings = SettingsManager(ctx).settings.value

        assertEquals(90, settings.trackingOpacityNewest)
        assertEquals(30, settings.trackingOpacityOldest)
        // No old transparency keys were present, so nothing to invert/remove.
        assertFalse(ctx.prefs.contains("tracking_transparency_newest"))
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences] so the
 * [SettingsManager] version migration can be exercised from a known starting state.
 */
private class SeededContext(
    seed: Map<String, Any>
) : ContextWrapper(null) {
    val prefs: SharedPreferences = SeededSharedPreferences(seed)

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
