package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.temperature.SeaSource.COPERNICUS
import ykws.android.maro.data.temperature.SeaSource.ECMWF
import ykws.android.maro.data.temperature.SeaSource.MARINE
import java.util.Locale

/** The figures of the info column's temperature source line. */
class TemperatureSourcesLineTest {

    @Test
    fun eachSourceWithAValueHasItsFigureInOrderInTheDevicesDecimalMark() {
        assertEquals(
            listOf(MARINE to "17.4", ECMWF to "17.1", COPERNICUS to "16.8"),
            seaFigures(listOf(17.4, 17.1, 16.8), Locale.US)
        )
        assertEquals(listOf(MARINE to "17,4"), seaFigures(listOf(17.4), Locale.FRANCE))
    }

    @Test
    fun aSourceWithNoValueIsLeftOut() {
        assertEquals(
            listOf(MARINE to "17.4", COPERNICUS to "16.8"),
            seaFigures(listOf(17.4, null, 16.8), Locale.US)
        )
        assertEquals(listOf(ECMWF to "17.1"), seaFigures(listOf(null, 17.1), Locale.US))
        assertEquals(emptyList<Any>(), seaFigures(emptyList(), Locale.US))
    }

    @Test
    fun aFigureHasOneDecimalAtMostRoundedHalfUpAndAWholeDegreeNone() {
        assertEquals(
            listOf("17", "17.7", "17.3"),
            seaFigures(listOf(17.0, 17.66, 17.25), Locale.US).map { it.second }
        )
    }
}
