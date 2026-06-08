@echo off
setlocal
REM ---------------------------------------------------------------------------
REM Bake the depth grid: gather .asc sources -> merge -> clip to the 6 NM-of-coast zone ->
REM   data\app-assets\depth\<region>.bin. The envelope + 6 NM clip DERIVE from the coastline, so
REM   coastline.bin is REQUIRED - auto-resolved by running bake-coastline if missing.
REM Flags:  --no-auto-deps  (hard-fail instead of auto-running missing dependencies)
REM Run from the repo root.
REM ---------------------------------------------------------------------------
set "REGION=nice-frejus"
set "AUTODEPS=1"
if /i "%~1"=="--no-auto-deps" set "AUTODEPS=0"

REM Key the coastline dep on the .bbox sidecar (bake-env needs it): an old .bin without a sidecar
REM re-bakes the coastline, which emits both .bin + .bbox.
set "COAST=data\app-assets\coastlines\%REGION%.bbox"
set "EMASC=data\app-assets\depth\emodnet-%REGION%.asc"

if not exist "%COAST%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] %COAST% missing -^> auto-running bake-coastline ^(needs network^)...
    call "%~dp0bake-coastline.bat" || (echo [bake-depth] coastline bake failed & exit /b 1)
  ) else (echo [bake-depth] ERROR: %COAST% missing ^(--no-auto-deps^). Bake the coastline first. & exit /b 1)
)
if not exist "%EMASC%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] %EMASC% missing -^> auto-running bake-emodnet...
    call "%~dp0bake-emodnet.bat" || (echo [bake-depth] emodnet bake failed & exit /b 1)
  ) else (echo [bake-depth] ERROR: %EMASC% missing ^(--no-auto-deps^). Bake EMODnet first. & exit /b 1)
)
REM Litto3D is the optional shallow tier - bake only if tiles are present and the .asc is missing.
if exist "tools\litto3d_tiles\*.asc" if not exist "data\app-assets\depth\litto3d-%REGION%.asc.gz" call "%~dp0bake-litto3d.bat"

echo [bake-depth] Merge + 6 NM clip -^> %REGION%.bin ...
call gradlew testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true --rerun-tasks
exit /b %ERRORLEVEL%
