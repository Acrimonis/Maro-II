package ykws.android.maro.data.tracing

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File

/**
 * Minimal test double for Android [Context].
 *
 * Extends [ContextWrapper] (which already implements all abstract methods)
 * with a `null` base. Only [getFilesDir] and [getSharedPreferences] are
 * overridden to provide working implementations; ***any*** other method call
 * throws [NullPointerException] because the wrapped base is null.
 *
 * This is acceptable because [TripRepository] only calls [getFilesDir] and
 * [SettingsManager] only calls [getSharedPreferences] (plus [getPackageName]).
 */
class StubContext(
    private val filesDir: File = createTempDir("stubContext")
) : ContextWrapper(null) {

    private val prefs = InMemorySharedPreferences()

    override fun getFilesDir(): File = filesDir
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun getPackageName(): String = "ykws.android.maro.test"
}

/**
 * Minimal in-memory [SharedPreferences] implementation for unit tests.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    private val store = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValue
    override fun getInt(key: String, defValue: Int): Int = (store[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (store[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (store[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (store[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = InMemoryEditor(store, listeners)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(listener) }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(listener) }
}

private class InMemoryEditor(
    private val store: MutableMap<String, Any?>,
    private val listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener>
) : SharedPreferences.Editor {
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
