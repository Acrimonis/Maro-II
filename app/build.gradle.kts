plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "ykws.android.maro"
    compileSdk = 34

    defaultConfig {
        applicationId = "ykws.android.maro"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Region corridor single source of truth — the W/E coastline-point longitudes (gradle.properties,
        // overridable with -Pmaro.region.lonWest=…). Exposed to Kotlin so CoastlineGenerator and the
        // derived depth envelope read ONE definition; the bake scripts read the same props via bake-env.bat.
        val regionLonWest = (project.findProperty("maro.region.lonWest") as String?)?.toDouble() ?: 6.70
        val regionLonEast = (project.findProperty("maro.region.lonEast") as String?)?.toDouble() ?: 7.31
        buildConfigField("double", "REGION_LON_WEST", regionLonWest.toString())
        buildConfigField("double", "REGION_LON_EAST", regionLonEast.toString())

        // ── Layer default visibility from maro.properties ─────────────────
        val maroProps = mutableMapOf<String, String>()
        val maroFile = rootProject.file("maro.properties")
        if (maroFile.exists()) {
            maroFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    val eq = trimmed.indexOf('=')
                    if (eq > 0) {
                        maroProps[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                    }
                }
            }
        }
        fun propBool(key: String, default: Boolean): Boolean =
            maroProps[key]?.lowercase()?.toBooleanStrictOrNull() ?: default
        fun propInt(key: String, default: Int): Int =
            maroProps[key]?.toIntOrNull()?.coerceIn(0, 100) ?: default
        fun propDouble(key: String, default: Double): Double =
            maroProps[key]?.toDoubleOrNull() ?: default
        fun propString(key: String, default: String): String =
            maroProps[key] ?: default

        buildConfigField("boolean", "LAYER_ZONE300_DEFAULT", propBool("layer.zone300.default", true).toString())
        buildConfigField("boolean", "LAYER_REGULATED_ZONES_DEFAULT", propBool("layer.regulatedZones.default", false).toString())
        buildConfigField("boolean", "LAYER_COASTLINE_DEFAULT", propBool("layer.coastline.default", true).toString())
        buildConfigField("boolean", "LAYER_LOW_DEPTH_DEFAULT", propBool("layer.lowDepthWarning.default", true).toString())

        // ── Icon background opacity from maro.properties ─────────────────
        buildConfigField("int", "ICON_BACK_ACTIVE_ALPHA", (propInt("icon.back.active.transparency", 75) * 255 / 100).toString())
        buildConfigField("int", "ICON_BACK_INACTIVE_ALPHA", (propInt("icon.back.inactive.transparency", 50) * 255 / 100).toString())

        // ── Regulated zone bake-time filtering from maro.properties ──────
        buildConfigField("double", "REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M",
            propDouble("regulatedZones.defaultVesselLengthM", 6.0).toString())
        buildConfigField("String", "REGULATED_ZONES_FILTERED_TYPES",
            "\"${propString("regulatedZones.filteredTypes", "ENVIRONMENTAL,FISHING_PROHIBITED,OTHER")}\"")

        // ── Speed zone hysteresis from maro.properties ──────────────────
        buildConfigField("double", "SPEED_ZONE_HYSTERESIS_M",
            propDouble("speedZone.hysteresisM", 5.0).coerceAtLeast(0.0).toString())

        // ── Speed zone max search distance from maro.properties ─────────
        buildConfigField("double", "SPEED_ZONE_MAX_SEARCH_M",
            propDouble("speedZone.maxSearchM", 750.0).toString())

        // ── Distance threshold for zone exit preview from maro.properties ──
        buildConfigField("double", "SPEED_ZONE_DISTANCE_OUT_OF_ZONE_INFO_M",
            propDouble("speedZone.distanceOutOfZoneInfoM", 200.0)
                .coerceAtLeast(10.0).toString())

        // ── Track recording defaults from maro.properties ──────────
        buildConfigField("double", "TRACK_ORIGIN_LAT", propDouble("track.originLat.default", 43.55).toString())
        buildConfigField("double", "TRACK_ORIGIN_LON", propDouble("track.originLon.default", 7.00).toString())
        buildConfigField("double", "TRACK_GEOFENCE_RADIUS_M", propDouble("track.geofenceRadiusM", 500.0).toString())
        buildConfigField("boolean", "TRACK_ENABLED_DEFAULT", propBool("track.enabled.default", false).toString())

        // ── Track rendering defaults from maro.properties ──────────
        buildConfigField("int", "TRACKING_RENDER_NB", propInt("tracking.render.nb", 5).coerceIn(0, 20).toString())
        buildConfigField("int", "TRACKING_COLOR_ACTIVE", propInt("tracking.color.active", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_HISTORY", propInt("tracking.color.history", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_HISTORY_END", propInt("tracking.color.historyEnd", 0xFF0000FF.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PINNED", propInt("tracking.color.pinned", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PAST_FROM", propInt("tracking.color.pastFrom", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PAST_TO", propInt("tracking.color.pastTo", 0xFF0000FF.toInt()).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_FROM", propInt("tracking.transparency.from", 100).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_TO", propInt("tracking.transparency.to", 30).toString())
        buildConfigField("int", "TRACKING_COLOR_PINNED_FROM", propInt("tracking.color.pinnedFrom", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PINNED_TO", propInt("tracking.color.pinnedTo", 0xFF1565C0.toInt()).toString())

        // ── Stop detection GPS dormant percent from maro.properties ──────
        buildConfigField("int", "STOP_DETECTION_GPS_DORMANT_PCT",
            propInt("stopDetection.gpsDormantPct", 80).toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Don't package the GDAL/OSM bake intermediates that ride along in data/app-assets/depth/
        // (a corridor-wide Litto3D .asc is ~1 GB); the app only reads the cooked .bin. Keeps the APK lean.
        ignoreAssetsPatterns += listOf("*.asc", "*.asc.gz", "*.asc.aux.xml", "*.prj", "*.vrt")
    }

    sourceSets {
        getByName("main") {
            // Incorporate the prebaked 300 m band into the APK assets at build time WITHOUT
            // committing the binary: apk-bake.bat writes it under the gitignored data/ tree
            // (data/app-assets/coastlines/nice-frejus.bin); this adds that folder as an assets
            // source root, so it is packaged at assets/coastlines/nice-frejus.bin. When the
            // folder is absent (never baked), it's simply ignored and the app falls back to a
            // live fetch at runtime.
            assets.srcDir(rootProject.file("data/app-assets"))
        }
    }
}

protobuf {
    protoc {
        // Protoc compiler artifact — hardcoded version to avoid DSL access issues
        // inside the protobuf extension block. Keep in sync with libs.versions.toml.
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

tasks.withType<Test> {
    // Propagate the prebake gate to the test JVM so the @prebake build-tools (CoastlinePrebakeTest,
    // DepthPrebakeTest) run only with -Dmaro.prebake=true; normal runs skip them (Assume).
    systemProperty("maro.prebake", System.getProperty("maro.prebake") ?: "false")
    // The depth prebake parses large GDAL-baked .asc grids (millions of cells) in-memory; give the
    // forked test JVM generous headroom so it doesn't OOM. Harmless for normal (small) unit tests.
    maxHeapSize = "4g"
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.osmdroid.android)
    implementation(libs.protobuf.javalite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Forward the opt-in flag for on-demand, network-dependent test "tools" (the
// Zone300AssetBaker coastline/band baker, and the Zone300WaterOracleHarness EMODnet check)
// from the Gradle invocation to the forked test JVM. They self-skip unless run with the flag,
// e.g.  gradlew :app:testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true
tasks.withType<Test>().configureEach {
    systemProperty("maro.bake", System.getProperty("maro.bake", "false"))
    systemProperty("maro.validate", System.getProperty("maro.validate", "false"))
    // Repo root, so the baker can resolve <repo>/data/app-assets regardless of the test CWD.
    systemProperty("maro.repoDir", rootProject.projectDir.absolutePath)
}
