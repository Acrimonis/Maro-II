# Context Hydration — Ui_Dashboard — 2026-10-12

**Last Bake:** 2026-10-12 08:58 UTC — written by `#bake`; absence means never baked

**Directive trace:** All five covered action classes were met with a verdict line and none stopped — no dependency added, no machine-shaped data file opened, every step taken on an explicit order, the device untouched, and every claim about the code backed by a file read. The one git write, the branch creation, ran on the user's own `#new` invocation.

## State

The dashboard's Speed tile now reads **whole km/h while the dashboard position is on land** and knots at sea: `Units.kt` holds the one conversion (`KMH_PER_KNOT = 1.852`, `knotsToKmh`), a `dash_value_kmh_int` key joined both locales, and `SpeedCard` took `isWater` and a `when` that keeps the off-land integer/one-decimal knot forms, the `> 99.9 kn` guard and the zone-compliance colour untouched. The layout normalisation from the 2026-10-03 bake still stands beneath it, with its open items W20 and the device pass. `apk-build.bat` is green.

## Target Files

- `app/src/main/java/ykws/android/maro/spatial/Units.kt` — the one unit-conversion home, now carrying `KMH_PER_KNOT` and `knotsToKmh`
- `app/src/main/java/ykws/android/maro/ui/map/DashboardPanel.kt` — `SpeedCard` with its `isWater` argument and the land/sea format `when`
- `app/src/main/res/values/strings.xml` · `values-fr/strings.xml` — the `dash_value_kmh_int` key
- `app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt` — the `isWater` source feeding `DashboardPanel`
- `app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt` · `DashboardBandGeometry.kt` — the shared frame and band coercion from the layout work

## Next Step

Nothing outstanding from this session. The standing items remain the layout work's device pass: the square corners at every size, the map shrinking and returning as a card opens and closes, the tightened inset, the landscape column's spacing, and whether the dashboard sits behind the navigation bar (plan §14 W19).
