@echo off
setlocal enabledelayedexpansion

REM ===================================================
REM  Maro II APK Deploy Script (orchestrator)
REM  Builds the debug APK, then pushes to device.
REM  If build fails, push is skipped.
REM ===================================================

set "APP_NAME=Maro II"
set "EXIT_CODE=0"

echo.
echo  /====================================================\
echo  ^|           !APP_NAME! - APK Deploy Tool              ^|
echo  \====================================================/
echo.

REM --- Step 1: Build ---
echo  [1/2] Building APK...
echo.
call "%~dp0apk-build.bat"
if !ERRORLEVEL! NEQ 0 (
    echo  [ERROR] Build failed with exit code !ERRORLEVEL!.
    echo          Aborting deploy. Fix the build error and try again.
    set "EXIT_CODE=!ERRORLEVEL!"
    goto :end
)

echo  [OK] Build succeeded.

REM --- Step 2: Push ---
echo  [2/2] Pushing APK to device...
echo.
call "%~dp0apk-push.bat"
set "EXIT_CODE=!ERRORLEVEL!"

:end
echo.
echo ======================================
if !EXIT_CODE! EQU 0 (
    echo  !APP_NAME! - Deploy completed successfully!
) else (
    echo  !APP_NAME! - Deploy FAILED (exit code: !EXIT_CODE!)
)
echo ======================================
echo.
exit /b !EXIT_CODE!
