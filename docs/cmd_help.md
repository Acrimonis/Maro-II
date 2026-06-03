View:     #list                compact feature table (by last modified)
          #status              feature details (active)
          #status [name]       feature details (named)
Manage:   #track [name]        create feature
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
Docs:     #doc                 key files (scope-aware)
          #doc list            list docs with scope tags
          #doc create [name]   create doc + scope prompt
          #doc read [name]     load doc into context
          #doc attach [name]   link doc (scope-aware)
          #doc detach [name]   unlink doc (bare = prompt)
Session:  #bake                snapshot session
          #help                this list
```
