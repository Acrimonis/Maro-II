<!-- scope: reference -->
## #brief · #full

Output mode — `#brief` subtracts the contract's three optional parts, `#full` restores them. The
switch reports the mode it leaves you in.

  #brief           Switch to brief output for the rest of the session.
  #full            Restore full output.

The mode is **session-lived**: `#focus` resets it to full, and it survives until then without being
written anywhere. Neither command touches hydration or feature state.

The three optional parts, and only these:

  ELIJP            The one-or-two plain sentences added to a multi-step report.
  Containment      The list, table or code block a bullet may carry when the content does not fit.
  Verification     The short checked list a report may carry after a multi-step change.

Nothing else is subtracted, and a command's own output is not one of the three — `#rule`'s legend or
its tier dump prints whole, brief mode or not, and so do the walk's cursor position and the review's
sweep, the two other outputs a brief reader must still see. `Recommendations argue against themselves`, the question
threshold, the gate rules and every Core Directive stay in force in brief mode — as does `Answer only
what was asked, then stop`, which a shorter reply obeys more easily, not less.
