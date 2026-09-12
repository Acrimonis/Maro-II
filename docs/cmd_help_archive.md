<!-- scope: reference -->
## #archive

Retire a feature-scoped plan or document into `xTrack/[Feature]/xxArchive/`.

Fires **only on explicit invocation** — nothing triggers it implicitly, and no other command may
enter `xxArchive/`.

  [no param]       List the active feature's plans and documents, then offer a multi-select of what
                   to archive. Before moving anything, ask the one qualifying question:
                   "does anything here still define current behaviour?"
                     yes → promotion is mandatory first: the retained rule goes to ## Rules and the
                           decisions to FEAT_DOC_[Feature]_decisions.md
                     no  → carry on

  [name]           Archive one plan: write its ## Outcome if absent (what shipped versus what was
                   planned), add its row to xxArchive/INDEX.md, detach it from ## Docs, drop its
                   pointer from ## Implemented (the bare one-liner stays), then move the file.

  search [terms]   Fuzzy-search the index only — never the archived bodies. Scoped to the active
                   feature; `search all [terms]` sweeps every feature's index. A hit does not
                   authorise opening the file: that needs a separate request naming it.

  restore [name]   Move the file back, re-attach it in ## Docs, restore its ## Implemented pointer,
                   delete its index row. The next #bake records the event.

Index columns: `File | Created | Archived | Status (shipped/superseded/promoted) | Summary | Tags | Superseded-by`

Read policy: the folder is never read by default — every feature-summarising command (`#bake`,
`#status`, `#doctor`, `#doc list`, `#doc audit`, `#doc update`) excludes it. Inside an invoked
`#archive`, only `INDEX.md` is read; one archived body needs an explicit per-file request, and no
unlock persists to the next request.

Abandoned material — plans nobody will ever execute — is **deleted**, never filed here.
