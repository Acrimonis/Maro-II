<!-- scope: feature -->

# UI Guidelines Consolidation — component / drawer / lists

## Context

Three UI guideline docs overlap on shared patterns. Ask review identified cross-file duplication (D1–D6),
obsolete content (O1–O5), and useless distinctions (U1–U3). User arbitration rulings (A1–A5) below.

## Rulings

- **A1** — `ui-component-guidelines.md` is the **card-surface authority** (bg, radius, densities) — consistent
  with the Issue-1 inception-rule home.
- **A2** — popup-styling spec becomes **canonical in `ui-component-guidelines.md`**.
- **A3** — plan as defined: fold the DrawerScaffold "Not Migrated" list into a single §12 sentence; drop the
  "Migration Guide" before/after block.
- **A4** — **dump all history**: remove the drawer §11 Decision Log entirely. Guidelines are the norm; history is not kept.
- **A5** — leave component-guidelines §5.4/§5.5 (map-overlay patterns) in place.

## Canonical homes (single source of truth)

| Pattern | Canonical home | Others become pointers |
|---|---|---|
| Card surface primitive (bg `uiCardBackground`, 12dp radius, Wide/Tight densities) | component-guidelines | drawer §8, lists Visual Tokens |
| Divider token + visible-vs-spacer rule | component-guidelines §2.6 | drawer §8/§9, lists |
| Header typography (`SectionHeader`/`SubSectionHeader`) | component-guidelines §2.9 | drawer §7, lists |
| List-item card pattern (Track+Marker) | drawer-guidelines §9 | component §5.2 → one-line pointer |
| Navigation chevron rule (28dp muted) | drawer-guidelines §10 | lists |
| Popup styling | component-guidelines (new) | lists → pointer |
| Drawer header tokens (`DrawerHeader`) | drawer-guidelines §6/§12 | lists |

## Execution steps

### ui-component-guidelines.md
1. Add a **card-surface primitive** section (authority for `uiCardBackground`, 12dp radius, Wide 16×10 / Tight 8×4
   densities) — or make §2.1/§2.2/§2.3 the explicit authority and reference it from drawer/lists.
2. Add the **popup-styling spec** (moved from lists) as a canonical section.
3. Reduce §5.2 (list-item card) to a one-line pointer to drawer-guidelines §9.
4. Keep §2.9 header typography as authority; keep §2.6 divider as authority.
5. Add an explicit "drawer-internal card gap = 8dp" row to §3 spacing table (U2).

### ui-drawer-guidelines.md
6. §8: keep only drawer-specific row-height/divider-gap rules; point to component-guidelines for the card surface.
7. §7 (SectionHeader): reduce to a pointer to component-guidelines §2.9.
8. §6: keep the header token table as authority; delete the duplicated hand-rolled `Row` code block (O3) — §12
   `DrawerHeader` points to §6.
9. §9: keep as the canonical list-item card spec.
10. §10: keep as the canonical chevron rule.
11. §12: fold "Not Migrated" into a single sentence; delete the "Migration Guide" before/after block (O1).
12. §4: delete the pre-DrawerScaffold skeleton code block (O2); keep the 5-rule contract.
13. **Delete §11 Decision Log entirely** (A4).

### ui-lists-guidelines.md
14. Drop duplicated visual-token tables that restate card bg / header / divider / chevron (D2/D3/D5/D4) — keep only
    list-specific tokens (accent stripe, snackbar, undo, multiselect feedback).
15. Popup-styling section → one-line pointer to component-guidelines (A2).
16. Keep list-overlay behavior (ListOverlayScaffold API, filter/sort, swipe/multiselect, ViewModel pipeline).

### Cross-cutting
17. Do NOT rename `ui-tokens.properties` references yet (O4) — that happens in the properties-normalization pass.
18. Preserve all unique, still-valid guidance; only remove duplication/obsolete/history.

## Files Affected
- `docs/ui-component-guidelines.md`
- `docs/ui-drawer-guidelines.md`
- `docs/ui-lists-guidelines.md`

## Verification
- No information loss: every unique rule still appears exactly once (canonical) or as a pointer.
- Grep for removed obsolete terms (Decision Log entries, "Migration Guide", pre-DrawerScaffold skeleton).
