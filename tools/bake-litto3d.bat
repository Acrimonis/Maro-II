@echo off
setlocal enabledelayedexpansion
call "%~dp0gdal_env.bat"
call "%~dp0bake-env.bat" || exit /b 1
REM Bake SHOM Litto3D PACA 2015 shallow tier. Output -> D:\.src\.data\depth\
set "TILES=D:\.src\.data\litto3d"
set "WORK=D:\.tmp\litto3d"
set "SHARED=D:\.src\.data\depth"
if /i "%~1"=="--fresh" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fetch_litto3d_paca.ps1" -Mnt5m -TilesDir "D:\.src\.data\litto3d"
if not exist "%TILES%\*.asc" (echo No .asc tiles in %TILES% - run fetch_litto3d_paca.ps1 first. & exit /b 1)
if not exist "%SHARED%" mkdir "%SHARED%"
if not exist "%WORK%" mkdir "%WORK%"
if exist "%SHARED%\litto3d-%REGION%.asc.gz" (echo [bake-litto3d] Already baked -> %SHARED%\litto3d-%REGION%.asc.gz & goto :eof)
echo [1/3] Building VRT mosaic...
gdalbuildvrt -a_srs EPSG:2154 "%WORK%\mosaic.vrt" "%TILES%\*.asc" || (echo gdalbuildvrt failed & exit /b 1)
echo [2/3] Reprojecting + clipping...
gdalwarp -s_srs EPSG:2154 -t_srs EPSG:4326 -te %W% %S% %E% %N% -tr 0.00005 0.00005 -r min -srcnodata -99999 -dstnodata -99999 -overwrite "%WORK%\mosaic.vrt" "%WORK%\litto3d.tif" || (echo gdalwarp failed & exit /b 1)
echo [3/4] Converting to ASCII...
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "%WORK%\litto3d.tif" "%SHARED%\litto3d-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)
echo [4/4] gzip...
powershell -NoProfile -Command "$i=[IO.File]::OpenRead('%SHARED%\litto3d-%REGION%.asc');$o=[IO.File]::Create('%SHARED%\litto3d-%REGION%.asc.gz');$g=New-Object IO.Compression.GZipStream($o,[IO.Compression.CompressionMode]::Compress);$i.CopyTo($g);$g.Dispose();$o.Dispose();$i.Dispose()" || (echo gzip failed & exit /b 1)
del "%SHARED%\litto3d-%REGION%.asc"
echo Done -> %SHARED%\litto3d-%REGION%.asc.gz
endlocal
