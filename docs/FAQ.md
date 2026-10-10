<!-- scope: reference -->

# FAQ — Troubleshooting

Build, setup and git questions. The onboarding walkthrough itself is
[`docs/SETUP.md`](SETUP.md).

---

## Build Failures

| Symptom | Likely cause |
|---------|-------------|
| `android.useAndroidX` error | Missing `gradle.properties` with `android.useAndroidX=true` |
| Compose BOM not found | Check the BOM version exists in `gradle/libs.versions.toml` |
| SDK platform missing | Run `sdkmanager` for the platform [`app/build.gradle.kts`](../app/build.gradle.kts) declares as `compileSdk` |
| `JAVA_HOME` not set | `set JAVA_HOME=C:\Path\To\JDK` |

---

## Git and SSH

### Why isn't the SSH key stored in the repository?

The **private key** (`id_github_acrimonis`) authenticates you on GitHub. Committing it would let anyone impersonate you — **never do this**.

The **public key** (`id_github_acrimonis.pub`) is safe to share, but it's already registered on your GitHub account. Storing it in the repo is redundant.

### I cloned on a new machine and `git push` asks for a password — why?

The Git config (user name, SSH command, remote URL) lives in `.git/config`, which is **not version-controlled**. See [`docs/SETUP.md`](SETUP.md#git-config-per-machine).

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
