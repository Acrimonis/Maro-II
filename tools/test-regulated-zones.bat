@echo off
REM Run regulated-zones tests. Usage: test-regulated-zones.bat [--no-live]
set "LIVE="
if /i not "%1"=="--no-live" set "LIVE=--tests CapAntibesZoneTest"
if defined LIVE echo   + Live SHOM WFS test
if not defined LIVE echo   (live test skipped)
echo.
gradlew testDebugUnitTest --tests RegulatedZone* --tests Regulation* --tests ShomRegulation* %LIVE% --rerun-tasks
if %ERRORLEVEL% NEQ 0 (echo. & echo ====== FAILED ======) else (echo. & echo ====== PASSED ======)
start "" "app\build\reports\tests\testDebugUnitTest\index.html"
exit /b %ERRORLEVEL%
