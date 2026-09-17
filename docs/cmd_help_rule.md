<!-- scope: reference -->
## #rule

Display the tier legend, or reload one tier's rules.

  [no param]   Print the tier legend.

  [tier]       Reload that tier's rules from `AGENTS.md` and print them with a fingerprint line.
               Accepts the tier's name or its glyph — AUTHORISATION · BOUNDARY · CONDUCT · AUTHORSHIP ·
               GUIDELINE, or 🛑 ⛔ 💬 🧹 🟢. Partial names are not resolved: use the name or the glyph.

  all          Read the whole file and report whether the copy this session holds is stale —
               unchanged, or the sections that moved. The read is itself the reload, so the verdict
               describes the state after it. The comparison is the file's rule count plus a hash over
               its rule lines — the same function the tier form uses, applied to the whole file.

  Explicit     Fires only on invocation — never automatic, never on a cadence.

The fingerprint reads *tier · rule count · a short hash of the lines loaded*, so a second call on an
unchanged tier is visibly a re-print while an edited tier is visibly a reload.

Why `all` exists: the rulebook reaches a session once, so an edit made mid-conversation stays invisible
until something reads the file. This is that read, made deliberately — and it answers with a verdict
rather than a dump, the point being whether the session is stale, not what the file says.

A printed tier re-anchors attention and does not enforce; the refusals inside `#push`, `#commit` and
`#merge` are what bind.
