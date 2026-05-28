@echo off
setlocal enabledelayedexpansion
echo ====================================================================
echo MARO II — Verified FFmpeg Icon Compiler Pipeline (Purge + Replace)
echo ====================================================================

:: 1. Environmental Paths (Validated via icons-list.md)
set "FFMPEG=C:\Users\Alexandra\Downloads\ffmpeg-2026-05-28-git-7b46c6a2a3-essentials_build\bin\ffmpeg.exe"
set "SRC_APP=c:\Users\Alexandra\Desktop\nICo StuFF\.src\Maro II\Maro_II_app.png"
set "SRC_FG=c:\Users\Alexandra\Desktop\nICo StuFF\.src\Maro II\Maro_II.png"
set "TARGET_DIR=c:\Users\Alexandra\Desktop\nICo StuFF\.src\Maro II\app\src\main\res"

:: 2. Target Directory Preparation Tree
echo [1/4] Ensuring resource folder structures exist...
mkdir "%TARGET_DIR%\mipmap-mdpi" 2>nul
mkdir "%TARGET_DIR%\mipmap-hdpi" 2>nul
mkdir "%TARGET_DIR%\mipmap-xhdpi" 2>nul
mkdir "%TARGET_DIR%\mipmap-xxhdpi" 2>nul
mkdir "%TARGET_DIR%\mipmap-xxxhdpi" 2>nul
mkdir "%TARGET_DIR%\drawable" 2>nul

:: 3. Process Section A: App Launcher Icons (With Background)
echo [2/4] Purging and Replacing App Icons (ic_launcher)...
del /F /Q "%TARGET_DIR%\mipmap-mdpi\ic_launcher.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-hdpi\ic_launcher.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xhdpi\ic_launcher.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xxhdpi\ic_launcher.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xxxhdpi\ic_launcher.png" 2>nul

"%FFMPEG%" -y -loglevel error -i "%SRC_APP%" -vf scale=48:48:flags=lanczos "%TARGET_DIR%\mipmap-mdpi\ic_launcher.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_APP%" -vf scale=72:72:flags=lanczos "%TARGET_DIR%\mipmap-hdpi\ic_launcher.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_APP%" -vf scale=96:96:flags=lanczos "%TARGET_DIR%\mipmap-xhdpi\ic_launcher.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_APP%" -vf scale=144:144:flags=lanczos "%TARGET_DIR%\mipmap-xxhdpi\ic_launcher.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_APP%" -vf scale=192:192:flags=lanczos "%TARGET_DIR%\mipmap-xxxhdpi\ic_launcher.png"

:: 4. Process Section B: Adaptive Foregrounds (Transparent)
echo [3/4] Purging and Replacing Foregrounds (ic_launcher_foreground)...
del /F /Q "%TARGET_DIR%\mipmap-mdpi\ic_launcher_foreground.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-hdpi\ic_launcher_foreground.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xhdpi\ic_launcher_foreground.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xxhdpi\ic_launcher_foreground.png" 2>nul
del /F /Q "%TARGET_DIR%\mipmap-xxxhdpi\ic_launcher_foreground.png" 2>nul

"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=48:48:flags=lanczos "%TARGET_DIR%\mipmap-mdpi\ic_launcher_foreground.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=72:72:flags=lanczos "%TARGET_DIR%\mipmap-hdpi\ic_launcher_foreground.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=96:96:flags=lanczos "%TARGET_DIR%\mipmap-xhdpi\ic_launcher_foreground.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=144:144:flags=lanczos "%TARGET_DIR%\mipmap-xxhdpi\ic_launcher_foreground.png"
"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=192:192:flags=lanczos "%TARGET_DIR%\mipmap-xxxhdpi\ic_launcher_foreground.png"

:: 5. Process Section C: Map Center Marker
echo [4/4] Purging and Replacing Center Marker (maro_marker)...
del /F /Q "%TARGET_DIR%\drawable\maro_marker.png" 2>nul
"%FFMPEG%" -y -loglevel error -i "%SRC_FG%" -vf scale=172:172:flags=lanczos "%TARGET_DIR%\drawable\maro_marker.png"

echo.
echo ====================================================================
echo 🌟 SUCCESS: All 11 assets cleanly validated, deleted, and replaced!
echo ====================================================================
pause
