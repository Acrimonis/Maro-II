## ADB Debug Device

**Device**: `192.168.1.81:5555` (Wi-Fi, Xiaomi)
**Alias**: `adb connect 192.168.1.81:5555`

### Install APK
```cmd
"%adb%" -s 192.168.1.81:5555 install -r "app\build\outputs\apk\debug\app-debug.apk"
```

### Uninstall
```cmd
"%adb%" -s 192.168.1.81:5555 uninstall ykws.android.maro
```

### List devices
```cmd
"%adb%" devices
```
