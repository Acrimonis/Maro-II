package ykws.android.maro.data.depth.raster

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSource
import java.util.concurrent.TimeUnit

/**
 * Client for the **open, no-auth** EMODnet Bathymetry WCS (validated live June 2026):
 *   https://ows.emodnet-bathymetry.eu/wcs — coverage id **`emodnet__mean`** (confirmed via
 *   GetCapabilities; the colon form `emodnet:mean` is NOT valid for WCS 2.0.1 GetCoverage).
 *
 * ⚠️ Validated reality: the GetCoverage `formatSupported` list is
 * `application/gml+xml`, `image/jpeg|png`, `image/tiff`, `image/tiff;application=geotiff`,
 * `text/plain` — there is **no ESRI ASCII grid output**. So the bbox-raster value fetch
 * needs a **float GeoTIFF decoder** (request `image/tiff`) or a **GML tupleList parser**
 * (`application/gml+xml`). That decoder is the remaining piece (DepthMappingPlan.md § 12);
 * [AsciiGridParser] handles `.asc` from SHOM/Litto3D/baked grids instead.
 *
 * For a validated, working live value source today use [EmodnetRestClient.depthSample]
 * (point) — the generator's full-grid deep tier awaits the GeoTIFF/GML decoder.
 */
class EmodnetWcsClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
    private val coverageId: String = COVERAGE_ID,
    private val format: String = FORMAT_TIFF,
    private val resM: Double = EMODNET_RES_M
) {
    /** Build the WCS 2.0.1 GetCoverage URL for [bbox] (subset on the Lat/Long axes). */
    fun coverageUrl(bbox: BoundingBox): String = buildString {
        append(baseUrl)
        append("?service=WCS&version=2.0.1&request=GetCoverage")
        append("&coverageId=").append(coverageId)
        append("&subset=Lat(").append(bbox.latSouth).append(',').append(bbox.latNorth).append(')')
        append("&subset=Long(").append(bbox.lonWest).append(',').append(bbox.lonEast).append(')')
        append("&format=").append(format)
    }

    /**
     * Fetch + decode the EMODnet DTM clipped to [bbox] as a depth [SourceRaster].
     *
     * ⚠️ EMODnet WCS emits no ESRI ASCII; [format] is GeoTIFF/GML. Decoding those is the
     * remaining piece, so this currently throws [NotImplementedError] unless a text/ASCII
     * format is supplied. Use [EmodnetRestClient] for validated live point sampling today.
     */
    suspend fun fetchCoverage(
        bbox: BoundingBox,
        onProgress: (Int) -> Unit = {}
    ): SourceRaster = withContext(Dispatchers.IO) {
        onProgress(5)
        val request = Request.Builder()
            .url(coverageUrl(bbox))
            .header("User-Agent", "MaroII-Depth-Fetcher/1.0")
            .build()

        val bytes = httpClient.newCall(request).execute().use { resp ->
            val b = resp.body?.bytes()
            if (!resp.isSuccessful || b == null) {
                throw IllegalStateException("EMODnet WCS HTTP ${resp.code}")
            }
            b
        }
        onProgress(60)
        // Only the text/ASCII path is implemented; GeoTIFF/GML decode is pending.
        if (format == FORMAT_TIFF || format.startsWith("image/")) {
            throw NotImplementedError(
                "EMODnet WCS returns $format; a float GeoTIFF/GML decoder is not yet " +
                    "implemented. Use EmodnetRestClient.depthSample for live values, or bake " +
                    "an .asc grid offline and load via AsciiGridParser."
            )
        }
        val raster = AsciiGridParser.parse(String(bytes), DepthSource.EMODNET, resM = resM, negate = true)
        onProgress(100)
        raster
    }

    companion object {
        const val BASE_URL = "https://ows.emodnet-bathymetry.eu/wcs"
        // Confirmed live via GetCapabilities (WCS 2.0.1 uses '__' not ':').
        const val COVERAGE_ID = "emodnet__mean"
        const val FORMAT_TIFF = "image/tiff"
        const val FORMAT_GML = "application/gml+xml"
        const val EMODNET_RES_M = 115.0

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
