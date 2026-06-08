@echo off
REM -- Bake region / clip env (single source = the coastline) --------------------
REM The GDAL .asc clip box is the depth-zone ENVELOPE (coastline bbox + 6 NM), emitted by
REM bake-coastline as a text sidecar  data\app-assets\coastlines\<region>.bbox = "latS latN lonW lonE".
REM Nothing hardcoded: the box follows the real coast. If the sidecar is missing, bake the coastline
REM first (bake-depth auto-runs it). No setlocal -- callers (bake-emodnet/litto3d) consume REGION/W/S/E/N.
set "REGION=nice-frejus"
set "BBOX=%~dp0..\data\app-assets\coastlines\%REGION%.bbox"
if not exist "%BBOX%" (
  echo [bake-env] ERROR: clip envelope sidecar missing:
  echo            %BBOX%
  echo            Bake the coastline first -- it emits the envelope:  tools\bake-coastline.bat
  exit /b 1
)
for /f "usebackq tokens=1-4" %%a in ("%BBOX%") do (set "S=%%a" & set "N=%%b" & set "W=%%c" & set "E=%%d")
echo [bake-env] region=%REGION%  clip from coastline envelope:  W %W%  S %S%  E %E%  N %N%
