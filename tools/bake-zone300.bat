@echo off
REM Refresh the 300 m band from the EXISTING coastline asset - NO network (band-only).
setlocal
echo [bake-zone300] Rebuilding 300 m band from coastline (no OSM fetch)...

call gradlew testDebugUnitTest --tests "*Zone300BandRefreshTest*" -Dmaro.bake=true --rerun-tasks
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

if not exist "D:\.src\.data\coastlines" mkdir "D:\.src\.data\coastlines"
copy /Y "data\app-assets\coastlines\*" "D:\.src\.data\coastlines\" >nul
echo [bake-zone300] Archived to D:\.src\.data\coastlines
endlocal
