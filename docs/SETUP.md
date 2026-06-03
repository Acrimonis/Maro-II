<!-- scope: onboarding -->

# Setup — Machine Onboarding

Everything needed to get a development machine ready for Maro II.

---

## Prerequisites

| Dependency | Check |
|-----------|-------|
| **Java 17+** | `java -version` |
| **Android SDK** | `$ANDROID_SDK` must point to a valid SDK with `platforms;android-34` and `build-tools;34.0.0` installed |

---

## Environment Variables

| Variable | Value |
|----------|-------|
| `ANDROID_SDK` | `C:\Users\nbadino\Programs_nICo\_Dev_\Android_SDK_CLI` |
| `JAVA_HOME` | `C:\Users\nbadino\.java\corretto-21.0.7` |
| `GRADDLE_HOME` | `C:\Users\nbadino\Programs_nICo\_Dev_\Graddle` |

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

### Why isn't the SSH key stored in the repository?

The **private key** (`id_github_acrimonis`) authenticates you on GitHub. Committing it would let anyone impersonate you — **never do this**.

The **public key** (`id_github_acrimonis.pub`) is safe to share, but it's already registered on your GitHub account. Storing it in the repo is redundant.

### I cloned on a new machine and `git push` asks for a password — why?

The Git config (user name, SSH command, remote URL) lives in `.git/config`, which is **not version-controlled**. See [Git Config](#git-config-per-machine) above.

### Why can't I just use my global GitHub account?

You can — this project uses a dedicated `Acrimonis` account for separation. Your global account (`nbadino-doca`) stays untouched and works as before for all other repositories.

### I use TortoiseGit — will the SSH key work?

Yes. TortoiseGit uses the same SSH client as the command line. Point it to the key at:

```
C:\Users\<you>\.ssh\id_github_acrimonis
```

Or set it globally in your `%USERPROFILE%\.ssh\config`:

```
Host github.com
    IdentityFile ~/.ssh/id_github_acrimonis
```
