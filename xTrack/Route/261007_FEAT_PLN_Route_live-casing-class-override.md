# Route — the live casing, and the class tier the reads do not build

**Status:** shipped 2026-10-07 — the live casing leaf, its read, and the banner correction
**Created:** 2026-10-07
**Owner:** the need is Route's; the change spans the `path.*` read in AppConfig (Tracks owns the family) and its banner (Ui_Settings owns the taxonomy)

---

## Request (the user's words, 2026-10-07)

- A bigger casing for the live route line, and for that line alone.
- The scheme lets a class override the kind and the common leaf, so the override should be possible by class.

## Assessment

- **The premise holds.** `kind` and `class` are optional segments, and a class-bearing candidate outranks both the kind and the common leaf ([`PathProperties.kt:50`](../../app/src/main/java/ykws/android/maro/config/PathProperties.kt:50)).
- **Facultative in the key, mandatory in the read.** A tier exists only where the read builds it, so a key can exercise only the tiers its read asks for.
- **Three shipped keys prove the class tier works** — `path.line.width.live`, `path.line.color.pinned.from`, `path.arrow.enabled.acquisition` are all read today.
- **The casing is the exception.** [`AppConfig.kt:1880`](../../app/src/main/java/ykws/android/maro/config/AppConfig.kt:1880) passes **no** class, so its whole candidate list is `path.route.line.casing.width` and `path.line.casing.width`.
- **One read serves both phases from one field** ([`RouteHost.kt:294`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:294)), so the two cannot differ until that read is split in two.
- **The file's added line is dead.** `path.route.line.casing.width.live=12` ([`maro.properties:462`](../../app/src/main/assets/maro.properties:462)) puts the class after the sub, a shape no read builds.

## Plan

- **P1 — the key's shape.** Reshape that line to `path.route.line.casing.live.width=12`, and correct the banner's grammar block, which is transposed and drops the `<sub>` segment ([`maro.properties:411`](../../app/src/main/assets/maro.properties:411)).
- **P2 — the live read.** Add a second read with `pathClass = PathClass.LIVE`, defaulting to the shared width so an absent key changes nothing.
- **P3 — the draw site.** Take the live field when `followed != null`; the acquisition rung keeps the shared 8.
- **P4 — the same audit elsewhere.** Other roles read without the axis they need: the arrows' tuning, the dash, the selection and the count are read track-qualified, and the casing is read class-lessly. Decide per role whether a tier is wanted; each is one line at its own read.
- **P5 — verify.** `apk-build.bat` plus the scoped `ui.map` + `config` suite; the look over both phases is the device's.

## Decision (settled 2026-10-07)

- **The class tier is opt-in per read, kept, and made visible.** The family stays opt-in rather than gaining a uniform role context, but each leaf's real tiers are stated where the leaf is written, so the file stops promising a tier its read does not build.
- **The live casing read carries `PathClass.LIVE`.** The shared `path.route.line.casing.width` stays the acquisition rung's and the live line's fallback, so an absent live key is today's behaviour and the widening is purely additive.

## Open point

- Whether P4's axis-less roles are taken now or each on its own trigger; none of them blocks the casing work. The heatmap axis's class leaf, which this plan raised, is settled below.

## Discussion — opt-in per read, or uniform for the family

- **Opt-in (today).** The class tier is added one read at a time: cheap, and an unused leaf costs nothing. Its cost is drift, which is exactly what bit here — the banner advertised a class list the reads do not honour.
- **Uniform.** Carry a path-role context from the draw site into the resolver, so every leaf can be class-overridden without touching a read. It removes the drift, at the price of every read site changing and leaves gaining tiers nobody sets.
- **Objection to the settled choice.** Per-leaf notes are one more thing to keep in step, and the drift returns the moment one is forgotten; the uniform context is the only shape that cannot drift.

## Outcome (2026-10-07)

- Shipped: **`path.route.line.casing.live.width=12`** beside the shared `path.route.line.casing.width=8`; `AppConfig.routeLineCasingLiveWidthDp` resolved with `PathClass.LIVE` and seeded from the shared field; [`RouteHost.kt:288`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:288) taking the live field when `followed != null`; and the corrected PATH banner — the grammar block now reads class-before-sub with its `<sub>` segment, followed by the "Tiers in practice" note ([`maro.properties:411`](../../app/src/main/assets/maro.properties:411)).
- `apk-build.bat` SUCCESSFUL; the scoped `ui.map` + `config` suite reads **410 / 411**.
- **The one red is not this work's.** [`PathKeyCandidatesTest.kt:117`](../../app/src/test/java/ykws/android/maro/config/PathKeyCandidatesTest.kt:117) expects `path.heatmap.enabled.acquisition`, while the working tree carries `path.heatmap.enabled.live` ([`maro.properties:583`](../../app/src/main/assets/maro.properties:583)) — uncommitted drift, present before this change.
- **Why it matters beyond the test:** the read site is [`RouteHost.kt:355`](../../app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt:355), where the acquisition rung asks for `PathClass.ACQUISITION`. With `.live` in the file that leaf is unread, so the **followed** line is forced plain and the **acquisition** rung falls back to the route's persisted setting — the reverse of what the note above it says.
- **Resolved the same session (2026-10-07):** `.live` was a slip. Corrected to **`path.heatmap.enabled.acquisition=false`** ([`maro.properties:585`](../../app/src/main/assets/maro.properties:585)), which restores the documented silence on the search's own rung and greens [`PathKeyCandidatesTest.kt:117`](../../app/src/test/java/ykws/android/maro/config/PathKeyCandidatesTest.kt:117).
- With it: the shared-casing note now names itself the acquisition rung's width and the live line's fallback, the banner's class list says which names are actually spelled in keys (`casing` is the field, never a class in a key), three stray double blanks in the PATH block are collapsed, and a sweep of all **21** class-bearing keys found no other orphan — the suite reads **411 / 411**, `apk-build.bat` SUCCESSFUL.
