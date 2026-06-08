@echo off
setlocal enabledelayedexpansion
call "%~dp0gdal_env.bat"
REM ---------------------------------------------------------------------------
REM Bake the EMODnet DTM 2024 deep backbone into the app's preloaded depth asset.
REM   Output: app\src\main\assets\depth\emodnet-nice-frejus.asc
REM           (ESRI ASCII, elevation rel. LAT -- the app negates it to depth.)
REM Requires on PATH: curl, tar (built into Win10+), GDAL (gdalwarp, gdal_translate).
REM Run from the repo root:  tools\bake_emodnet.bat
REM ---------------------------------------------------------------------------
set "W=6.66"
set "S=43.31"
set "E=7.34"
set "N=43.74"
set "REGION=nice-frejus"
set "URL=https://downloads.emodnet-bathymetry.eu/v12/E5_2024.tif.zip"
set "WORK=%TEMP%\emodnet_e5"
set "OUT=data\app-assets\depth"

if not exist "%OUT%" mkdir "%OUT%"
if not exist "%WORK%" mkdir "%WORK%"

echo [1/4] Downloading EMODnet tile E5 (large; hundreds of MB)...
curl -L -o "%WORK%\E5.tif.zip" "%URL%" || (echo Download failed & exit /b 1)

echo [2/4] Extracting GeoTIFF...
tar -xf "%WORK%\E5.tif.zip" -C "%WORK%" || (echo Extract failed & exit /b 1)

set "SRC="
for %%F in ("%WORK%\*.tif") do set "SRC=%%F"
if not defined SRC (echo No .tif found in %WORK% & exit /b 1)

echo [3/4] Clipping to Cannes-Menton box (native ~115 m resolution kept)...
gdalwarp -te %W% %S% %E% %N% -tr 0.0010416667 0.0010416667 -tap -dstnodata -9999 -t_srs EPSG:4326 -overwrite "!SRC!" "%WORK%\clip.tif" || (echo gdalwarp failed & exit /b 1)

echo [4/4] Converting to ESRI ASCII...
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "%WORK%\clip.tif" "%OUT%\emodnet-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)

echo.
echo Done -^> %OUT%\emodnet-%REGION%.asc
endlocal
