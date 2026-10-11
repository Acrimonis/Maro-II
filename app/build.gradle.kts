plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "ykws.android.maro"
    compileSdk = 36

    defaultConfig {
        applicationId = "ykws.android.maro"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Region corridor single source of truth — the W/E coastline-point longitudes (gradle.properties,
        // overridable with -Pmaro.region.lonWest=…). Exposed to Kotlin so CoastlineGenerator and the
        // derived depth envelope read ONE definition; the bake scripts read the same props via bake-env.bat.
        val regionLonWest = (project.findProperty("maro.region.lonWest") as String?)?.toDouble() ?: 6.70
        val regionLonEast = (project.findProperty("maro.region.lonEast") as String?)?.toDouble() ?: 7.55
        buildConfigField("double", "REGION_LON_WEST", regionLonWest.toString())
        buildConfigField("double", "REGION_LON_EAST", regionLonEast.toString())

        // Region identifier — single source for coastline, depth, and regulated-zone cache namespaces.
        // Changing this invalidates all baked .bin caches; consumed via BuildConfig.REGION_ID.
        val regionId = project.findProperty("maro.region.id") as String? ?: "nice-frejus"
        buildConfigField("String", "REGION_ID", "\"$regionId\"")

        // ── Values from maro.properties ───────────────────────────────────
        //
        // Read through a **provider**, not a plain `File.readLines()`, so the read is a tracked input: the
        // configuration cache records it and re-configures when the file changes, instead of Gradle having
        // no idea the file was consulted at all. What it does **not** do is recompile Kotlin — only a value
        // landing on a `buildConfigField` below can change `BuildConfig`, and the runtime keys (every
        // `route.*` among them) are read by `AppConfig` from the packaged asset and never reach the compiler.
        val maroProps = project.providers
            .fileContents(project.layout.projectDirectory.file("src/main/assets/maro.properties"))
            .asText
            .orElse("")
            .map { text ->
                val map = mutableMapOf<String, String>()
                text.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            map[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                        }
                    }
                }
                map
            }
            .get()
        fun propBool(key: String, default: Boolean): Boolean =
            maroProps[key]?.lowercase()?.toBooleanStrictOrNull() ?: default
        fun propInt(key: String, default: Int): Int =
            maroProps[key]?.toIntOrNull()?.coerceIn(0, 100) ?: default
        fun propDouble(key: String, default: Double): Double =
            maroProps[key]?.toDoubleOrNull() ?: default
        fun propString(key: String, default: String): String =
            maroProps[key] ?: default
        fun propLong(key: String, default: Long): Long =
            maroProps[key]?.toLongOrNull() ?: default

        buildConfigField("boolean", "LAYER_ZONE300_DEFAULT", propBool("layer.zone300", true).toString())
        buildConfigField("boolean", "LAYER_REGULATED_ZONES_DEFAULT", propBool("layer.regulatedZone", false).toString())
        buildConfigField("boolean", "LAYER_COASTLINE_DEFAULT", propBool("layer.coastline", true).toString())
        buildConfigField("boolean", "LAYER_LOW_DEPTH_DEFAULT", propBool("layer.lowDepth", true).toString())

        // ── Regulated zone bake-time filtering from maro.properties ──────
        buildConfigField("double", "REGULATED_ZONES_DEFAULT_VESSEL_LENGTH_M",
            propDouble("regulatedZone.defaultVesselLengthM", 6.0).toString())
        buildConfigField("String", "REGULATED_ZONES_FILTERED_TYPES",
            "\"${propString("regulatedZone.filteredTypes", "ENVIRONMENTAL,FISHING_PROHIBITED,OTHER")}\"")

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
        buildConfigField("double", "TRACK_ORIGIN_LAT", propDouble("tracking.originLat", 43.55).toString())
        buildConfigField("double", "TRACK_ORIGIN_LON", propDouble("tracking.originLon", 7.00).toString())
        buildConfigField("double", "TRACK_GEOFENCE_RADIUS_M", propDouble("tracking.geofenceRadiusM", 500.0).toString())
        buildConfigField("boolean", "TRACK_ENABLED_DEFAULT", propBool("tracking.enabled", false).toString())

        // ── The path family is read at RUNTIME (D6) ────────────────────────
        //
        // Every `path.*` value — the stroke table, the casing, the chevrons, the ramp, the stored
        // pairs, the count and the two route gates — is read by `AppConfig` from the packaged asset at
        // start, through the one resolver, so Gradle and the app can never fork on a rendering value.
        // The `path.*` emissions that used to live here are gone, and with them `propColor` and its
        // `UNREADABLE_COLOUR_KEYS` channel: R42's unreadable-colour report is now the runtime parser's
        // (`AppConfig.unreadableColourKeys`). Only values a BuildConfig constant must carry before
        // `AppConfig.init` keep an emission here.

        // ── Stop detection GPS dormant percent from maro.properties ──────
        buildConfigField("int", "STOP_DETECTION_GPS_DORMANT_PCT",
            propInt("stopDetection.gpsDormantPct", 80).toString())

        // ── GPS position processing from maro.properties ──────────────────
        buildConfigField("long", "GPS_IDLE_MAX_INTERVAL_MS",
            propLong("gps.idle.maxIntervalMs", 10000).toString())
        buildConfigField("int", "GPS_ACCURACY_GOOD_THRESHOLD_M",
            propInt("gps.accuracy.goodThresholdM", 10).toString())

        // ── Track recording speed caps from maro.properties ─────────────
        buildConfigField("double", "TRACKING_BOAT_MAX_SPEED_KN",
            propDouble("tracking.boatMaxSpeedKn", 32.0).toString())
        buildConfigField("double", "TRACKING_LAND_MAX_SPEED_KN",
            propDouble("tracking.landMaxSpeedKn", 90.0).toString())

        // ── User marker proximity defaults from maro.properties ──────────
        buildConfigField("double", "MARKER_PROXIMITY_PIN_M",
            propDouble("marker.proximity.pinM", 200.0).toString())
        buildConfigField("double", "MARKER_PROXIMITY_ZONE_MULTIPLIER",
            propDouble("marker.proximity.zoneMultiplier", 3.0).toString())
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
            assets.srcDir(rootProject.file("data/app-assets"))
        }
    }

    // maro.properties lives in src/main/assets — single source of truth.
    // AppConfig loads it from assets at runtime; build.gradle.kts reads it
    // directly for BuildConfig constants.
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
    // Surface the tests' own stdout into the Gradle test log only when asked, so an instrumented harness
    // that reports through `println` (the selective-perf eval, its PERF-* lines) is readable in the
    // `test-*.log` the repo habit names without echoing every other class's output. Enable with the Gradle
    // property `-Pmaro.testStdout=true`; the run recipe in RouteSelectivePerfEvalTest names it (D25).
    val showStdout = project.providers.gradleProperty("maro.testStdout")
        .map { it.toBoolean() }
        .getOrElse(false)
    testLogging {
        showStandardStreams = showStdout
    }
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
    implementation(libs.compose.material.icons.extended)
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
    // On-demand GPX repair tool (GpxBBoxCleanToolTest) — inert unless -Dmaro.cleanGpx=true.
    systemProperty("maro.cleanGpx", System.getProperty("maro.cleanGpx", "false"))
    // Probe point for the PrebakedDataDiagnostic containment dump — `<lat>,<lon>`.
    systemProperty("maro.point", System.getProperty("maro.point", ""))
}
