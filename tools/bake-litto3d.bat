@echo off
setlocal enabledelayedexpansion
call "%~dp0gdal_env.bat"
call "%~dp0bake-env.bat"
REM ---------------------------------------------------------------------------
REM Bake SHOM Litto3D PACA 2015 shallow tier -> data\app-assets\depth\litto3d-<region>.asc
REM   (ESRI ASCII, elevation rel. IGN69 in WGS84; the app negates AND shifts IGN69->LAT ~0.40 m).
REM PREREQUISITE: 1 m .asc tiles in tools\litto3d_tiles\ (fetch: tools\fetch_litto3d_paca.ps1).
REM Clip box (W/S/E/N) + REGION come from bake-env.bat. "-r min" keeps the shoalest value
REM (collision-conservative). Requires GDAL on PATH. Run from repo root.
REM ---------------------------------------------------------------------------
set "RES=0.00005"
set "TILES=tools\litto3d_tiles"
set "WORK=%TEMP%\litto3d"
set "OUT=data\app-assets\depth"
if not exist "%TILES%\*.asc" (echo No .asc tiles in %TILES% - run tools\fetch_litto3d_paca.ps1 first. & exit /b 1)
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%WORK%" mkdir "%WORK%"
echo [1/3] Building VRT mosaic of Litto3D tiles ^(Lambert-93 / IGN69^)...
gdalbuildvrt -a_srs EPSG:2154 "%WORK%\mosaic.vrt" "%TILES%\*.asc" || (echo gdalbuildvrt failed & exit /b 1)
echo [2/3] Reprojecting Lambert-93 -^> WGS84, clip to [%W% %S% %E% %N%], downsample ^(shoalest^)...
gdalwarp -s_srs EPSG:2154 -t_srs EPSG:4326 -te %W% %S% %E% %N% -tr %RES% %RES% -r min ^
  -srcnodata -99999 -dstnodata -99999 -overwrite "%WORK%\mosaic.vrt" "%WORK%\litto3d.tif" || (echo gdalwarp failed & exit /b 1)
echo [3/3] Converting to ESRI ASCII...
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "%WORK%\litto3d.tif" "%OUT%\litto3d-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)
echo Done -^> %OUT%\litto3d-%REGION%.asc
endlocal
