package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.data.track.toGpx
import kotlinx.coroutines.launch

/**
 * Sanitizes a track name for use as a filesystem file name.
 *
 * Replaces Windows-forbidden and control characters (0x00-1F), trims trailing
 * spaces/dots, caps length at 100, falls back to "track" when blank, and guards
 * against reserved device names (CON, PRN, AUX, NUL, COM1-9, LPT1-9).
 */
internal fun sanitizeFileName(name: String): String {
    val sanitized = name
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .trim(' ', '.')
        .take(100)
    val fallback = sanitized.ifBlank { "track" }
    val base = fallback.substringBefore('.').uppercase()
    val reserved = base == "CON" || base == "PRN" || base == "AUX" || base == "NUL" ||
        base.startsWith("COM") && base.removePrefix("COM").toIntOrNull() in 1..9 ||
        base.startsWith("LPT") && base.removePrefix("LPT").toIntOrNull() in 1..9
    return if (reserved) "_$fallback" else fallback
}

/**
 * Builds the shared file base name for a track: `yyyy_MM_dd_HH_mm-title`.
 *
 * Uses the track's start time (falling back to now when unknown) and sanitizes
 * the title (spaces become underscores) via [sanitizeFileName].
 */
internal fun trackFileBaseName(name: String, startTimeMs: Long): String {
    val ts = java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm", java.util.Locale.US)
        .format(java.util.Date(if (startTimeMs == 0L) System.currentTimeMillis() else startTimeMs))
    val title = sanitizeFileName(name.replace(" ", "_"))
    return "$ts-$title"
}

/**
 * Share a track as a GPX file via Android's share intent.
 */
internal fun shareTrackGpx(
    context: android.content.Context,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    trackId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (String?) -> Unit = {}
) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        onProgress(context.getString(R.string.exporting_tracks))
        val track = trackViewModel.loadTrackDetail(trackId)
        if (track == null) {
            onProgress(null)
            return@launch
        }
        val gpx = track.toGpx()
        val safeName = "${trackFileBaseName(track.name, track.startTimeMs)}-1"
        val gpxFile = java.io.File(context.filesDir, "tracks/$safeName.gpx")
        gpxFile.parentFile?.mkdirs()
        gpxFile.writeText(gpx)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onProgress(null)
        context.startActivity(android.content.Intent.createChooser(intent, "Share GPX"))
    }
}

/**
 * Zip multiple track GPX files and share via Android's share intent.
 */
internal fun shareTracksZip(
    context: android.content.Context,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    trackIds: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (String?) -> Unit = {}
) {
    scope.launch {
        if (trackIds.isEmpty()) return@launch
        onProgress(context.getString(R.string.exporting_tracks))
        val timestamp = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zipFile = java.io.File(context.filesDir, "tracks/maro-tracks-$timestamp.zip")
        zipFile.parentFile?.mkdirs()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tracks = trackIds
                .mapNotNull { id -> trackViewModel.loadTrackDetail(id) }
                .sortedWith(compareBy({ it.startTimeMs }, { it.id }))
            val counters = HashMap<String, Int>()
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                tracks.forEach { track ->
                    val gpx = track.toGpx()
                    val base = trackFileBaseName(track.name, track.startTimeMs)
                    val counter = (counters[base] ?: -1) + 1
                    counters[base] = counter
                    val entry = java.util.zip.ZipEntry("$base-$counter.gpx")
                    entry.time = track.startTimeMs
                    zos.putNextEntry(entry)
                    zos.write(gpx.toByteArray())
                    zos.closeEntry()
                }
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onProgress(null)
        context.startActivity(android.content.Intent.createChooser(intent, "Share Tracks"))
    }
}
