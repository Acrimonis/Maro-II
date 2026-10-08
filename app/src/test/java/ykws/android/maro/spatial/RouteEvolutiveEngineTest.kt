package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Properties
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.UniformGridPlan
import ykws.android.maro.spatial.multipass.clockSampleM

/**
 * `evolutive`'s own semantics: the two sizes its plan answers and the precision the shipped file holds.
 * The adaptive grid itself is the grid plan's to build; this pins the **metres** the walk and the clock
 * read today, so the device pass measures the grid rather than a sizing mistake.
 */
class RouteEvolutiveEngineTest {

    /** The shipped `maro.properties`: the `app` module's CWD by default, `maro.repoDir` honoured first. */
    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    /**
     * The precision is a **fact stated in metres, never a ratio**: the shipped file carries 20 m, which is
     * the contract's own ceiling, the fine cell never exceeds the coarse one, and the coarse cell divides it
     * exactly — the nesting the two-layer grid will rest on.
     */
    @Test
    fun theShippedFineCellHoldsTheTwentyMetrePrecision() {
        val shipped = shippedProperties()
        val coarseM = shipped.getProperty("route.evolutive.grid.cellM")!!.trim().toDouble()
        val fineM = shipped.getProperty("route.evolutive.grid.fineCellM")!!.trim().toDouble()

        assertEquals("the 20 m contract is the clamp's own ceiling", 20.0, fineM, 1e-9)
        assertTrue("a fine cell is never coarser than the walk it refines", fineM <= coarseM)
        assertEquals("and the coarse cell divides it exactly", 0.0, coarseM % fineM, 1e-9)
    }

    /**
     * The plan answers the sizes the walk actually runs at, and the clock's step follows the fine one, so
     * no reader derives a size of its own.
     */
    @Test
    fun theEvolutivePlanAnswersTheShippedSizes() {
        val corridor = BBox(43.49, 43.51, 6.99, 7.01)
        val coarseM = EvolutiveGridPlan.firstWalkGrid(corridor, AppConfig.routeAvoidGridCellM).first().cellM
        val fineM = EvolutiveGridPlan.fineCellM(coarseM)

        assertEquals(
            "the first walk's cell is the engine's own key",
            AppConfig.routeEvolutiveGridCellM,
            coarseM,
            1e-9
        )
        assertEquals(
            "and the plan's fine cell is its metres twin",
            AppConfig.routeEvolutiveGridFineCellM,
            fineM,
            1e-9
        )
        assertEquals(
            "the clock steps at half the finest cell walked",
            AppConfig.routeEvolutiveGridFineCellM / 2.0,
            clockSampleM(coarseM, fineM),
            1e-9
        )
    }

    /**
     * `avoid`'s own fine cell is untouched: the shipped plan reads its own metres key, so nothing this
     * engine's keys say can move `avoid`'s answer.
     */
    @Test
    fun theUniformPlanReadsItsOwnMetresFineCell() {
        assertEquals(
            "the plan's fine cell is `avoid`'s own metres key, whatever the base cell",
            AppConfig.routeAvoidGridFineCellM,
            UniformGridPlan.fineCellM(AppConfig.routeAvoidGridCellM),
            1e-9
        )
    }
}
