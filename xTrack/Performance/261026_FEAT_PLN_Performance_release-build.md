<!-- scope: feature -->
# Separate assessment — release-build performance

## Disposition: packaging verified; optimization and device performance unverified
This branch is independent from upstream develop `b1f40aa211c02c2882bb676514bee753cecacebe`. It changes no build configuration, source, dependency or keep rule.

`app/build.gradle.kts` leaves release minification disabled, explicitly pending device exercise. `app/proguard-rules.pro` contains serialization, protobuf-lite and osmdroid keep rules, but their presence does not establish correctness under shrinking. No device measurements or release exercise were supplied in this session. R8 is therefore not enabled as an assumed performance improvement.

## Baseline and verification
The existing release configuration packaged successfully with `:app:assembleRelease --no-daemon` using the temporary JDK 17/Android SDK toolchain. Gradle reported 3m39s, 53 actionable tasks (47 executed, five from cache, one up-to-date). This is a host build observation, not a repeatable build-speed benchmark or an application latency result. The output is an unsigned release APK; no install, launch or device access occurred.

| Question | Evidence |
|---|---|
| Existing unminified release packages | Passed |
| Shrunk release packages and preserves reflection/serialization/maps | Not tested; no candidate enabled |
| Release versus debug routing/recording/frame timings | Unmeasured |
| Real-region/baked assets exercised | Unverified |
| Device recording recovery, GPS cadence and visual semantics | Unverified |
| Size, memory or latency benefit from shrinking | Unknown; no before/after candidate comparison |

The release question is deliberately separate from the runtime stage PRs. Packaging success does not close the performance or keep-rule gate, and the desktop synthetic stage results do not predict an optimized Android release's gain.
