@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
REM -- Bake selector - writes the gitignored data\app-assets tree (apk-build.bat packages it) --
REM Interactive menu, OR non-interactive (any order/count):
REM   apk-bake.bat all | coastline | band | emodnet | litto3d | depth
if not "%~1"=="" (
  for %%T in (%*) do call :do_%%T
  goto done
)

echo ============================================
echo   Bake selector   ^(present / MISSING^)
echo ============================================
call :status "Coastline + 300 m band" "data\app-assets\coastlines\nice-frejus.bin"
call :status "EMODnet deep .asc"       "data\app-assets\depth\emodnet-nice-frejus.asc"
call :status "Litto3D shallow .asc"    "data\app-assets\depth\litto3d-nice-frejus.asc"
call :status "Depth .bin (merge+clip)" "data\app-assets\depth\nice-frejus.bin"
echo.
set "A=N" & set /p "A=Bake coastline + 300 m band (OSM, network)? [y/N]: "
if /i "!A:~0,1!"=="y" call :do_coastline
set "A=N" & set /p "A=Bake EMODnet deep backbone (large download, cached)? [y/N]: "
if /i "!A:~0,1!"=="y" call :do_emodnet
set "A=N" & set /p "A=Bake Litto3D shallow tier (needs tiles)? [y/N]: "
if /i "!A:~0,1!"=="y" call :do_litto3d
set "A=N" & set /p "A=Bake depth .bin (merge + 6 NM clip; auto-resolves deps)? [y/N]: "
if /i "!A:~0,1!"=="y" call :do_depth
goto done

:status
set "MARK=[MISSING]"
if exist "%~2" set "MARK=[present]"
echo   !MARK! %~1
exit /b 0
:do_all
call :do_coastline & call :do_emodnet & call :do_litto3d & call :do_depth
exit /b 0
:do_coastline
call "tools\bake-coastline.bat"
exit /b 0
:do_emodnet
call "tools\bake-emodnet.bat"
exit /b 0
:do_litto3d
call "tools\bake-litto3d.bat"
exit /b 0
:do_band
call "tools\bake-zone300.bat"
exit /b 0
:do_depth
call "tools\bake-depth.bat"
exit /b 0
:done
echo ============================================
echo   Bake done.   Build: apk-build.bat    Deploy: apk-deploy.bat
echo ============================================
endlocal
