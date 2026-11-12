package ykws.android.maro.ui.notice

/**
 * Every notice key in one place: a post under a key replaces the notice already showing or waiting under it,
 * so two callers sharing a key share a slot, and two keys never collide by accident. `NoticeKeysTest` holds
 * them distinct.
 */
internal object NoticeKeys {
    /** The "press back again" pill; its dwell is the double-back window. */
    const val EXIT = "exit"

    /** The lock and unlock confirmation. */
    const val LOCK = "lock"

    /** An import's outcome, so a second import replaces the first. */
    const val IMPORT = "import"

    /** The transfer status (an export or import running); a status, cleared by its owner. */
    const val TRACK_OP = "trackOp"

    /** A track or route just saved. */
    const val TRACK_SAVED = "trackSaved"

    /** A route search's confirmations and refusals. */
    const val ROUTE = "route"

    /** The arrival status, standing while the settled reading holds. */
    const val ROUTE_ARRIVAL = "routeArrival"

    /** The temperature tile's refresh line and problem line, one slot. */
    const val TEMPERATURE = "temperature"

    /** The outcome of Settings' speed-history Calculate. */
    const val SPEED_HISTORY = "speedHistory"

    /** Startup: colour keys the runtime parser could not read. */
    const val STARTUP_COLOURS = "startup.colours"

    /** Startup: a persisted route-engine id no registry row claims. */
    const val STARTUP_ROUTE_ENGINE = "startup.routeEngine"

    /** The fine-tile store's first reclamation. */
    const val FINE_TILE_RECLAIM = "fineTileReclaim"

    /** Every key, for the test that holds them distinct. */
    val all: List<String> = listOf(
        EXIT, LOCK, IMPORT, TRACK_OP, TRACK_SAVED, ROUTE, ROUTE_ARRIVAL, TEMPERATURE, SPEED_HISTORY,
        STARTUP_COLOURS, STARTUP_ROUTE_ENGINE, FINE_TILE_RECLAIM
    )
}
