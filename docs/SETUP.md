<!-- scope: onboarding -->

# Setup — Machine Onboarding

Everything needed to get a development machine ready for Maro II.

---

## Prerequisites

| Dependency | Check |
|-----------|-------|
| **JDK 17+** (Corretto 21 is what `JAVA_HOME` currently points at) | `java -version` |
| **Android SDK** | `$ANDROID_SDK` must point to a valid SDK carrying the platform and build-tools levels [`app/build.gradle.kts`](../app/build.gradle.kts) declares (`compileSdk` / `targetSdk`) |

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `ANDROID_SDK` | `C:\Users\nbadino\Programs_nICo\_Dev_\Android_SDK_CLI` |
| `JAVA_HOME` | `C:\Users\nbadino\.java\corretto-21.0.7` |
| `GRADLE_HOME` | `C:\Users\nbadino\Programs_nICo\_Dev_\Graddle` |

---

## GDAL

GDAL is needed only to **regenerate** baked data — the app itself never requires it. The bake
scripts wire it up for the duration of a bake; it does not need to be on the global PATH.

Install (Windows — pick one):

- **Portable (minimal):** from <https://gisinternals.com/release.php> download the **x64 · MSVC 2022 ·
  latest stable GDAL** "Compiled binaries" zip, extract it, and run `SDKShell.bat` (sets `PATH` +
  `PROJ_LIB` + `GDAL_DATA`); launch the build from that shell.
- **conda:** `conda install -c conda-forge gdal`.

**Simplest — one variable:** set **`GDAL_HOME`** = your extracted GDAL root (e.g. `D:\…\GDal`).
The bake scripts (`tools\gdal_env.bat`) derive `PATH` + `GDAL_DATA` + `PROJ_LIB`/`PROJ_DATA` from it
automatically (GISInternals layout) — nothing else to configure.

Or put GDAL on `PATH` globally (`<GDAL>` = the root): `PATH` += `<GDAL>\bin` and
`<GDAL>\bin\gdal\apps`; `GDAL_DATA` = `<GDAL>\bin\gdal-data`; `PROJ_DATA` = `<GDAL>\bin\proj9\SHARE`.

Verify with `gdalwarp --version` and `projinfo EPSG:2154` (the latter confirms PROJ).

> The bakes read the region bounding box from `maro.region.lonWest` / `maro.region.lonEast` in
> [`gradle.properties`](../gradle.properties) — the single region source of truth.

---

## SSH Key Setup (per machine)

```bash
# 1. Generate a dedicated key for this project
ssh-keygen -t ed25519 -f ~/.ssh/id_github_acrimonis -C "acrimonis@gmail.com"

# 2. Print the public key, then add it at https://github.com/settings/ssh/new
type ~/.ssh/id_github_acrimonis.pub

# 3. In the cloned repo, tell Git to use this key
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis"
```

> The **private** key (`id_github_acrimonis`) stays on the machine — **never commit it**.
> The **public** key (`id_github_acrimonis.pub`) is uploaded to GitHub only.

---

## Git Config (per machine)

On each new machine, re-run:

```bash
git config user.name "Acrimonis"
git config user.email "acrimonis@gmail.com"
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis"
```

These values live in `.git/config`, which is not version-controlled.

---

## ADB Debug Device

**Device**: `192.168.1.81:5555` (Wi-Fi, Xiaomi)

```cmd
:: Connect
adb connect 192.168.1.81:5555

:: Install APK
adb -s 192.168.1.81:5555 install -r "app\build\outputs\apk\debug\app-debug.apk"

:: Uninstall
adb -s 192.168.1.81:5555 uninstall ykws.android.maro

:: List devices
adb devices
```

---

## FAQ

Build, setup and git questions live in the one FAQ: [`docs/FAQ.md`](FAQ.md).
