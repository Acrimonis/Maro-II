@echo off
REM Maro II APK Deploy: sync D:\.src\.data -> data\app-assets, build, push.
echo.
echo  /====================================================\
echo  ^|           Maro II - APK Deploy Tool                 ^|
echo  \====================================================/
echo.

echo  [0/3] Syncing data from D:\.src\.data ...
for %%D in (coastlines depth regulated-zones) do (
    if exist "D:\.src\.data\%%D\*" (
        if not exist "data\app-assets\%%D" mkdir "data\app-assets\%%D"
        copy /Y "D:\.src\.data\%%D\*" "data\app-assets\%%D\" >nul
    )
)
echo  [OK] Data synced.

echo  [1/3] Building APK...
call "%~dp0apk-build.bat"
if errorlevel 1 (
    echo  [ERROR] Build failed.
    goto :end
)
echo  [OK] Build succeeded.

echo  [2/3] Pushing APK to device...
call "%~dp0apk-push.bat"
call "%~dp0_timestamp.bat"
if errorlevel 1 (
    echo  Maro II - Deploy FAILED. [%TS%]
) else (
    echo  Maro II - Deploy completed successfully. [%TS%]
)

:end
echo ======================================
