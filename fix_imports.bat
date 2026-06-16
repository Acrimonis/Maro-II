@echo off
setlocal enabledelayedexpansion

set "files=app\src\main\java\ykws\android\maro\data\depth\RasterCache.kt app\src\main\java\ykws\android\maro\ui\map\ArcLayoutToggle.kt app\src\main\java\ykws\android\maro\ui\map\CoastlineViewModel.kt app\src\main\java\ykws\android\maro\ui\map\DashboardPanel.kt app\src\main\java\ykws\android\maro\ui\map\DepthColorRamp.kt app\src\main\java\ykws\android\maro\ui\map\DepthViewModel.kt app\src\main\java\ykws\android\maro\ui\map\FanIconComponents.kt app\src\main\java\ykws\android\maro\ui\map\LowDepthWarningBitmap.kt app\src\main\java\ykws\android\maro\ui\map\MapScreen.kt app\src\main\java\ykws\android\maro\ui\map\RegulatedZoneIconProvider.kt"

for %%f in (%files%) do (
    echo Processing %%f
    powershell -Command "$c = Get-Content '%%f' -Raw; $c = $c -replace '(?s)import ykws\.android\.maro\.config\.AppConfig\r?\n?', ''; $c = $c -replace '(package ykws\.android\.maro\.[^\r\n]+)', ('${1}' + [char]13 + [char]10 + 'import ykws.android.maro.config.AppConfig'); Set-Content -NoNewline '%%f' -Value $c; echo Done."
)
