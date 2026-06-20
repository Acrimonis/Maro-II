## Git — workflow shortcuts

Convenience wrappers over standard git. **🔴 See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for the Hard Rule — `#merge`/`#push`/`#commit` refuse on `develop`/`main`.**

  #new [branch]       fetch `origin/develop`, checkout `-b [branch]` tracking it.
  #commit             #bake + git add -A && git commit. ALWAYS prompts for confirmation
                      — even when chained. 🚫 refuses on develop/main.
  #push               git push origin [current-branch]. 🚫 refuses on develop/main.
  #move [branch]      stash → switch (existing) → pop.
  #move new [branch]  stash → create from origin/develop → pop.
  #cherry [target]    list unpushed commits, interactive pick to cherry-pick to [target].
  #copy [target]      alias for #cherry.
  #rename [branch]    git branch -m [branch].
  #merge              rebase + force-push; AI auto-resolves conflicts via feature ownership (## OwnedFiles). 🚫 refuses on develop/main.
  #merge [branch]     rebase onto [branch] + force-push; AI auto-resolves. 🚫 refuses on develop/main.
