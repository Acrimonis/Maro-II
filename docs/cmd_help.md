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
Git:      #new [branch]        fetch develop, create + switch to new branch from remote develop
          #commit              run #bake + git add -A && git commit
          #push                push current branch to remote
          #move [branch]       stash uncommitted changes, switch to existing branch, pop stash
          #move new [branch]   stash uncommitted changes, create new branch from develop, pop stash
          #cherry [target]     interactive: list unpushed commits, select which to copy to [target]
          #copy [target]       alias for #cherry
          #rename [branch]     rename current branch to [branch]
          #merge               rebase current branch onto origin/develop + force-push (PR-ready)
          #merge [branch]      rebase current branch onto [branch] + force-push
Health:   #doctor              lint xTrack for drift
          #doctor fix          auto-repair
