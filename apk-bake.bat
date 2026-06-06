@echo off
REM ======================================================================
REM  Maro II - Pre-deploy 300 m band baker
REM ----------------------------------------------------------------------
REM  Generates the coastline + 300 m band OFFLINE on this build machine and
REM  writes it into the GITIGNORED data/ tree (NOT committed):
REM      data\app-assets\coastlines\nice-frejus.bin
REM  app\build.gradle.kts adds data\app-assets as an assets source root, so the
REM  next build packages it into the APK at assets\coastlines\nice-frejus.bin.
REM  The app then ships this prebaked band (CoastlineRepository.loadCoastline
REM  prefers the bundled asset), so a fresh install shows it immediately.
REM
REM  Requires internet (Overpass). Run this only when the OSM coastline or the
REM  band algorithm changed; then just rebuild + deploy (nothing to commit).
REM ======================================================================

echo ======================================
echo  Baking Maro II coastline + 300 m band...
echo ======================================

REM --rerun-tasks: the baker's output (.bin) is not a declared Gradle task
REM output, so force the test task to run even if it looks up-to-date.
call gradlew :app:testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true --rerun-tasks

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Bake failed with error code %ERRORLEVEL%
    echo         Needs internet for Overpass. See the test output above.
    exit /b %ERRORLEVEL%
)

set "ASSET=data\app-assets\coastlines\nice-frejus.bin"
if not exist "%ASSET%" (
    echo [ERROR] Bake reported success but %ASSET% is missing.
    exit /b 1
)

echo ======================================
echo  Bake OK: %ASSET%  (gitignored - do NOT commit)
echo  Next: apk-build.bat   then   apk-deploy.bat
echo        (the build incorporates the band from data\app-assets into the APK)
echo ======================================
