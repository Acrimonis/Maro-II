@echo off
setlocal enabledelayedexpansion
for /f "tokens=1-3 delims=/" %%a in ("%DATE%") do set "DD=%%a" & set "MM=%%b" & set "YYYY=%%c"
for /f "tokens=1-4 delims=:, " %%a in ("%TIME%") do set "HH=%%a" & set "MN=%%b" & set "SS=%%c"
set "MONTHS=JANFEBMARAPRMAYJUNJULAUGSEPOCTNOVDEC"
REM Strip leading zero to avoid octal interpretation (e.g. 08 = invalid octal)
set /a "MM_NUM=1!MM!-100"
set /a "MOFF=(!MM_NUM!-1)*3"
for %%i in (!MOFF!) do set "MMM=!MONTHS:~%%i,3!"
endlocal & set "TS=%YYYY:~2,2%%MMM%%DD% %HH%:%MN%:%SS%"
