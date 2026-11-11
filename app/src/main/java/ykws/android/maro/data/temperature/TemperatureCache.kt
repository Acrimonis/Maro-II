package ykws.android.maro.data.temperature

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One cached answer for one sample point. A null [valueC] is an empty answer: the source had nothing for
 * that point, and the cache remembers it so the point is not asked again until it expires.
 *
 * [forced] marks a value the user asked to refresh: it is still shown (subdued) until a fresh one
 * replaces it, but it counts as expired.
 */
@Serializable
data class CacheEntry(
    val valueC: Double?,
    val fetchedAtMs: Long,
    val forced: Boolean = false
)

/**
 * The temperature cache: one [CacheEntry] per sample point key, expiring by time.
 *
 * The cache holds no clock — callers pass `nowMs` — and no I/O: [encode] and [restore] turn it to and from
 * a string so the owner decides where it lives. Not thread-safe; one coroutine owns it.
 */
class TemperatureCache(private val expiryMs: (TemperatureSource) -> Long) {
    private val entries = HashMap<String, CacheEntry>()

    fun get(key: String): CacheEntry? = entries[key]

    fun put(key: String, entry: CacheEntry) {
        entries[key] = entry
    }

    /** Whether [entry] is still inside its source's expiry and was not marked for refresh. */
    fun isFresh(source: TemperatureSource, entry: CacheEntry, nowMs: Long): Boolean {
        if (entry.forced) return false
        val age = nowMs - entry.fetchedAtMs
        // A clock that moved backwards makes an entry look newer than now: treat it as expired.
        return age in 0 until expiryMs(source)
    }

    /**
     * The user's "refresh now": every held value is marked expired (and stays visible until replaced), and
     * every remembered empty answer is dropped so the point can be asked again.
     */
    fun invalidateAll() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.valueC == null) iterator.remove()
            else entry.setValue(entry.value.copy(forced = true))
        }
    }

    val size: Int get() = entries.size

    /** The cache as a string, stamped with [signature] so a change of cell geometry discards it. */
    fun encode(signature: String): String =
        JSON.encodeToString(Persisted.serializer(), Persisted(signature, entries.toMap()))

    /**
     * Loads [text] if it was written under the same [signature], dropping entries already past their
     * expiry as of [nowMs]. Anything unreadable or mismatched leaves the cache empty.
     */
    fun restore(text: String, signature: String, nowMs: Long, sourceOf: (String) -> TemperatureSource?) {
        val persisted = runCatching { JSON.decodeFromString(Persisted.serializer(), text) }.getOrNull() ?: return
        if (persisted.signature != signature) return
        persisted.entries.forEach { (key, entry) ->
            val source = sourceOf(key) ?: return@forEach
            if (isFresh(source, entry, nowMs)) entries[key] = entry
        }
    }

    @Serializable
    private data class Persisted(val signature: String, val entries: Map<String, CacheEntry>)

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
