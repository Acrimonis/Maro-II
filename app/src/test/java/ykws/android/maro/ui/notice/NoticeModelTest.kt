package ykws.android.maro.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig

/** The factories tie each class to its dwell key. */
class NoticeModelTest {

    private val text = NoticeText.Resolved("x")

    @Test
    fun theExitPillStandsExactlyAsLongAsTheDoubleBackWindow() {
        val exit = Notice.exit("exit", text, danger = false)

        assertEquals(NoticeClass.EXIT, exit.cls)
        assertEquals(AppConfig.exitBackWindowMs, exit.dwellMs)
    }

    @Test
    fun onlyTheRecordingExitWearsTheDangerBorder() {
        assertTrue(Notice.exit("exit", text, danger = true).danger)
        assertFalse(Notice.exit("exit", text, danger = false).danger)
        assertFalse(Notice.confirm("c", text).danger)
    }

    @Test
    fun eachFactoryReadsItsOwnDwellKey() {
        assertEquals(AppConfig.noticeDwellShortMs, Notice.confirm("c", text).dwellMs)
        assertEquals(AppConfig.noticeDwellReadableMs, Notice.result("r", text).dwellMs)
        assertEquals(AppConfig.noticeDwellErrorMs, Notice.error("e", text).dwellMs)
        assertEquals(null, Notice.status("s", text).dwellMs)
    }
}
