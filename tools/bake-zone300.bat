@echo off
REM Refresh the 300 m band from the EXISTING coastline asset - NO network (band-only).
REM Use after a band builder/classifier tweak when the coastline geometry itself is unchanged;
REM rebuilds the band in place in data\app-assets\coastlines\<region>.bin. Run from repo root.
echo [bake-zone300] Rebuilding 300 m band from data\app-assets\coastlines ^(no OSM fetch^)...
call gradlew testDebugUnitTest --tests "*Zone300BandRefreshTest*" -Dmaro.bake=true --rerun-tasks
exit /b %ERRORLEVEL%
