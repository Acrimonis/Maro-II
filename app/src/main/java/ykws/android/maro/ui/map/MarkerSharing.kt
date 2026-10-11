package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.data.markers.toMarkerGpx
import ykws.android.maro.data.model.markers.UserMarker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The marker transfer's own scratch space — a directory of its own beside the tracks' `tracks/`, so
 * the repository's `markers/` folder keeps holding nothing but `user_markers.json` and its backups.
 * `res/xml/provider_paths.xml` exposes it as `marker_export/`.
 */
private const val MARKER_EXPORT_DIR = "marker_export"

/**
 * Builds the shared file base name for a marker: `yyyy_MM_dd_HH_mm-<sanitized name>`.
 *
 * A marker carries no start time, so its creation time is the stamp; a legacy marker with none falls
 * back to now. The title is sanitized through the same [sanitizeFileName] the track transfer uses, so
 * one rule names both kinds' files.
 */
internal fun markerFileBaseName(name: String, createdAtEpochMs: Long): String {
    val ts = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.US)
        .format(Date(if (createdAtEpochMs == 0L) System.currentTimeMillis() else createdAtEpochMs))
    val title = sanitizeFileName(name.replace(" ", "_"))
    return "$ts-$title"
}

/** The markers a transfer names, read from the view model's own full list. */
private fun markersOf(markersViewModel: MarkersViewModel, ids: Set<String>): List<UserMarker> =
    markersViewModel.allMarkers.value.filter { it.id in ids }

/**
 * Share one marker as a GPX file through Android's share sheet.
 *
 * The file is written into the app's own marker-export folder and handed out by the same
 * `FileProvider` the track transfer uses; the sheet's own title comes from strings, exactly as the
 * track side's does.
 */
internal fun shareMarkerGpx(
    context: android.content.Context,
    markersViewModel: MarkersViewModel,
    markerId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (String?) -> Unit = {}
) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        onProgress(context.getString(R.string.exporting_markers))
        val marker = markersOf(markersViewModel, setOf(markerId)).firstOrNull()
        if (marker == null) {
            onProgress(null)
            return@launch
        }
        val gpxFile = File(
            File(context.filesDir, MARKER_EXPORT_DIR),
            "${markerFileBaseName(marker.name, marker.createdAtEpochMs)}-1.gpx"
        )
        gpxFile.parentFile?.mkdirs()
        gpxFile.writeText(marker.toMarkerGpx())
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
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_marker_title)))
    }
}

/**
 * Zip several markers' GPX files and share the archive — the backup in one file.
 *
 * Two markers may share a name, so entries are made distinct with the track transfer's own per-base
 * counter.
 */
internal fun shareMarkersZip(
    context: android.content.Context,
    markersViewModel: MarkersViewModel,
    markerIds: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    onProgress: (String?) -> Unit = {}
) {
    scope.launch {
        if (markerIds.isEmpty()) return@launch
        onProgress(context.getString(R.string.exporting_markers))
        val timestamp = SimpleDateFormat("yyyy_MM_dd_HHmmss", Locale.US).format(Date())
        val zipFile = File(File(context.filesDir, MARKER_EXPORT_DIR), "maro-markers-$timestamp.zip")
        zipFile.parentFile?.mkdirs()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val markers = markersOf(markersViewModel, markerIds)
                .sortedWith(compareBy({ it.createdAtEpochMs }, { it.id }))
            val counters = HashMap<String, Int>()
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                markers.forEach { marker ->
                    val base = markerFileBaseName(marker.name, marker.createdAtEpochMs)
                    val counter = (counters[base] ?: -1) + 1
                    counters[base] = counter
                    val entry = java.util.zip.ZipEntry("$base-$counter.gpx")
                    entry.time = marker.createdAtEpochMs
                    zos.putNextEntry(entry)
                    zos.write(marker.toMarkerGpx().toByteArray())
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
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_markers_title)))
    }
}
