package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.temperature.TemperatureProblem
import ykws.android.maro.data.temperature.TemperatureRefresh
import ykws.android.maro.data.temperature.TemperatureSource
import ykws.android.maro.ui.notice.NoticeClass
import ykws.android.maro.ui.notice.NoticeKeys
import ykws.android.maro.ui.notice.NoticeText
import java.util.Locale

/** How the temperature tile's and the speed history's outcomes reach the notice host (step 2). */
class TemperatureNoticeTest {

    @Test
    fun aRefreshIsAShortConfirmationUnderTheTilesKey() {
        val notice = TemperatureNotice.Refreshing.asNotice(problemText = { error("no problem") }, refreshText = { error("no answers") })

        assertEquals(NoticeKeys.TEMPERATURE, notice.key)
        assertEquals(NoticeClass.CONFIRM, notice.cls)
        assertEquals(AppConfig.noticeDwellShortMs, notice.dwellMs)
        assertEquals(NoticeText.Res(R.string.temperature_refresh_toast), notice.text)
    }

    @Test
    fun aProblemIsAnErrorThatStaysLongEnoughToReadUnderTheSameKey() {
        val problem = TemperatureProblem(TemperatureSource.WATER, 429, "Too many requests")

        val notice = TemperatureNotice.Problem(problem).asNotice(problemText = { "line for ${it.code}" }, refreshText = { error("no answers") })

        assertEquals(NoticeKeys.TEMPERATURE, notice.key)
        assertEquals(NoticeClass.ERROR, notice.cls)
        assertEquals(AppConfig.noticeDwellErrorMs, notice.dwellMs)
        assertEquals(NoticeText.Resolved("line for 429"), notice.text)
    }

    @Test
    fun aRefreshsAnswersAreAResultToReadUnderTheSameKey() {
        val refresh = TemperatureRefresh(17.3, listOf(17.0, 15.5, null))

        val notice = TemperatureNotice.Refreshed(refresh).asNotice(
            problemText = { error("no problem") },
            refreshText = { "answers ${it.waterC?.size}" }
        )

        assertEquals(NoticeKeys.TEMPERATURE, notice.key)
        assertEquals(NoticeClass.RESULT, notice.cls)
        assertEquals(AppConfig.noticeDwellReadableMs, notice.dwellMs)
        assertEquals(NoticeText.Resolved("answers 3"), notice.text)
    }

    @Test
    fun aRefreshNumberHasOneDecimalAtMostAndTheDevicesDecimalMark() {
        assertEquals("17", refreshDegrees(17.0, Locale.US))
        assertEquals("15.5", refreshDegrees(15.5, Locale.US))
        assertEquals("17.7", refreshDegrees(17.66, Locale.US))
        assertEquals("17.3", refreshDegrees(17.25, Locale.US))
        assertEquals("15,5", refreshDegrees(15.5, Locale.FRANCE))
        assertEquals(null, refreshDegrees(null, Locale.US))
    }

    @Test
    fun calculateWithTripsSaysHowManyAndWithNoneSaysTheHistoryStood() {
        val some = speedHistoryNotice(trips = 3)
        val none = speedHistoryNotice(trips = 0)

        assertEquals(NoticeText.Plural(R.plurals.settings_speed_history_calculated, 3), some.text)
        assertEquals(NoticeText.Res(R.string.settings_speed_history_none_eligible), none.text)
        assertEquals(NoticeClass.RESULT, some.cls)
        assertEquals(NoticeKeys.SPEED_HISTORY, none.key)
    }
}
