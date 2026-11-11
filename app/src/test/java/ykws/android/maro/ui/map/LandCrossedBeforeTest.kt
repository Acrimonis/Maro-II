package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The land check on the way to a zone ahead ([landCrossedBefore]). */
class LandCrossedBeforeTest {

    @Test
    fun `open water all the way is not crossed`() {
        assertFalse(landCrossedBefore(100.0, 10.0) { false })
    }

    @Test
    fun `land mid-way is crossed`() {
        assertTrue(landCrossedBefore(100.0, 10.0) { d -> d in 40.0..60.0 })
    }

    @Test
    fun `the walk stops at the first land answer`() {
        val asked = mutableListOf<Double>()
        landCrossedBefore(100.0, 10.0) { d -> asked += d; d >= 30.0 }

        assertEquals(listOf(10.0, 20.0, 30.0), asked)
    }

    @Test
    fun `the boundary point itself is not asked`() {
        val asked = mutableListOf<Double>()
        landCrossedBefore(30.0, 10.0) { d -> asked += d; false }

        assertEquals(listOf(10.0, 20.0), asked)
    }

    @Test
    fun `a zone nearer than one step has nothing to cross`() {
        assertFalse(landCrossedBefore(8.0, 10.0) { true })
    }

    @Test
    fun `a spit narrower than the step can slip through`() {
        assertFalse(landCrossedBefore(100.0, 10.0) { d -> d in 41.0..46.0 })
    }
}
