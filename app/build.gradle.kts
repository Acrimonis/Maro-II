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
        /**
         * The colour keys [propColor] could not read, in the order it met them. Published through
         * `UNREADABLE_COLOUR_KEYS` below so the **app** reports them at start: R42's subject is the app
         * showing an error, and this helper stays the one reader of the key — the app reads the
         * published names rather than parsing `maro.properties` again.
         */
        val unreadableColourKeys = mutableListOf<String>()
        /**
         * A colour key in the app's live `#AARRGGBB` spelling — what `route.line.color`,
         * `map.navigation.line.color` and the trace pair below use, and what [propInt] cannot carry:
         * its `toIntOrNull()` drops any ARGB value above `Int.MAX_VALUE` and its `coerceIn(0, 100)`
         * would clamp one that fits, so an ARGB value read through it silently never applies.
         *
         * A value this cannot read falls back to the caller's literal — the sibling key's shipped
         * value — and its name lands in [unreadableColourKeys], which the app reports at start; the
         * build's own error line rides beside it, because a silent fallback is the trap this helper
         * exists to close.
         */
        fun propColor(key: String, fallback: Int): Int {
            val raw = maroProps[key] ?: return fallback
            val hex = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            val parsed = hex.takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16)?.let { value ->
                if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
            }
            if (parsed == null) {
                unreadableColourKeys += key
                logger.error(
                    "maro.properties: '$key' = '$raw' is not an #AARRGGBB colour — " +
                        "falling back to 0x${fallback.toUInt().toString(16).uppercase()}."
                )
                return fallback
            }
            return parsed
        }
        fun propDouble(key: String, default: Double): Double =
            maroProps[key]?.toDoubleOrNull() ?: default
        fun propString(key: String, default: String): String =
            maroProps[key] ?: default
        fun propLong(key: String, default: Long): Long =
            maroProps[key]?.toLongOrNull() ?: default

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
        // The active line's colour: its property key went with the three dead ones below, so the build
        // script's literal is its only default — the Settings row owns the user's own choice.
        buildConfigField("int", "TRACKING_COLOR_ACTIVE", 0xFF1565C0.toInt().toString())
        buildConfigField("int", "TRACKING_COLOR_PAST_FROM", propInt("tracking.color.pastFrom", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PAST_TO", propInt("tracking.color.pastTo", 0xFF0000FF.toInt()).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_FROM", propInt("tracking.transparency.from", 20).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_TO", propInt("tracking.transparency.to", 80).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_PINNED_FROM", propInt("tracking.transparency.pinnedFrom", 0).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_PINNED_TO", propInt("tracking.transparency.pinnedTo", 20).toString())
        buildConfigField("int", "TRACKING_COLOR_PINNED_FROM", propInt("tracking.color.pinnedFrom", 0xFFFF6F00.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_PINNED_TO", propInt("tracking.color.pinnedTo", 0xFFFF8F00.toInt()).toString())

        // ── The trace role's own values: its pair, its ladder, its count and its two gates ──
        buildConfigField("int", "TRACKING_COLOR_TRACE_FROM",
            propColor("tracking.color.traceFrom", 0xFF1565C0.toInt()).toString())
        buildConfigField("int", "TRACKING_COLOR_TRACE_TO",
            propColor("tracking.color.traceTo", 0xFF0000FF.toInt()).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_TRACE_FROM",
            propInt("tracking.transparency.traceFrom", 20).toString())
        buildConfigField("int", "TRACKING_TRANSPARENCY_TRACE_TO",
            propInt("tracking.transparency.traceTo", 80).toString())
        buildConfigField("int", "TRACKING_TRACE_RENDER_NB",
            propInt("tracking.trace.render.nb", 5).coerceIn(0, 20).toString())
        buildConfigField("boolean", "TRACKING_TRACE_ALLOW_SPEED_COLOR",
            propBool("tracking.trace.allowSpeedColor", false).toString())
        buildConfigField("boolean", "TRACKING_TRACE_ALLOW_SPEED_ARROWS",
            propBool("tracking.trace.allowSpeedArrows", true).toString())
        // R42's report channel: the colour keys [propColor] could not read, comma-joined and empty when
        // it read them all, for the app to say at start. Declared after every propColor call above, so
        // the list is complete before it is published.
        buildConfigField("String", "UNREADABLE_COLOUR_KEYS",
            "\"${unreadableColourKeys.joinToString(",")}\"")

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
            propDouble("marker.proximity.pin_m", 200.0).toString())
        buildConfigField("double", "MARKER_PROXIMITY_ZONE_MULTIPLIER",
            propDouble("marker.proximity.zone_multiplier", 3.0).toString())
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
}
