<!-- scope: reference -->
## #walk

Cursor over an enumerated set — one item expanded at a time, the rest as flat markers.

  [no param]       Start on the pending set: the active feature's open todos and unimplemented
                   plan items, in ship order; with a stack already open, resume its top level.

  [source]         A name or ID starts the cursor there instead of at the top.

  #next            Advance one item; past the last item the level closes.
  #prev            Step back one item.
  #skip            Park the active item without resolving it.

Walk state lives in the feature file's `## Walk` section — never the hydration, which `#bake`
regenerates — and carries its own date. Exhaustion closes a level with a bullet summary of
resolutions and of anything dropped, and a drop's open points are promoted into the parent item at
level 2 and into the digest's `## Outcome` at level 1. Stepping never interrogates: the three exits —
close, resume, park — are challenged at the gates below, where open points can actually be lost. A
bare `#review` resolves to the active item, so review and stepping share one cursor.

**Rendered shape.** One expanded item in a headed, visually separated block carrying `Why it is here`
and, where a gate exists, `What closing it means` and `Open question`; the rest as flat numbered
markers with the cursor position marked, and a footer naming any parked level beneath. The expanded
block is the only prose, and it stays inside the Output Contract's bullet cap.

**Closed levels.** A level marked `Closed` is one closed by decision, and the corpus keeps resume
points inside such levels — `xTrack/Tracks/FEAT_DSC_Tracks.md` line 289 is the live example, a
`Closed:` level whose unticked item is the point to resume from. An open landing on a closed level
reports it in one line, names any unticked item as a parked point with its resume condition, and asks
before resuming that point; otherwise it builds a fresh set — it never resumes the level silently and
never refuses the parked point.

**The stack — one level deep.** A walk descends once into its own active item, so a subject needing
more than one exchange gets its own thread instead of an oversized reply.

  Level 1          The walk itself — the bold **Level 1** line carrying date, source and active item,
                   with its items as flat numbered markers beneath it.
  Level 2          The child — a bold **Level 2** line below the items, carrying its own date, a
                   `Parent:` pointer to the parent item's number, and its own active item. Never
                   `###`-prefixed, so `#bake` and `#doctor` cannot read it as a feature section.
  Opening          Automatic and agent-side: a child opens when the active item's answer ends in a
                   gate — the question you must answer before the item can close. No facet opens it,
                   and the reply names the item it descended into.
  One at a time    A second subject waits until the current child closes. A level-2 walk never opens a
                   level-3 — a sub-item that would need one is challenged at the gate instead, with
                   promotion to a plan file as the escape.
  Return           Advancing past the last item of a child closes it and puts the cursor back on its
                   parent, which is ticked in the same step with a one-line summary naming resolutions
                   and drops. While a child is open its parent item cannot be exhausted, so nothing
                   moves the cursor by hand.
  Frozen numbers   Parent items are not reordered, inserted or renumbered while a child is open, so
                   the `Parent:` pointer cannot dangle.
  Parked           A level left unexhausted is parked, and a parked level counts as an open walk.

**Gates and read path.** An open walk is reported at session start, when the feature file is opened —
the top of the stack, naming any parked parent beneath it. Any open level blocks both gates that would
otherwise lose it: `#bake`'s fold and `#archive`'s retirement. `#bake` snapshots every level, clears
none, never trims walk items with its live trim, and never copies walk state into `FEAT_HYD_`, which
at most points at it.
