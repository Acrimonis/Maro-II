@echo off
setlocal enabledelayedexpansion

REM ===================================================
REM  Maro II APK Deploy Script
REM  Builds (optional) and installs the debug APK
REM  onto a connected Android device or emulator.
REM ===================================================

set "APP_NAME=Maro II"
set "APK_RELATIVE=app\build\outputs\apk\debug\app-debug.apk"
set "BUILD_SCRIPT=%~dp0apk-build.bat"
set "EXIT_CODE=0"
set "PKG_FALLBACK=ykws.android.maro"

REM  Resolve full path for the APK relative to this script's location
set "APK_PATH=%~dp0%APK_RELATIVE%"

REM --- Helper: print a formatted banner ---
call :print_banner

REM --- Step 1: Check prerequisites ---
call :check_adb
if !ERRORLEVEL! NEQ 0 (
    set "EXIT_CODE=1"
    goto :end
)

REM --- Step 2: Check / build the APK ---
call :ensure_apk
if !ERRORLEVEL! NEQ 0 (
    set "EXIT_CODE=!ERRORLEVEL!"
    goto :end
)

REM --- Step 3: Detect device(s) ---
call :select_device
if !ERRORLEVEL! NEQ 0 (
    set "EXIT_CODE=!ERRORLEVEL!"
    goto :end
)

REM --- Step 4: Install the APK ---
call :install_apk
if !ERRORLEVEL! NEQ 0 (
    set "EXIT_CODE=!ERRORLEVEL!"
    goto :end
)

REM --- Step 5: Launch the app (optional) ---
call :launch_app

:end
echo/
echo ======================================
if !EXIT_CODE! EQU 0 (
    echo  !APP_NAME! — Deploy completed successfully!
) else (
    echo  !APP_NAME! — Deploy FAILED ^(exit code: !EXIT_CODE!^)
)
echo ======================================
echo/
exit /b !EXIT_CODE!

REM ===================================================
REM  FUNCTIONS
REM ===================================================

:print_banner
echo/
echo  /====================================================\
echo  ^|           !APP_NAME! — APK Deploy Tool              ^|
echo  \====================================================/
echo/
exit /b 0

:check_adb
echo  [1/4] Checking prerequisites...
where adb >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo  [ERROR] adb not found in PATH.
    echo          Install Android SDK Platform Tools and add adb to your PATH.
    echo          https://developer.android.com/tools/releases/platform-tools
    exit /b 1
)
echo  [OK] adb found.
echo.
exit /b 0

:ensure_apk
echo  [2/4] Checking APK...
if exist "!APK_PATH!" (
    echo  [OK] APK found at: !APK_RELATIVE!
    echo.
    exit /b 0
)

echo  [WARN] APK not found at: !APK_RELATIVE!
echo.

REM Ask user whether to build
set /p BUILD_CHOICE="  Build the APK now? (Y/n): "
if /i "!BUILD_CHOICE!"=="n" (
    echo  [SKIP] Build skipped. Exiting.
    exit /b 1
)

if not exist "!BUILD_SCRIPT!" (
    echo  [ERROR] Build script not found at: !BUILD_SCRIPT!
    exit /b 1
)

echo  [BUILD] Running apk-build.bat...
echo.
call "!BUILD_SCRIPT!"
if !ERRORLEVEL! NEQ 0 (
    echo  [ERROR] Build failed with exit code !ERRORLEVEL!
    exit /b !ERRORLEVEL!
)

if not exist "!APK_PATH!" (
    echo  [ERROR] APK still not found after build: !APK_RELATIVE!
    exit /b 1
)

echo  [OK] APK ready at: !APK_RELATIVE!
echo.
exit /b 0

:select_device
echo  [3/4] Detecting connected device(s)...

REM Collect online devices only (skip header, daemon messages, offline/unauthorized)
set "DEVICE_COUNT=0"
for /f "skip=1 tokens=1,2" %%a in ('adb devices 2^>nul') do (
    if /i "%%b"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_!DEVICE_COUNT!=%%a"
    )
)

if !DEVICE_COUNT! EQU 0 (
    echo  [ERROR] No Android devices or emulators detected.
    echo          Connect a device via USB ^(with USB debugging enabled^)
    echo          or start an emulator via Android Studio / AVD Manager.
    echo.
    echo  Tip: Run 'adb devices' to debug connectivity.
    exit /b 1
)

echo  [OK] !DEVICE_COUNT! device^(s^) found.

if !DEVICE_COUNT! EQU 1 (
    set "TARGET_DEVICE=!DEVICE_1!"
    echo  [INFO] Using device: !TARGET_DEVICE!
    echo.
    exit /b 0
)

REM Multiple devices — let the user pick
echo.
echo  Multiple devices detected. Please select one:
echo.

for /l %%i in (1,1,!DEVICE_COUNT!) do (
    echo    [%%i] !DEVICE_%%i!
)

echo.
set /p DEVICE_SEL="  Enter device number (1-!DEVICE_COUNT!): "

REM Validate input
if "!DEVICE_SEL!"=="" (
    echo  [ERROR] No selection made.
    exit /b 1
)

REM Resolve selected device via call-set (nested expansion workaround)
call set "TARGET_DEVICE=%%DEVICE_!DEVICE_SEL!%%"
if "!TARGET_DEVICE!"=="" (
    echo  [ERROR] Invalid selection: !DEVICE_SEL!
    exit /b 1
)

echo  [INFO] Using device: !TARGET_DEVICE!
echo.
exit /b 0

:install_apk
echo  [4/4] Installing APK...
echo  Device : !TARGET_DEVICE!
echo  APK    : !APK_RELATIVE!
echo.

adb -s "!TARGET_DEVICE!" install -r "!APK_PATH!"
set "INSTALL_EXIT=!ERRORLEVEL!"

if !INSTALL_EXIT! NEQ 0 (
    echo.
    echo  [ERROR] Installation failed ^(exit code: !INSTALL_EXIT!^).
    echo          Possible causes:
    echo            - App is already installed with a different signature
    echo            - Insufficient storage on device
    echo            - adb connection lost
    echo.
    echo  Tip: Uninstall first with:
    echo    adb -s !TARGET_DEVICE! uninstall ^<package.name^>
    exit /b !INSTALL_EXIT!
)

echo  [OK] APK installed successfully!
echo.
exit /b 0

:launch_app
echo  Launching app...

REM Try to extract the package name from the APK manifest
set "PACKAGE_NAME="

REM 1) Try aapt2 (modern Android SDK)
for /f "tokens=2 delims='" %%a in ('aapt2 dump badging "!APK_PATH!" 2^>nul ^| find "package: name="') do (
    set "PACKAGE_NAME=%%a"
)
if not "!PACKAGE_NAME!"=="" goto :launch_now

REM 2) Try legacy aapt
for /f "tokens=2 delims='" %%a in ('aapt dump badging "!APK_PATH!" 2^>nul ^| find "package: name="') do (
    set "PACKAGE_NAME=%%a"
)
if not "!PACKAGE_NAME!"=="" goto :launch_now

REM 3) Use the hardcoded fallback from build.gradle.kts (ykws.android.maro)
set "PACKAGE_NAME=!PKG_FALLBACK!"

:launch_now
echo  [INFO] Starting package: !PACKAGE_NAME!
adb -s "!TARGET_DEVICE!" shell monkey -p "!PACKAGE_NAME!" -c android.intent.category.LAUNCHER 1 >nul 2>&1
if !ERRORLEVEL! NEQ 0 (
    echo  [WARN] Could not launch the app automatically.
    echo         Open it manually on the device.
) else (
    echo  [OK] App launched on device.
)
echo.
exit /b 0
