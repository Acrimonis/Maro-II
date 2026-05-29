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
