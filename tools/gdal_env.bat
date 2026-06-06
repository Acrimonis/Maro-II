@echo off
REM ===========================================================================
REM  Derive the full GDAL environment from a single GDAL_HOME (the extracted
REM  GISInternals root), so callers only set GDAL_HOME — not PATH + GDAL_DATA +
REM  PROJ_LIB. NO `setlocal` here: these vars must persist to the calling bake
REM  script. If GDAL_HOME is unset/invalid, fall back to whatever GDAL is already
REM  on PATH (system install / SDKShell). See docs/DepthMappingBake.md.
REM ===========================================================================
if not defined GDAL_HOME goto :eof
if exist "%GDAL_HOME%\bin\gdal\apps\gdalwarp.exe" goto :gisinternals
if exist "%GDAL_HOME%\bin\gdalwarp.exe" goto :simple
echo WARNING: GDAL_HOME="%GDAL_HOME%" is set but gdalwarp was not found under it; using PATH GDAL if any.
goto :eof

:gisinternals
REM GISInternals layout: EXEs in bin\gdal\apps, DLLs in bin, data + proj.db alongside.
set "PATH=%GDAL_HOME%\bin;%GDAL_HOME%\bin\gdal\apps;%GDAL_HOME%\bin\proj9\apps;%PATH%"
set "GDAL_DATA=%GDAL_HOME%\bin\gdal-data"
set "PROJ_LIB=%GDAL_HOME%\bin\proj9\SHARE"
set "PROJ_DATA=%GDAL_HOME%\bin\proj9\SHARE"
echo Using GDAL from GDAL_HOME=%GDAL_HOME%
goto :eof

:simple
REM Flat layout: EXEs directly in bin (e.g. conda / some portable builds).
set "PATH=%GDAL_HOME%\bin;%PATH%"
if exist "%GDAL_HOME%\bin\gdal-data" set "GDAL_DATA=%GDAL_HOME%\bin\gdal-data"
if exist "%GDAL_HOME%\share\proj"    set "PROJ_LIB=%GDAL_HOME%\share\proj"
if exist "%GDAL_HOME%\share\proj"    set "PROJ_DATA=%GDAL_HOME%\share\proj"
echo Using GDAL from GDAL_HOME=%GDAL_HOME%
goto :eof
