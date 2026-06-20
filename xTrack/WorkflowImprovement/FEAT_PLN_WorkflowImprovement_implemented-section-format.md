<!-- scope: feature -->
# `## Implemented` Section — Format Decision

## Context

The `## Implemented` section in `FEAT_DSC_[Feature].md` files is used by the `#implement` pipeline (AGENTS.md §7b.16) to record built features as technical release notes.

## Decision (2026-06-18)

Format: **Title (bold) + Description (plain text) + Files**

```markdown
## Implemented

- **Feature name** — Concise description of what was built and why.
  *Files:* `File1.kt`, `File2.kt`
```

### Explicitly excluded
- `*Key type:*` line — dropped per user preference
- Method signatures, config keys, breaking changes — not needed at this granularity

## AGENTS.md changes needed

Update §7b.16 (line 108) to reflect the agreed format:

**Current:**
> a `## Implemented` where we keep track implmented features..

**Proposed:**
> a `## Implemented` section recording built features as concise bullet-point release notes (bold title + description + files). Updated automatically by the `#implement` pipeline (§7b.16).
