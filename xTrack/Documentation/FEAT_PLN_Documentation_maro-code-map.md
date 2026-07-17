# Plan: maro-code.md — Feature-to-Code Navigation Map

**Status:** discussion — granularity, rot strategy, Key Files relationship being decided
**Created:** 2026-07-17 09:10
**Updated:** 2026-07-17 09:24
**Branch:** feature/doc-update

## Motivation

No single document maps features/packages to source locations. Agents currently discover code structure via `list_files` + `search_files` loops, burning ~1,000–2,000 tokens per task on exploration. A pre-loaded code map would let agents jump directly to relevant files.

## Existing Coverage Gap

| Doc | Covers | Does NOT cover |
|-----|--------|----------------|
| MARO_ARCHITECTURE.md | Spatial engine constraints, memory-mapped I/O, prebake pipeline | Package layout, class responsibilities, feature→code mapping |
| AGENTS.md | Rules, conventions, lazy-load index | Source tree navigation |
| GLOBAL_CONTEXT.md | Keyword → feature file routing | Source tree navigation |
| Feature files | Per-feature `## Key Files` | Cross-feature dependency view |

## Proposed Content

1. **Package map** — `data/`, `spatial/`, `ui/` with one-line role descriptions
2. **Feature ↔ Package cross-reference** — e.g. RegulatedZones → `data/regulation/` + `ui/map/RegulatedZone*.kt`
3. **Key class index** — ~15–20 major classes with one-liner roles
4. **Dependency direction** — `ui/` → `spatial/` + `data/*/` → `data/model/`
5. **"Where to look" guide** — common task → files to touch

## Token Optimization

Current: agent reads feature file → lists source tree → reads candidates → starts work.
With maro-code.md: agent reads feature file → maro-code.md already in prefix cache → jumps to right files.
Estimated savings: 1,000–2,000 tokens per code-navigation task.

## Integration Points

- AGENTS.md Lazy-Load Index: add row for code navigation domain
- GLOBAL_CONTEXT.md Cross-Reference Docs: add entry
- Feature files' `## Key Files`: TBD — replace or complement (see §Decisions)

---

## Discussion: Rot vs. Granularity

### Rot curve by granularity level

| Level | Example | Change frequency | Rot risk |
|-------|---------|-----------------|----------|
| **Package** | `data/regulation/` | Rarely — only on major restructure | Near-zero |
| **Anchor class** | `RegulatedZonesRepository` | Renamed/split on significant refactor | Low — ~1–2 events per feature lifecycle |
| **Supporting class** | `RegulationFilter`, `SpeedZoneBuilder` | Added/removed as feature evolves | Medium |
| **Method/function** | `fun isInsideZone(latlng)` | Changes regularly | High — rots within days |
| **Parameter/signature** | `fun isInsideZone(lat: Double, lng: Double)` | Changes constantly | Immediate |

The rot curve is **exponential** as granularity increases. Each level deeper adds ~10x the change frequency.

### Rule-based drift warning (low token cost)

Instead of periodic scans (which cost tokens), add a **passive detection rule**:

> When an agent navigates to a file/class based on a `maro-code.md` reference and finds it missing, renamed, or structurally wrong — flag it immediately in the hydration file under `## Drift Log`.

- Zero incremental token cost — detection happens during normal workflow
- `#doctor` surfaces accumulated drift entries
- `#doctor fix` clears acknowledged items

This is cheaper than any active scan and catches rot exactly when it matters (when someone tries to use the stale reference).

---

## Discussion: Granularity Compromise

**Recommendation: Package-level entries with ~15–20 anchor classes.**

Package entries are the stable skeleton (near-zero rot). Anchor classes are the entry points — the first file an agent should open for each domain. They're the most stable classes in each package (repositories, viewmodels, major composables), not utility classes.

Example anchor classes:
- `CoastlineSpatialIndex` — not `CoastlinePoint` (model, rarely the entry point)
- `TrackRecorder` — not `TrackSample` (data class)
- `MapScreen` — not individual overlay composables

Supporting classes are discovered naturally from the anchor — the map gets the agent to the right neighborhood, then the agent explores within.

---

## Discussion: Replace vs Complement ## Key Files

**Recommendation: Replace.** Single source of truth.

Rationale:
- `## Key Files` in feature files today is a flat list of "files this feature touched" — it duplicates what maro-code.md would cover at the system level
- maro-code.md's feature→package cross-reference serves the same purpose with more structure
- Dual maintenance guarantees drift

**What feature files keep:** files *unique to the feature's current work* — temporary artifacts, plan files, work-in-progress — that wouldn't appear in the stable system map. The system map is the architecture; the feature file `## Key Files` section becomes a lightweight pointer: "see maro-code.md §RegulatedZones for the stable layout; files specific to this feature iteration below."
