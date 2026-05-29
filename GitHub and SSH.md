## How to Deal with GitHub and Authentication

### Concept

- **Private Key (`id_github_acrimonis`)**: Authenticates you on GitHub. Committing it would let anyone impersonate you — **never do this**.

- **Public Key (`id_github_acrimonis.pub`)**: Safe to share, but it's already registered on your GitHub account. Storing it in the repo is redundant.

### SSH Setup (Per Machine)

Each development machine needs its own SSH key pair added to the Acrimonis GitHub account.

#### Option A: Global Configuration (Recommended & Fully Generic)

Instead of handling paths via Git commands, you configure OpenSSH directly. It natively understands the Unix tilde (`~`) expansion on Windows 11.

1. Open or create the plain text file `~/.ssh/config` (or `%USERPROFILE%\.ssh\config`).

2. Add these exact lines:

   Plaintext

   ```
   Host github.com
      IdentityFile ~/.ssh/id_github_acrimonis
      IdentitiesOnly yes
   ```

#### Option B: Repository-Specific Configuration

If you prefer not to use a global config file, you must configure Git. *Note: Git's internal engine handles `~` properly when executing SSH via its configuration.*

##### 1. Generate a dedicated key for this project

- **For Command Prompt (CMD):**
  
  DOS
  
  ```
  ssh-keygen -t ed25519 -f %USERPROFILE%\.ssh\id_github_acrimonis -C "acrimonis@gmail.com"
  ```

- **For PowerShell:**
  
  PowerShell
  
  ```
  ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\id_github_acrimonis -C "acrimonis@gmail.com"
  ```

##### 2. Print the public key, then add it at [Sign in to GitHub · GitHub](https://github.com/settings/ssh/new)

- **For Command Prompt (CMD):**
  
  DOS
  
  ```
  type %USERPROFILE%\.ssh\id_github_acrimonis.pub
  ```

- **For PowerShell:**
  
  PowerShell
  
  ```
  Get-Content $env:USERPROFILE\.ssh\id_github_acrimonis.pub
  ```

##### 3. Link the key to the local repository

*Run this command inside your repository folder:*

DOS

```
git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis -o IdentitiesOnly=yes"
```

### FAQ / Machine Migration

#### I cloned on a new machine and git push asks for a password — why?

The Git configuration lives locally in `.git\config`. On a new machine, you must initialize your repository settings.

- **If using Option A (Global Config):**
  
  DOS
  
  ```
  git config user.name "Acrimonis"
  git config user.email "acrimonis@gmail.com"
  ```

- **If using Option B (Repo-Specific):**
  
  DOS
  
  ```
  git config user.name "Acrimonis"
  git config user.email "acrimonis@gmail.com"
  git config core.sshCommand "ssh -i ~/.ssh/id_github_acrimonis -o IdentitiesOnly=yes"
  ```

#### I use TortoiseGit — will the SSH key work?

Yes. TortoiseGit routes through the Windows OpenSSH client.

- **If using Option A:** Leave TortoiseGit settings default; it automatically honors the `~/.ssh/config` file rules.

- **If configuring manually:** Point TortoiseGit's secondary identity target to:
  
  Plaintext
  
  ```
  %USERPROFILE%\.ssh\id_github_acrimonis
  ```
