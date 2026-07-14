@echo off
setlocal enabledelayedexpansion
for /f "tokens=1-3 delims=/" %%a in ("%DATE%") do set "DD=%%a" & set "MM=%%b" & set "YYYY=%%c"
for /f "tokens=1-4 delims=:, " %%a in ("%TIME%") do set "HH=%%a" & set "MN=%%b" & set "SS=%%c"
set "MONTHS=JANFEBMARAPRMAYJUNJULAUGSEPOCTNOVDEC"
set /a "MOFF=(!MM!-1)*3"
for %%i in (!MOFF!) do set "MMM=!MONTHS:~%%i,3!"
endlocal & set "TS=%YYYY:~2,2%%MMM%%DD% %HH%:%MN%:%SS%"
