# Context Hydration — Ui_General — 2026-09-23

**Last Bake:** 2026-09-23 20:57 UTC — written by `#bake`; absence means never baked

**Directive trace:** all five covered action classes were met this session and none stopped — no dependency was added, no machine-shaped file was opened, every write followed an order, the device was touched only for a read-only logcat after the user's own deploy, and every claim about the code rests on a file read in the session.

## State
The route's two surfaces and the app's own vocabulary were reworked together. The panel that owns the dashboard slot now reads like the list card — a header row carrying the phase's title with its **status in the right corner**, the card's rule, a **two-column table** (`Start` · `Destination`, then `Dist` · `ETA`) on the shared `StatCell`, a second rule, the pin, and the actions **bottom-anchored** — while the exit dialog took the doctrine's order and words and the five hand-built checkbox rows became the one `OptionRow`. The saved line's word became **Route** everywhere (the flag, the role family, the keys, the labels and the living docs), so the pair now reads a **Track** (a recording) against a **Route** (a line the mode saved). The aim ring left Compose for a `RouteHost`-owned osmdroid overlay in the track band, and the device's own log then named a long-standing defect: the marker pass swept the route's destination pin, so the line was never fed — the sweep now spares it and the lines no longer depend on it. `apk-build.bat` and the unit suites are green; nothing is committed, and three diagnostic log lines still stand in `RouteHost` pending the verifying run.

## Target Files
- `app/src/main/java/ykws/android/maro/ui/map/RouteConfirmPanel.kt` — the panel's anatomy and the doctrine's action roles
- `app/src/main/java/ykws/android/maro/ui/map/RouteHost.kt` — the map objects, the ring overlay, and the three diagnostic log lines to remove
- `app/src/main/java/ykws/android/maro/ui/map/MarkerOverlay.kt` — the marker pass's sweep, which now spares the route's pin
- `app/src/main/java/ykws/android/maro/ui/components/OptionRow.kt` · `StatCell.kt` — the shared checkbox row and the shared reading cell
- `docs/ui-component-guidelines.md` §5.6 · §5.8 — the button-role doctrine and the route panel's anatomy

## Next Step
Deploy the current build and take the device pass the work owes: aim a route and follow it (the line draws from the boat with the ring under it, the destination dot survives the marker pass, the panel reads header · table · rule · pin with the actions at its foot), then remove the three diagnostic log lines from `RouteHost`.
