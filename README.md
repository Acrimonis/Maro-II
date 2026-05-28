# Maro II

A Modern Android Development (MAD) greenfield project — Jetpack Compose, Material3, Kotlin DSL.

> **Status:** Active development on `develop` branch.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **Language** | Kotlin 2.0.0 |
| **UI** | Jetpack Compose + Material3 (BOM 2024.05.00) |
| **Architecture** | ViewModel + StateFlow + Repository |
| **Min / Target SDK** | 26 / 34 |
| **Build** | Gradle 8.7 — Kotlin DSL + Version Catalog |
| **Java** | Corretto 21 (JDK 17 toolchain) |

---

## SSH Setup (per machine)

Each development machine needs its own SSH key pair added to the
[Acrimonis GitHub account](https://github.com/settings/keys).

```bash
# 1. Generate a dedicated key for this project
ssh-keygen -t ed25519 -f ~/.ssh/id_github_acrimonis -C "acrimonis@gmail.com"

# 2. Print the public key, then add it at https://github.com/settings/ssh/new
type ~/.ssh/id_github_acrimonis.pub

# 3. In the cloned repo, tell Git to use this key
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis"
```

> The **private** key (`id_ed25519_acrimonis`) stays on the machine — never commit it.
> The **public** key (`id_ed25519_acrimonis.pub`) is uploaded to GitHub only.

---

## Prerequisites

| Dependency | Check |
|-----------|-------|
| **Java 17+** | `java -version` |
| **Android SDK** | `$ANDROID_SDK` must point to a valid SDK with `platforms;android-34` and `build-tools;34.0.0` installed |

---

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Git Workflow

```
main        ●───────────────────────  (tagged releases only)
             \
develop      ●──●──●────●──●──●──●  (HEAD — daily development)
                   \
feature/xxx         ●──●──●──        (isolated feature work)
```

### Rules

| Branch | Purpose | Source | Merges into |
|--------|---------|--------|-------------|
| `main` | Production releases | — | — |
| `develop` | Integration branch | `main` | `main` (at release) |
| `feature/*` | Isolated feature work | `develop` | `develop` |

### Starting a feature

```bash
git checkout develop
git checkout -b feature/my-feature
# work, commit, optionally push
```

### Completing a feature

```bash
git checkout develop
git merge --no-ff feature/my-feature
git branch -d feature/my-feature
```

> `--no-ff` preserves the feature branch topology in the commit history.

### Releasing

```bash
git checkout main
git merge --no-ff develop
git tag v0.1.0
git push --tags
```

### Notes

- Feature branches may be pushed to GitHub at the developer's discretion.
- `main` is merged from `develop` only when a release is cut.
- All day-to-day work targets `develop`.

---

## Project Structure

```
├── build.gradle.kts              # Root build (plugin aliases, apply false)
├── settings.gradle.kts           # Module includes, repository config
├── gradle.properties             # AndroidX, JVM args
├── local.properties              # SDK path (gitignored)
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/                  # Gradle 8.7 wrapper
├── app/
│   ├── build.gradle.kts          # App module config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       └── java/com/example/newapp/
│           └── MainActivity.kt
└── README.md
```

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `ANDROID_SDK` | `C:\Users\nbadino\Programs_nICo\_Dev_\Android_SDK_CLI` |
| `JAVA_HOME` | `C:\Users\nbadino\.java\corretto-21.0.7` |
| `GRADDLE_HOME` | `C:\Users\nbadino\Programs_nICo\_Dev_\Graddle` |
