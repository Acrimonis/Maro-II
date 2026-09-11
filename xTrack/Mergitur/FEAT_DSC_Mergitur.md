---
name: Mergitur
status: active
created: 2026-09-11 20:04
modified: 2026-09-11 20:57
---

# Feature: Mergitur

**Description:**
Three-branch integration epic on `feature/mergitur` (cut from `origin/develop` at `025e1bc`): consolidate `feature/tracks-recording` (TR), `feature/tracks-import` (TI) and `feature/global` (GL) into one branch — order TR → TI → GL, one merge commit per branch — then land on `develop` via PR. The TI hop was the risky one (structural move vs in-place rewrite) and was resolved by re-seating TR's edits into TI's restructured map-render path.

## Todos
- [ ] Push `feature/mergitur` and open the PR to `develop` (per-hop SHAs `735cd29`, `e4d56f6`, `431d165`)
- [ ] Device smoke test: confirm dialog, resume-with-backup, live polyline, map track visibility/selection, depth/coastline layers

## Key Files
- `xTrack/Mergitur/260911_FEAT_PLN_Mergitur_three-branch-integration.md` — integration plan v3 (authority) with `## Outcome`

## Implemented
- Three-branch integration complete on `feature/mergitur` — TR → TI → GL merged as `735cd29`, `e4d56f6`, `431d165` on base `025e1bc` (safety tag `pre-mergitur`); build green every hop and after the KDoc correction, scoped TI tests green with no new failures, survival gate proven, Ask review's findings both closed → `260911_FEAT_PLN_Mergitur_three-branch-integration.md`
