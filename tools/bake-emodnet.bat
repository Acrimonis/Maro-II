@echo off
setlocal enabledelayedexpansion
call "%~dp0gdal_env.bat"
call "%~dp0bake-env.bat" || exit /b 1
REM ---------------------------------------------------------------------------
REM Bake the EMODnet DTM 2024 deep backbone -> data\app-assets\depth\emodnet-<region>.asc
REM   (ESRI ASCII, elevation rel. LAT -- the app negates it to depth.) Gitignored (/data/).
REM Clip box (W/S/E/N) + REGION come from bake-env.bat (derived from the region props).
REM The big E5 tile download is CACHED in %TEMP%; delete that folder to force a re-download.
REM Requires on PATH: curl, tar (Win10+), GDAL (gdalwarp, gdal_translate). Run from repo root.
REM ---------------------------------------------------------------------------
set "URL=https://downloads.emodnet-bathymetry.eu/v12/E5_2024.tif.zip"
set "WORK=%TEMP%\emodnet_e5"
set "OUT=data\app-assets\depth"
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%WORK%" mkdir "%WORK%"
if /i "%~1"=="--fresh" echo [bake-emodnet] --fresh: clearing the cached E5 tile to force a re-download...
if /i "%~1"=="--fresh" del /q "%WORK%\*.tif" "%WORK%\*.zip" 2>nul

REM Cache: reuse the extracted .tif if present (the E5 tile is hundreds of MB).
set "SRC="
for %%F in ("%WORK%\*.tif") do set "SRC=%%F"
if defined SRC (
  echo [1/3] Using cached EMODnet tile: !SRC!
) else (
  echo [1/3] Downloading EMODnet tile E5 -- LARGE ^(hundreds of MB^); curl progress meter below:
  curl -L -o "%WORK%\E5.tif.zip" "%URL%" || (echo Download failed & exit /b 1)
  tar -xf "%WORK%\E5.tif.zip" -C "%WORK%" || (echo Extract failed & exit /b 1)
  for %%F in ("%WORK%\*.tif") do set "SRC=%%F"
)
if not defined SRC (echo No .tif found in %WORK% & exit /b 1)

echo [2/3] Clipping to corridor box [%W% %S% %E% %N%] ^(native ~115 m kept^)...
gdalwarp -te %W% %S% %E% %N% -tr 0.0010416667 0.0010416667 -tap -dstnodata -9999 -t_srs EPSG:4326 -overwrite "!SRC!" "%WORK%\clip.tif" || (echo gdalwarp failed & exit /b 1)

echo [3/3] Converting to ESRI ASCII...
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "%WORK%\clip.tif" "%OUT%\emodnet-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)
echo Done -^> %OUT%\emodnet-%REGION%.asc
endlocal
