package ykws.android.maro.config

/**
 * The two kinds of line the path engine draws, and the **kind prefix** each contributes to the
 * `path.*` cascade: `path.track.*` or `path.route.*` (D4).
 *
 * A kind is an axis of the file, never a class: a class is never named `track` or `route`, so the
 * two override axes cannot collide (D4).
 */
enum class PathKind(val prefix: String) {
    TRACK("track"),
    ROUTE("route")
}

/**
 * The **class qualifier** a `path.<group>.<field>.<class>[.<sub>]` key carries (D4): the role one
 * drawn line plays, whatever its kind.
 *
 * The two stroke qualifiers sit here beside the five roles because the class axis is one axis: a
 * `casing` belongs to the selected line and a `dimmed` to the candidate lines, and both are read
 * through the same cascade as `live` or `pinned`.
 */
enum class PathClass(val qualifier: String) {
    LIVE("live"),
    SELECTED("selected"),
    NEWEST("newest"),
    HISTORY("history"),
    PINNED("pinned"),
    CASING("casing"),
    DIMMED("dimmed")
}

/**
 * **The four candidate keys for one leaf, most specific first** (D5) — the cascade the resolver walks
 * before it falls through to the code default:
 *
 * 1. `path.<kind>.<group>.<field>.<class>[.<sub>]` — kind **and** class;
 * 2. `path.<group>.<field>.<class>[.<sub>]` — the class alone, which **outranks** the kind;
 * 3. `path.<kind>.<group>.<field>[.<sub>]` — the kind alone;
 * 4. `path.<group>.<field>[.<sub>]` — the common value.
 *
 * An absent [pathClass] drops candidates 1 and 2, which is what makes a class-less read (a stored
 * route, say) resolve on the kind and common axes alone. An empty [group] reads a top-level leaf
 * (`path.count`). Pure, so the precedence is unit-testable without a `Properties` bag.
 */
internal fun pathKeyCandidates(
    kind: PathKind,
    group: String,
    field: String,
    sub: String? = null,
    pathClass: PathClass? = null
): List<String> = buildList {
    val g = if (group.isEmpty()) "" else "$group."
    val tail = if (sub.isNullOrEmpty()) "" else ".$sub"
    if (pathClass != null) {
        add("path.${kind.prefix}.$g$field.${pathClass.qualifier}$tail")
        add("path.$g$field.${pathClass.qualifier}$tail")
    }
    add("path.${kind.prefix}.$g$field$tail")
    add("path.$g$field$tail")
}
