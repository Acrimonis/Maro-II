<!-- scope: reference -->

# Git Workflow

Branching strategy, feature lifecycle, and release process for Maro II.

---

## Branch Model

```
main        ●───────────────────────  (tagged releases only)
             \
develop      ●──●──●────●──●──●──●  (HEAD — daily development)
                   \
feature/xxx         ●──●──●──        (isolated feature work)
```

## Rules

| Branch | Purpose | Source | Merges into |
|--------|---------|--------|-------------|
| `main` | Production releases | — | — |
| `develop` | Integration branch | `main` | `main` (at release) |
| `feature/*` | Isolated feature work | `develop` | `develop` |

---

## Starting a Feature

```bash
git checkout develop
git checkout -b feature/my-feature
# work, commit, optionally push
```

## Completing a Feature

```bash
git checkout develop
git merge --no-ff feature/my-feature
git branch -d feature/my-feature
```

> `--no-ff` preserves the feature branch topology in the commit history.

## Releasing

```bash
git checkout main
git merge --no-ff develop
git tag v0.1.0
git push --tags
```

## Notes

- Feature branches may be pushed to GitHub at the developer's discretion.
- `main` is merged from `develop` only when a release is cut.
- All day-to-day work targets `develop`.
