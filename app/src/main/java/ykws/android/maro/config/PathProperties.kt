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
 * The two stroke qualifiers sit here beside the roles because the class axis is one axis: a `casing`
 * belongs to the selected line and a `dimmed` to the candidate lines, and both are read through the
 * same cascade as `live` or `pinned`. **`acquisition`** (2026-10-07) is the route search's own line-type:
 * the rung under the selection wears it while the mode is choosing, so the class leaf can silence that
 * one line's chevrons and bands through the ordinary cascade rather than a bespoke key.
 */
enum class PathClass(val qualifier: String) {
    LIVE("live"),
    SELECTED("selected"),
    NEWEST("newest"),
    HISTORY("history"),
    PINNED("pinned"),
    CASING("casing"),
    DIMMED("dimmed"),
    /** The route search's rung under the selection, while the mode is choosing — never the live line. */
    ACQUISITION("acquisition")
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
    if (pathClass != null) addAll(pathClassKeyCandidates(kind, group, field, sub, pathClass))
    val g = if (group.isEmpty()) "" else "$group."
    val tail = if (sub.isNullOrEmpty()) "" else ".$sub"
    add("path.${kind.prefix}.$g$field$tail")
    add("path.$g$field$tail")
}

/**
 * **The class-bearing candidates alone, most specific first** (D5):
 * `path.<kind>.<group>.<field>.<class>[.<sub>]` then `path.<group>.<field>.<class>[.<sub>]`.
 *
 * The runtime **override** reader walks these and only these (2026-10-07): a class overrides the kind
 * and common leaves, which are the **seeds** the persisted setting starts from at load rather than
 * values to fall back to while drawing. One home for the class key's shape, shared with
 * [pathKeyCandidates].
 */
internal fun pathClassKeyCandidates(
    kind: PathKind,
    group: String,
    field: String,
    sub: String? = null,
    pathClass: PathClass
): List<String> {
    val g = if (group.isEmpty()) "" else "$group."
    val tail = if (sub.isNullOrEmpty()) "" else ".$sub"
    return listOf(
        "path.${kind.prefix}.$g$field.${pathClass.qualifier}$tail",
        "path.$g$field.${pathClass.qualifier}$tail"
    )
}
