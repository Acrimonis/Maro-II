package ykws.android.maro.data.model

/** Actions emitted by [ListOverlayScaffold] to the host screen. */
sealed class ListAction {
    // ── Delete lifecycle ──────────────────────────────────────────────
    /** Item swiped left — scaffold added to internal pending set. */
    data class SoftDelete(val id: String, val title: String) : ListAction()
    /** Undo tapped in snackbar — scaffold removed from internal pending set. */
    data class UndoDelete(val id: String) : ListAction()
    /** Dismiss or snackbar swiped left — perform permanent deletion. */
    data class PermanentDelete(val id: String) : ListAction()

    // ── Item interaction ──────────────────────────────────────────────
    /** Item tapped — show details / open viewer. */
    data class SelectItem(val id: String) : ListAction()
    /** Edit action triggered — open wizard / editor. */
    data class EditItem(val id: String) : ListAction()
    /** Export GPX — share track file. */
    data class ExportGpx(val id: String) : ListAction()

    // ── Refresh ───────────────────────────────────────────────────────
    /** Re-sort and refresh the card list with the given sort state. */
    data class RefreshList(val sortState: ykws.android.maro.data.model.ListSortState) : ListAction()
    /** Invalidate and redraw the map overlay layer (polylines, markers). */
    data object RefreshLayer : ListAction()
}
