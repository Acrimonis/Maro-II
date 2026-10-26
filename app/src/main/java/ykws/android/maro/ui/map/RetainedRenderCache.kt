package ykws.android.maro.ui.map

/** Per-owner cache, pruned to the current drawn set after a completed render pass. */
internal class RetainedRenderCache<G, P, S, V> {
    private data class Entry<G, P, S, V>(val geometry: G, val prepared: P, val style: S, val value: V)
    private val entries = mutableMapOf<String, Entry<G, P, S, V>>()

    fun render(id: String, geometry: G, style: S, prepare: (G) -> P, build: (P) -> V): V {
        val old = entries[id]
        val sameGeometry = old != null && old.geometry == geometry
        if (sameGeometry && old!!.style == style) return old.value
        val prepared = if (sameGeometry) old!!.prepared else prepare(geometry)
        return build(prepared).also { entries[id] = Entry(geometry, prepared, style, it) }
    }

    fun retain(ids: Set<String>) { entries.keys.retainAll(ids) }
    fun clear() { entries.clear() }
    internal val size: Int get() = entries.size
}

/** Keep foreign objects and unchanged identities; only reorder the managed band when necessary. */
internal fun <T> reconcileOwnedOverlays(target: MutableList<T>, previous: List<T>, wanted: List<T>): Boolean {
    val wantedSet = wanted.toHashSet()
    val removed = previous.filter { it !in wantedSet }.toHashSet()
    var changed = target.removeAll(removed)
    if (target.filter { it in wantedSet } == wanted) return changed
    var at = target.indexOfFirst { it in wantedSet }.let { if (it < 0) target.size else it }
    for (item in wanted) {
        val old = target.indexOf(item)
        if (old == at) { at++; continue }
        if (old >= 0) {
            target.removeAt(old)
            if (old < at) at--
        }
        target.add(at++, item)
        changed = true
    }
    return changed
}
