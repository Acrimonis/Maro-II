package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [DepthSample.gatedForEmodnetShallow] — the runtime EMODnet shallow-water gate
 * that backs the configurable "EMODnet shallow filter" setting.
 */
class DepthSampleGateTest {

    private fun emodnet(depthM: Float) = DepthSample(depthM, DepthSource.EMODNET, 60, true)

    @Test
    fun `gates EMODnet shallower than cutoff to no-data`() {
        val gated = emodnet(1.2f).gatedForEmodnetShallow(2.0f)
        assertFalse(gated.hasData)
        assertEquals(DepthSource.NONE, gated.source)
    }

    @Test
    fun `gates the negative above-datum EMODnet artefact too`() {
        // The Cap-d'Antibes −1.7 m reading: above datum, must not surface as a depth.
        val gated = emodnet(-1.7f).gatedForEmodnetShallow(2.0f)
        assertFalse(gated.hasData)
    }

    @Test
    fun `keeps EMODnet at or below the cutoff depth`() {
        val atCutoff = emodnet(2.0f)
        assertSame(atCutoff, atCutoff.gatedForEmodnetShallow(2.0f)) // 2.0 is NOT < 2.0
        val deep = emodnet(8f)
        assertSame(deep, deep.gatedForEmodnetShallow(2.0f))
    }

    @Test
    fun `does not gate fine sources`() {
        val litto = DepthSample(0.5f, DepthSource.LITTO3D, 90, true)
        assertSame(litto, litto.gatedForEmodnetShallow(2.0f))
    }

    @Test
    fun `cutoff of zero disables the gate`() {
        val s = emodnet(0f)
        assertSame(s, s.gatedForEmodnetShallow(0f)) // 0 is NOT < 0
    }

    @Test
    fun `no-data sample passes through unchanged`() {
        assertSame(DepthSample.NONE, DepthSample.NONE.gatedForEmodnetShallow(2.0f))
    }
}
