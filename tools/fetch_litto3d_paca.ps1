<#
.SYNOPSIS
  Fetch the SHOM Litto3D PACA 2015 collision-tier tiles covering the Maro zone
  (Cannes->Menton) and extract the 1 m MNT .asc into tools\litto3d_tiles\ for bake_litto3d.bat.

.DESCRIPTION
  Litto3D PACA 2015 is OPEN DATA (Etalab Licence Ouverte v2.0) and is published through the
  SHOM INSPIRE pre-package download service -- a PUBLIC, NO-ACCOUNT API (INSPIRE pre-defined
  dataset download services are open by directive). This replaces the old manual
  diffusion.shom.fr account + cart flow; nothing here is gated.

  Public endpoints (host services.data.shom.fr):
    Group list : GET /INSPIRE/telechargement/prepackageGroup?request=GetCapabilities
                 -> XML of <PrepackageGroup><Name>...  (PACA Litto3D = LITTO3D_PACA_2015_PACK_DL)
    Tile list  : GET /INSPIRE/telechargement/prepackageGroup/LITTO3D_PACA_2015_PACK_DL
                 -> JSON {prepackageResources:[{prepackageName:"XXXX_YYYY", metadataIds:[...]}]}
                    (259 tiles for PACA, named by Lambert-93 km of the tile anchor)
    File list  : GET .../prepackageGroup/LITTO3D_PACA_2015_PACK_DL/prepackage/<XXXX_YYYY>
                 -> JSON {downloadFiles:[{fileName,fileSize,fileMd5}]}   (one .7z per prepaquet)
    Download   : GET .../prepackage/<XXXX_YYYY>/file/<fileName>
                 -> the .7z  (application/x-7z-compressed, HTTP range/resume supported, MD5 above)

  Each .7z contains 1 km sub-tiles, each with MNT1m/ + MNT5m/ ESRI ASCII (Lambert-93 / IGN69,
  nodata -99999), a point cloud, masks, and the Etalab licence PDF. We extract only the 1 m
  MNT .asc, flattened into tools\litto3d_tiles\, which bake_litto3d.bat mosaics + reprojects.

  Uses ONLY built-in curl.exe + tar.exe (Windows bsdtar reads .7z) -- no 7-Zip required.
  Idempotent + resumable: archives are MD5-verified and skipped if already complete.

  Full zone = 38 prepaquets, ~2.8 GB to DOWNLOAD. Each .7z bundles 1 m + 5 m + point cloud, so
  the download size is the SAME regardless of -Mnt5m -- the flag only changes what is extracted.
  Extracted .asc: ~15-20 GB at 1 m, or ~0.3-1 GB at 5 m. The bake downsamples to ~5 m anyway
  (-r min, shoalest), so -Mnt5m is nearly equivalent (loses only the per-1m shoalest sub-sampling)
  and far lighter to extract + bake.

.PARAMETER ListOnly  List the selected tiles + total size, download nothing.
.PARAMETER Mnt5m     Extract the 5 m DEM instead of the 1 m DEM (far smaller on disk to extract
                     and bake; the .7z DOWNLOAD is unchanged -- it contains both resolutions).
.EXAMPLE
  tools\fetch_litto3d_paca.ps1 -ListOnly
  tools\fetch_litto3d_paca.ps1            # full 1 m fetch for the zone, then run bake_litto3d.bat
#>
[CmdletBinding()]
param(
  # Lambert-93 km window covering DepthConstants.WATER_BBOX (lon 6.70-7.31, lat 43.40-43.75),
  # with a one-tile margin; the bake clips to the exact WGS84 box afterwards.
  [int]$Xmin = 995, [int]$Xmax = 1050,
  [int]$Ymin = 6255, [int]$Ymax = 6305,
  [string]$TilesDir = (Join-Path $PSScriptRoot 'litto3d_tiles'),
  [switch]$Mnt5m,
  [switch]$ListOnly
)
$ErrorActionPreference = 'Stop'
$base    = 'https://services.data.shom.fr/INSPIRE/telechargement/prepackageGroup'
$group   = 'LITTO3D_PACA_2015_PACK_DL'
$pattern = if ($Mnt5m) { '*_MNT5_*.asc' } else { '*_MNT_*.asc' }

function Get-ShomJson($url) { (curl.exe -s $url) | ConvertFrom-Json }

Write-Host "Listing prepackages in $group ..."
$all = (Get-ShomJson "$base/$group").prepackageResources
$sel = $all | Where-Object {
  $p = $_.prepackageName -split '_'
  $p.Count -eq 2 -and [int]$p[0] -ge $Xmin -and [int]$p[0] -le $Xmax -and `
                      [int]$p[1] -ge $Ymin -and [int]$p[1] -le $Ymax
} | Sort-Object prepackageName

Write-Host ("Selected {0} tiles (Lambert-93 km X {1}-{2}, Y {3}-{4})." -f $sel.Count,$Xmin,$Xmax,$Ymin,$Ymax)

$items = foreach ($t in $sel) {
  $f = (Get-ShomJson "$base/$group/prepackage/$($t.prepackageName)").downloadFiles[0]
  [pscustomobject]@{ tile = $t.prepackageName; file = $f.fileName; size = [long]$f.fileSize; md5 = $f.fileMd5 }
}
$totMB = [math]::Round(($items | Measure-Object size -Sum).Sum / 1MB, 0)
$items | ForEach-Object { Write-Host ("  {0,-10} {1,8:N1} MB  {2}" -f $_.tile, ($_.size/1MB), $_.file) }
Write-Host ("Total compressed download: ~{0} MB across {1} tiles ({2} DEM)." -f $totMB, $items.Count, $(if($Mnt5m){'5 m'}else{'1 m'}))
if ($ListOnly) { return }

New-Item -ItemType Directory -Force $TilesDir | Out-Null
$arcDir = Join-Path $TilesDir '_archives'
New-Item -ItemType Directory -Force $arcDir | Out-Null

$n = 0
foreach ($it in $items) {
  $n++
  $arc = Join-Path $arcDir $it.file
  $haveOk = (Test-Path $arc) -and ((Get-FileHash -Algorithm MD5 $arc).Hash.ToLower() -eq $it.md5.ToLower())
  if (-not $haveOk) {
    Write-Host ("[{0}/{1}] downloading {2} ({3:N1} MB) ..." -f $n, $items.Count, $it.file, ($it.size/1MB))
    curl.exe -s -L -C - -o $arc "$base/$group/prepackage/$($it.tile)/file/$($it.file)"
    $got = (Get-FileHash -Algorithm MD5 $arc).Hash.ToLower()
    if ($got -ne $it.md5.ToLower()) {
      Write-Warning ("MD5 mismatch for {0} (got {1}, want {2}) -- skipping extract." -f $it.file, $got, $it.md5)
      continue
    }
  } else {
    Write-Host ("[{0}/{1}] {2} already present (MD5 ok)." -f $n, $items.Count, $it.file)
  }
  # Extract only the chosen MNT .asc, flattened into TilesDir (sub-tile names are unique).
  Push-Location $TilesDir
  try { tar -xf $arc --strip-components=3 $pattern 2>$null } finally { Pop-Location }
}

$asc = @(Get-ChildItem $TilesDir -Filter *.asc -File -ErrorAction SilentlyContinue)
Write-Host ''
Write-Host ("Done: {0} .asc tiles in {1}" -f $asc.Count, $TilesDir)
Write-Host ("Archives kept in {0} (~{1} MB) -- safe to delete after baking." -f $arcDir, $totMB)
Write-Host 'Next: tools\bake_litto3d.bat   (mosaic -> reproject Lambert-93->WGS84 -> clip -> shoalest 5 m)'
