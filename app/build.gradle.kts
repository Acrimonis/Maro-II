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
    implementation(libs.osmdroid.android)
    implementation(libs.protobuf.javalite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
