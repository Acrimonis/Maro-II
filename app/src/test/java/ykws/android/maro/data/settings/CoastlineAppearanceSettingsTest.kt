package ykws.android.maro.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File

/**
 * The six shoreline/zone appearance settings added with the stroke-width change:
 * `zone300BoundaryWidthPx`, `regulatedZoneOutlineWidthPx`, `coastlineWidthPx`,
 * `coastlineTransparencyPct`, `coastlineMainlandColor` and `coastlineIslandColor`.
 *
 * Three rules are held here.
 *  - Each default is **seeded from `AppConfig`**, so the property stays the single home of the value
 *    rather than a literal in Kotlin (the drift `zone300Color` used to carry).
 *  - Every value **round-trips** through SharedPreferences.
 *  - The three widths and the coastline transparency are **clamped on read** — a slider's span is a
 *    UI fact, never a guarantee about what storage holds.
 *
 * The fake [Context] follows `MarkerFilterMigrationTest`'s seeded-prefs pattern.
 */
class CoastlineAppearanceSettingsTest {

    private fun settingsFrom(seed: Map<String, Any> = emptyMap()): AppSettings =
        SettingsManager(SeededContext(seed)).settings.value

    @Test
    fun everyDefaultIsSeededFromTheProperties() {
        val s = settingsFrom()

        assertEquals(AppConfig.mapZone300BoundaryWidthPx, s.zone300BoundaryWidthPx)
        assertEquals(AppConfig.mapRegulatedZoneOutlineWidthPx, s.regulatedZoneOutlineWidthPx)
        assertEquals(AppConfig.mapCoastlineWidthPx, s.coastlineWidthPx)
        assertEquals(AppConfig.mapCoastlineTransparencyPct, s.coastlineTransparencyPct)
        assertEquals(AppConfig.mapCoastlineMainlandColor, s.coastlineMainlandColor)
        assertEquals(AppConfig.mapCoastlineIslandColor, s.coastlineIslandColor)
    }

    @Test
    fun theZone300ColourIsSeededFromTheMovedPropertyRatherThanALiteral() {
        assertEquals(AppConfig.mapZone300Boundary, settingsFrom().zone300Color)
    }

    @Test
    fun everyWidthIsClampedIntoTheSliderSpanOnRead() {
        val over = settingsFrom(
            mapOf(
                "zone300_boundary_width_px" to 999,
                "regulated_zone_outline_width_px" to 999,
                "coastline_width_px" to 999
            )
        )
        assertEquals(20, over.zone300BoundaryWidthPx)
        assertEquals(20, over.regulatedZoneOutlineWidthPx)
        assertEquals(20, over.coastlineWidthPx)

        val under = settingsFrom(
            mapOf(
                "zone300_boundary_width_px" to -5,
                "regulated_zone_outline_width_px" to 0,
                "coastline_width_px" to 0
            )
        )
        assertEquals(1, under.zone300BoundaryWidthPx)
        assertEquals(1, under.regulatedZoneOutlineWidthPx)
        assertEquals(1, under.coastlineWidthPx)
    }

    @Test
    fun theCoastlineTransparencyIsClampedToThePercentageRangeOnRead() {
        assertEquals(100, settingsFrom(mapOf("coastline_transparency_pct" to 150)).coastlineTransparencyPct)
        assertEquals(0, settingsFrom(mapOf("coastline_transparency_pct" to -3)).coastlineTransparencyPct)
    }

    @Test
    fun everyValueRoundTripsThroughPrefs() {
        val ctx = SeededContext(emptyMap())

        SettingsManager(ctx).update {
            it.copy(
                zone300BoundaryWidthPx = 12,
                regulatedZoneOutlineWidthPx = 7,
                coastlineWidthPx = 15,
                coastlineTransparencyPct = 30,
                coastlineMainlandColor = 0xFF112233.toInt(),
                coastlineIslandColor = 0xFF445566.toInt()
            )
        }

        val reloaded = SettingsManager(ctx).settings.value
        assertEquals(12, reloaded.zone300BoundaryWidthPx)
        assertEquals(7, reloaded.regulatedZoneOutlineWidthPx)
        assertEquals(15, reloaded.coastlineWidthPx)
        assertEquals(30, reloaded.coastlineTransparencyPct)
        assertEquals(0xFF112233.toInt(), reloaded.coastlineMainlandColor)
        assertEquals(0xFF445566.toInt(), reloaded.coastlineIslandColor)
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences], so a
 * [SettingsManager] can be exercised from a known starting state and re-created over
 * the same store to prove the round-trip.
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
