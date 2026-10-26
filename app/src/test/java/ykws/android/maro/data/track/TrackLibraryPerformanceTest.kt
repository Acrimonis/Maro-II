package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Same workload on the upstream implementation and candidate; opt in with MARO_LIBRARY_PERF=1. */
@OptIn(ExperimentalSerializationApi::class)
class TrackLibraryPerformanceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun measureLibraryMutations() = runBlocking {
        assumeTrue(System.getenv("MARO_LIBRARY_PERF") == "1")
        val points = List(1_500) { TrackPoint(43.5 + it * 0.00001, 7.0, speedMps = 3f, timeOffsetMs = it * 1_000L) }
        for (count in listOf(10, 200)) {
            val tracks = List(count) { Track("t-$it", "trip $it", startTimeMs = it * 2_000_000L,
                endTimeMs = it * 2_000_000L + 1_500_000L, trackPoints = points) }
            val encoded = tracks.associate { it.id to ProtoBuf.encodeToByteArray(Track.serializer(), it) }
            for (workload in listOf("rename", "import", "merge-delete")) {
                repeat(7) { round ->
                    val dir = File(temp.root, "$count-$workload-$round").apply { mkdirs() }
                    encoded.forEach { (id, bytes) -> File(dir, "$id.bin").writeBytes(bytes) }
                    val repository = TrackRepository(dir)
                    repository.listTracks() // Build the starting index outside the timed operation.
                    val decodes = AtomicInteger()
                    val indexes = AtomicInteger()
                    repository.workObserver = {
                        when (it) {
                            TrackRepository.Work.TRACK_DECODE -> decodes.incrementAndGet()
                            TrackRepository.Work.INDEX_WRITE -> indexes.incrementAndGet()
                        }
                    }
                    val start = System.nanoTime()
                    when (workload) {
                        "rename" -> repository.updateMetadata(tracks.first().id, name = "renamed")
                        "import" -> importBatch(repository, tracks.take(5).map { it.copy(id = "import-${it.id}") })
                        else -> {
                            val sources = tracks.take(5)
                            val merged = TrackMerger().merge(sources, "merged")
                            repository.save(merged)
                            deleteBatch(repository, sources.map { it.id }.toSet())
                        }
                    }
                    val ns = System.nanoTime() - start
                    println("LIBRARY-PERF count=$count workload=$workload round=$round ns=$ns decodes=${decodes.get()} indexes=${indexes.get()}")
                    dir.deleteRecursively()
                }
            }
        }
    }

    private suspend fun importBatch(repo: TrackRepository, tracks: List<Track>) {
        repo.saveAll(tracks)
    }
    private suspend fun deleteBatch(repo: TrackRepository, ids: Set<String>) {
        repo.deleteAll(ids)
    }
}
