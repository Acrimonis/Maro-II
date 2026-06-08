# Fuzzy Resolution Cascade

Every name-based lookup in xTrack (`#focus`, `#track`, `#sub`, `#sub focus`,
`#status [name]`, `#doc` subcommands, `#diff`, and cross-feature mention
interception) runs through this cascade. Apply it with judgment — most lookups
resolve at the exact or substring stage, which needs no computation. The typo
stage is a "did you mean?" convenience, not a precision instrument; when unsure,
confirm rather than guess.

> **Canonical spec:** `AGENTS.md` (§ 7b). The `#`-command specs, file templates,
> and fuzzy resolution are all defined there.

## The cascade

1. **Exact (case-insensitive)** → accept silently.
2. **Substring** — query contained in a candidate, or vice versa. Unique
   substring match → accept silently. Multiple substring matches → ambiguous,
   confirm (offer the matches).
3. **Closest by typo distance** — when nothing matches above, choose the nearest
   candidate by approximate character-edit distance, then apply the gates below.

## Confidence gates (typo stage)

Think of edit distance as "how many single-character changes to get from the
query to the candidate." Use it as a guide, not a hard threshold:

**Single candidate:**
| Closeness | Decision |
|-----------|----------|
| Very close (≈≤4 edits) | accept |
| Plausible (≈5–8 edits) | confirm ("did you mean *X*?") |
| Far (≳8 edits) | reject |

**Multiple candidates:**
- One clearly closer than the rest (the runner-up is roughly twice as far) →
  accept it.
- Several similarly close → confirm, presenting the top few.
- All far (≳8 edits) → reject.

**No candidates** → reject.

## Acting on the verdict

- **accept** — proceed silently using the match.
- **confirm** — ask the user to confirm, offering the close alternatives. Do not
  mutate any file until confirmed.
- **reject** — no usable match. For features, offer `#track [name]` to create
  it; for commands, offer `#help`; for docs, offer `#doc list`.

## Examples

Candidates: `ShoreDistance`, `MarkerSizing`, `CoastlineCache`

| Query | Stage | Decision |
|-------|-------|----------|
| `shoredistance` | exact (case-insensitive) | accept → ShoreDistance |
| `shore` | unique substring | accept → ShoreDistance |
| `Shor Distanse` | typo, very close | confirm → "did you mean ShoreDistance?" |
| `Marker` | substring of MarkerSizing | accept → MarkerSizing |
| `Coast` | substring | accept → CoastlineCache |
| `ZZZQuux` | typo, far from all | reject → offer `#track` |
