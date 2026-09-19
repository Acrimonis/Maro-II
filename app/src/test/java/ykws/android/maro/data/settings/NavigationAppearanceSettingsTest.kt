package ykws.android.maro.data.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import java.io.File

/**
 * The seven heading-line and cap-arrow appearance settings added with the appearance change:
 * `navigationLineWidthDp`, `navigationLineTransparencyPct`, `navigationLineColor`,
 * `navigationArrowWidthDp`, `navigationArrowTransparencyPct`, `navigationArrowColor` and
 * `navigationArrowFollowSpeedColour`.
 *
 * The same three rules `CoastlineAppearanceSettingsTest` holds for its six.
 *  - Each default is **seeded from `AppConfig`**, so the property stays the single home of the value
 *    rather than a literal in Kotlin.
 *  - Every value **round-trips** through SharedPreferences.
 *  - The two widths and the two transparencies are **clamped on read** — a slider's span is a UI fact,
 *    never a guarantee about what storage holds.
 */
class NavigationAppearanceSettingsTest {

    private fun settingsFrom(seed: Map<String, Any> = emptyMap()): AppSettings =
        SettingsManager(NavSeededContext(seed)).settings.value

    @Test
    fun everyDefaultIsSeededFromTheProperties() {
        val s = settingsFrom()

        assertEquals(AppConfig.mapNavigationLineWidthDp, s.navigationLineWidthDp, 0f)
        assertEquals(AppConfig.mapNavigationLineTransparencyPct, s.navigationLineTransparencyPct)
        assertEquals(AppConfig.mapNavigationLineColor, s.navigationLineColor)
        assertEquals(AppConfig.mapNavigationArrowWidthDp, s.navigationArrowWidthDp, 0f)
        assertEquals(AppConfig.mapNavigationArrowTransparencyPct, s.navigationArrowTransparencyPct)
        assertEquals(AppConfig.mapNavigationArrowColor, s.navigationArrowColor)
        assertEquals(AppConfig.mapNavigationArrowFollowSpeedColour, s.navigationArrowFollowSpeedColour)
    }

    @Test
    fun theShippedDefaultsAreTheOnesTheAppearanceTableNames() {
        val s = settingsFrom()

        assertEquals(2.25f, s.navigationArrowWidthDp, 0f)
        assertEquals(0, s.navigationArrowTransparencyPct)
        assertFalse(s.navigationArrowFollowSpeedColour)
        assertEquals(1f, s.navigationLineWidthDp, 0f)
        assertEquals(70, s.navigationLineTransparencyPct)
    }

    @Test
    fun theLineColourSeedsOpaqueBecauseTheTransparencyCarriesItsAlpha() {
        // The stripped `#4D` is the point: an opaque hue beside a transparency of its own, so the
        // line's 30 % has one home. A re-packed alpha would fail here.
        val lineColor = settingsFrom().navigationLineColor

        assertEquals(0xFF, lineColor ushr 24 and 0xFF)
        assertEquals(0x1565C0, lineColor and 0x00FFFFFF)
    }

    @Test
    fun everyWidthIsClampedIntoTheSliderSpanOnRead() {
        val over = settingsFrom(
            mapOf(
                "navigation_line_width_dp" to 99f,
                "navigation_arrow_width_dp" to 99f
            )
        )
        assertEquals(4f, over.navigationLineWidthDp, 0f)
        assertEquals(8f, over.navigationArrowWidthDp, 0f)

        val under = settingsFrom(
            mapOf(
                "navigation_line_width_dp" to 0f,
                "navigation_arrow_width_dp" to 0f
            )
        )
        assertEquals(0.5f, under.navigationLineWidthDp, 0f)
        assertEquals(1f, under.navigationArrowWidthDp, 0f)
    }

    @Test
    fun everyTransparencyIsClampedToThePercentageRangeOnRead() {
        assertEquals(100, settingsFrom(mapOf("navigation_line_transparency_pct" to 150)).navigationLineTransparencyPct)
        assertEquals(0, settingsFrom(mapOf("navigation_line_transparency_pct" to -3)).navigationLineTransparencyPct)
        assertEquals(100, settingsFrom(mapOf("navigation_arrow_transparency_pct" to 150)).navigationArrowTransparencyPct)
        assertEquals(0, settingsFrom(mapOf("navigation_arrow_transparency_pct" to -3)).navigationArrowTransparencyPct)
    }

    @Test
    fun everyValueRoundTripsThroughPrefs() {
        val ctx = NavSeededContext(emptyMap())

        SettingsManager(ctx).update {
            it.copy(
                navigationLineWidthDp = 2.75f,
                navigationLineTransparencyPct = 40,
                navigationLineColor = 0xFF112233.toInt(),
                navigationArrowWidthDp = 6.25f,
                navigationArrowTransparencyPct = 15,
                navigationArrowColor = 0xFF445566.toInt(),
                navigationArrowFollowSpeedColour = true
            )
        }

        val reloaded = SettingsManager(ctx).settings.value
        assertEquals(2.75f, reloaded.navigationLineWidthDp, 0f)
        assertEquals(40, reloaded.navigationLineTransparencyPct)
        assertEquals(0xFF112233.toInt(), reloaded.navigationLineColor)
        assertEquals(6.25f, reloaded.navigationArrowWidthDp, 0f)
        assertEquals(15, reloaded.navigationArrowTransparencyPct)
        assertEquals(0xFF445566.toInt(), reloaded.navigationArrowColor)
        assertTrue(reloaded.navigationArrowFollowSpeedColour)
    }
}

/**
 * Minimal [Context] exposing a pre-seeded in-memory [SharedPreferences], so a [SettingsManager] can be
 * exercised from a known starting state and re-created over the same store to prove the round-trip —
 * the seeded-prefs pattern `CoastlineAppearanceSettingsTest` introduced, named apart from its copy.
 */
private class NavSeededContext(
    seed: Map<String, Any>
) : ContextWrapper(null) {
    private val prefs = NavSeededSharedPreferences(seed)

    override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"), "navSeededCtx")
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun getPackageName(): String = "ykws.android.maro.test"
}

private class NavSeededSharedPreferences(seed: Map<String, Any>) : SharedPreferences {
    private val store = seed.toMutableMap()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = NavSeededEditor(store)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
}

private class NavSeededEditor(private val store: MutableMap<String, Any>) : SharedPreferences.Editor {
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
