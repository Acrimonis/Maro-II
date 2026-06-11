## #doc sync

Generate or update a feature profile document from the feature's front-matter
and the Feature Summaries table.

  [name]          Fuzzy-resolve [name] against existing features. Create or overwrite
                  xTrack/[name]/FEAT_DOC_[name]_profile.md with: name, status, one_liner,
                  created/modified dates, subfeature completion ratio, list of attached docs.
                  Scope tag: feature.
                  If [name] is omitted, sync the active feature.
