@echo off
REM Build script for Maro II APK
REM Generates a signed/unsigned debug APK and copies it to the project root

echo ======================================
echo  Building Maro II APK...
echo ======================================

call gradlew assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo ======================================
echo  Build successful!
echo ======================================

set SOURCE_APK=app\build\outputs\apk\debug\app-debug.apk
set TARGET_APK=Maro2.apk

if exist "%SOURCE_APK%" (
    copy /Y "%SOURCE_APK%" "%TARGET_APK%"
    echo  APK copied to: %TARGET_APK%
) else (
    echo [WARN] APK not found at %SOURCE_APK%
)

echo.
echo  Output: %TARGET_APK%
echo.
