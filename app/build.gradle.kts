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
