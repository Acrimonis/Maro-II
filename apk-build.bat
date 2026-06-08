@echo off
REM Build script for Maro II APK
REM Generates a signed/unsigned debug APK and copies it to the project root

echo ======================================
echo  Building Maro II APK...
echo ======================================

REM -- Optional data preprocessing (ALL prebaking happens here on the computer) --
REM Default N for each: existing bundled assets are used as-is and ship to the device on deploy.
REM Coastline baking writes the band the APP ACTUALLY LOADS: the gitignored
REM   data\app-assets\coastlines\nice-frejus.bin  (Zone300AssetBaker; packaged at
REM   assets\coastlines\) — the SAME path apk-bake.bat uses. Do NOT bake CoastlinePrebakeTest
REM here: it writes the committed src\main\assets\coastline\ (singular) test default, which the
REM app does NOT read, so it would build green yet change nothing on the device.
set "PREBAKE_COAST=N"
set /p "PREBAKE_COAST=Prebake coastline (OSM fetch + Zone300 band -> data\app-assets; needs network)? [y/N]: "
if /i "%PREBAKE_COAST:~0,1%"=="y" call gradlew testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true --rerun-tasks

set "PREBAKE_DEPTH=N"
set /p "PREBAKE_DEPTH=Preprocess depth (GDAL gather, then merge/serialize)? [y/N]: "
if /i "%PREBAKE_DEPTH:~0,1%"=="y" (
    call tools\bake_depth.bat
    call gradlew testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true
)
echo.

call gradlew assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo ======================================
echo  Build successful!
echo ======================================
