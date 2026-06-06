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
