@echo off
REM -- Bake region / clip env (single source = the W/E coastline-point props) ----
REM Reads maro.region.lonWest / lonEast from gradle.properties (the SAME corridor CoastlineGenerator
REM clips to, via BuildConfig). The GDAL .asc clip uses them as-is for E/W; S/N is a GENEROUS seaward
REM fetch window - NOT a coverage cap. Real coverage = coastline + 6 NM, derived at merge; the .asc
REM only has to CONTAIN it. No float math needed: the 6 NM that matters is seaward (south), covered by
REM the generous S; the E/W ends are the corridor cut, beyond which is out-of-region sea.
REM No setlocal: callers (bake-emodnet/litto3d.bat) consume REGION / W / S / E / N.
set "REGION=nice-frejus"
set "PROPS=%~dp0..\gradle.properties"
set "LON_WEST=6.70"
set "LON_EAST=7.31"
if exist "%PROPS%" (
  for /f "usebackq tokens=2 delims==" %%v in (`findstr /b /c:"maro.region.lonWest=" "%PROPS%"`) do set "LON_WEST=%%v"
  for /f "usebackq tokens=2 delims==" %%v in (`findstr /b /c:"maro.region.lonEast=" "%PROPS%"`) do set "LON_EAST=%%v"
)
set "W=%LON_WEST%"
set "E=%LON_EAST%"
set "S=43.28"
set "N=43.80"
echo [bake-env] region=%REGION%  corridor lon %LON_WEST%..%LON_EAST%  seaward clip S/N %S%/%N%
