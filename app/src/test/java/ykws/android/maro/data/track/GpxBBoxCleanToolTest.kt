package ykws.android.maro.data.track

import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/**
 * On-demand repair tool — NOT a normal unit test (self-skips unless opted in).
 *
 * Removes the accidental demo-mode continuation (every point NOT dated 2026-09-06) from a Maro
 * GPX export, in BOTH the standard `<trkpt>` polyline and the `<maro:data>` protobuf blob, so all
 * import paths (foreign GPX, IMPORT_NEW, UPDATE_EXISTING) are clean. The blob is decoded and
 * re-encoded with the app's own [Track] serializer and [withDerivedStats], guaranteeing fidelity.
 *
 * Run:
 *   gradlew :app:testDebugUnitTest --tests "*GpxBBoxCleanToolTest*" -Dmaro.cleanGpx=true
 *
 * The original export is never modified; the cleaned copy is written beside it as
 * `<name>_no-resume.gpx`.
 */
class GpxBBoxCleanToolTest {

    private val proto = ProtoBuf.Default

    /** The original outing date (UTC). Anything not on this date is the accidental resume. */
    private val keepDate: LocalDate = LocalDate.of(2026, 9, 6)
    private val keepDatePrefix = "2026-09-06T"

    @Test
    fun dropResumedTail() {
        Assume.assumeTrue(
            "on-demand tool - enable with -Dmaro.cleanGpx=true",
            System.getProperty("maro.cleanGpx") == "true"
        )

        val repoDir = File(System.getProperty("maro.repoDir") ?: "..")
        val input = repoDir.listFiles { f ->
            f.isFile && f.name.startsWith("2026_09_06_15_52") && f.name.endsWith(".gpx") &&
                !f.name.contains("_clean") && !f.name.contains("_no-resume")
        }?.maxByOrNull { it.length() } ?: error("input GPX not found in " + repoDir.absolutePath)
        println("Input : " + input.absolutePath + " (" + input.length() + " bytes)")

        val xml = input.readText(Charsets.UTF_8)
        val trksegOpen = "<trkseg>"
        val blobOpen = "<maro:data>"
        val blobClose = "</maro:data>"
        val trksegStart = xml.indexOf(trksegOpen)
        val trksegEnd = xml.indexOf("</trkseg>")
        val blobStart = xml.indexOf(blobOpen)
        val blobEnd = xml.indexOf(blobClose)
        check(trksegStart >= 0 && trksegEnd > trksegStart) { "malformed trkseg" }
        check(blobStart >= 0 && blobEnd > blobStart) { "missing maro:data" }

        // ── 1. Filter the standard trkpt blocks by date ───────────────────────
        val kept = ArrayList<String>()
        var total = 0
        var droppedTrkpt = 0
        var cursor = 0
        while (true) {
            val s = xml.indexOf("<trkpt ", cursor)
            if (s < 0) break
            val e = xml.indexOf("</trkpt>", s)
            if (e < 0) break
            val end = e + "</trkpt>".length
            val block = xml.substring(s, end)
            cursor = end
            total++
            if (block.contains(keepDatePrefix)) kept.add(block) else droppedTrkpt++
        }
        println("trkpt : total=" + total + " kept=" + kept.size + " removed=" + droppedTrkpt)

        // ── 2. Decode + filter the maro protobuf blob by date ─────────────────
        val b64 = xml.substring(blobStart + blobOpen.length, blobEnd).trim()
        val track = proto.decodeFromByteArray(Track.serializer(), Base64.getMimeDecoder().decode(b64))
        fun stamp(p: TrackPoint): Instant = Instant.ofEpochMilli(track.startTimeMs + p.timeOffsetMs)
        fun onKeepDate(p: TrackPoint): Boolean =
            stamp(p).atZone(ZoneOffset.UTC).toLocalDate() == keepDate
        val keptPoints = track.trackPoints.filter { onKeepDate(it) }
        val droppedPoints = track.trackPoints.filterNot { onKeepDate(it) }
        println("blob  : total=" + track.trackPoints.size + " kept=" + keptPoints.size +
            " removed=" + droppedPoints.size)
        if (keptPoints.isNotEmpty()) {
            println("kept  : span " + stamp(keptPoints.first()) + " .. " + stamp(keptPoints.last()))
        }
        if (droppedPoints.isNotEmpty()) {
            println("drop  : span " + stamp(droppedPoints.first()) + " .. " + stamp(droppedPoints.last()) +
                " byDay=" + droppedPoints.groupingBy { stamp(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    .eachCount())
        }

        // Recompute derived stats with the app's own function, and align endTimeMs with the last
        // kept real point (same convention the importer uses on UPDATE — see updateTrackFromBlob).
        val statsApplied = track.copy(trackPoints = keptPoints).withDerivedStats()
        val cleaned = if (statsApplied.lastPointTimeMs > 0) {
            statsApplied.copy(endTimeMs = statsApplied.lastPointTimeMs)
        } else {
            statsApplied
        }
        val newBlob = Base64.getEncoder()
            .encodeToString(proto.encodeToByteArray(Track.serializer(), cleaned))

        // Self-check: the scrubbed blob must round-trip and contain only kept-date points.
        val verified = proto.decodeFromByteArray(Track.serializer(), Base64.getDecoder().decode(newBlob))
        check(verified.trackPoints.size == keptPoints.size) { "blob point count mismatch" }
        check(verified.trackPoints.all { onKeepDate(it) }) { "blob still contains off-date points" }
        check(verified.endTimeMs == statsApplied.lastPointTimeMs) { "endTimeMs not aligned" }
        println("verify: blob re-decodes OK, points=" + verified.trackPoints.size +
            " endTimeMs=" + Instant.ofEpochMilli(verified.endTimeMs ?: 0L) +
            " derivedDistanceNm=" + verified.distanceNm)

        // ── 3. Reassemble the XML ─────────────────────────────────────────────
        val sb = StringBuilder(xml.length)
        sb.append(xml, 0, trksegStart + trksegOpen.length)
        for (b in kept) sb.append(b)
        sb.append(xml, trksegEnd, blobStart + blobOpen.length)
        sb.append(newBlob)
        sb.append(xml, blobEnd, xml.length)

        val out = File(repoDir, "2026_09_06_15_52-Iles_de_Lerins_no-resume.gpx")
        out.writeText(sb.toString(), Charsets.UTF_8)
        println("Wrote : " + out.absolutePath + " (" + out.length() + " bytes)")
    }
}
