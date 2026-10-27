package ykws.android.maro.data.track

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RegionBounds
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import java.io.File
import java.io.IOException

class TrackRepositoryIncrementalTest {
    @get:Rule val temp = TemporaryFolder()
    private fun track(id: String) = Track(id, id, startTimeMs = 1_000, endTimeMs = 5_000,
        route = id.startsWith("route"), routeStartMarkerId = "origin", routeDestinationMarkerId = "destination",
        trackPoints = List(12) { TrackPoint(43.5, 7.5, timeOffsetMs = it * 1_000L,
            accuracyM = 2f, type = if (it == 4) PointType.GAP else PointType.NORMAL) })

    private suspend fun assertMatchesRebuild(repo: TrackRepository) {
        val incremental = repo.listTracks()
        assertTrue(File(temp.root, "index.bin").delete())
        assertEquals("full rebuild includes ordering, counts and every summary field", incremental, repo.listTracks())
    }

    @Test fun mutationsMatchFullRebuildIncludingClassificationAndOrdering() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.attachPositionClassifier({ _, _ -> true }, RegionBounds(43.0, 44.0, 7.0, 8.0))
        repo.saveAll(listOf(track("first"), track("route-second"), track("third")))
        assertMatchesRebuild(repo)
        repo.updateMetadata("first", "renamed", "comment")
        repo.setPinned("third", true)
        repo.saveAll(listOf(track("route-second").copy(demo = true), track("fourth")))
        assertMatchesRebuild(repo)
        repo.deleteAll(setOf("first", "third"))
        assertMatchesRebuild(repo)
    }

    @Test fun batchDoesNotDecodeUnchangedTracksAndPublishesOnce() = runBlocking {
        val repo = TrackRepository(temp.root)
        val work = mutableListOf<TrackRepository.Work>()
        repo.workObserver = { work += it }
        repo.saveAll(List(10) { track("t-$it") })
        assertEquals(1, work.count { it == TrackRepository.Work.INDEX_WRITE })
        work.clear()
        repo.saveAll(listOf(track("t-0"), track("new")))
        assertEquals(listOf(TrackRepository.Work.INDEX_WRITE), work)
        work.clear()
        repo.deleteAll(setOf("t-1", "t-2"))
        assertEquals(listOf(TrackRepository.Work.INDEX_WRITE), work)
        assertMatchesRebuild(repo)
    }

    @Test fun failedIndexPublicationRebuildsFromDurableTrackFiles() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.save(track("before"))
        repo.workObserver = { if (it == TrackRepository.Work.INDEX_WRITE) throw IOException("index failure") }
        try { repo.save(track("after")); fail("failure expected") } catch (_: IOException) { }
        assertFalse(File(temp.root, "index.bin").exists())
        val restarted = TrackRepository(temp.root)
        assertEquals(setOf("before", "after"), restarted.listTracks().map { it.id }.toSet())
    }

    @Test fun mutationRepairsACorruptIndexWithoutDroppingUnchangedTracks() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.saveAll(listOf(track("one"), track("two")))
        File(temp.root, "index.bin").writeBytes(byteArrayOf(0x0a, 0xff.toByte()))
        repo.save(track("three"))
        assertEquals(setOf("one", "two", "three"), repo.listTracks().map { it.id }.toSet())
        assertMatchesRebuild(repo)
    }

    @Test fun interruptedBatchLeavesAnIndexThatCanBeRebuilt() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.save(track("before"))
        File(temp.root, "blocked.bin").mkdir()
        File(temp.root, "blocked.bin/child").writeText("prevent directory replacement")
        try { repo.saveAll(listOf(track("saved"), track("blocked"))); fail("failure expected") }
        catch (_: IOException) { }
        assertFalse(File(temp.root, "index.bin").exists())
        assertEquals(setOf("before", "saved"), TrackRepository(temp.root).listTracks().map { it.id }.toSet())
    }

    @Test fun concurrentMetadataUpdatesKeepBothEdits() = runBlocking {
        val repo = TrackRepository(temp.root)
        repo.save(track("one"))
        listOf(async { repo.updateMetadata("one", name = "name") },
            async { repo.updateMetadata("one", comment = "comment") }).awaitAll()
        assertEquals("name", repo.load("one")!!.name)
        assertEquals("comment", repo.load("one")!!.comment)
        assertMatchesRebuild(repo)
    }

    @Test fun batchCascadePreservesUserAndActiveTrackMarkers() = runBlocking {
        val markers = UserMarkerRepository(File(temp.root, "markers"))
        val repo = TrackRepository(temp.root, markers)
        val geometry = MarkerGeometry.Pin(LatLng(43.5, 7.5))
        markers.saveAll(listOf(
            UserMarker("auto", "auto", geometry, origin = MarkerOrigin.IDLE_AUTO, trackId = "one"),
            UserMarker("user", "user", geometry, origin = MarkerOrigin.USER, trackId = "one"),
            UserMarker("live", "live", geometry, origin = MarkerOrigin.IDLE_AUTO, trackId = "live")
        ))
        repo.saveAll(listOf(track("one"), track("live")))
        repo.deleteAll(setOf("one", "live"), excludeActiveTrackId = "live")
        assertEquals(setOf("user", "live"), markers.loadAll().map { it.id }.toSet())
        assertTrue(repo.listTracks().isEmpty())
    }
}
