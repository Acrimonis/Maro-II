package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with MARO_RECORDING_PERF=1; both paths run interleaved in the same JVM. No timing assertions. */
@OptIn(ExperimentalSerializationApi::class)
class RecordingPerformanceTest {
    private data class Reading(val ns: Long, val cpuNs: Long, val allocated: Long, val writes: Int, val bytes: Long)
    // Android's compile classpath omits JDK management types; this opt-in harness runs only on the JVM.
    private val bean = Class.forName("java.lang.management.ManagementFactory")
        .getMethod("getThreadMXBean").invoke(null)
    private val allocatedBytes = Class.forName("com.sun.management.ThreadMXBean")
        .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val cpuTime = Class.forName("java.lang.management.ThreadMXBean")
        .getMethod("getCurrentThreadCpuTime")
    private fun allocated(thread: Long) = allocatedBytes.invoke(bean, thread) as Long
    private fun cpu() = cpuTime.invoke(bean) as Long
    @Volatile private var sink = 0L

    private fun measure(points: List<TrackPoint>, chunked: Boolean, checkpointEvery: Int, idle: Int): Reading {
        val thread = Thread.currentThread().id
        val allocated = allocated(thread)
        val cpu = cpu()
        val start = System.nanoTime()
        var writes = 0
        var bytes = 0L
        var track = Track("perf", "perf", startTimeMs = 0)
        val builder = RecordingPoints(256)
        val save: suspend (Track) -> Unit = {
            val encoded = ProtoBuf.encodeToByteArray(it)
            bytes += encoded.size
            writes++
            sink += encoded.lastOrNull()?.toLong() ?: 0L
        }
        val checkpoints = RecordingCheckpoint(save)
        runBlocking {
            for (point in points) {
                track = track.copy(trackPoints = if (chunked) builder.append(point) else track.trackPoints + point)
                if (checkpointEvery > 0 && track.trackPoints.size % checkpointEvery == 0) {
                    if (chunked) checkpoints.write(RecordingCheckpoint.Snapshot(track.trackPoints.size.toLong(), track))
                    else save(track)
                }
            }
            repeat(idle) {
                if (chunked) checkpoints.write(RecordingCheckpoint.Snapshot(points.size.toLong(), track))
                else save(track)
            }
        }
        sink += track.trackPoints.lastOrNull()?.timeOffsetMs ?: 0L
        return Reading(System.nanoTime() - start, cpu() - cpu,
            allocated(thread) - allocated, writes, bytes)
    }

    @Test fun compareReferenceAndCandidate() {
        assumeTrue(System.getenv("MARO_RECORDING_PERF") == "1")
        for ((count, cadence, idle) in listOf(Triple(120, 30, 0), Triple(3_600, 30, 0),
            Triple(21_600, 30, 0), Triple(21_600, 0, 120))) {
            val points = List(count) { TrackPoint(43.5 + it * 0.00001, 7.0, speedMps = 3f, timeOffsetMs = it * 1_000L) }
            repeat(10) { round ->
                val order = if (round % 2 == 0) listOf(false, true) else listOf(true, false)
                val readings = order.associateWith { measure(points, it, cadence, idle) }
                if (idle == 0) {
                    assertEquals(readings[false]!!.writes, readings[true]!!.writes)
                    assertEquals(readings[false]!!.bytes, readings[true]!!.bytes)
                }
                readings.forEach { (candidate, r) ->
                    println("RECORDING-PERF count=$count cadence=$cadence idle=$idle round=$round candidate=$candidate " +
                        "ns=${r.ns} cpuNs=${r.cpuNs} allocated=${r.allocated} writes=${r.writes} bytes=${r.bytes}")
                }
            }
        }
    }
}
