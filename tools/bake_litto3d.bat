@echo off
setlocal enabledelayedexpansion
call "%~dp0gdal_env.bat"
REM ---------------------------------------------------------------------------
REM Bake the SHOM Litto3D PACA 2015 shallow tier into the app's preloaded asset.
REM   Output: app\src\main\assets\depth\litto3d-nice-frejus.asc
REM           (ESRI ASCII, elevation rel. IGN69 in WGS84; the app negates it AND
REM            applies the IGN69->LAT shift of ~0.40 m at parse time.)
REM
REM PREREQUISITE -- fetch the 1 m .asc tiles covering Cannes->Menton via the PUBLIC SHOM
REM   INSPIRE pre-package API (no account, no cart):
REM       tools\fetch_litto3d_paca.ps1
REM   (downloads + extracts *.asc, Lambert-93 / IGN69, into tools\litto3d_tiles\). The old
REM   diffusion.shom.fr account+cart UI is now only an optional fallback. See docs\DepthMappingBake.md.
REM Requires on PATH: GDAL (gdalbuildvrt, gdalwarp, gdal_translate). "-r min" keeps the
REM   shoalest value when downsampling (collision-conservative).
REM Run from the repo root:  tools\bake_litto3d.bat
REM ---------------------------------------------------------------------------
REM Clip box defaults to the full WATER_BBOX; override via env vars (W/S/E/N) to bake only a
REM focused coast stretch when you fetched a sub-window (keeps the .asc small; merge fills the rest).
if not defined W set "W=6.70"
if not defined S set "S=43.40"
if not defined E set "E=7.31"
if not defined N set "N=43.75"
set "REGION=nice-frejus"
set "RES=0.00005"
set "TILES=tools\litto3d_tiles"
set "WORK=%TEMP%\litto3d"
set "OUT=app\src\main\assets\depth"

if not exist "%TILES%\*.asc" (echo No .asc tiles in %TILES% - run tools\fetch_litto3d_paca.ps1 first ^(see header^). & exit /b 1)
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%WORK%" mkdir "%WORK%"

echo [1/3] Building VRT mosaic of Litto3D tiles (Lambert-93 / IGN69)...
gdalbuildvrt -a_srs EPSG:2154 "%WORK%\mosaic.vrt" "%TILES%\*.asc" || (echo gdalbuildvrt failed & exit /b 1)

echo [2/3] Reprojecting Lambert-93 -^> WGS84, clip to box, downsample (shoalest)...
gdalwarp -s_srs EPSG:2154 -t_srs EPSG:4326 -te %W% %S% %E% %N% -tr %RES% %RES% -r min ^
  -srcnodata -99999 -dstnodata -99999 -overwrite "%WORK%\mosaic.vrt" "%WORK%\litto3d.tif" || (echo gdalwarp failed & exit /b 1)

echo [3/3] Converting to ESRI ASCII...
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "%WORK%\litto3d.tif" "%OUT%\litto3d-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)

echo.
echo Done -^> %OUT%\litto3d-%REGION%.asc  (app applies the IGN69-^>LAT shift of ~0.40 m)
endlocal
