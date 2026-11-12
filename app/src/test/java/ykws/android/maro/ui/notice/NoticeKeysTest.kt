package ykws.android.maro.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeKeysTest {

    @Test
    fun everyKeyIsDistinctSoNoTwoSurfacesShareASlotByAccident() {
        assertEquals(NoticeKeys.all.size, NoticeKeys.all.toSet().size)
    }
}
