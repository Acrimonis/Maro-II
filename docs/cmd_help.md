View:     #now                 current context: active feature + subs list + working path [alias #context #here #feat #feature]
          #list                compact feature table (from GLOBAL_CONTEXT.md summaries) [alias #features]
          #status              feature details (active, subfeature-scoped when focused)
          #status [name]       feature details (named)
          #status diff [name]  changes since last #bake (active/named)
Manage:   #track [name]        create feature (xTrack/[name]/FEAT_DSC_, YAML front-matter + summaries row)
          #focus [name]        switch to feature
          #focus sub [name]    drill into subfeature
          #focus out           exit subfeature → parent
          #sub [name]          add subfeature (active)
          #sub                 list subfeatures (active←focused)
Track:    #todo                list todos (scope-aware)
          #todo [desc]         add todo (scope-aware)
          #todo [tgt]:[desc]   add todo (tgt: feature|parent|global)
          #rule                list rules (scope-aware)
          #rule [desc]         add rule (scope-aware)
          #rule [tgt]:[desc]   add rule (tgt: feature|parent|global)
Docs:     #doc                 feature docs (scope-aware)
          #doc list            list docs grouped by source (DOC / PLN / docs/)
          #doc create [name]   create doc (prompts: feature-scoped → xTrack/*/FEAT_DOC_ or cross-cutting → docs/)
          #doc read [name]     load doc into context (scans xTrack/*/FEAT_DOC_/xTrack/*/FEAT_PLN_/docs/)
          #doc attach [name]   link doc → feature ## Docs (bare = prompt)
          #doc detach [name]   unlink doc (bare = prompt)
          #doc sync [name]     generate/update xTrack/[name]/FEAT_DOC_[name]_profile.md from feature + summaries
          #doc audit           check docs for missing scope tags, orphans, invalid scopes
Session:  #bake                snapshot session (updates summaries table + xTrack/[Feature]/FEAT_HYD_)
          #help                this list
Git:      🔴 See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for full rules + enforcement.
          #new [branch]        create `feature/[branch]` from origin/develop
          #commit              bake + add + commit. 🚫 refuses on develop/main.
          #push                push current branch. 🚫 refuses on develop/main.
          #move [branch]       stash → switch → pop (existing)
          #move new [branch]   stash → create from develop → pop
          #cherry [target]     interactive cherry-pick unpushed commits
          #copy [target]       alias for #cherry
          #rename [branch]     git branch -m
          #merge               rebase + force-push. 🚫 refuses on develop/main.
Health:   #doctor              lint xTrack for drift
          #doctor fix          auto-repair
