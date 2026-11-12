package ykws.android.maro.ui.notice

/**
 * The notice lane's rules, as pure reducers over a [NoticeBoard] with the clock and the slot count handed in —
 * no Compose, no Android, so every rule is a unit test.
 *
 * - **A key coalesces.** A post under a key already showing replaces it in place and restarts its dwell; under
 *   a key waiting, it replaces the waiting one in place.
 * - **A free slot shows at once**, appended on the band's edge.
 * - **A full lane ranks.** A notice that outranks the lowest standing one displaces it to the queue's head
 *   (so [NoticeClass.EXIT], the top class, always finds room unless only EXITs stand); otherwise it waits,
 *   first-come within its class and behind every higher class.
 * - **A status never times out.** It stands until [clear]; a displaced status waits, still posted.
 * - **A waiting confirmation goes stale.** One that has waited longer than its own dwell is dropped on promotion
 *   rather than shown late; errors and results wait as long as it takes.
 *
 * The action rows are a separate lane and never reach here: a pill can neither delay nor displace a row.
 */
internal object NoticeArbiter {

    fun post(board: NoticeBoard, notice: Notice, nowMs: Long, maxVisible: Int): NoticeBoard {
        val showing = board.visible.indexOfFirst { it.notice.key == notice.key }
        if (showing >= 0) {
            return board.copy(
                visible = board.visible.toMutableList().also { it[showing] = ShownNotice(notice, nowMs) }
            )
        }
        val waiting = board.queued.indexOfFirst { it.notice.key == notice.key }
        if (waiting >= 0) {
            return board.copy(
                queued = board.queued.toMutableList().also { it[waiting] = QueuedNotice(notice, nowMs) }
            )
        }
        if (board.visible.size < maxVisible) {
            return board.copy(visible = board.visible + ShownNotice(notice, nowMs))
        }
        val victim = board.visible
            .filter { it.notice.cls.ordinal > notice.cls.ordinal }
            .maxWithOrNull(compareBy({ it.notice.cls.ordinal }, { -it.shownAtMs }))
        if (victim != null) {
            return NoticeBoard(
                visible = board.visible - victim + ShownNotice(notice, nowMs),
                queued = listOf(QueuedNotice(victim.notice, nowMs)) + board.queued
            )
        }
        val at = board.queued.indexOfFirst { it.notice.cls.ordinal > notice.cls.ordinal }
            .let { if (it < 0) board.queued.size else it }
        return board.copy(
            queued = board.queued.toMutableList().also { it.add(at, QueuedNotice(notice, nowMs)) }
        )
    }

    /** Removes the notice under [key], showing or waiting, and fills the slot it frees. */
    fun clear(board: NoticeBoard, key: String, nowMs: Long, maxVisible: Int): NoticeBoard =
        promote(
            NoticeBoard(
                visible = board.visible.filterNot { it.notice.key == key },
                queued = board.queued.filterNot { it.notice.key == key }
            ),
            nowMs,
            maxVisible
        )

    /** Removes every standing notice whose dwell has run out, then fills the slots they free. */
    fun expire(board: NoticeBoard, nowMs: Long, maxVisible: Int): NoticeBoard =
        promote(
            board.copy(visible = board.visible.filterNot { it.expiredAt(nowMs) }),
            nowMs,
            maxVisible
        )

    /**
     * Restarts every dwell at [nowMs]: a notice posted before any host existed (a startup diagnostic) must
     * stand for its full dwell once there is a host to draw it, not run it down unseen.
     */
    fun restamp(board: NoticeBoard, nowMs: Long): NoticeBoard =
        NoticeBoard(
            visible = board.visible.map { it.copy(shownAtMs = nowMs) },
            queued = board.queued.map { it.copy(postedAtMs = nowMs) }
        )

    /** The instant the earliest standing dwell runs out, or null while only statuses (or nothing) stand. */
    fun nextExpiry(board: NoticeBoard): Long? =
        board.visible.mapNotNull { shown -> shown.notice.dwellMs?.let { shown.shownAtMs + it } }.minOrNull()

    private fun ShownNotice.expiredAt(nowMs: Long): Boolean =
        notice.dwellMs?.let { nowMs - shownAtMs >= it } ?: false

    private fun promote(board: NoticeBoard, nowMs: Long, maxVisible: Int): NoticeBoard {
        var visible = board.visible
        val waiting = board.queued.toMutableList()
        while (visible.size < maxVisible && waiting.isNotEmpty()) {
            val next = waiting.removeAt(0)
            val stale = next.notice.cls == NoticeClass.CONFIRM &&
                next.notice.dwellMs?.let { nowMs - next.postedAtMs > it } == true
            if (!stale) visible = visible + ShownNotice(next.notice, nowMs)
        }
        return NoticeBoard(visible, waiting)
    }
}
