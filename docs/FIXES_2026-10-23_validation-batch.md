<!-- scope: review -->
# Batch three: validation (2026-10-23)

> The batch both reviewers asked for before anything merges, on `feature/validation-ci`, stacked on
> the on-water branch. It adds the machine that compiles the work, the regression tests for the
> recorder's asynchronous paths, and the release keep rules. **Written without a JDK**: the first run
> of the workflow is itself the validation of everything on the three stacked branches.

## 1. What it adds

| Item | What | Files |
|---|---|---|
| CI (E01) | A GitHub Actions workflow that builds the debug APK and runs the JVM unit suite on every pull request and on every push to a `feature/**` or `claude/**` branch, uploading the test report and the APK. The baked assets are not in the repository, so the tests that need them skip themselves; the report's `skipped` count is where that shows. | `.github/workflows/android.yml` |
| Recorder regression tests (D09, review round one) | Five JVM tests over the real recorder and the real repository on a temp directory, with `save` gated or failed by a test subclass: Stop returns at once and reads ON until the save lands; a Discard during a finalize waits and then finds nothing to discard; a second Stop during a finalize does not finalize twice; a failed save retains the recording as a checkpoint and the next Stop retries; a failed save over a stale checkpoint leaves the newer snapshot. | `app/src/test/…/data/track/TrackRecorderLifecycleTest.kt`, `TrackRepository.kt` (now `open`, its four writes overridable, for this seam alone) |
| Release keep rules (E01) | `proguard-rules.pro` with the serialization, protobuf-lite and osmdroid rules, wired into a `release` build type with shrinking left **off** until a release build has been exercised on a device. | `app/proguard-rules.pro`, `app/build.gradle.kts` |

## 2. What the first workflow run will tell you

- Whether the three branches compile at all. Every edit since the review was read-verified only.
- Which of the new tests pass. The recorder tests use real dispatchers and bounded waits (5 s), not
  timing assertions, so they should be stable on a shared runner.
- How many tests skip for want of the baked assets (expected: the regional harness tier).

## 3. Still outside any machine's reach

- The clean-install launch with no grants on Android 14+ (the H1 fix).
- The landscape compass check, marker z-order after a selection, the long-press unlock, and a
  geofence auto-start at the standby cadence.
- The raster owner's suspended-build takeover, which needs a `Context` and a `Bitmap`.

## 4. Strongest objection

Opening `TrackRepository` for a test subclass widens a production class for a test's convenience;
an interface would be cleaner. It was chosen because the repository has one implementation and the
seam is four `open` keywords rather than a new abstraction every caller must name.
