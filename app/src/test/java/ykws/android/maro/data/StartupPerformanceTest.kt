package ykws.android.maro.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.regulation.*
import ykws.android.maro.ui.map.sortMarkers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import kotlin.math.sin

/** Synthetic assets and marker files only. Opt in with MARO_STARTUP_PERF=1. */
class StartupPerformanceTest {
    @get:Rule val temp = TemporaryFolder()
    @Volatile private var sink = 0

    @Test fun repeatedLoadsAndMarkerRefreshes() = runBlocking {
        assumeTrue(System.getenv("MARO_STARTUP_PERF") == "1")
        val zones = RegulatedZoneSet(
            zones = List(200) { i -> RegulatedZone(
                outerRing = listOf(LatLng(43.5, 7.1), LatLng(43.6, 7.2), LatLng(43.5, 7.3), LatLng(43.5, 7.1)),
                zoneType = RegulatedZoneType.SPEED_LIMIT, speedLimitKn = 10.0,
                name = "Zone $i", source = "SHOM", sourceRef = "ref-$i") },
            metadata = RegulationMetadata("test", 1_000, 1, 200)
        )
        val bytes = RegulatedZoneSerializer.serialize(zones)
        val coast = listOf(CoastlineSegment(osmWayId = 1L, isMainland = true, isClosed = false,
            points = List(4000) { k -> CoastlinePoint((43.5 + .008 * sin(k / 100.0)).toFloat(), (7.0 + k * .00001).toFloat()) }))
        val expected = CoastlineSpatialIndex(coast).query(43.51, 7.02)
        repeat(12) { round ->
            for (candidate in if (round % 2 == 0) listOf(false, true) else listOf(true, false)) {
                val owner = RetryingInitializer()
                var index: CoastlineSpatialIndex? = null
                var builds = 0
                val start = System.nanoTime()
                repeat(10) {
                    if (candidate) owner.start(this, { index != null }) {
                        index = withContext(Dispatchers.Default) { builds++; CoastlineSpatialIndex(coast) }
                    }?.join()
                    else { builds++; index = CoastlineSpatialIndex(coast) }
                }
                val elapsed = System.nanoTime() - start
                assertEquals(expected, index!!.query(43.51, 7.02))
                assertEquals(if (candidate) 1 else 10, builds)
                println("STARTUP-PERF workload=index candidate=$candidate round=$round ns=$elapsed builds=$builds")
            }
        }
        // Alternate within one JVM: cross-process host load is not a comparison.
        repeat(12) { round ->
            val variants = if (round % 2 == 0) listOf(false, true) else listOf(true, false)
            for (candidate in variants) {
                val repository = RegulatedZonesRepository("test")
                var opens = 0
                val start = System.nanoTime()
                repeat(10) {
                    if (candidate) {
                        repository.loadFrom { path ->
                            if (!path.endsWith(".bin")) throw FileNotFoundException(path)
                            opens++
                            ByteArrayInputStream(bytes)
                        }
                        assertEquals(zones, repository.zoneSet.value)
                    } else withContext(Dispatchers.IO) {
                        // Original load: open, read, decode, apply missing overrides, publish.
                        opens++
                        val parsed = ByteArrayInputStream(bytes).use { RegulatedZoneSerializer.deserialize(it.readBytes()) }
                        val applied = RegulationSpeedOverrides.apply(parsed.zones,
                            RegulationSpeedOverrides.Overrides(emptyMap(), emptyMap()))
                        val result = parsed.copy(zones = applied, metadata = parsed.metadata.copy(totalZones = applied.size))
                        assertEquals(zones, result)
                        sink = result.zones.size
                    }
                }
                println("STARTUP-PERF workload=zones candidate=$candidate round=$round ns=${System.nanoTime()-start} reads=$opens")
            }
        }
        repeat(12) { round ->
            val variants = if (round % 2 == 0) listOf(false, true) else listOf(true, false)
            for (candidate in variants) {
                val repo = UserMarkerRepository(File(temp.root, "markers-$round-$candidate"))
                val markers = List(20) { UserMarker("m-$it", "Marker $it", MarkerGeometry.Pin(LatLng(43.5, 7.5))) }
                repo.saveAll(markers)
                var reads = 0
                val load: suspend () -> List<UserMarker> = { reads++; repo.loadAll() }
                val publish: (List<UserMarker>) -> Unit = { sink = sortMarkers(it, ListSortState()).size }
                val owner = RevisionRefresh({ repo.revision.value }, load, publish)
                val start = System.nanoTime()
                repeat(25) { update ->
                    repo.update(markers.first().copy(name = "Edit $update"))
                    repeat(2) { if (candidate) owner.refresh() else publish(load()) }
                }
                assertEquals(20, sink)
                assertEquals(if (candidate) 25 else 50, reads)
                println("STARTUP-PERF workload=markers candidate=$candidate round=$round ns=${System.nanoTime()-start} reads=$reads")
            }
        }
    }
}
