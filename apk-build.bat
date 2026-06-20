@echo off
REM Build the Maro II debug APK. This ONLY builds - it does NOT bake data.
REM Bake assets first with apk-bake.bat (it writes the gitignored data\app-assets tree, which the
REM build packages into the APK); deploy with apk-deploy.bat. Whatever has been baked ships; what
REM hasn't is simply absent (the app falls back to a live fetch at runtime where it can).
echo ======================================
echo  Building Maro II APK...
echo ======================================
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
echo ======================================
echo  Build successful -^> app\build\outputs\apk\debug\app-debug.apk
echo  Deploy: apk-deploy.bat (build + push) or apk-push.bat (push only)
echo ======================================
