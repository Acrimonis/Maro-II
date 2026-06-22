@echo off
REM Bake the coastline + 300 m band (OSM fetch). Needs network (Overpass).
REM Output goes to D:\.src\.data\coastlines\ — the shared master copy.
setlocal
echo [bake-coastline] Fetching OSM from Overpass + building coastline + 300 m band...
echo                  (network step -- can take 30s to a few minutes)

call gradlew testDebugUnitTest --tests "*Zone300AssetBaker*" -Dmaro.bake=true --rerun-tasks
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

if not exist "D:\.src\.data\coastlines" mkdir "D:\.src\.data\coastlines"
copy /Y "data\app-assets\coastlines\*" "D:\.src\.data\coastlines\" >nul
echo [bake-coastline] Archived to D:\.src\.data\coastlines
endlocal
