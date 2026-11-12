package ykws.android.maro.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notice lane's rules (`xTrack/Ui_General/261111_FEAT_PLN_Ui_General_notice-host.md` D3 and D4). */
class NoticeArbiterTest {

    private fun text(s: String) = NoticeText.Resolved(s)
    private fun confirm(key: String, dwell: Long = 2_000L) =
        Notice(key, text(key), NoticeClass.CONFIRM, dwell)
    private fun result(key: String) = Notice(key, text(key), NoticeClass.RESULT, 6_000L)
    private fun error(key: String) = Notice(key, text(key), NoticeClass.ERROR, 6_000L)
    private fun exit(key: String = "exit") = Notice(key, text(key), NoticeClass.EXIT, 2_000L)
    private fun status(key: String) = Notice(key, text(key), NoticeClass.STATUS, null)

    private fun NoticeBoard.post(n: Notice, now: Long, max: Int = 2) = NoticeArbiter.post(this, n, now, max)
    private fun NoticeBoard.keys() = visible.map { it.notice.key }
    private fun NoticeBoard.waiting() = queued.map { it.notice.key }

    @Test
    fun aFreeSlotShowsAtOnceAndTheNewestSitsOnTheEdge() {
        val board = NoticeBoard().post(confirm("a"), 0).post(confirm("b"), 10)

        assertEquals(listOf("a", "b"), board.keys())
        assertTrue(board.waiting().isEmpty())
    }

    @Test
    fun aKeyShowingIsReplacedInPlaceAndItsDwellRestarts() {
        val board = NoticeBoard().post(confirm("a"), 0).post(confirm("b"), 10).post(confirm("a"), 1_500)

        assertEquals(listOf("a", "b"), board.keys())
        assertEquals(1_500L, board.visible.first().shownAtMs)
    }

    @Test
    fun aKeyWaitingIsReplacedInPlaceNotQueuedTwice() {
        val full = NoticeBoard().post(result("a"), 0).post(result("b"), 0)
        val board = full.post(confirm("c"), 10).post(confirm("c"), 20)

        assertEquals(listOf("c"), board.waiting())
    }

    @Test
    fun aFullLaneQueuesALowerOrEqualClassBehindTheStandingOnes() {
        val board = NoticeBoard().post(result("a"), 0).post(result("b"), 0).post(result("c"), 5)

        assertEquals(listOf("a", "b"), board.keys())
        assertEquals(listOf("c"), board.waiting())
    }

    @Test
    fun aHigherClassDisplacesTheLowestStandingOneToTheQueuesHead() {
        val board = NoticeBoard().post(confirm("a"), 0).post(result("b"), 0).post(error("e"), 100)

        assertEquals(listOf("b", "e"), board.keys())
        assertEquals(listOf("a"), board.waiting())
    }

    @Test
    fun exitPreEmptsAFullLaneAndNeverQueues() {
        val board = NoticeBoard().post(error("e1"), 0).post(error("e2"), 0).post(exit(), 50)

        assertTrue("exit stands", "exit" in board.keys())
        assertTrue("exit never waits", "exit" !in board.waiting())
        assertEquals(1, board.waiting().size)
    }

    @Test
    fun theQueueIsOrderedByClassThenByArrival() {
        val full = NoticeBoard().post(error("e1"), 0).post(error("e2"), 0)
        val board = full.post(confirm("c1"), 1).post(result("r1"), 2).post(confirm("c2"), 3).post(result("r2"), 4)

        assertEquals(listOf("r1", "r2", "c1", "c2"), board.waiting())
    }

    @Test
    fun anExpiredDwellFreesItsSlotForTheQueueHead() {
        val short = Notice("a", text("a"), NoticeClass.RESULT, 2_000L)
        val board = NoticeBoard().post(short, 0).post(result("b"), 0).post(result("c"), 100)

        val after = NoticeArbiter.expire(board, 2_000, 2)

        assertEquals(listOf("b", "c"), after.keys())
        assertTrue(after.waiting().isEmpty())
        assertEquals(2_000L, after.visible.last().shownAtMs)
    }

    @Test
    fun aStatusNeverExpires() {
        val board = NoticeBoard().post(status("op"), 0)

        assertNull(NoticeArbiter.nextExpiry(board))
        assertEquals(listOf("op"), NoticeArbiter.expire(board, 10_000_000, 2).keys())
    }

    @Test
    fun clearingAStatusFillsItsSlotFromTheQueue() {
        val board = NoticeBoard().post(status("op"), 0).post(status("arrived"), 0).post(result("r"), 10)

        val after = NoticeArbiter.clear(board, "op", 20, 2)

        assertEquals(listOf("arrived", "r"), after.keys())
    }

    @Test
    fun clearingAWaitingKeyRemovesItFromTheQueue() {
        val board = NoticeBoard().post(result("a"), 0).post(result("b"), 0).post(result("c"), 1)

        assertTrue(NoticeArbiter.clear(board, "c", 2, 2).waiting().isEmpty())
    }

    @Test
    fun aConfirmationThatWaitedPastItsDwellIsDroppedNotShownLate() {
        val board = NoticeBoard().post(result("a"), 0).post(result("b"), 0).post(confirm("c"), 100)

        val after = NoticeArbiter.expire(board, 6_000, 2)

        assertTrue("a stale confirmation is not shown", "c" !in after.keys())
        assertTrue("and does not keep waiting", after.waiting().isEmpty())
    }

    @Test
    fun aResultThatWaitedIsStillShown() {
        val board = NoticeBoard().post(result("a"), 0).post(result("b"), 0).post(result("c"), 100)

        val after = NoticeArbiter.expire(board, 6_000, 2)

        assertEquals(listOf("c"), after.keys().filter { it == "c" })
    }

    @Test
    fun whenTwoStandingNoticesAreTheLowestClassTheOldestIsDisplaced() {
        val board = NoticeBoard().post(confirm("a"), 0).post(confirm("b"), 10).post(error("e"), 20)

        assertEquals(listOf("b", "e"), board.keys())
        assertEquals(listOf("a"), board.waiting())
    }

    @Test
    fun anErrorOverAFullLaneOfResultsDisplacesTheOldestResultAndItReturnsWhenTheErrorGoes() {
        val full = NoticeBoard().post(result("r1"), 0).post(result("r2"), 5).post(error("e"), 10)

        assertEquals(listOf("r2", "e"), full.keys())
        assertEquals(listOf("r1"), full.waiting())
        assertEquals(listOf("r2", "r1"), NoticeArbiter.clear(full, "e", 20, 2).keys())
    }

    @Test
    fun aDisplacedStatusWaitsStillPostedAndComesBackWhenASlotFrees() {
        val board = NoticeBoard().post(status("s1"), 0).post(status("s2"), 5).post(error("e"), 10)

        assertEquals(listOf("s2", "e"), board.keys())
        assertEquals(listOf("s1"), board.waiting())
        assertEquals(listOf("s2", "s1"), NoticeArbiter.clear(board, "e", 20, 2).keys())
    }

    @Test
    fun aProblemPostedUnderTheKeyOfAStandingRefreshReplacesItInPlace() {
        val board = NoticeBoard()
            .post(confirm("temperature"), 0)
            .post(error("temperature"), 500)

        assertEquals(listOf("temperature"), board.keys())
        assertEquals(NoticeClass.ERROR, board.visible.single().notice.cls)
        assertEquals(500L, board.visible.single().shownAtMs)
    }

    @Test
    fun theNextExpiryIsTheEarliestStandingDwell() {
        val board = NoticeBoard().post(result("a"), 1_000).post(confirm("b"), 500)

        assertEquals(2_500L, NoticeArbiter.nextExpiry(board))
    }
}
