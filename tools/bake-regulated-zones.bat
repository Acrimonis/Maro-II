@echo off
REM Bake regulated zones: fetch SHOM WFS -> aggregate with seeds -> serialize -> data\app-assets\regulated-zones\<region>.bin
REM Run from repo root (apk-bake.bat does). No network-free mode (always fetches SHOM WFS).
echo [bake-regulated-zones] Fetching SHOM WFS regulation zones + serializing...
call gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true --rerun-tasks
exit /b %ERRORLEVEL%
