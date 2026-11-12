package ykws.android.maro.ui.notice

/**
 * The notice lane's rules, as pure reducers over a [NoticeBoard] with the clock and the slot count handed in —
 * no Compose, no Android, so every rule is a unit test.
 *
 * - **A key coalesces.** A post under a key already showing replaces it in place and restarts its dwell; under
 *   a key waiting, it replaces the waiting one and is ranked afresh by its new class, as any new post is.
 * - **A free slot shows at once**, appended on the band's edge.
 * - **A full lane ranks.** A notice that outranks the lowest standing one displaces it to the queue's head
 *   (so [NoticeClass.EXIT], the top class, always finds room unless only EXITs stand); otherwise it waits,
 *   first-come within its class and behind every higher class.
 * - **A status never times out.** It stands until [clear]; a displaced status waits, still posted.
 * - **A waiting notice is shown when its turn comes, for its full dwell** — a confirmation too, late rather
 *   than never: the lock line is the user's only lock feedback, and a save confirmation that lapsed unseen
 *   leaves them unsure it happened.
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
        // A waiting entry under the key is dropped and the post ranked as a new one, so a replacement of
        // another class lands where its class belongs rather than where the old notice stood.
        val queued = board.queued.filterNot { it.notice.key == notice.key }
        if (board.visible.size < maxVisible) {
            return NoticeBoard(board.visible + ShownNotice(notice, nowMs), queued)
        }
        val victim = board.visible
            .filter { it.notice.cls.ordinal > notice.cls.ordinal }
            .maxWithOrNull(compareBy({ it.notice.cls.ordinal }, { -it.shownAtMs }))
        if (victim != null) {
            return NoticeBoard(
                visible = board.visible - victim + ShownNotice(notice, nowMs),
                queued = listOf(QueuedNotice(victim.notice)) + queued
            )
        }
        val at = queued.indexOfFirst { it.notice.cls.ordinal > notice.cls.ordinal }
            .let { if (it < 0) queued.size else it }
        return NoticeBoard(
            visible = board.visible,
            queued = queued.toMutableList().also { it.add(at, QueuedNotice(notice)) }
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
     * Restarts the dwell, at [nowMs], of the standing notices under [keys]: ones posted before any host
     * existed (a startup diagnostic) must stand for their full dwell once there is a host to draw them,
     * not run it down unseen. Waiting notices have no dwell running and are left as they are.
     */
    fun restamp(board: NoticeBoard, keys: Set<String>, nowMs: Long): NoticeBoard =
        board.copy(
            visible = board.visible.map { if (it.notice.key in keys) it.copy(shownAtMs = nowMs) else it }
        )

    /** The instant the earliest standing dwell runs out, or null while only statuses (or nothing) stand. */
    fun nextExpiry(board: NoticeBoard): Long? =
        board.visible.mapNotNull { shown -> shown.notice.dwellMs?.let { shown.shownAtMs + it } }.minOrNull()

    private fun ShownNotice.expiredAt(nowMs: Long): Boolean =
        notice.dwellMs?.let { nowMs - shownAtMs >= it } ?: false

    private fun promote(board: NoticeBoard, nowMs: Long, maxVisible: Int): NoticeBoard {
        val free = (maxVisible - board.visible.size).coerceAtLeast(0)
        if (free == 0 || board.queued.isEmpty()) return board
        val next = board.queued.take(free).map { ShownNotice(it.notice, nowMs) }
        return NoticeBoard(board.visible + next, board.queued.drop(free))
    }
}
