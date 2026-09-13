<!-- scope: reference -->
## #help

Display command reference.

  [no param]  Print the full reference table from docs/cmd_help.md.

  [cmd]       Fuzzy-resolve cmd against the stems of docs/cmd_help_*.md (AGENTS.md §7b),
              which covers sub-command pages (status diff, doc audit, doc update).
              Read and print the matching docs/cmd_help_[cmd].md file as a code block.
              If no match, print the full table and suggest the closest match.
