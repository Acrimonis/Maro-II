@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
REM -- Bake selector - writes the gitignored data\app-assets tree (apk-build.bat packages it) --
REM Each interactive target also OFFERS a fresh re-download/overwrite of its source. Non-interactive:
REM   apk-bake.bat [--fresh] all | coastline | band | emodnet | litto3d | depth
REM   --fresh forces re-fetch/overwrite (EMODnet re-downloads the tile; Litto3D fetches tiles; ...).
set "FRESH="
if not "%~1"=="" (
  for %%T in (%*) do if /i "%%T"=="--fresh" set "FRESH=--fresh"
  for %%T in (%*) do if /i not "%%T"=="--fresh" call :do_%%T
  goto done
)

echo ============================================
echo   Bake selector   ^(present / MISSING^)
echo ============================================
call :status "Coastline + 300 m band" "data\app-assets\coastlines\nice-frejus.bin"
call :status "EMODnet deep .asc"       "data\app-assets\depth\emodnet-nice-frejus.asc"
call :status "Litto3D shallow .asc.gz" "data\app-assets\depth\litto3d-nice-frejus.asc.gz"
call :status "Depth .bin (merge+clip)" "data\app-assets\depth\nice-frejus.bin"
echo.
call :ask "Bake coastline + 300 m band (OSM, network)? [y/N]: " "  Re-fetch OSM / overwrite? [y/N]: " coastline
call :ask "Bake EMODnet deep backbone (large download, cached)? [y/N]: " "  Re-download the E5 tile? [y/N]: " emodnet
call :ask "Bake Litto3D shallow tier (needs tiles)? [y/N]: " "  Re-fetch tiles (download missing)? [y/N]: " litto3d
call :ask "Bake depth .bin (merge + 6 NM clip; auto-resolves deps)? [y/N]: " "  Re-bake depth sources fresh? [y/N]: " depth
goto done

REM %1 = bake prompt, %2 = fresh prompt, %3 = target. Body is top-level (no paren-block), so the
REM parens inside the prompt strings are safe.
:ask
set "A=N" & set /p "A=%~1"
if /i not "!A:~0,1!"=="y" exit /b 0
set "FRESH="
set "FR=N" & set /p "FR=%~2"
if /i "!FR:~0,1!"=="y" set "FRESH=--fresh"
call :do_%~3
exit /b 0

:status
set "MARK=[MISSING]"
if exist "%~2" set "MARK=[present]"
echo   !MARK! %~1
exit /b 0
:do_all
call :do_coastline & call :do_emodnet & call :do_litto3d & call :do_depth
exit /b 0
:do_coastline
call "tools\bake-coastline.bat" %FRESH%
exit /b 0
:do_emodnet
call "tools\bake-emodnet.bat" %FRESH%
exit /b 0
:do_litto3d
call "tools\bake-litto3d.bat" %FRESH%
exit /b 0
:do_band
call "tools\bake-zone300.bat"
exit /b 0
:do_depth
call "tools\bake-depth.bat" %FRESH%
exit /b 0
:done
echo ============================================
echo   Bake done.   Build: apk-build.bat    Deploy: apk-deploy.bat
echo ============================================
endlocal
