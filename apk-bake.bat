@echo off
setlocal
cd /d "%~dp0"
set FRESH=
if not "%~1"=="" (
  for %%T in (%*) do (
    if /i "%%T"=="--fresh" (
      set "FRESH=--fresh"
    ) else (
      call :do_%%T
    )
  )
  goto done
)
echo ============================================
echo   Bake selector
echo ============================================
echo   Run: apk-bake.bat all
echo   Or:  apk-bake.bat coastline / emodnet / litto3d / depth / regulatedzones
echo ============================================
goto done

:do_all
call :do_coastline
call :do_emodnet
call :do_litto3d
call :do_depth
call :do_regulatedzones
exit /b 0

:do_coastline
call "tools\bake-coastline.bat"
exit /b 0

:do_emodnet
if defined FRESH (call "tools\bake-emodnet.bat" --fresh) else (call "tools\bake-emodnet.bat")
exit /b 0

:do_litto3d
if defined FRESH (call "tools\bake-litto3d.bat" --fresh) else (call "tools\bake-litto3d.bat")
exit /b 0

:do_band
call "tools\bake-zone300.bat"
exit /b 0

:do_depth
if defined FRESH (call "tools\bake-depth.bat" --fresh) else (call "tools\bake-depth.bat")
exit /b 0

:do_regulatedzones
call "tools\bake-regulated-zones.bat"
exit /b 0

:done
endlocal
