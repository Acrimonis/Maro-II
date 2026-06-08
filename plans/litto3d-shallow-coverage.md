# Litto3D shallow coverage — full range, no hardcode

> Design capture (attached to **BakeNormalization**). How to get all Litto3D shallow data across the
> whole corridor with **nothing hardcoded** and without the ~1 GB `.asc` bloat.

## Problem
The Litto3D shallow `.asc` ballooned to ~972 MB. A hardcoded coast-band clip (`S=43.40 / N=43.74`)
was a bad fix — region-specific, and it can clip real offshore shallows.

## Key fact — the deep data isn't published
Litto3D is a coastal LiDAR/sonar product; SHOM surveys only the **littoral** (guaranteed marine extent
~the −10 m isobath, patchy beyond, nothing deep). So every offshore/deep cell in the clip box is
`nodata` (−99999). **The 972 MB is nodata text padding, not data.** ⇒ The size is a *format* problem,
not a coverage one — and clipping for correctness is unnecessary (the merge skips nodata).

## Approach — full range, nothing hardcoded
1. **Have the data — fetch the full corridor tiles.** `fetch_litto3d_paca.ps1` *without* a focused
   `-Xmin/-Xmax` window (~2.8 GB DL; `-Mnt5m` ~25× smaller on disk; cache tiles). "All shallow on the
   whole range" = the 0–10 m strip along the entire E/W coast; the deep south has no shallow water.
2. **Derive the clip — kill the hardcode.** `bake-coastline` writes a text sidecar
   `data/app-assets/coastlines/<region>.bbox` = `latS latN lonW lonE` (it's already a JVM step).
   `bake-env` reads it (CR-safe) and sets the GDAL clip = coast bbox ± 6 NM; E/W from the props.
   EMODnet + Litto3D share it. No hardcoded numbers (also retires bake-env's `43.28/43.80`).
3. **Make nodata free — gzip.** Write `…/litto3d-<region>.asc.gz` (GDAL `/vsigzip/` or a post step);
   the −99999 padding compresses ~50–100× → ~972 MB → ~10–30 MB. Teach
   `AsciiGridParser.parse(File)` to wrap `.gz` in `GZIPInputStream` — keeps the plain-text
   (no binary raster parser) design.

## Tradeoffs
- One-time ~2.8 GB tile fetch (cache it).
- Edits: `bake-coastline` (+sidecar), `bake-env` (read sidecar; drop hardcoded S/N),
  `AsciiGridParser` (`.gz`), bake gzip step.
- gzip fixes **disk, not RAM**: a full 5 m corridor grid is still a ~508 MB `FloatArray` in the merge
  (~127 M cells, mostly fast-skipped nodata, ~seconds; fits 4 g). Coarsen to ~10 m (`-r min` stays
  collision-safe) for leaner RAM/merge — orthogonal to gzip.
- No detail deeper than ~10 m from Litto3D — needs SHOM survey lots / the planned dive tiers.

## Net
Remove the hardcoded band → derive the clip from the coastline (sidecar) → fetch the full tiles →
gzip `.asc` + parser `.gz`. Full shallow coverage, zero hardcoding, ~10–30 MB on disk.
