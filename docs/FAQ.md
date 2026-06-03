<!-- scope: reference -->

# FAQ — Troubleshooting

Common build and setup issues.

---

## Build Failures

| Symptom | Likely cause |
|---------|-------------|
| `android.useAndroidX` error | Missing `gradle.properties` with `android.useAndroidX=true` |
| Compose BOM not found | Check the BOM version exists in `gradle/libs.versions.toml` |
| SDK platform missing | Run `sdkmanager "platforms;android-34"` |
| `JAVA_HOME` not set | `set JAVA_HOME=C:\Path\To\JDK` |
