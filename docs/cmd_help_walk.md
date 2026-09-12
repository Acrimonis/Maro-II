<!-- scope: reference -->
## #walk

Cursor over an enumerated set — one item expanded at a time, the rest as flat markers.

  [no param]       Start on the pending set: the active feature's open todos and unimplemented
                   plan items, in ship order.

  [source]         A name or ID starts the cursor there instead of at the top.

  #next            Advance one item.
  #prev            Step back one item.
  #skip            Park the active item without resolving it.
  #done            Close the walk; with points still open it challenges instead — close, resume or
                   park, where parking promotes the open points into the digest's ## Outcome.

Walk state lives in the feature file's `## Walk` section — never the hydration, which `#bake`
regenerates — and carries its own date. Exhaustion closes it with a bullet summary of resolutions
and of anything dropped. A bare `#review` resolves to the active item, so review and stepping share
one cursor.

An open walk is reported at session start and again before the session closes, and it blocks both
gates that would otherwise lose it: `#bake`'s fold and `#archive`'s retirement.
