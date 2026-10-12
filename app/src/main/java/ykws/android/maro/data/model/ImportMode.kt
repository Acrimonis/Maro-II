package ykws.android.maro.data.model

/**
 * How an import should treat a payload whose identity already exists locally.
 *
 * Identity is the payload's own `id` when the file carries one (a Maro payload), else the entity's
 * `name` (a foreign file). The tracks' GPX import and the markers' GPX import both read this one
 * policy, so the two doors cannot answer the same question differently.
 */
enum class ImportMode {
    /** Keep what is here: a match is skipped, so a re-import is idempotent. */
    SKIP_EXISTING,

    /** Replace the matching entity in place, keeping its id so links to it survive. */
    UPDATE_EXISTING,

    /** Always write a new entity: a fresh id and a name made distinct from the ones already held. */
    IMPORT_NEW
}
