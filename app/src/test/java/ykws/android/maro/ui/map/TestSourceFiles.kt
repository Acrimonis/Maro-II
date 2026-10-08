package ykws.android.maro.ui.map

import org.junit.Assume.assumeTrue
import java.io.File

/**
 * The one file-first reader the map tests share: a path relative to the `app` module, resolved from the
 * repo root when `maro.repoDir` is set (a repo-root run), else from the test CWD (the module). The
 * convention lives here once so `SpeedDisplayChipsTest` and `TrackRenderStringsTest` cannot drift (F7).
 */
internal fun sourceText(relative: String): String {
    val file = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/$relative") }
        ?.takeIf { it.isFile }
        ?: File(relative)
    assumeTrue("$relative not found", file.isFile)
    return file.readText()
}
