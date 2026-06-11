## #help

Display command reference.

  [no param]  Print the full reference table from docs/cmd_help.md.

  [cmd]       Fuzzy-resolve cmd against known commands (now, list, status, track,
              focus, sub, todo, rule, doc, bake, help, doctor,
              new, commit, push, move, cherry, copy
              + sub-commands status diff / doc sync / doc audit).
              Read and print the matching docs/cmd_help_[cmd].md file as a code block.
              If no match, print the full table and suggest the closest match.
