View:     #features            compact feature table (front-matter, by modified) [alias #list]
          #feature             current context: active feature + subs list + working path
          #status              feature details (active)
          #status [name]       feature details (named)
Manage:   #track [name]        create feature (with YAML front-matter)
          #focus [name]        switch to feature
          #sub [name]          add subfeature (active)
          #sub                 list subfeatures (active←focused)
          #sub focus [name]    drill into subfeature
          #sub out             exit subfeature → parent
Track:    #todo                list todos (scope-aware)
          #todo [desc]         add todo (scope-aware)
          #todo [tgt]:[desc]   add todo (tgt: feature|parent|global)
          #rule                list rules (scope-aware)
          #rule [desc]         add rule (scope-aware)
          #rule [tgt]:[desc]   add rule (tgt: feature|parent|global)
Docs:     #doc                 feature docs (scope-aware)
          #doc list            list docs with scope tags (recursive docs/**)
          #doc create [name]   create doc + scope prompt (core|onboarding|feature|reference|archived)
          #doc read [name]     load doc into context
          #doc attach [name]   link doc → feature ## Docs (bare = prompt)
          #doc detach [name]   unlink doc (bare = prompt)
Session:  #bake                snapshot session (per-feature hydration)
          #help                this list
Health:   #doctor              lint xTrack for drift (#doctor fix = auto-repair)
