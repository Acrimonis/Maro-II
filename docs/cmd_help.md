<!-- scope: reference -->
<!-- derived from AGENTS.md §7b — regenerate from it; do not hand-edit -->
View:     #now                 current context: active feature + working path (top Focus History entry) [alias #context #here #feat #feature]
          #list                compact feature table with Modified column, sorted by Modified desc (from GLOBAL_CONTEXT.md summaries) [alias #features]
          #status              feature details (active = top Focus History entry)
          #status [name]       feature details (named)
          #status diff [name]  changes since last #bake (active/named)
Manage:   #track [name]        create feature (xTrack/[name]/FEAT_DSC_, YAML front-matter + summaries row; prints the command delta)
          #focus [name]        switch to feature (push Focus History entry; prints the command delta)
          #focus [name] [sec]  hydrate only that section (transient, no state)
Track:    #todo                list todos (scope-aware)
          #todo [desc]         add todo (scope-aware)
          #todo [tgt]:[desc]   add todo (tgt: feature|section|parent|global)
          #rule                print the tier legend; [tier] (exact name or glyph) = reload that tier's rules (one-line fingerprint); all = report whether the held copy is stale. Explicit invocation only
Docs:     #doc                 feature docs (scope-aware)
          #doc list            list docs grouped by source (DOC / PLN / docs/)
          #doc create [name]   create doc (prompts: feature-scoped → xTrack/*/FEAT_DOC_ or cross-cutting → docs/)
          #doc read [name]     load doc into context (scans xTrack/*/FEAT_DOC_/xTrack/*/FEAT_PLN_/docs/)
          #doc attach [name]   link doc → feature ## Docs (bare = prompt)
          #doc detach [name]   unlink doc (bare = prompt)
          #doc update          refresh feature docs to implementation (decisions doc, ## Implemented rewrite, prune completed todos, bump dates, #bake)
          #doc audit           check docs for missing scope tags, orphans, invalid scopes
Session:  #bake                snapshot + consolidation (checkmarks, section fold/trim/split/merge, summaries table, FEAT_HYD_, the top Focus History entry rewritten to what shipped, prune Focus History > 10)
          #help                this list
          #archive             retire a feature-scoped plan or doc into xxArchive/ (explicit invocation only)
          #review              review the cascade-resolved target — walk item → plan in design → last #implement run → live proposal (challenge); also sweeps the five covered action classes that ran without a verdict line
          #walk                cursor over the pending set, one item at a time, exhaustion closes — facets #next #prev #skip; a Closed level is closed by decision, not a bar to resuming its parked point
          #brief / #full       output mode — brief subtracts ELIJP, containment blocks, verification lists; #focus resets to full
Pipeline: #implement           full pipeline: Code → build → Ask review → Architect report
          #go                  agree with the open question (re-asks if the proposal moved); #go impl = agree + run the pipeline
Git:      🛑 See [`docs/GIT_WORKFLOW.md`](GIT_WORKFLOW.md) for full rules + enforcement.
           #new [branch]        create `feature/[branch]` from origin/develop; if that branch exists locally, report it and offer recreate / another name / abort
           #commit              stage + commit; offers a bake first when the feature moved since its last bake. Confirms the staged set and the message. 🚫 refuses on develop/main.
           #push                push current branch (user-invoked only — never proposed or reminded). Confirms the branch and the remote. 🚫 refuses on develop/main.
           #move [branch]       stash → switch → pop (existing)
           #move new [branch]   stash → create feature/[branch] from develop → pop
           #cherry [target]     interactive cherry-pick unpushed commits
           #copy [target]       alias for #cherry
           #rename [branch]     git branch -m
           #merge               pre-flight → trivial/non-trivial → auto-select rebase/merge → confirm (yes=direct, #implement=pipeline). 🚫 refuses on develop/main.
Health:   #doctor              lint xTrack and the rulebook for drift (checks a–s; flags active_subfeature/#sub remnants, registry divergence, retired-file drift, a rule bullet without a tier glyph)
          #doctor fix          auto-repair
