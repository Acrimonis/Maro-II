# FEAT_PLN — Route — the home area keeps its tiles

**Status:** implemented · **branch:** `feature/rte-cche` · **opened:** 2026-10-17

## The ask

- The warm-up covers the home area and grows outward in rings until the store holds `route.prewarm.targetPct` (80) of its ceiling.
- When the store must reclaim, it must never take a tile from the home area; it takes the least recently accessed tile outside it.
- The reclaim must stop paying a full directory listing and a sort on every overflowing save, and the ceiling rises 72 → 96 MiB.

**Words.** *Home area* is `route.prewarm.box` clamped to the baked region — the seed the walk starts from; *outer* is every other tile. Neither `primary zone` nor `preferred zone` appears in the code, so this plan says **home area** throughout and the code should too.

## What the code does today

- Reclaim is already an access clock, not FIFO: [`DirectoryFineTileStore.trim()`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:194) sorts the directory by last-modified, and [`load`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:96) touches the file it serves. What is missing is only the home-area exemption.
- The store cannot tell home from outer: an entry is named by a SHA-256 of its key ([`FineTileCodec.entryName`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCodec.kt:45)), so the directory is anonymous and the trim sees ages alone.
- Every overflowing save re-lists and re-sorts the whole directory: [`trim()`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:194) runs inside [`save`](../../app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt:102), and protection would make the over-ceiling state the normal one.
- The walk stops at 80 % of the ceiling while the trim fires at 100 %: [`warmOutward`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt:200) and [`FineTilePrewarm.run`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTilePrewarm.kt:154) probe [`StoreFullness.hasRoomAt`](../../app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt:34), so the reserve is filled by route water and by stale entries.
- Every acquisition writes back only the tiles it was missing ([`loadOrBuild`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileMap.kt:163)); a warm tile is read and re-dated, never rewritten.
- The protection would be lost on recreation: [`FineTileCache.install`](../../app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt:118) runs in `MainActivity.onCreate` and builds a new store each time.

## Decisions

- **D1 — Protection is derived, never stored.** The live primary tiles are the entry names of the seed ring the walk itself derives; nothing is written into an entry, the codec stays at v4, and a re-warm after a stamp change is the accepted cost.
- **D2 — The set carries its byte total.** `protect(names)` sums the sizes the index knows, and the total is adjusted on every save, delete and drop of a protected name, so "can any deletion reach the ceiling?" is one subtraction.
- **D3 — One access-ordered index.** A `LinkedHashMap` of entry name → bytes, primed by the single listing that already primes the byte total, tail-moved on load and save, pruned on delete.
- **D4 — The file stays the truth.** `load` keeps opening, decoding and comparing the whole key; any miss drops that name's accounting on the spot. The index may over-report fullness; it may never serve a tile.
- **D5 — The trim walks the index.** Head-first, skipping protected names, unlinking and dropping as it goes; it returns before any I/O only when the store holds nothing but protected bytes, because a protected floor above the ceiling is still no reason to keep outer water that would otherwise go. **`list()` is not the index's**: it keeps listing the directory, because the prewarm reads a name as proof that a file stands (D4).
- **D6 — The once-only latch is on the line, never on the behaviour.** A logged "nothing evictable" must not stop a later trim after the set shrinks — the lesson behind `StoreFullness` being live rather than latched.
- **D7 — The app edge protects before the walk runs.** `MapRoutePrewarmEffect` derives the seed box's requests once, turns their entry names into the set and installs it, then hands those same requests to `warmOutward` as its seed ring — so protection never depends on the walk's gate or on its fullness stop, and no derivation is paid twice. `FineTileCache` keeps the last set and re-applies it at `install`, so a recreation cannot strip protection.
- **D8 — The ceiling is 96 MiB** (100 663 296), moved in one write across `route.fineTile.maxBytes`, `AppConfig.routeFineTileMaxBytes` and `DirectoryFineTileStore.DEFAULT_MAX_BYTES`, with the ceiling test renamed off `SeventyTwo`.
- **D9 — The change is judged on a number.** One `STORE trim` line per reclamation names evicted entries, remaining bytes, protected bytes and elapsed ms, and the walk prints the map's hit rate when it ends.

## The design

- **Seam.** `TileStore` gains `protect(names: Set<String>)`, inert in `NoOp`; its KDoc's "pure read/write" sentence is amended, because this is a policy knob exactly as `fullness` already is.
- **Store state.** Under the existing `@Synchronized` lock: `index: LinkedHashMap<String, Long>`, `dirBytes`, `lastEntryBytes`, `protectedNames`, `protectedBytes`. Priming consults `protectedNames` so a set installed before the first listing still counts its bytes.
- **Trim.** `while (dirBytes > maxBytes)`: if `dirBytes <= protectedBytes` break — there is nothing it could take; otherwise walk the index from the head, skip names in `protectedNames`, unlink the file; a file already gone costs an accounting drop and counts as evicted. Break when the iterator ends, which is the honest "the ceiling cannot be reached" case, logged once.
- **Load.** On a miss the entry is deleted today; it now also leaves the index and, where protected, the protected total.
- **List.** `list()` keeps answering a real listing: the prewarm's `storedNames` drives a **skip**, so a name the index still carries after the OS purged the file would suppress that tile's build for the whole walk. One listing per walk is nothing beside the per-save scan this plan removes.
- **Wiring.** `MapRoutePrewarmEffect` derives `area.seedBox` itself, protects the `FineTileCodec.entryName` of every request it finds, and passes that same list into `warmOutward` as the seed ring, so ring zero costs no second derivation and protection happens even when the walk stops at TARGET or GATE on its first check.
- **Ceiling.** 96 MiB leaves the reserve at 80 % of the new ceiling minus the home area's bytes — 76.8 MiB against 57.6 today, so 19.2 MiB more room for route water if the home area keeps its size.

## Rejected

- **A geographic rectangle read from each entry header.** Needs one small file read per candidate, and it pins stale home tiles forever because it cannot tell a live key from a dead one.
- **A persisted `primary` flag in the entry.** Same staleness trap, plus a format bump to v5 and a migration that does not exist by design.
- **Two directories, `home/` and `outer/`.** Structural and restart-surviving, but every existing entry becomes an orphan and the classification still has to be derived at write time.
- **One pack file with a persisted index.** Fewer inodes and one purge target, but a format rewrite, compaction and a much larger corruption blast radius for a reclaim question.
- **Keeping the listing and only latching "nothing evictable".** A fraction of the code, but blind: with protection in place the over-ceiling state is normal, so the listing and sort would run on nearly every tile a route writes.

## Tests

- A protected entry survives an overflow; the oldest unprotected one goes first.
- An all-protected store returns before any I/O and prints its line; the flag does not stop a later trim once the set shrinks.
- A file deleted behind the store drops its accounting on the next load and on the next trim.
- A stale key, absent from the live names, is still evicted.
- `list()` names only files that stand: an index entry whose file the OS purged is not served by a listing, so the prewarm rebuilds that tile rather than skipping it.
- Re-installing the store re-applies the remembered set.
- The ceiling test holds all four homes at 96 MiB.
- Fixtures set `lastModified` explicitly so access order is deterministic off the wall clock.

## Readings

- `STORE trim evicted=… remaining=… protected=… ms=…` per reclamation — the scan's replacement.
- The walk's end line plus the map's hit rate — whether the home area is really warm across routes.
- `PREWARM ring tiles=` and `STORE save bytes=` — the home area's own byte count, the one number this plan still needs before its ceiling can be called right.
- Device pass (the user's): several routes over cold outer water, then a reload; the home tiles must survive and no `STORE trim` may name one.

## Files

- `app/src/main/java/ykws/android/maro/spatial/DirectoryFineTileStore.kt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/TileStore.kt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileCache.kt`
- `app/src/main/java/ykws/android/maro/spatial/multipass/FineTileWarm.kt`
- `app/src/main/java/ykws/android/maro/ui/map/MapRouteEffects.kt`
- `app/src/main/java/ykws/android/maro/config/AppConfig.kt` · `app/src/main/assets/maro.properties`
- `app/src/test/java/ykws/android/maro/config/FineTileStoreCeilingPropertiesTest.kt` and the multipass store suites
- `xTrack/Route/FEAT_DSC_Route.md` · `FEAT_DOC_Route_engines.md`

## Implemented

**2026-10-17 — landed on `feature/rte-cche`.**

- `TileStore.protect` ships non-suspend, inert both as a default and in `NoOp`; `DirectoryFineTileStore` holds the set and its own byte total, and every save, delete, drop and touch keeps the pair true.
- The reclaim walks one access-ordered index primed by the listing that already primes the total, so no save lists or sorts the directory; an all-protected store returns on an arithmetic test alone.
- `list()` deliberately stayed a directory listing — the prewarm reads a name as proof a file stands (D4), and an index-backed list would suppress a purged tile for a whole walk.
- `MapRoutePrewarmEffect` derives the seed box once, protects its entry names and hands the same list to `warmOutward` as ring zero, so protection lands before the clock, gate and fullness checks; the walk stopping at `TARGET` on its first check still leaves the store protected. This closed review blocker 2.
- The index-backed `list()` was review blocker 1 and was withdrawn.
- `FineTileCache` remembers the last set and re-applies it at `install`, so a recreation cannot strip protection.
- The ceiling is 96 MiB across its four homes, and the ceiling test is renamed off `SeventyTwo`.
- Readings now emitted: `STORE trim evicted=… remaining=… protected=… ms=…`, `STORE trim cannot reach ceiling …` once per store, and `hitRate=NN%` on `PREWARM walk done`.
- Gate: `:app:testDebugUnitTest :app:assembleDebug` green — 1317 tests at the first landing, one clean run after the review fixes. No dependency added, no entry format change.
- Owed: the device pass — several routes over cold outer water then a reload, reading that no `STORE trim` names a home tile and that the home area stays warm.
- The `list()` regression landed after the first close: [`aListingReadsTheDirectoryNeverTheIndex`](../../app/src/test/java/ykws/android/maro/spatial/DirectoryFineTileStoreTest.kt:373) deletes a tile's file behind the store and asserts the listing omits it and the prewarm rebuilds it — so the one untested guard now has its test, and the gate stayed green.
- The stray patch fragment at `FineTilePrewarm.kt:56` was deleted on the user's word after the landing; the home area's own byte count remains the one open reading.

