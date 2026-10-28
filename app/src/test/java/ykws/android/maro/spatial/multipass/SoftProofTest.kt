package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * **The price walk's span proof reads only what can move its verdict, and answers as it always did.** The
 * comparison the walk made before [PullContext.softProves] — `min(field, berth)` finite and at least the
 * half-length, both sides read in full on a memo-less context — is the oracle. The two are compared over a
 * shoal behind the measured gate, at points from inside the shoal to two kilometres off it and half-lengths from
 * a group's to a kilometre chord's, under the shipped berth law and three more the clamps allow: an outer edge
 * inside the margin with an inner ramp, an outer edge past the far read's own 150 m, and a law whose near
 * reading's clearance itself passes 150 m (no margin, the outer edge at the clamp's 500 m) — the term of the
 * proof's ceiling a 150 m constant would miss.
 */
class SoftProofTest {
    private val shoalNorth = 1300.0
    private val shoalEast = 1900.0
    private val raster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
        if (hypot(n - shoalNorth, e - shoalEast) < 80.0) 2.0 else 15.0
    }
    private val depth = GateTestFixtures.depthOf(raster)
    private val discCentre = GateTestFixtures.at(1100.0, 1500.0)

    private val laws = listOf(
        "shipped" to BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5),
        "outer inside the margin" to BerthLaw(marginM = 60.0, outerM = 30.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5),
        "outer past the far reach" to BerthLaw(marginM = 20.0, outerM = 220.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5),
        "near clearance past 150 m" to BerthLaw(marginM = 0.0, outerM = 500.0, secPerM = 0.1),
        "no berth" to null
    )

    private val halfLengths = listOf(
        5.0, 12.5, 25.0, 31.0, 49.0, 62.5, 100.0, 125.0, 148.5, 149.0, 150.0, 151.0, 175.0, 200.0, 250.0, 500.0, 1000.0
    )

    @Test
    fun theProofAnswersTheFullComparisonEverywhere() {
        val random = java.util.Random(20261027L)
        val points = points(random)
        for ((lawName, law) in laws) {
            for ((fieldName, field) in fields()) {
                val setup = setup(field, law)
                val oracle = setup.context()
                // One memo across every point and every half-length, asked in a shuffled order, so a short far
                // read's "none" and a long one's are both held when a later question arrives.
                val ctx = setup.context(memo = MarkMemo())
                for (p in points) {
                    for (halfM in halfLengths.shuffled(random)) {
                        val clearance = min(oracle.field.priceClearanceM(p), oracle.berthClearanceM(p))
                        val expected = clearance < Double.MAX_VALUE && clearance >= halfM
                        assertEquals("$lawName / $fieldName at $p, half $halfM", expected, ctx.softProves(p, halfM))
                    }
                }
            }
        }
    }

    /** A walk asks each midpoint one half-length; the proof pays fewer edge reads than the full comparison did. */
    @Test
    fun theProofPaysFewerEdgeReads() {
        val random = java.util.Random(20261028L)
        val asked = points(random).map { it to halfLengths[random.nextInt(halfLengths.size)] }
        for ((lawName, law) in laws.filter { it.second != null }) {
            val setup = setup(fields().first().second, law)
            val oldMemo = MarkMemo()
            val old = setup.context(memo = oldMemo)
            val newMemo = MarkMemo()
            val new = setup.context(memo = newMemo)
            for ((p, halfM) in asked) {
                val clearance = min(old.field.priceClearanceM(p), old.berthClearanceM(p))
                assertEquals(clearance < Double.MAX_VALUE && clearance >= halfM, new.softProves(p, halfM))
            }
            val oldReads = oldMemo.edgeMisses + oldMemo.farEdgeMisses
            val newReads = newMemo.edgeMisses + newMemo.farEdgeMisses
            assertTrue("$lawName: fewer edge reads ($newReads against $oldReads)", newReads < oldReads)
        }
    }

    /**
     * Points over the whole raster, and as many again within 300 m of the shoal's rim — where the near read,
     * the ramp and the far read's short reach all live.
     */
    private fun points(random: java.util.Random): List<LatLng> {
        val spread = List(600) { GateTestFixtures.at(random.nextDouble() * 2600.0, random.nextDouble() * 3800.0) }
        val near = List(600) {
            val angle = random.nextDouble() * 2.0 * Math.PI
            val r = random.nextDouble() * 380.0
            GateTestFixtures.at(shoalNorth + r * sin(angle), shoalEast + r * cos(angle))
        }
        return spread + near
    }

    /** A slow disc whose rim names its boundary, and a source that names none (`MAX_VALUE`, never proved alone). */
    private fun fields(): List<Pair<String, RouteCostField>> {
        val gateSource = depthGateSource(3.0) { depth(it.latitude, it.longitude) }
        fun discPrice(p: LatLng) = if (SpatialOperations.haversine(p, discCentre) <= 400.0) 0.3 else 0.0
        return listOf(
            "disc" to RouteCostField(listOf(
                gateSource,
                RouteCostSource.Soft(
                    priceSec = ::discPrice, tag = MultipassCellState.BAND,
                    clearanceAt = { abs(SpatialOperations.haversine(it, discCentre) - 400.0) }
                )
            )),
            "silent" to RouteCostField(listOf(
                gateSource,
                RouteCostSource.Soft(priceSec = ::discPrice, tag = MultipassCellState.BAND)
            ))
        )
    }

    private fun setup(field: RouteCostField, law: BerthLaw?) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0, field = field,
        start = GateTestFixtures.at(10.0, 10.0), aim = GateTestFixtures.at(2590.0, 3790.0),
        gate = GateTestFixtures.gateOver(
            raster, hardM = law?.floorM ?: 20.0, preferredM = law?.marginM ?: 20.0, berthOuterM = law?.outerM ?: 0.0
        ),
        berth = law, berthLambda = if (law != null) 3.0 else 0.0, paceMps = 15.0
    )
}
