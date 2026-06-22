@echo off
setlocal
REM Bake the depth grid: merge EMODnet + Litto3D -> clip to 6 NM-of-coast.
REM Master copy goes to D:\.src\.data\depth\. apk-deploy.bat syncs to data\app-assets\.
set "REGION=nice-menton"
set "SHARED_COAST=D:\.src\.data\coastlines"
set "SHARED_DEPTH=D:\.src\.data\depth"
set "AUTODEPS=1"
set FRESH=
for %%A in (%*) do (
  if /i "%%A"=="--no-auto-deps" set "AUTODEPS=0"
  if /i "%%A"=="--fresh" set "FRESH=--fresh"
)

set "COASTBBOX=%SHARED_COAST%\%REGION%.bbox"
set "COASTBIN=%SHARED_COAST%\%REGION%.bin"
set "EMASC=%SHARED_DEPTH%\emodnet-%REGION%.asc"

if defined FRESH echo [bake-depth] --fresh: clearing cached depth sources...
if defined FRESH del /q "%EMASC%" "%SHARED_DEPTH%\litto3d-%REGION%.asc.gz" 2>nul

if not exist "%COASTBBOX%" if exist "%COASTBIN%" (
  echo [bake-depth] Emitting .bbox offline from coastline...
  call gradlew testDebugUnitTest --tests "*CoastlineBboxTest*" -Dmaro.bake=true --rerun-tasks
  if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)
if not exist "%COASTBBOX%" if not exist "%COASTBIN%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] Auto-running bake-coastline...
    call "%~dp0bake-coastline.bat"
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
  ) else (echo [bake-depth] ERROR: coastline missing & exit /b 1)
)
if not exist "%EMASC%" (
  if "%AUTODEPS%"=="1" (
    echo [bake-depth] Auto-running bake-emodnet...
    call "%~dp0bake-emodnet.bat" %FRESH%
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
  ) else (echo [bake-depth] ERROR: EMODnet missing & exit /b 1)
)
if defined FRESH call "%~dp0bake-litto3d.bat" %FRESH%
if not defined FRESH if exist "D:\.src\.data\litto3d\*.asc" if not exist "%SHARED_DEPTH%\litto3d-%REGION%.asc.gz" call "%~dp0bake-litto3d.bat"

REM Sync shared -> local so Gradle test can find source files
if not exist "data\app-assets\coastlines" mkdir "data\app-assets\coastlines"
if not exist "data\app-assets\depth" mkdir "data\app-assets\depth"
copy /Y "%SHARED_COAST%\*" "data\app-assets\coastlines\" >nul
copy /Y "%SHARED_DEPTH%\*" "data\app-assets\depth\" >nul 2>nul

echo [bake-depth] Merge + 6 NM clip...
call gradlew testDebugUnitTest --tests "*DepthPrebakeTest*" -Dmaro.prebake=true --rerun-tasks
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

REM Archive result to shared store
if not exist "%SHARED_DEPTH%" mkdir "%SHARED_DEPTH%"
copy /Y "data\app-assets\depth\*.bin" "%SHARED_DEPTH%\" >nul
copy /Y "data\app-assets\depth\*.asc" "%SHARED_DEPTH%\" >nul 2>nul
copy /Y "data\app-assets\depth\*.asc.gz" "%SHARED_DEPTH%\" >nul 2>nul
echo [bake-depth] Archived to %SHARED_DEPTH%
endlocal
