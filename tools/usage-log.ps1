<#
.SYNOPSIS
  Workflow-usage record for Maro-II: capture, flush, compound, report, backfill.

.DESCRIPTION
  Plan: xTrack/WorkflowImprovement/261111_FEAT_PLN_WorkflowImprovement_workflow-tracking.md
  Shapes: docs/xtrack-templates.md (usage section).

  -Capture    UserPromptSubmit hook: reads the hook JSON on stdin, spools the #-commands it holds. Never fails.
  -Flush      Spool (+ lines.txt from #bake, or -LinesFile) -> keyed blocks in usage/raw/YYMM.md, then -Compound.
  -Compound   Raw months closed for a full month -> one counted block each in usage/digest.md, raw file deleted
              after the unmerged-branch safety check.
  -Slugs      Flow slugs in use, with counts (the bake reuses one before minting).
  -Report     The usage report (#doctor usage).
  -Backfill   One-off: count #-commands from the local Claude Code transcripts into digest blocks.

  -LinesFile  Text file, one line each:  flow | slug | gist   cmd | #name [sub]   alias | old | new
              Default: lines.txt in the spool dir (<git-common-dir>/maro-usage/), consumed by the flush.
  -DryRun     Print what Flush / Compound would write; touch nothing.

  The file is pure ASCII on purpose: Windows PowerShell 5.1 reads a BOM-less script as ANSI.
#>
[CmdletBinding()]
param(
  [switch]$Capture, [switch]$Flush, [switch]$Slugs, [switch]$Compound, [switch]$Report, [switch]$Backfill,
  [string]$LinesFile, [string]$TranscriptDir, [int]$WindowDays = 90,
  [switch]$Force, [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $Root
$Utf8   = New-Object System.Text.UTF8Encoding($false)
$Dot    = [string][char]0x00B7
$Arrow  = [string][char]0x2192
$Dash   = [string][char]0x2014
$Sep    = " $Dot "
$UsageDir   = Join-Path $Root 'xTrack/WorkflowImprovement/usage'
$RawDir     = Join-Path $UsageDir 'raw'
$DigestPath = Join-Path $UsageDir 'digest.md'
$DotRx = [regex]::Escape($Dot)
$ReKey    = '^## (\d{6}-\d{4}) ' + $DotRx + ' (.+)$'
$ReLine   = '^(\d{6}) (\d{2}:\d{2}) ' + $DotRx + ' (cmd|flow|rule|alias) ' + $DotRx + ' (.*)$'
$ReDigHdr = '^## (\d{4}) ' + $DotRx + ' ([0-9a-f]{8}) ' + $DotRx
$ReDigRow = '^(cmd|flow) ' + $DotRx + ' (.+?) ' + $DotRx + ' (\d+) ' + $DotRx + ' (\d+) ' + $DotRx + ' (\d{6})(?: ' + $DotRx + ' (.*))?$'

# What each command takes as a keyword argument (a free argument - a feature, a branch - is never recorded).
# Mirrors the sub-commands AGENTS.md section 7b and the cmd_help pages name.
$SubMap = @{
  doc = 'create','list','read','attach','detach','audit','update'; status = 'diff'; move = 'new'; rule = 'all'
  doctor = 'fix','usage'; archive = 'search','restore'; go = 'impl'
}

# ---------------------------------------------------------------- helpers
# Under 'Stop', PowerShell 5.1 turns any native stderr line into a terminating error even when redirected; git speaks on stderr.
function Get-Git { $ErrorActionPreference = 'Continue'; $o = & git @args 2>$null; if ($LASTEXITCODE -ne 0) { return @() }; return @($o) }
# For a check that guards a deletion: a git failure throws instead of reading as an empty answer.
function Get-GitStrict { $ErrorActionPreference = 'Continue'; $o = & git @args 2>$null; if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed" }; return @($o) }
function Get-Stamp { [DateTime]::UtcNow.ToString('yyMMdd HH:mm') }
function Get-Branch { $b = (Get-Git branch --show-current | Select-Object -First 1); if ($b) { $b } else { 'detached' } }
function Get-Topic([string]$b) { if ($b -like 'feature/*') { $b.Substring(8) } else { $b } }
function Read-Lines([string]$p) { if (Test-Path -LiteralPath $p) { [IO.File]::ReadAllLines($p, $Utf8) } else { @() } }
function Add-Text([string]$p, [string]$text) {
  $d = Split-Path -Parent $p
  if (-not (Test-Path -LiteralPath $d)) { [void](New-Item -ItemType Directory -Path $d -Force) }
  [IO.File]::AppendAllText($p, $text, $Utf8)
}

function Get-Feature {
  foreach ($l in (Read-Lines (Join-Path $Root 'xTrack/GLOBAL_CONTEXT.md'))) {
    if ($l -match '^- \[[^\]]+\] (\S+)') { return $Matches[1] }
  }
  'unknown'
}

function Get-SpoolDir {
  $c = (Get-Git rev-parse --git-common-dir | Select-Object -First 1)
  if (-not $c) { throw 'not a git repository' }
  [IO.Path]::GetFullPath([IO.Path]::Combine($Root, $c, 'maro-usage'))
}

# Command names and aliases, read from the AGENTS.md section 7b table - the one home of the command list.
function Get-CommandNames {
  $names = New-Object 'System.Collections.Generic.HashSet[string]'
  foreach ($l in (Read-Lines (Join-Path $Root 'AGENTS.md'))) {
    if ($l -notmatch '^\|\s*`#') { continue }
    $cells = $l.Split('|')
    foreach ($m in [regex]::Matches($cells[1], '`#([a-z][a-z-]*)')) { [void]$names.Add($m.Groups[1].Value) }
    $rest = ($cells[2..($cells.Length - 1)] -join '|')
    foreach ($am in [regex]::Matches($rest, '[Aa]lias(?:es)?:\s*((?:`#[a-z][a-z-]*`[,\s]*)+)')) {
      foreach ($m in [regex]::Matches($am.Groups[1].Value, '#([a-z][a-z-]*)')) { [void]$names.Add($m.Groups[1].Value) }
    }
  }
  , $names
}

# exact -> unique substring (the section 7b cascade, minus edit distance) -> reject
function Resolve-Name([string]$tok, $names) {
  if ($names.Contains($tok)) { return $tok }
  if ($tok.Length -lt 3) { return $null }
  $hits = @($names | Where-Object { $_.Contains($tok) })
  if ($hits.Count -eq 1) { return $hits[0] }
  $null
}

# '#bake #commit' / 'doc list' -> '#bake', '#commit', '#doc list' (one entry per command token, any line)
function Get-PromptCommands([string]$text, $names) {
  $out = @()
  foreach ($line in ($text -split "\r?\n")) {
    foreach ($m in [regex]::Matches($line, '(?i)(?<![\w/&#`])#([a-z][a-z-]{1,})\b(?:[ \t]+([a-z]+))?')) {
      $n = Resolve-Name $m.Groups[1].Value.ToLowerInvariant() $names
      if (-not $n) { continue }
      $entry = '#' + $n
      $sub = $m.Groups[2].Value.ToLowerInvariant()
      if ($sub -and $SubMap.ContainsKey($n) -and ($SubMap[$n] -contains $sub)) { $entry += ' ' + $sub }
      $out += $entry
    }
  }
  $out
}

function Get-Hash8([string]$s) {
  $sha = [Security.Cryptography.SHA1]::Create()
  (($sha.ComputeHash($Utf8.GetBytes($s)) | ForEach-Object { $_.ToString('x2') }) -join '').Substring(0, 8)
}

# The block key's time: the branch's creation in the reflog (oldest entry), remembered per clone so every
# later flush of the same branch carries the same key. Its only job is to make the blocks merge.
function Get-BranchKey([string]$branch, [string]$spoolDir) {
  $map = Join-Path $spoolDir 'branches'
  foreach ($l in (Read-Lines $map)) {
    $p = $l.Split("`t"); if ($p.Length -eq 2 -and $p[0] -eq $branch) { return $p[1] }
  }
  $key = $null
  $last = Get-Git reflog show --date=unix '--format=%gd' "refs/heads/$branch" | Select-Object -Last 1
  if ($last -and $last -match '@\{(\d+)\}') {
    $key = [DateTimeOffset]::FromUnixTimeSeconds([long]$Matches[1]).UtcDateTime.ToString('yyMMdd-HHmm')
  }
  if (-not $key) { $key = [DateTime]::UtcNow.ToString('yyMMdd-HHmm') }
  Add-Text $map ("$branch`t$key`n")
  $key
}

# ---------------------------------------------------------------- parse the record
function Read-RawEntries([string]$path) {
  $key = $null; $topic = $null
  foreach ($l in (Read-Lines $path)) {
    if ($l -match $ReKey) { $key = $Matches[1]; $topic = $Matches[2]; continue }
    if ($l -notmatch $ReLine) { continue }
    $date = $Matches[1]; $stamp = "$($Matches[1]) $($Matches[2])"; $kind = $Matches[3]
    $p = $Matches[4].Split(@($Sep), [StringSplitOptions]::None)
    [pscustomobject]@{
      Month = $date.Substring(0, 4); Date = $date; Stamp = $stamp; Key = $key; Topic = $topic; Kind = $kind
      Name = $p[0]; Feature = $(if ($p.Length -gt 1) { $p[1] } else { '' })
      Gist = $(if ($p.Length -gt 2) { ($p[2..($p.Length - 1)] -join $Sep) } else { '' }); Text = "$topic|$l"
    }
  }
}

# raw entries -> counted records, one per (month, kind, name): exactly what a digest block holds
function Group-Records($entries) {
  foreach ($g in ($entries | Where-Object { $_.Kind -in 'cmd', 'flow' } | Group-Object Month, Kind, Name)) {
    $latest = $g.Group | Sort-Object Stamp | Select-Object -Last 1
    [pscustomobject]@{
      Month = $latest.Month; Kind = $latest.Kind; Name = $latest.Name; Count = $g.Count
      Branches = @($g.Group | ForEach-Object { $_.Topic } | Select-Object -Unique).Count
      Last = $latest.Date; Gist = $latest.Gist
    }
  }
}

function Read-Digest {
  $records = @(); $aliases = @{}; $hashes = @{}; $month = $null; $backfill = $false
  foreach ($l in (Read-Lines $DigestPath)) {
    if ($l -match '^# backfill') { $backfill = $true; continue }
    if ($l -match $ReDigHdr) { $month = $Matches[1]; $hashes["$($Matches[1])|$($Matches[2])"] = $true; continue }
    if ($l -match ('^alias ' + $DotRx + ' (.+) ' + [regex]::Escape($Arrow) + ' (.+)$')) { $aliases[$Matches[1]] = $Matches[2]; continue }
    if ($l -match $ReDigRow) {
      $records += [pscustomobject]@{
        Month = $month; Kind = $Matches[1]; Name = $Matches[2]; Count = [int]$Matches[3]
        Branches = [int]$Matches[4]; Last = $Matches[5]; Gist = $Matches[6]
      }
    }
  }
  [pscustomobject]@{ Records = $records; Aliases = $aliases; Hashes = $hashes; Backfilled = $backfill }
}

function Get-RawFiles { if (Test-Path -LiteralPath $RawDir) { Get-ChildItem -LiteralPath $RawDir -Filter '*.md' | Where-Object { $_.BaseName -match '^\d{4}$' } | Sort-Object Name } }

# everything the record holds, flow slugs folded through the aliases
function Get-AllRecords {
  $d = Read-Digest
  $records = @($d.Records); $aliases = @{} + $d.Aliases; $raw = @()
  foreach ($f in (Get-RawFiles)) { $raw += @(Read-RawEntries $f.FullName) }
  foreach ($e in ($raw | Where-Object { $_.Kind -eq 'alias' })) {
    $p = $e.Name.Split(@(" $Arrow "), [StringSplitOptions]::None); if ($p.Length -eq 2) { $aliases[$p[0]] = $p[1] }
  }
  $records += @(Group-Records $raw)
  foreach ($r in $records) {
    $n = $r.Name; $hops = 0
    while ($r.Kind -eq 'flow' -and $aliases.ContainsKey($n) -and $hops -lt 8) { $n = $aliases[$n]; $hops++ }
    $r.Name = $n
  }
  [pscustomobject]@{ Records = $records; Digest = $d; Raw = $raw }
}

# ---------------------------------------------------------------- capture
function Invoke-Capture {
  try {
    $json = [Console]::In.ReadToEnd()
    if (-not $json) { return }
    $p = $json | ConvertFrom-Json
    $text = if ($p.prompt) { [string]$p.prompt } elseif ($p.user_prompt) { [string]$p.user_prompt } else { '' }
    if ($text -notmatch '#[A-Za-z]') { return }
    $cmds = @(Get-PromptCommands $text (Get-CommandNames))
    if ($cmds.Count -eq 0) { return }
    $spool = Get-SpoolDir; $stamp = Get-Stamp; $branch = Get-Branch; $feature = Get-Feature
    $null = Get-BranchKey $branch $spool
    $rows = ($cmds | ForEach-Object { "$stamp`t$branch`tcmd`t$_`t$feature`n" }) -join ''
    Add-Text (Join-Path $spool 'spool.log') $rows
  } catch { }   # a hook must never block a prompt
}

# ---------------------------------------------------------------- flush
function Invoke-Flush {
  $spool = Get-SpoolDir; $spoolFile = Join-Path $spool 'spool.log'
  $branch = Get-Branch; $stamp = Get-Stamp; $feature = Get-Feature
  $items = @()   # @{ Branch; Line }
  foreach ($l in (Read-Lines $spoolFile)) {
    $c = $l.Split("`t"); if ($c.Length -lt 5) { continue }
    $items += [pscustomobject]@{ Branch = $c[1]; Line = "$($c[0])$Sep" + "cmd$Sep$($c[3])$Sep$($c[4])" }
  }
  $linesPath = if ($LinesFile) { $LinesFile } else { Join-Path $spool 'lines.txt' }
  if (Test-Path -LiteralPath $linesPath) {
    foreach ($l in (Read-Lines $linesPath)) {
      $c = @($l.Split('|') | ForEach-Object { $_.Trim() }); if ($c.Length -lt 2 -or -not $c[1]) { continue }
      $line = switch ($c[0]) {
        'cmd'   { "$stamp$Sep" + "cmd$Sep$($c[1])$Sep$feature" }
        'flow'  { "$stamp$Sep" + "flow$Sep$($c[1])$Sep$feature$Sep" + $(if ($c.Length -gt 2) { $c[2] } else { '' }) }
        'alias' { if ($c.Length -gt 2) { "$stamp$Sep" + "alias$Sep$($c[1]) $Arrow $($c[2])" } }
        default { $null }
      }
      if ($line) { $items += [pscustomobject]@{ Branch = $branch; Line = $line } }
    }
  }
  if ($items.Count -gt 0) {
    $block = ''
    foreach ($g in ($items | Group-Object Branch)) {
      $key = Get-BranchKey $g.Name $spool
      $block += "## $key$Sep$(Get-Topic $g.Name)`n" + (($g.Group | ForEach-Object { $_.Line }) -join "`n") + "`n"
    }
    $month = [DateTime]::UtcNow.ToString('yyMM'); $raw = Join-Path $RawDir "$month.md"
    if ($DryRun) { Write-Output "would append to raw/$month.md :"; Write-Output $block }
    else {
      if (-not (Test-Path -LiteralPath $raw)) { Add-Text $raw "# Workflow usage $Dash raw $month`n" }
      Add-Text $raw $block
      if (Test-Path -LiteralPath $spoolFile) { Remove-Item -LiteralPath $spoolFile }
      if ((Test-Path -LiteralPath $linesPath) -and ((Split-Path -Parent $linesPath) -eq $spool)) { Remove-Item -LiteralPath $linesPath }
      Write-Output "flushed $($items.Count) line(s) to raw/$month.md"
    }
  } else {
    # a lines file that yielded nothing (all malformed) must not linger and be read by a later branch's flush
    if (-not $DryRun -and (Test-Path -LiteralPath $linesPath) -and ((Split-Path -Parent $linesPath) -eq $spool)) { Remove-Item -LiteralPath $linesPath }
    Write-Output 'nothing to flush'
  }
  Invoke-Compound
}

# ---------------------------------------------------------------- compound
# The branches (local, and origin's) holding unmerged changes to a raw file; the current branch is the one writing.
function Get-RawBlockers([string]$relPath) {
  $current = Get-Branch
  try { $refs = Get-GitStrict for-each-ref '--format=%(refname:short)' --no-merged=origin/develop refs/heads refs/remotes/origin }
  catch { return @('git error: branches not listable') }   # fail closed: a failed check holds the month
  foreach ($r in $refs) {
    if ($r -in 'origin', 'origin/HEAD', 'origin/develop', 'origin/main', 'develop', 'main', $current, "origin/$current") { continue }
    try { if (@(Get-GitStrict diff --name-only "origin/develop...$r" -- $relPath).Count -gt 0) { $r } }
    catch { "$r (git error)" }
  }
}

function Invoke-Compound {
  $files = @(Get-RawFiles); if ($files.Count -eq 0) { return }
  $now = [DateTime]::UtcNow; $cur = ($now.Year % 100) * 12 + $now.Month
  $due = @($files | Where-Object { $cur - ([int]$_.BaseName.Substring(0, 2) * 12 + [int]$_.BaseName.Substring(2, 2)) -ge 2 })
  if ($due.Count -eq 0) { return }
  if (@(Get-Git rev-parse --verify -q origin/develop).Count -eq 0) { Write-Output 'compound skipped: no origin/develop to check against'; return }
  foreach ($f in $due) {
    $rel = "xTrack/WorkflowImprovement/usage/raw/$($f.Name)"
    $blockers = @(Get-RawBlockers $rel)
    if ($blockers.Count -gt 0) { Write-Output "compound $($f.BaseName): held - unmerged changes on $($blockers -join ', ')"; continue }
    $entries = @(Read-RawEntries $f.FullName); $digest = Read-Digest; $block = ''
    foreach ($m in ($entries | Group-Object Month)) {
      $texts = [string[]]@($m.Group | ForEach-Object { $_.Text }); [Array]::Sort($texts, [StringComparer]::Ordinal)
      $hash = Get-Hash8 ($texts -join "`n")
      if ($digest.Hashes.ContainsKey("$($m.Name)|$hash")) { continue }
      $rows = @(Group-Records $m.Group | Sort-Object Kind, Name | ForEach-Object {
        "$($_.Kind)$Sep$($_.Name)$Sep$($_.Count)$Sep$($_.Branches)$Sep$($_.Last)" + $(if ($_.Gist) { "$Sep$($_.Gist)" } else { '' })
      })
      $rows += @($m.Group | Where-Object { $_.Kind -eq 'alias' } | ForEach-Object { "alias$Sep$($_.Name)" } | Select-Object -Unique)
      $block += "## $($m.Name)$Sep$hash$Sep$($now.ToString('yyMMdd-HHmm'))`n" + ($rows -join "`n") + "`n"
    }
    if ($DryRun) { Write-Output "would compound $($f.BaseName):"; Write-Output $block; continue }
    if ($block) {
      if (-not (Test-Path -LiteralPath $DigestPath)) { Add-Text $DigestPath "# Workflow usage $Dash digest`n" }
      Add-Text $DigestPath $block
    }
    Remove-Item -LiteralPath $f.FullName
    Write-Output "compounded raw/$($f.Name) into digest.md"
  }
}

# ---------------------------------------------------------------- slugs, report
function Invoke-Slugs {
  $all = Get-AllRecords
  $all.Records | Where-Object { $_.Kind -eq 'flow' } | Group-Object Name |
    ForEach-Object { [pscustomobject]@{ Slug = $_.Name; Uses = ($_.Group | Measure-Object Count -Sum).Sum } } |
    Sort-Object @{ e = 'Uses'; Descending = $true }, Slug | ForEach-Object { "$($_.Slug) ($($_.Uses))" }
}

function Get-Distance([string]$a, [string]$b) {
  $prev = 0..$b.Length
  for ($i = 1; $i -le $a.Length; $i++) {
    $cur = @($i)
    for ($j = 1; $j -le $b.Length; $j++) {
      $cost = if ($a[$i - 1] -eq $b[$j - 1]) { 0 } else { 1 }
      $cur += [Math]::Min([Math]::Min($prev[$j] + 1, $cur[$j - 1] + 1), $prev[$j - 1] + $cost)
    }
    $prev = $cur
  }
  $prev[$b.Length]
}

function Invoke-Report {
  $names = Get-CommandNames; $all = Get-AllRecords; $today = [DateTime]::UtcNow
  $cutoff = $today.AddDays(-$WindowDays).ToString('yyMMdd')
  $cmds = @($all.Records | Where-Object { $_.Kind -eq 'cmd' })
  $minMonth = ($all.Records | ForEach-Object { $_.Month } | Sort-Object | Select-Object -First 1)
  $spanOk = $minMonth -and ([DateTime]::ParseExact('20' + $minMonth + '01', 'yyyyMMdd', $null) -le $today.AddDays(-$WindowDays))
  Write-Output "Workflow usage report - $($today.ToString('yyyy-MM-dd')) - window $WindowDays d (since $cutoff), record from $minMonth"
  Write-Output "uses are summed over the digest/raw blocks whose last use is in the window; branches are summed per block (a nudge, not a verdict)"

  # known entries: every name and alias, plus each named sub-command
  $known = New-Object 'System.Collections.Generic.List[string]'
  foreach ($n in ($names | Sort-Object)) { $known.Add("#$n"); if ($SubMap.ContainsKey($n)) { foreach ($s in $SubMap[$n]) { $known.Add("#$n $s") } } }
  $rows = foreach ($k in $known) {
    $mine = @($cmds | Where-Object { $_.Name -eq $k -or ($k -notmatch ' ' -and $_.Name.StartsWith($k + ' ')) })
    $win = @($mine | Where-Object { $_.Last -ge $cutoff })
    [pscustomobject]@{
      Name = $k; Uses = [int](($win | Measure-Object Count -Sum).Sum); Branches = [int](($win | Measure-Object Branches -Sum).Sum)
      Last = $(if ($mine.Count) { ($mine | Sort-Object Last | Select-Object -Last 1).Last } else { 'never' })
    }
  }
  Write-Output ''
  $present = @($all.Records | ForEach-Object { $_.Month } | Select-Object -Unique)
  $gaps = @(); $m = [DateTime]::ParseExact('20' + $cutoff.Substring(0, 4) + '01', 'yyyyMMdd', $null)
  while ($m -le $today) { $k = $m.ToString('yyMM'); if ($present -notcontains $k) { $gaps += $k }; $m = $m.AddMonths(1) }
  $zero = @($rows | Where-Object { $_.Uses -eq 0 })
  if (-not $spanOk) { Write-Output "REMOVAL CANDIDATES - none yet: the record spans less than $WindowDays days" }
  else {
    $note = if ($gaps.Count) { " - PROVISIONAL: no data for $($gaps -join ', ') in the window" } else { '' }
    Write-Output "REMOVAL CANDIDATES - no use in the window ($($zero.Count) of $($rows.Count))$note"
    $zero | ForEach-Object { "  $($_.Name) - $(if ($_.Last -eq 'never') { 'never recorded' } else { "last $($_.Last)" })" }
  }
  Write-Output ''
  Write-Output 'LOW USE - 1 to 2 uses in the window (watch, do not remove)'
  $rows | Where-Object { $_.Uses -ge 1 -and $_.Uses -le 2 } | ForEach-Object { "  $($_.Name) - $($_.Uses) use(s), last $($_.Last)" }
  Write-Output ''
  Write-Output 'TOP COMMANDS'
  $rows | Where-Object { $_.Uses -gt 2 } | Sort-Object @{ e = 'Uses'; Descending = $true } | Select-Object -First 12 |
    ForEach-Object { "  $($_.Name) - $($_.Uses) uses, $($_.Branches) branch-months, last $($_.Last)" }
  # unmatched = recorded names no longer in the table
  $stale = @($cmds | ForEach-Object { ($_.Name -split ' ')[0].TrimStart('#') } | Select-Object -Unique | Where-Object { -not $names.Contains($_) })
  if ($stale.Count) { Write-Output "  (recorded under a name no longer in the table: $($stale -join ', '))" }

  $flows = @($all.Records | Where-Object { $_.Kind -eq 'flow' -and $_.Last -ge $cutoff } | Group-Object Name | ForEach-Object {
    [pscustomobject]@{ Slug = $_.Name; Uses = ($_.Group | Measure-Object Count -Sum).Sum; Branches = ($_.Group | Measure-Object Branches -Sum).Sum
                       Gist = ($_.Group | Sort-Object Last | Select-Object -Last 1).Gist }
  })
  Write-Output ''
  Write-Output 'COMMAND CANDIDATES - a flow with 3+ uses on 2+ branches in the window'
  $hot = @($flows | Where-Object { $_.Uses -ge 3 -and $_.Branches -ge 2 })
  if ($hot.Count) { $hot | ForEach-Object { "  $($_.Slug) - $($_.Uses) uses, $($_.Branches) branches: $($_.Gist)" } } else { Write-Output "  none ($($flows.Count) flow slug(s) recorded)" }
  $slugs = @($flows | ForEach-Object { $_.Slug })
  $drift = for ($i = 0; $i -lt $slugs.Count; $i++) { for ($j = $i + 1; $j -lt $slugs.Count; $j++) {
    if ((Get-Distance $slugs[$i] $slugs[$j]) -le 3) { "  $($slugs[$i]) ~ $($slugs[$j])" } } }
  Write-Output ''
  Write-Output 'SLUG DRIFT - near-duplicate flow slugs awaiting an alias line'
  if ($drift) { $drift } else { Write-Output '  none' }

  $glyphs = '(?:' + [char]::ConvertFromUtf32(0x1F6D1) + '|' + [char]::ConvertFromUtf32(0x26D4) + ')'
  $guards = @(Read-Lines (Join-Path $Root 'AGENTS.md') | ForEach-Object { if ($_ -match ('^- \*\*' + $glyphs + '\s*([^:*]+)')) { $Matches[1].Trim() } })
  Write-Output ''
  Write-Output "SILENT GUARDS - $($guards.Count) authorisation / boundary rules: no data until stage 2 (rule lines); a guard that never fires is working, never a removal candidate"

  $pending = @(Get-RawFiles)
  $spoolLines = @(Read-Lines (Join-Path (Get-SpoolDir) 'spool.log')).Count
  Write-Output ''
  Write-Output "COMPOUNDING - raw months open: $(if ($pending.Count) { ($pending | ForEach-Object { $_.BaseName }) -join ', ' } else { 'none' }); spool: $spoolLines unflushed line(s)"
  $DryRun = $true; Invoke-Compound
}

# ---------------------------------------------------------------- backfill
function Invoke-Backfill {
  $d = Read-Digest
  if ($d.Backfilled -and -not $Force) { Write-Output 'backfill already done (digest.md carries its marker); -Force to repeat'; return }
  if (-not $TranscriptDir) {
    $TranscriptDir = (Get-ChildItem -LiteralPath (Join-Path $HOME '.claude/projects') -Directory |
      Where-Object { $_.Name -like '*src-Maro-II' } | Select-Object -First 1).FullName
  }
  if (-not $TranscriptDir -or -not (Test-Path -LiteralPath $TranscriptDir)) { Write-Output 'no transcript directory found; pass -TranscriptDir'; return }
  $names = Get-CommandNames; $seen = New-Object 'System.Collections.Generic.HashSet[string]'
  $hits = New-Object 'System.Collections.Generic.List[object]'; $sessions = 0; $prompts = 0
  foreach ($f in (Get-ChildItem -LiteralPath $TranscriptDir -Filter '*.jsonl')) {
    $sessions++
    foreach ($line in [IO.File]::ReadLines($f.FullName)) {
      if (-not $line.Contains('"type":"user"') -or $line.Contains('tool_use_id') -or $line.Contains('"isMeta":true')) { continue }
      if ($line -notmatch '#[A-Za-z]{3}') { continue }
      $o = $line | ConvertFrom-Json
      if ($o.isSidechain -or $o.isMeta -or ($o.origin -and $o.origin.kind -ne 'human')) { continue }
      if ($o.uuid -and -not $seen.Add([string]$o.uuid)) { continue }   # a resumed session repeats its history
      $c = $o.message.content
      $texts = if ($c -is [string]) { @($c) } else { @($c | Where-Object { $_.type -eq 'text' } | ForEach-Object { $_.text }) }
      if ($line -notmatch '"timestamp":"20(\d{2})-(\d{2})-(\d{2})') { continue }
      $yy = $Matches[1]; $mm = $Matches[2]; $dd = $Matches[3]
      foreach ($t in $texts) {
        if ($t.StartsWith('<system-reminder') -or $t.StartsWith('<local-command')) { continue }
        $found = @(Get-PromptCommands $t $names); if ($found.Count -eq 0) { continue }
        $prompts++
        foreach ($n in $found) {
          $hits.Add([pscustomobject]@{ Month = $yy + $mm; Date = $yy + $mm + $dd
                                       Topic = Get-Topic ([string]$o.gitBranch); Name = $n })
        }
      }
    }
  }
  $now = [DateTime]::UtcNow; $out = ''
  foreach ($m in ($hits | Group-Object Month | Sort-Object Name)) {
    $rows = foreach ($g in ($m.Group | Group-Object Name | Sort-Object Name)) {
      $last = ($g.Group | Sort-Object Date | Select-Object -Last 1).Date
      "cmd$Sep$($g.Name)$Sep$($g.Count)$Sep$(@($g.Group | ForEach-Object { $_.Topic } | Select-Object -Unique).Count)$Sep$last"
    }
    $out += "## $($m.Name)$Sep$(Get-Hash8 ($rows -join "`n"))$Sep$($now.ToString('yyMMdd-HHmm'))`n" + ($rows -join "`n") + "`n"
  }
  if ($DryRun) { Write-Output $out; return }
  if (-not (Test-Path -LiteralPath $DigestPath)) { Add-Text $DigestPath "# Workflow usage $Dash digest`n" }
  Add-Text $DigestPath ("# backfill: commands only, from $sessions local transcript(s) until $($now.ToString('yyMMdd-HHmm')), $prompts prompt(s)`n" + $out)
  Write-Output "backfilled $($hits.Count) command use(s) from $prompts prompt(s) in $sessions transcript(s)"
}

# ---------------------------------------------------------------- dispatch
if     ($Capture)  { Invoke-Capture; exit 0 }
elseif ($Flush)    { Invoke-Flush }
elseif ($Compound) { Invoke-Compound }
elseif ($Slugs)    { Invoke-Slugs }
elseif ($Report)   { Invoke-Report }
elseif ($Backfill) { Invoke-Backfill }
else { Get-Help -Name $PSCommandPath | Out-String | Write-Output }
