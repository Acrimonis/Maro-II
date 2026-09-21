<!-- scope: feature -->
# The position counts lose their zero — a land track that reads as water

- **Feature:** Tracks
- **Branch:** `feature/on-water-lannd-filter`
- **Date:** 2026-09-21
- **Status:** in design
- **Type:** defect fix on the storage of the sampled counts, found while chasing a TracksImport report

Filed under Tracks because every file it touches is this feature's: `Track.kt` and `TrackRepository.kt` for
the storage, `TrackPosition.kt` for the sampler and `ListFilter.kt` for the axis that reads the verdict.

## 1. The report

"On land tracks showing on an in-water filter" — reported on the device after the selection-predicate fix
shipped the same day, and reproducible in the list as well as on the map, since both read the same summary.

## 2. The mechanism, verified

The verdict is one property on the summary (`Track.kt:98-99`):

```
positionIsWater = waterPointCount < 0 || landPointCount < 0 || waterPointCount >= landPointCount
```

Its first two clauses are the "never classified" sentinel, and both counts are Int fields defaulted to that
sentinel (`Track.kt:83-85`, `@ProtoNumber(17)` and `(18)`, default `TrackPositionCounts.UNCLASSIFIED` = `-1`).
Protocol buffers omit a field equal to its type's default and decode an absent field as the property's Kotlin
default, so **a legitimate zero cannot survive a round trip**:

- a track whose 32 samples were all land holds `(0, 32)`; the zero is dropped on write, the field comes back
  as the `-1` default, and the verdict's first clause answers **water** without consulting a sample;
- an all-water track holds `(32, 0)`, loses its zero the same way and reads `(32, -1)` — which still answers
  water, by accident. Both ends of the axis lose a zero; only one of them changes the answer, and that is the
  whole reason this stayed invisible for a week.

The write sites are `TrackRepository.kt:307-326` (the summary build) and `:389-396` (the repair pass), and the
carry rule at `:358-365` reads the same sentinel back to decide whether a track needs re-sampling.

## 3. Why the repair pass cannot cover for it

`classifyUnclassified` latches `positionPassDone` after its one run (`TrackRepository.kt:397`), so it repairs a
corrupted decode only while that latch is clear. Any save, delete, merge or import rebuilds the index and the
next list call re-reads it, which re-introduces the dropped zero — and by then the latch is set, so the
corrupted value stands for the rest of the session. That is why the symptom appears after recording something
rather than at launch.

The same clause is why the 2026-09-19 plan's guard looked sufficient: §3 of
`260919_FEAT_PLN_Tracks_position-filter.md` reasons about protobuf's default-`false` trap, and the Int sentinel
it chose instead has a default a count legitimately takes.

## 4. The fix — let the flag carry the sentinel

The counts keep meaning what they say and a third field records whether they were ever filled:

- `TrackSummary.waterPointCount` and `.landPointCount` default to `0`, which is now a count like any other;
- `@ProtoNumber(19) positionClassified: Boolean = false` — its default is the *correct* value for an absent
  field, which is precisely the trap the 2026-09-19 plan described, used the right way round;
- `positionIsWater = !positionClassified || waterPointCount >= landPointCount`, so an unclassified track still
  reads water, by rule and deliberately (§4 D2 of the 2026-09-19 plan, unchanged);
- the in-memory `TrackPositionCounts` keeps its `-1` sentinel and its `isClassified`, so the pure sampler and
  its nine tests are untouched — the translation happens at the summary boundary.

**Legacy indexes self-heal, and no migration is written.** `index.bin` is an internal cache the repository
rebuilds on demand, so an index written before this change decodes with `positionClassified = false` — the
honest reading of a file whose counts may be lying — and the existing one-shot pass re-samples it, writing the
flag with the fresh counts. No user-held blob is affected: the GPX export carries the `Track`, never a summary.

**Rejected: shifting the stored value by one** so zero never reaches the wire. It works and is smaller, but it
makes the stored field stop being the count it is named after, and every reader then has to know the offset —
the same class of hidden convention that produced this defect.

## 5. Edit set

- `data/track/Track.kt` — the two counts default to `0`, the new `positionClassified` at `@ProtoNumber(19)`
  with its KDoc, and `positionIsWater` reading the flag instead of the sign test.
- `data/track/TrackRepository.kt` — `rebuildIndex` writes `positionClassified = counts.isClassified`;
  `classifyOrCarry` carries on the previous summary's flag rather than on two non-negative counts;
  `classifyUnclassified` selects and skips on the flag, and copies it with the fresh counts.
- `data/track/TrackPosition.kt` — unchanged; its sentinel stays in memory where a sentinel is safe.
- `data/model/ListFilter.kt` — unchanged; the axis reads `positionIsWater`, which now carries the whole rule.

## 6. Tests

- **The proof, and the case that was missing:** a `TrackSummary` with `waterPointCount = 0`,
  `landPointCount = 32` and the flag set survives an encode/decode round trip with both counts intact and
  `positionIsWater == false`. This is the experiment §2 rests on, and it fails on today's code.
- The mirror: `(32, 0)` round-trips as water, with its zero still visible.
- The legacy decode: a summary blob carrying neither count nor flag decodes as unclassified, and reads water.
- The truth table on the property: unclassified → water; `0/32` → land; `32/0` → water; `16/16` → water (the
  tie, which is the 2026-09-19 decision and stays).
- `TrackPositionTest` keeps its nine sampler cases untouched and green — the diff does not reach the sampler —
  but its `summary(...)` helper (`TrackPositionTest.kt:37-38`) builds summaries with explicit counts, so it
  must pass the new flag: the verdict reads the flag first now, and the helper is where the test says that a
  count that was set is a track that was classified.
- Where the round-trip cases live is settled in the Code hop against whatever index or repository test file
  already exists; a new file is acceptable if none does.

## 7. Verification

- `apk-build.bat` clean and the scoped Tracks + `config` run green, with `TrackPositionTest` unchanged.
- Device pass, owed by the user: a land track and a water track side by side, checked under On land and On
  water in **both** the list and the map, after recording one of them in the same session — the case the
  latch used to break — and once more after an app restart, which is the round trip that was corrupting it.

## 8. Non-goals

- The sampler's own rules: the 32-point stride, water winning a tie, and unclassifiable counting as water are
  all 2026-09-19 decisions and all unchanged. If the tie or the unknown default is itself now in question, it
  is a separate decision with its own plan.
- The `positionPassDone` latch (`TrackRepository.kt:397`). It stops being load-bearing once the decode is
  honest, since a re-read index then carries correct counts; it is left as it is rather than widened.
- The marker count, the render cap, and the shell-side pinned carve-out recorded by the TracksImport plan.

## 9. Beside this — the part of the earlier fix that is reverted

The TracksImport fix of the same day moved the menu's track count onto the drawn set. That half does not bear
on this report — it changes a number, not which tracks are drawn — and it carried two consequences of its own
(a zero badge until the first overlay pass, and the count following the layer toggle while the marker count
does not). It is reverted to counting the map filter's matches, as before, and the TracksImport plan and the
feature file are amended to say so.

The other half of that fix — the map filter becoming authoritative, so the session boost no longer rides past
it — is **kept**: it is load-bearing here, since a land track recorded or imported in the same session would
otherwise still be drawn under an On water filter no matter how honestly it is classified.
