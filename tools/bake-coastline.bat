@echo off
REM Bake the coastline + 300 m band (OSM fetch -> assemble -> band) into the gitignored
REM data\app-assets\coastlines\<region>.bin that the app actually loads. Needs network (Overpass).
REM Run from the repo root (apk-bake.bat does). Band-only refresh (no network): tools\bake-zone300.bat.
echo [bake-coastline] Fetching OSM from Overpass + building coastline + 300 m band...
echo                  ^(network step -- can take 30s to a few minutes; Gradle shows a spinner, no byte progress^)
call gradlew testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true --rerun-tasks
exit /b %ERRORLEVEL%
