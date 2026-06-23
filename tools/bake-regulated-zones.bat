@echo off
REM Bake regulated zones: fetch SHOM WFS -> aggregate -> serialize.
REM Master copy goes to D:\.src\.data\regulated-zones\.
setlocal
echo [bake-regulated-zones] Fetching SHOM WFS regulation zones + serializing...

call gradlew testDebugUnitTest --tests "*RegulatedZonePrebakeTest*" -Dmaro.prebake=true --rerun-tasks
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

if not exist "D:\.src\.data\regulated-zones" mkdir "D:\.src\.data\regulated-zones"
copy /Y "data\app-assets\regulated-zones\*.bin" "D:\.src\.data\regulated-zones\" >nul
echo [bake-regulated-zones] Archived to D:\.src\.data\regulated-zones
endlocal
