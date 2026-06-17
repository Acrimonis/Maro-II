<!-- scope: feature -->
# Regulated Zones — Quick Fixes Discussion

## 1. `appliesTo()` — "between X and Y" not handled

**Problem:** "Mandatory anchoring area for vessels between 24 m and 80 m in length" → regex doesn't match "between" → zone passes for a 6m boat.

**Fix (Option B):** Add `between` to the min-pattern keyword list:
```kotlin
(more than|over|exceeding|greater than or equal to|greater than|between|>|≥|minimum|...)
```

This extracts "24" as min, and the existing max regex catches "80" via `(less than|under|below|<|≤|maximum|...)` → no, actually the max regex wouldn't match "between" either. Need to also handle the case where "between" produces both a min AND max. So we need a range regex too:
```kotlin
val rangeMatch = Regex("""between\s+(\d+)\s*m\s*(?:and|to)\s*(\d+)\s*m""", RegexOption.IGNORE_CASE).find(desc)
```

**Files:** `RegulatedZone.kt` — `appliesTo()` function

## 2. NAVIGATION_RESTRICTION zones with speed info

**Problem:** Zones typed `NAVIGATION_RESTRICTION` with "10 knots" in description — the speed keyword detection (`"speed" in desc`, `"knot" in desc`) should already catch these and add `SPEED_LIMIT` category. If it's not working, the issue may be that `parseSpeedFromDescription()` returns null for the current text patterns.

**Fix:** Verify speed extraction works for "speed is limited to 10 knots" (it should: `(\d+)\s*(knots?)` matches "10 knots").

**Alternative:** Change the `INFORMATION` fallback emoji from ℹ️ to 🚫 (forbidden sign) so NAVIGATION_RESTRICTION zones with no specific category show a prohibition icon instead of info.

**Files:** `RegulatedZoneIconProvider.kt` — `emojiForCategory(INFORMATION)`

## 3. Collapsible "Regulation info display" toggle

**Problem:** User wants a toggle at the top of the regulated zones card to show/hide the zone info text panel (text beside icons).

**Design:** Add a new `AppSettings` field:
```kotlin
val regulationInfoVisible: Boolean = false  // OFF by default
```

When OFF: only the icon stack shows, no text panel.
When ON: both icons + text show as current.

Add to SettingsManager persistence, and wire in MapScreen to conditionally show `RegulatedZoneInfoText`.

## 4. Rename "Categories visibility" → "Categories"

Simple label change in the SettingsExpander.

## Summary Table

| # | Change | Files | Scope |
|---|--------|-------|-------|
| 1 | `appliesTo()` add "between X and Y" range | `RegulatedZone.kt` | Small |
| 2 | INFORMATION emoji ℹ️→🚫 | `RegulatedZoneIconProvider.kt` | Small |
| 3 | Reg info toggle (default off) | `AppSettings` + `MapScreen.kt` | Medium |
| 4 | Rename label | `MapScreen.kt` | Tiny |

