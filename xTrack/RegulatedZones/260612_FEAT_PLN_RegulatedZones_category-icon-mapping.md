<!-- scope: feature -->
# Regulated Zones — Functional Category-to-Icon Mapping

## Purpose

Trace every raw source code through classification to its final icon and map colour, so we can validate the data extraction produces the expected visual results.

---

## 1. Full Chain Mapping

### SHOM Sources

| Raw Code | System | `RegulatedZoneType` | `ZoneDisplayCategory` | Icon | Map Colour | Speed Field |
|----------|--------|-------------------|----------------------|------|-----------|-------------|
| CATREA=27 | S-57 | `SPEED_LIMIT` | `SPEED_LIMIT` | **10** or **5** (bold white on red) | Blue fill `#1565C0` | vitesse_max / INFORM / TXTDSC |
| CATREA=12 | S-57 | `NAVIGATION_RESTRICTION` | *(none — map only)* | — | Purple fill `#8E24AA` | n/a |
| RESTRN=1 | S-57 | `ANCHORING_PROHIBITED` | `NO_ANCHOR` | ⚓ on amber `#FF8F00` + strike | Amber fill `#FF8F00` | n/a |
| RESTRN=2 | S-57 | `ANCHORING_PROHIBITED` | `NO_ANCHOR` | ⚓ on amber `#FF8F00` + strike | Amber fill `#FF8F00` | n/a |
| RESTRN=7 | S-57 | `ACCESS_PROHIBITED` | `NO_ACCESS` | 🚤 on dark blue `#1565C0` + strike | Red fill `#E53935` | n/a |
| RESTRN=8 | S-57 | `ACCESS_PROHIBITED` | `NO_ACCESS` | 🚤 on dark blue `#1565C0` + strike | Red fill `#E53935` | n/a |
| RESTRN=10 | S-57 | `OTHER` | `NO_DIVING` | 🤿 on dark blue `#1565C0` + strike | Grey fill `#78909C` | n/a |
| restrn=1 | S-101 | `SPEED_LIMIT` | `SPEED_LIMIT` | **5**/**10** (bold white on red) | Blue fill `#1565C0` | vitesse_max / INFORM / TXTDSC |
| restrn=7 | S-101 | `ANCHORING_PROHIBITED` | `NO_ANCHOR` | ⚓ on amber `#FF8F00` + strike | Amber fill `#FF8F00` | n/a |
| restrn=10/11/12 | S-101 | `ACCESS_PROHIBITED` | `NO_ACCESS` | 🚤 on dark blue `#1565C0` + strike | Red fill `#E53935` | n/a |
| restrn=18 | S-101 | `MOORING` | `MOORING` | 🛥️ on dark blue `#1565C0` | Teal fill `#00897B` | n/a |
| restrn=27 | S-101 | `NAVIGATION_RESTRICTION` | *(none — map only)* | — | Purple fill `#8E24AA` | n/a |
| restrn=28 | S-101 | `ENVIRONMENTAL` | *(none — map only)* | — | Green fill `#2E7D32` | n/a |

### INPN Sources

| Raw Code | System | `RegulatedZoneType` | `ZoneDisplayCategory` | Icon | Map Colour | Notes |
|----------|--------|-------------------|----------------------|------|-----------|-------|
| mpa_type=`Natura 2000` | INPN | `ENVIRONMENTAL` | *(none — map only)* | — | Green fill `#2E7D32` | General surveillance |
| mpa_type=`Arrêté de biotope` | INPN | `ACCESS_PROHIBITED` | `NO_ACCESS` | 🚤 on dark blue `#1565C0` + strike | Red fill `#E53935` | Strict access/engine bans |
| mpa_type=`Réserve Naturelle` | INPN | `NAVIGATION_RESTRICTION` | *(none — map only)* | — | Purple fill `#8E24AA` | Strict navigation rules |

### Seed Sources (Hardcoded Fallbacks)

| Zone | `RegulatedZoneType` | `ZoneDisplayCategory` | Icon | Map Colour |
|------|-------------------|----------------------|------|-----------|
| Cap d'Antibes 10 kn | `SPEED_LIMIT` | `SPEED_LIMIT` | **10** (bold white on red) | Blue fill `#1565C0` |
| Îles de Lérins | `NAVIGATION_RESTRICTION` | *(none — map only)* | — | Purple fill `#8E24AA` |
| Baie des Anges 10 kn | `SPEED_LIMIT` | `SPEED_LIMIT` | **10** (bold white on red) | Blue fill `#1565C0` |

---

## 2. Visual Summary — What the User Sees

### Warning Strip Icons (Bottom-Left)

| Icon | Meaning | Appears When |
|------|---------|-------------|
| ⚓ + strike | Anchoring prohibited | RESTRN=1/2, restrn=7, or description contains "mouillage" |
| 🛥️ | Mooring area | restrn=18 or description contains "mooring"/"amarrage" |
| **10** | Speed limit 10 kn | vitesse_max=10, or INFORM text, or TXTDSC `FR_PREMAR_MED_134_2021` |
| **5** | Speed limit 5 kn | vitesse_max=5, or INFORM text, or TXTDSC `FR_PREMAR_MED_2012_064` |
| 🤿 + strike | Diving prohibited | RESTRN=10 or description contains "diving"/"plongée" |
| 🚤 + strike | Access prohibited | RESTRN=7/8, restrn=10/11/12, or mpa_type=Arrêté de biotope |

### Map Polygon Colours

| Colour | Zone Type | Example Data Source |
|--------|-----------|-------------------|
| Blue `#1565C0` | Speed limit | CATREA 27, INSPIRE restrn 1 |
| Amber `#FF8F00` | Anchoring prohibited | RESTRN 1/2 |
| Red `#E53935` | Access prohibited | RESTRN 7/8 |
| Green `#2E7D32` | Environmental | INPN Natura 2000 |
| Teal `#00897B` | Mooring | INSPIRE restrn 18 |
| Yellow `#FDD835` | Fishing prohibited | INSPIRE restrn 8/9 |
| Purple `#8E24AA` | Navigation restriction | CATREA 12, INPN Réserve Naturelle |
| Grey `#78909C` | Other (fallback) | RESTRN 10, unclassified |

---

## 3. Validation Matrix

To validate the pipeline, run the prebake test and check:

| Check | Pass Condition |
|-------|---------------|
| CATREA=27 zones appear as Blue polygons with SPEED_LIMIT icon | Each CATREA=27 zone has `zoneType=SPEED_LIMIT` and `speedLimitKn` is populated (from one of 4 extraction methods) |
| RESTRN=1/2 zones appear as Amber polygons with ⚓+strike icon | Each RESTRN=1/2 zone has `zoneType=ANCHORING_PROHIBITED` and `displayCategories()` contains `NO_ANCHOR` |
| RESTRN=10 zones appear as Grey polygons with 🤿+strike icon | Each RESTRN=10 zone has `zoneType=OTHER` but `displayCategories()` contains `NO_DIVING` |
| INPN Natura 2000 zones appear as Green polygons with no strip icon | Each Natura 2000 zone has `zoneType=ENVIRONMENTAL` and `displayCategories()` is empty |
| INPN Arrêté de biotope zones appear as Red polygons with 🚤+strike icon | Each biotope zone has `zoneType=ACCESS_PROHIBITED` and `displayCategories()` contains `NO_ACCESS` |
| Speed from INFORM `"10 nds"` | `speedLimitKn=10.0`, `speedSource=INFORM_TEXT` |
| Speed from TXTDSC `FR_PREMAR_MED_134_2021` | `speedLimitKn=10.0`, `speedSource=TXTDSC_MAP`, `legalDecreeRef="FR_PREMAR_MED_134_2021"` |
| No zone produces two different icons for the same polygon | Single zone → single map colour, possibly multiple strip icons (e.g. NO_ANCHOR + SPEED_LIMIT) |

