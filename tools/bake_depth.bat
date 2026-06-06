@echo off
setlocal
REM ===========================================================================
REM  Depth preprocessing orchestrator — encapsulates ALL depth source bakes.
REM  Confirms each source individually (showing present/MISSING) and runs only
REM  the confirmed ones. Outputs -> app\src\main\assets\depth\  (bundled in the
REM  APK -> pushed to the device on deploy).
REM  Run from the repo root (apk-build.bat does this). Needs GDAL on PATH.
REM  See docs\DepthMappingBake.md.
REM ===========================================================================
set "DEPTH_ASSETS=app\src\main\assets\depth"

echo ============================================
echo  Depth preprocessing (per-source confirm)
echo ============================================

REM -- EMODnet deep backbone (10-60 m) --
if exist "%DEPTH_ASSETS%\emodnet-nice-frejus.asc" (set "EM=present") else (set "EM=MISSING")
set "DO_EM=N"
set /p "DO_EM=  Bake EMODnet deep backbone [%EM%] (downloads tile E5)? [y/N]: "
if /i "%DO_EM:~0,1%"=="y" (call tools\bake_emodnet.bat) else (echo   - skipped EMODnet)

REM -- SHOM Litto3D shallow/collision tier (0-10 m) --
if exist "%DEPTH_ASSETS%\litto3d-nice-frejus.asc" (set "LT=present") else (set "LT=MISSING")
set "DO_LT=N"
set /p "DO_LT=  Bake Litto3D collision tier [%LT%] (needs tiles in tools\litto3d_tiles\)? [y/N]: "
if /i "%DO_LT:~0,1%"=="y" (call tools\bake_litto3d.bat) else (echo   - skipped Litto3D)

REM -- Future depth sources (not yet implemented) --
echo   - SHOM survey lots (dive 25-60 m): not yet implemented
echo   - Sentinel-2 SDB (dive 10-25 m):  not yet implemented

echo ============================================
echo  Depth preprocessing done.
echo ============================================
endlocal
