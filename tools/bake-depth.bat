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
set "FRESH="
REM Parse flags WITHOUT shift -- shift also shifts %0, which would break every %~dp0 call below.
for %%A in (%*) do (
  if /i "%%A"=="--no-auto-deps" set "AUTODEPS=0"
  if /i "%%A"=="--fresh" set "FRESH=--fresh"
)

REM bake-env needs the .bbox envelope sidecar. If it's missing but the coastline.bin already exists,
REM emit the sidecar OFFLINE from it (no network) -- only a truly missing coastline re-fetches OSM.
set "COASTBBOX=data\app-assets\coastlines\%REGION%.bbox"
set "COASTBIN=data\app-assets\coastlines\%REGION%.bin"
set "EMASC=data\app-assets\depth\emodnet-%REGION%.asc"

if defined FRESH echo [bake-depth] --fresh: clearing cached depth sources to force a re-bake...
if defined FRESH del /q "%EMASC%" "data\app-assets\depth\litto3d-%REGION%.asc.gz" 2>nul

if not exist "%COASTBBOX%" if exist "%COASTBIN%" (
  echo [bake-depth] sidecar missing but coastline.bin present -^> emitting .bbox OFFLINE ^(no network^)...
  call gradlew testDebugUnitTest --tests "*CoastlineBboxTest*" -Dmaro.bake=true --rerun-tasks || (echo [bake-depth] bbox emit failed & exit /b 1)
)
if not exist "%COASTBBOX%" if not exist "%COASTBIN%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] coastline missing -^> auto-running bake-coastline ^(needs network^)...
    call "%~dp0bake-coastline.bat" || (echo [bake-depth] coastline bake failed & exit /b 1)
  ) else (echo [bake-depth] ERROR: coastline missing ^(--no-auto-deps^). Bake the coastline first. & exit /b 1)
)
if not exist "%EMASC%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] %EMASC% missing -^> auto-running bake-emodnet...
    call "%~dp0bake-emodnet.bat" %FRESH% || (echo [bake-depth] emodnet bake failed & exit /b 1)
  ) else (echo [bake-depth] ERROR: %EMASC% missing ^(--no-auto-deps^). Bake EMODnet first. & exit /b 1)
)
REM Litto3D shallow tier. With --fresh: always (re)bake (it fetches tiles first). Else: only if tiles
REM are present and the .asc.gz is missing.
if defined FRESH call "%~dp0bake-litto3d.bat" %FRESH%
if not defined FRESH if exist "tools\litto3d_tiles\*.asc" if not exist "data\app-assets\depth\litto3d-%REGION%.asc.gz" call "%~dp0bake-litto3d.bat"

echo [bake-depth] Merge + 6 NM clip -^> %REGION%.bin ...
call gradlew testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true --rerun-tasks
exit /b %ERRORLEVEL%
