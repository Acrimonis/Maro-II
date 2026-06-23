@echo off
call "%~dp0gdal_env.bat"
call "%~dp0bake-env.bat" || exit /b 1
mkdir "D:\.tmp\litto3d" 2>nul
mkdir "D:\.src\.data\depth" 2>nul
gdalbuildvrt -a_srs EPSG:2154 "D:\.tmp\litto3d\mosaic.vrt" "D:\.src\.data\litto3d\*.asc" || (echo gdalbuildvrt failed & exit /b 1)
gdalwarp -s_srs EPSG:2154 -t_srs EPSG:4326 -te %W% %S% %E% %N% -tr 0.00005 0.00005 -r min -srcnodata -99999 -dstnodata -99999 -overwrite "D:\.tmp\litto3d\mosaic.vrt" "D:\.tmp\litto3d\litto3d.tif" || (echo gdalwarp failed & exit /b 1)
gdal_translate -of AAIGrid -co FORCE_CELLSIZE=TRUE "D:\.tmp\litto3d\litto3d.tif" "D:\.src\.data\depth\litto3d-%REGION%.asc" || (echo gdal_translate failed & exit /b 1)
powershell -NoProfile -Command "$i=[IO.File]::OpenRead('D:\.src\.data\depth\litto3d-%REGION%.asc');$o=[IO.File]::Create('D:\.src\.data\depth\litto3d-%REGION%.asc.gz');$g=New-Object IO.Compression.GZipStream($o,[IO.Compression.CompressionMode]::Compress);$i.CopyTo($g);$g.Dispose();$o.Dispose();$i.Dispose()" || (echo gzip failed & exit /b 1)
del "D:\.src\.data\depth\litto3d-%REGION%.asc"
echo Done -^> D:\.src\.data\depth\litto3d-%REGION%.asc.gz
