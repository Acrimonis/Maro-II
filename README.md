<!-- scope: core -->

# Maro II

Marine navigation app for the Nice-to-Fréjus corridor. Jetpack Compose, Material3, Kotlin DSL,
ViewModel + StateFlow + Repository, osmdroid.

> **Status:** Active development.
>
> This file is orientation only: what the app is, how to run it, roughly where things live.
> Versions, file inventories and anything derivable are not repeated here — follow the pointers.

---

## Tech stack

Kotlin · Jetpack Compose + Material3 · ViewModel + StateFlow + Repository · osmdroid ·
Gradle Kotlin DSL with a version catalog.

Versions, SDK levels and toolchain pins are authoritative in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and
[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).

---

## Build pipeline

Three stages: **bake** (data prep — needs GDAL) → **build** (package only) → **deploy** (install + launch).

```cmd
apk-bake.bat              :: interactive — pick what to bake
apk-bake.bat all          :: bake everything
apk-build.bat             :: package only (gradlew assembleDebug)
apk-deploy.bat            :: sync data from D:\.src\.data, build, install, launch
apk-push.bat              :: install + launch an already-built APK
```

Whatever has been baked ships; whatever has not is simply absent (the app falls back to a live
fetch at runtime where it can). Bake outputs live in the gitignored `data/app-assets/` tree and are
packaged at build time through an asset srcDir — so a fresh clone bakes before it builds.

Granular, non-interactive `tools\bake-*.bat` scripts exist for headless use. The bake-vs-build
contract, the script inventory and the dependency rules live in the BakeNormalization feature file —
**do not restate them here**: [`xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md`](xTrack/BakeNormalization/FEAT_DSC_BakeNormalization.md).

GDAL installation and environment setup: [`docs/SETUP.md`](docs/SETUP.md#gdal).

---

## Layout

| Path | What it is |
|------|------------|
| `app/` | Android module — Compose UI, ViewModels, data layer, checked-in config assets |
| `data/app-assets/` | Baked assets — gitignored, produced by `apk-bake.bat` |
| `tools/` | Granular bake scripts + GDAL environment helpers |
| `docs/` | Cross-cutting reference — architecture, setup, FAQ, UI guidelines, `cmd_help_*` pages |
| `xTrack/` | Feature tracking — `FEAT_DSC_` epics, `FEAT_PLN_` plans, `FEAT_HYD_` session state |

Pointers, not copies:

- source packages and the feature-to-code map → [`docs/maro-code.md`](docs/maro-code.md)
- spatial engine, bathymetry, coastline → [`docs/MARO_ARCHITECTURE.md`](docs/MARO_ARCHITECTURE.md)
- rules, command registry, doc routing by task domain → [`AGENTS.md`](AGENTS.md) and its Lazy-Load Index
- machine onboarding, SSH, ADB, GDAL → [`docs/SETUP.md`](docs/SETUP.md)
