# Zones Override Project — Plan

## Status

Implemented — the mechanism is shipped and tested on `feature/zonetile`; the
per-zone values and ignore flags are manual edits in `zones.properties`.

## Goal

Let a hand-edited asset change the regulated-zone set app side: replace a zone's
speed value (or give one to a zone that has none), or hide a zone entirely.

## Design

- **Asset** — `app/src/main/assets/zones.properties`, next to `maro.properties`
  and shipped in the APK (no exclusion).
- **Scope** — one block per aggregated zone, speed or not.
- **Key families** — `regulatedZone.speedOverride.<key>=<kn>` (blank = no
  override; a value on a zone with none makes it a speed zone) and
  `regulatedZone.ignore.<key>=true|false`.
- **Key chain** — folded SHOM inspireid, else the legal decree ref, else the
  folded name plus a centroid slug.
- **Fold** — NFD-normalise, strip combining marks, map non-`[A-Za-z0-9._-]` to
  `_` for keys; comments keep spaces and punctuation.
- **Runtime** — `RegulatedZonesRepository.load` reads the asset and applies it to
  the deserialized `.bin`: ignored zones dropped, overridden speeds replaced.
- **Bake** — seeds the asset add-only and stops applying, so the `.bin` keeps the
  raw set. An existing entry keeps its comment text, value and flag; new zones
  are appended; a vanished zone becomes a `# stale` line.
- **Comments** — each block carries name, type, speed, source, inspireid,
  decree, speed source, restriction code, classification, vessel size, holes,
  vertices, description and centroid.

## Steps

1. `RegulationSpeedOverrides` — fold, key, parse, load (file + stream), apply, seed.
2. The bake seeds `zones.properties`; the repository applies it at load.
3. Unit tests — fold, key chain, apply, ignore, become-a-speed-zone, preservation,
   stale, malformed, stream.
4. Re-bake and `apk-build.bat` green.

## Classification fix (2026-10-10)

The asset exposed a mapping bug: the public INSPIRE `restrn` was read through an
S-101 table with `1 = speed limit`, so every code-1 zone — whose text reads
"anchoring is prohibited" — was typed `SPEED_LIMIT`. The values are S-57 RESTRN,
proven by the baked data (all code 1/2 zones forbid anchoring, code 7/8 zones are
entry/access). `parseRestrictionCode` and `parseRestrnAuth` now read one table,
`restrictionType`, and `RegulatedZone`'s field doc and `displayCategories()`'s
diving check follow. This contradicts the earlier design docs
(`260612_FEAT_PLN_RegulatedZones_reqs-formalized.md`,
`..._category-icon-mapping.md`), which claimed `restrn` is S-101.

## Open points

- The edited values are chosen after the first bake writes the file.
- Confirm a chosen zone is not re-mapped by
  `RegulatedZone.effectiveSpeedLimitKn()`, which runs after the asset is applied.
