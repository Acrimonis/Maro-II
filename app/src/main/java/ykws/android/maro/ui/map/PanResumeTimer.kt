package ykws.android.maro.ui.map

/**
 * The pan-resume timer's decision — what a drawer's open or close does to the deadline that returns
 * the camera to the boat.
 *
 * The timer itself lives in [NavigationViewModel]: a user pan sets auto-follow suppression and arms a
 * deadline of `settings.recenterDelaySeconds`; on expiry the boat takes the centre back. A drawer's
 * lifetime must not eat that deadline. Opening holds it — the pan outlives the drawer and the map
 * stays where the user dragged it — and closing restarts it from the close, a full delay rather than
 * the remainder of the old one, so the return is exactly what it would have been without the drawer.
 * A close with nothing to return to (the map still following the boat) does nothing at all.
 *
 * The one exception is the centre the inspect mode has on loan: the frame its exit left is handed
 * back on that same delay, and a card still standing keeps the hold. Kept pure so the rule can be
 * tested without a map, a drawer or a clock.
 */
internal enum class PanResumeAction {
    /** The deadline stands down for as long as a drawer is open — the hold. */
    HOLD,

    /** Arm a fresh deadline from this moment; a full delay, never the remainder of the old one. */
    RESTART,

    /** Nothing to resume or to hold: there is no pan outstanding and no loaned frame. */
    NONE
}

/**
 * The action a drawer's open or close takes on the pan-resume timer.
 *
 * @param open true when a drawer has just opened — the Where-Am-I dashboard is one of the drawer
 *   states, so it holds the deadline exactly as the wizard, the settings page and the lists do.
 * @param autoFollowSuppressed true while the user owns the centre: a pan has happened and has not
 *   been recentred, so there is a boat to return to.
 * @param inspectLoaned true while the centre is on loan to the frame the inspect mode's exit left.
 * @param inspectArmed true while the inspect mode is armed.
 * @param inspectCardOpen true while the card the inspect mode opened is still on screen.
 * @param routeDraftArmed true while the route mode is **choosing its destination** — the phase's own
 *   hold on the camera, and never the following one (R21).
 */
internal fun panResumeOnDrawerChange(
    open: Boolean,
    autoFollowSuppressed: Boolean,
    inspectLoaned: Boolean,
    inspectArmed: Boolean,
    inspectCardOpen: Boolean,
    /** Defaults to no draft: a caller with no route mode in hand has nothing to hold. */
    routeDraftArmed: Boolean = false
): PanResumeAction = when {
    // The hold: whatever deadline was live is stood down, and the close is what re-arms it.
    open -> PanResumeAction.HOLD
    // The loaned frame is handed back on the ordinary delay, never in this frame: an immediate
    // recentre on the card's close would undo the very frame the mode was armed on. A card still
    // standing keeps the hold instead, so this reads NONE.
    inspectLoaned && !inspectCardOpen -> PanResumeAction.RESTART
    // A panned map outlives the drawer, and its delay restarts from the close. The two holds — the
    // inspect one (the armed half or the card it opened) and the route draft's — outrank the pan, so a
    // close inside either resumes nothing: the frame the user is aiming across must not be handed back
    // to the boat while the destination is still being placed.
    autoFollowSuppressed && !(inspectArmed || inspectCardOpen || routeDraftArmed) ->
        PanResumeAction.RESTART
    else -> PanResumeAction.NONE
}
