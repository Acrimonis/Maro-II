package ykws.android.maro.data.temperature

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

@OptIn(ExperimentalCoroutinesApi::class)
class TemperatureEngineTest {

    private val originLat = 43.5696
    private val originLon = 7.1313
    private val hour = 3_600_000L

    private fun lat(yM: Double) = originLat + yM / 111_320.0
    private fun lon(xM: Double) = originLon + xM / (111_320.0 * cos(Math.toRadians(originLat)))
    private fun xOf(longitude: Double) = (longitude - originLon) * 111_320.0 * cos(Math.toRadians(originLat))

    private class Call(val source: TemperatureSource, val latitude: Double, val longitude: Double)

    private class FakeFetcher : TemperatureFetcher {
        val calls = mutableListOf<Call>()
        var air: FetchResult = FetchResult.Value(18.4)

        /** When set, a request waits here before it answers, so a test can move the marker mid-request. */
        var gate: CompletableDeferred<Unit>? = null
        var water: MutableList<FetchResult> = mutableListOf(FetchResult.Value(19.6))
        override suspend fun fetch(source: TemperatureSource, latitude: Double, longitude: Double): FetchResult {
            calls += Call(source, latitude, longitude)
            val answer = when (source) {
                TemperatureSource.AIR -> air
                TemperatureSource.WATER -> if (water.size > 1) water.removeAt(0) else water.first()
            }
            gate?.await() // the answer is fixed at the call, so a test can tell an old request from a new one
            return answer
        }
        fun count(source: TemperatureSource) = calls.count { it.source == source }
    }

    private class Rig(
        val engine: TemperatureEngine,
        val fetcher: FakeFetcher,
        val cache: TemperatureCache,
        val online: BooleanArray
    )

    /** Water lies east of x = [waterFromXM] metres; everything west of it is land. */
    private fun TestScope.rig(
        waterFromXM: Double = Double.NEGATIVE_INFINITY,
        timing: TemperatureTiming = TemperatureTiming(),
        onProblem: (TemperatureProblem) -> Unit = {},
        waterAt: (Double) -> Boolean = { x -> x > waterFromXM }
    ): Rig {
        val fetcher = FakeFetcher()
        val cache = TemperatureCache { source ->
            if (source == TemperatureSource.AIR) hour else 12 * hour
        }
        val online = booleanArrayOf(true)
        val engine = TemperatureEngine(
            scope = backgroundScope,
            fetcher = fetcher,
            airGrid = TemperatureGrid(TemperatureSource.AIR, originLat, originLon, 1_000.0),
            waterGrid = TemperatureGrid(TemperatureSource.WATER, originLat, originLon, 8_000.0),
            cache = cache,
            isWater = { _, longitude -> waterAt(xOf(longitude)) },
            isOnline = { online[0] },
            clock = { 1_000_000L + testScheduler.currentTime },
            onProblem = onProblem,
            timing = timing
        )
        return Rig(engine, fetcher, cache, online)
    }

    private fun TestScope.settle(ms: Long = 800L) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `a settled marker on land fetches air once and shows it`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(0, rig.fetcher.count(TemperatureSource.WATER))
        assertEquals(TemperatureReading(18, false), rig.engine.state.value.air)
        assertNull(rig.engine.state.value.water)
    }

    @Test
    fun `nothing is fetched while the feature is off`() = runTest {
        val rig = rig()
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle(120_000L)

        assertTrue(rig.fetcher.calls.isEmpty())
    }

    @Test
    fun `a pan keeps the settle timer running until the marker stops`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle(500L)
        rig.engine.onPosition(lat(150.0), lon(150.0))
        settle(500L)
        assertTrue("still panning", rig.fetcher.calls.isEmpty())

        settle(500L)
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a nearby cached value is reused without a request`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        rig.engine.onPosition(lat(150.0), lon(120.0))
        settle(70_000L)

        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a new nearest point is fetched as soon as the marker settles`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        // Four kilometres on: a different air point. The old value shows subdued while the marker is still
        // moving, then the new point is asked as soon as it stops.
        rig.engine.onPosition(lat(4_000.0), lon(4_000.0))
        assertEquals(TemperatureReading(18, true), rig.engine.state.value.air)
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        settle()
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(TemperatureReading(18, false), rig.engine.state.value.air)
    }

    @Test
    fun `an outdated value is asked again at the next settle, nothing is held back`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        // Value outdated, marker nudged within the same cell: asked at the settle, and only once.
        rig.cache.invalidateAll()
        rig.engine.onPosition(lat(110.0), lon(110.0))
        settle()
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))

        settle(120_000L)
        assertEquals("a fresh value ends the asking", 2, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a stationary marker refreshes air when its value expires`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        settle(hour + 120_000L)
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `on water the nearest on-water point is fetched and shown normally`() = runTest {
        val rig = rig(waterFromXM = -50_000.0)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(1, rig.fetcher.count(TemperatureSource.WATER))
        assertEquals(TemperatureReading(20, false), rig.engine.state.value.water)
    }

    @Test
    fun `when the nearest water point is on land the closest on-water one answers, not subdued`() = runTest {
        // Land west of x = 1000 m; the marker at x = 2000 m is on water, but the nearest 8 km point
        // (the origin) is on land.
        val rig = rig(waterFromXM = 1_000.0)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(0.0), lon(2_000.0))
        settle()

        val waterCall = rig.fetcher.calls.single { it.source == TemperatureSource.WATER }
        assertTrue("fetched on water, not at the origin", xOf(waterCall.longitude) > 1_000.0)
        assertEquals(TemperatureReading(20, false), rig.engine.state.value.water)
    }

    @Test
    fun `an empty sea answer moves on to the next point and stops at the first value`() = runTest {
        val rig = rig(waterFromXM = -50_000.0)
        rig.fetcher.water = mutableListOf(FetchResult.Empty, FetchResult.Empty, FetchResult.Value(21.2))
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(3, rig.fetcher.count(TemperatureSource.WATER))
        assertEquals(TemperatureReading(21, false), rig.engine.state.value.water)
    }

    @Test
    fun `an empty sea answer is remembered, so a burst is not repeated`() = runTest {
        val rig = rig(waterFromXM = -50_000.0)
        rig.fetcher.water = mutableListOf(FetchResult.Empty, FetchResult.Value(21.2))
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(2, rig.fetcher.count(TemperatureSource.WATER))

        // Nothing new is due, and the empty answer is remembered, so no further request goes out.
        settle(30_000L)
        assertEquals(2, rig.fetcher.count(TemperatureSource.WATER))
    }

    @Test
    fun `when every tried point is empty the water view shows a dash`() = runTest {
        val rig = rig(
            waterFromXM = -50_000.0,
            timing = TemperatureTiming(waterMaxTries = 3)
        )
        rig.fetcher.water = mutableListOf(FetchResult.Empty)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(3, rig.fetcher.count(TemperatureSource.WATER))
        assertEquals(TemperatureReading(null, false), rig.engine.state.value.water)
    }

    @Test
    fun `a failure with nothing to show leaves the dash`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.fetcher.air = FetchResult.Failure(null, "offline")
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(TemperatureReading(null, false), rig.engine.state.value.air)
    }

    @Test
    fun `failures back off one two then five minutes`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.fetcher.air = FetchResult.Failure(503, "down")
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        settle(60_000L) // first failure: asked again after one minute
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))

        settle(90_000L) // second failure waits two minutes
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
        settle(40_000L)
        assertEquals(3, rig.fetcher.count(TemperatureSource.AIR))

        settle(250_000L) // third failure waits five minutes
        assertEquals(3, rig.fetcher.count(TemperatureSource.AIR))
        settle(60_000L)
        assertEquals(4, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `nothing is fetched offline and the fetch follows when the connection returns`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.online[0] = false
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle(100_000L)
        assertTrue(rig.fetcher.calls.isEmpty())

        rig.online[0] = true
        settle(35_000L)
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `refresh now marks values outdated and fetches again at once`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        rig.engine.refreshNow()
        runCurrent()

        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(TemperatureReading(18, false), rig.engine.state.value.air)
    }

    @Test
    fun `refresh now forgets remembered empty sea answers`() = runTest {
        val rig = rig(
            waterFromXM = -50_000.0,
            timing = TemperatureTiming(waterMaxTries = 2)
        )
        rig.fetcher.water = mutableListOf(FetchResult.Empty)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(2, rig.fetcher.count(TemperatureSource.WATER))

        rig.fetcher.water = mutableListOf(FetchResult.Value(20.0))
        rig.engine.refreshNow()
        runCurrent()

        assertEquals(3, rig.fetcher.count(TemperatureSource.WATER))
        assertEquals(20, rig.engine.state.value.water!!.celsius)
    }

    @Test
    fun `turning the feature off stops fetching but keeps the cache`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        val cached = rig.cache.size
        assertTrue(cached > 0)

        rig.engine.setEnabled(false)
        rig.engine.onPosition(lat(9_000.0), lon(9_000.0))
        settle(2 * hour)

        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(cached, rig.cache.size)
        assertNotNull(rig.cache.get("air:A:0:0"))
    }

    @Test
    fun `a failed request is reported once per streak with its source and status, and backs off`() = runTest {
        val problems = mutableListOf<TemperatureProblem>()
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY, onProblem = { problems += it })
        rig.fetcher.air = FetchResult.Failure(400, "refused")
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(listOf(TemperatureProblem(TemperatureSource.AIR, 400, "refused")), problems)
        assertTrue("a failure is not an empty answer", rig.cache.get("air:A:0:0") == null)
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        settle(61_000L)
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals("the same streak is not reported again", 1, problems.size)

        rig.fetcher.air = FetchResult.Value(18.4)
        settle(121_000L)
        rig.fetcher.air = FetchResult.Failure(429, null)
        rig.cache.invalidateAll()
        settle(61_000L)
        assertEquals("a new streak is reported", 2, problems.size)
        assertEquals(TemperatureProblem(TemperatureSource.AIR, 429, null), problems.last())
    }

    @Test
    fun `a failed sea request is reported too`() = runTest {
        val problems = mutableListOf<TemperatureProblem>()
        val rig = rig(waterFromXM = -50_000.0, onProblem = { problems += it })
        rig.fetcher.water = mutableListOf(FetchResult.Failure(null, "timeout"))
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()

        assertEquals(listOf(TemperatureProblem(TemperatureSource.WATER, null, "timeout")), problems)
    }

    @Test
    fun `a refresh asks again after a failure and reports it again`() = runTest {
        val problems = mutableListOf<TemperatureProblem>()
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY, onProblem = { problems += it })
        rig.fetcher.air = FetchResult.Failure(503, null)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, problems.size)

        // Well inside the back-off: only the hold on the tile gets another attempt, and says how it went.
        rig.engine.refreshNow()
        settle(1L)
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(2, problems.size)
    }

    @Test
    fun `a refresh with no connection says so`() = runTest {
        val problems = mutableListOf<TemperatureProblem>()
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY, onProblem = { problems += it })
        rig.online[0] = false
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertTrue("silent until the user asks", problems.isEmpty())

        rig.engine.refreshNow()
        assertEquals(listOf(TemperatureProblem(null, null, null)), problems)
    }

    @Test
    fun `a sample point's water answer is asked once until the water test changes`() = runTest {
        var asked = 0
        val rig = rig(waterAt = { asked++; true })
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        val first = asked
        assertTrue("the first read tests the candidates", first > 10)

        rig.engine.onPosition(lat(100.0), lon(100.0))
        assertTrue("the second read tests only the marker", asked - first <= 2)

        rig.engine.waterTestChanged()
        val before = asked
        rig.engine.onPosition(lat(100.0), lon(100.0))
        assertTrue("after the change the candidates are tested again", asked - before > 10)
    }

    @Test
    fun `a ragged coast widens the window until ten sea points are found`() = runTest {
        // A sea channel two kilometres either side of x = 20 km: the 40 nearest sample points hold fewer than
        // ten of its centres, a wider window holds enough.
        val rig = rig(waterAt = { x -> kotlin.math.abs(x - 20_000.0) < 2_000.0 })
        rig.fetcher.water = mutableListOf(FetchResult.Empty)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(0.0), lon(20_000.0))
        settle()

        assertEquals(10, rig.fetcher.count(TemperatureSource.WATER))
    }

    @Test
    fun `a marker jittering across a square boundary asks once for each side`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.engine.setEnabled(true)
        repeat(4) {
            rig.engine.onPosition(lat(240.0), lon(240.0)) // nearest the origin point
            settle()
            rig.engine.onPosition(lat(260.0), lon(260.0)) // nearest the diagonal neighbour
            settle()
        }

        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a new square is held back while the source is backing off`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.fetcher.air = FetchResult.Failure(503, null)
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        rig.engine.onPosition(lat(4_000.0), lon(4_000.0))
        settle(20_000L)
        assertEquals("backing off, even at a new place", 1, rig.fetcher.count(TemperatureSource.AIR))

        settle(45_000L)
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a marker that moved during a request is served at the settle after it`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.fetcher.gate = CompletableDeferred()
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals("first request in flight", 1, rig.fetcher.count(TemperatureSource.AIR))

        rig.engine.onPosition(lat(4_000.0), lon(4_000.0))
        settle()
        assertEquals("one request at a time", 1, rig.fetcher.count(TemperatureSource.AIR))

        rig.fetcher.gate!!.complete(Unit)
        runCurrent()
        settle()
        assertEquals(2, rig.fetcher.count(TemperatureSource.AIR))
    }

    @Test
    fun `a refresh during a request drops it and asks again at once`() = runTest {
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY)
        rig.fetcher.gate = CompletableDeferred()
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals("first request in flight", 1, rig.fetcher.count(TemperatureSource.AIR))

        rig.fetcher.air = FetchResult.Value(25.0) // what the request started by the press will answer
        rig.engine.refreshNow()
        runCurrent()
        assertEquals("the press started a new request", 2, rig.fetcher.count(TemperatureSource.AIR))

        rig.fetcher.gate!!.complete(Unit)
        runCurrent()
        settle(5_000L)
        assertEquals("the dropped one is not retried", 2, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals("the old answer never lands", TemperatureReading(25, false), rig.engine.state.value.air)
    }

    @Test
    fun `a refresh with no connection leaves the request in flight to finish`() = runTest {
        val problems = mutableListOf<TemperatureProblem>()
        val rig = rig(waterFromXM = Double.POSITIVE_INFINITY, onProblem = { problems += it })
        rig.fetcher.gate = CompletableDeferred()
        rig.engine.setEnabled(true)
        rig.engine.onPosition(lat(100.0), lon(100.0))
        settle()
        assertEquals(1, rig.fetcher.count(TemperatureSource.AIR))

        rig.online[0] = false
        rig.engine.refreshNow()
        runCurrent()
        assertEquals(listOf(TemperatureProblem(null, null, null)), problems)
        assertEquals("nothing new was started", 1, rig.fetcher.count(TemperatureSource.AIR))

        rig.fetcher.gate!!.complete(Unit)
        runCurrent()
        assertEquals("the request in flight finished", 1, rig.fetcher.count(TemperatureSource.AIR))
        assertEquals(TemperatureReading(18, false), rig.engine.state.value.air)
    }
}
