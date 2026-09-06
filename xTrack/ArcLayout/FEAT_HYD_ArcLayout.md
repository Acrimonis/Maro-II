# Hydration: ArcLayout — control-stack button swap

**Baked:** 2026-09-06 21:19 UTC

## Micro-State Summary

Swapped the layer-fan and add-marker button positions in the main-screen control stack. The Add Zone (add-marker) button now sits above the layer FanLayout; the fan sits below it. Pure ordering change in `MapContent()` — no logic, geometry, or animation altered.

### What changed
- **MapScreen.kt**: In the right-edge control stack, moved the Add Zone `MapControlButton` (with its `Spacer(6.dp)`) from below the `FanLayout` Box to above it. Both keep their `alpha(if (anyFanOpen) 0f else 1f)` fade behavior.
- **FEAT_DSC_ArcLayout.md**: Added `control-stack-button-swap` one-liner under `## Implemented`; bumped front-matter `modified` to 2026-09-06 21:19.
- **GLOBAL_CONTEXT.md**: Updated ArcLayout Feature Summaries row (one-liner + modified date); pushed Focus History entry; pruned oldest entry to keep cap 10.

### Key design
- Ordering only — the fan still expands leftward from its anchor; the Add Zone button is a sibling control above it.
- Both controls share the same `MapControlButton` size/style and fade together when any fan is open.

### Next step
- Open ArcLayout todos remain: "Add second fan button in control stack" and "Replace hardcoded Spacer(136.dp) with computed value".
