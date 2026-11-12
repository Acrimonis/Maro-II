package ykws.android.maro.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The center applies the arbiter's reducers with its own clock and publishes the board. */
class NoticeCenterTest {

    private var now = 0L
    private val center = NoticeCenter(clock = { now }, maxVisible = { 2 })

    private fun keys() = center.board.value.visible.map { it.notice.key }

    @Test
    fun postThenClearLeavesAnEmptyBoard() {
        center.post(Notice.status("op", NoticeText.Resolved("Importing")))
        assertEquals(listOf("op"), keys())

        center.clear("op")

        assertTrue(center.board.value.visible.isEmpty())
    }

    @Test
    fun setStatusPostsAStatusAndANullClearsIt() {
        center.setStatus("op", "Exporting 3 / 10")
        center.setStatus("op", "Exporting 4 / 10")

        assertEquals(1, center.board.value.visible.size)
        assertEquals(NoticeText.Resolved("Exporting 4 / 10"), center.board.value.visible.single().notice.text)

        center.setStatus("op", null)

        assertTrue(center.board.value.visible.isEmpty())
    }

    @Test
    fun theFirstHostRestartsTheDwellOfANoticePostedBeforeIt() {
        center.post(Notice(key = "boot", text = NoticeText.Resolved("Bad key"), cls = NoticeClass.ERROR, dwellMs = 6_000L))

        now = 5_000L
        center.hostAttached()

        assertEquals(5_000L, center.board.value.visible.single().shownAtMs)
        now = 10_999L
        center.expire()
        assertEquals(listOf("boot"), keys())
    }

    @Test
    fun aLaterHostAttachChangesNothing() {
        center.hostAttached()
        center.post(Notice(key = "c", text = NoticeText.Resolved("Saved"), cls = NoticeClass.CONFIRM, dwellMs = 2_000L))

        now = 1_000L
        center.hostAttached()

        assertEquals(0L, center.board.value.visible.single().shownAtMs)
    }

    @Test
    fun expireUsesTheCentersOwnClock() {
        center.post(Notice(key = "c", text = NoticeText.Resolved("Saved"), cls = NoticeClass.CONFIRM, dwellMs = 2_000L))

        now = 1_999L
        center.expire()
        assertEquals(listOf("c"), keys())

        now = 2_000L
        center.expire()
        assertTrue(keys().isEmpty())
    }
}
