<!-- scope: feature -->
# Coastline Granularity — Metrics Comparison (50 km coastline)

| Metric | ε=3m | ε=8m | ε=10m | ε=15m | Why it matters |
|--------|------|------|-------|-------|----------------|
| **Points** | 16,000 | 6,250 | 5,000 | 3,300 | Fewer points = everything faster |
| **Cache file** | 130 KB | 50 KB | 40 KB | 26 KB | Disk space for multi-region cache |
| **Memory** | 640 KB | 250 KB | 200 KB | 130 KB | Less RAM = less app kills |
| **Distance query** | 16,000 checks | 6,250 checks | 5,000 checks | 3,300 checks | Every GPS poll scans all edges |
| **Water/land check** | 16,000 checks | 6,250 checks | 5,000 checks | 3,300 checks | Same as distance |
| **Island filter** | 16M checks | 2.5M checks | 1.6M checks | 720K checks | Only at generation time |
| **Map draw** | 16,000 verts | 6,250 verts | 5,000 verts | 3,300 verts | Smoother on low-end devices |
| **Max error** | < 3m | < 8m | < 10m | < 15m | How far from true coastline |
| **vs GPS accuracy** | GPS wins | GPS wins | GPS wins | GPS ≈ equal | Phone GPS is ~5-15m |
| **Zone 300m error** | < 1% | < 3% | < 3.5% | < 5% | Error at the zone boundary |
| **App complexity** | Same | Same | Same | Same | Just one constant change |

## Visual: What 15m vs 3m looks like at zoom 11

```
Zoom 11 view (1 cm on screen ≈ 50m on ground):

ε=3m:  ··············································
       Every dot is a vertex — you can't see them
       even if you zoom in 5× more

ε=15m: ··············································
       5× fewer dots, but the line looks IDENTICAL
       because the screen can't show detail under 50m
```

## Key takeaway

**ε=15m is the sweet spot** where:
- Data and queries are 5× faster than ε=3m
- Position error (15m) matches GPS accuracy (5-15m)
- The 300m zone error (5%) is negligible for regulatory enforcement
- The map looks identical at any usable zoom level

**ε=10m is the conservative choice** if you want a safety margin:
- 3× faster than ε=3m
- Error (10m) is well within GPS accuracy
- Zone error (3.5%) — very safe

**ε=3m** is only useful if you're using survey-grade GPS (< 1m accuracy) or mapping harbor details. For open-water navigation, it's unnecessary.

