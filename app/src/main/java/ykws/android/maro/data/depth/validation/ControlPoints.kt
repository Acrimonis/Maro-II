package ykws.android.maro.data.depth.validation

import ykws.android.maro.data.model.DepthDatum

/**
 * An independent ground-truth depth at a known location, used to validate the
 * extracted depth grid.
 *
 * @property knownDepthM metres below [datum], positive-down.
 * @property toleranceM expected uncertainty of the truth value itself.
 */
data class ControlPoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val knownDepthM: Double,
    val datum: DepthDatum,
    val source: String,
    val toleranceM: Double = 1.0
)

/**
 * Region control-point fixtures for `nice-frejus`.
 *
 * Depths are **cross-checked live against the EMODnet REST `/depth_sample` API** (each cites the
 * underlying SeaDataNet survey) and against the baked EMODnet + Litto3D rasters for coverage, on
 * 2026-06-06. EMODnet is the *deep* grid's own source, so for the dive tier these are a
 * bake-pipeline sanity check rather than a fully independent gate; the **collision/shallow tiers**
 * (Litto3D-authoritative) are the meaningful independent checks. Confirming the soundings against
 * official SHOM chart 7287 (Golfe de la Napoule / Îles de Lérins) remains the ideal —
 * `DepthValidatorTest` exercises the thresholds with synthetic fixtures.
 *
 * Each point sits on a location the baked grid actually **covers** (verified via `gdallocationinfo`);
 * the report only passes with ≥4 covered points (`minControlPoints`). The Côte d'Azur shelf is very
 * steep (it plunges from <10 m to hundreds of metres within ~1 km), so covered points in the
 * 10–60 m band are scarce.
 *
 * KNOWN COVERAGE GAP (no fixture — would be uncovered): the ~28 m "sec" off **Cap d'Antibes** sits
 * in an EMODnet nodata hole *and* beyond Litto3D's reliable shallow range, so it cannot be a useful
 * control point until a fine dive tier (SHOM survey lot / Sentinel-2 SDB) covers the cape. (Its
 * obvious coordinate 7.13,43.55 is the headland itself — land; the real sec is offshore.)
 */
object ControlPoints {
    val NICE_FREJUS: List<ControlPoint> = listOf(
        // Collision band (0–5 m). REST and Litto3D agree ~3.1–3.6 m; Litto3D (1 m LiDAR) is the
        // authority here, so the truth is held at the conservative shoalest end.
        ControlPoint(
            name = "Passe des Lérins (Ste-Marguerite ↔ St-Honorat)",
            lat = 43.5105, lon = 7.0465, knownDepthM = 3.0, datum = DepthDatum.LAT,
            source = "EMODnet REST survey S201300200-003 (avg −3.56 m); Litto3D ~3.1 m", toleranceM = 0.5
        ),
        // Shallow band (5–10 m). Surveyed sounding S of Sainte-Marguerite.
        ControlPoint(
            name = "Sounding S of Sainte-Marguerite (Lérins)",
            lat = 43.5000, lon = 7.0500, knownDepthM = 5.5, datum = DepthDatum.LAT,
            source = "EMODnet REST survey S201300200-003 (avg −5.5 m)", toleranceM = 1.0
        ),
        // Dive band (10–60 m), low end. Surveyed sounding off Juan-les-Pins.
        ControlPoint(
            name = "Surveyed sounding off Juan-les-Pins",
            lat = 43.5400, lon = 7.1000, knownDepthM = 13.8, datum = DepthDatum.LAT,
            source = "EMODnet REST survey S201300200-003 (avg −13.8 m)", toleranceM = 2.0
        ),
        // Dive band (10–60 m), deep end. Surveyed sounding E of Cap d'Antibes (EMODnet-covered;
        // Litto3D has no data this deep — a clean EMODnet check).
        ControlPoint(
            name = "Surveyed sounding E of Cap d'Antibes",
            lat = 43.5500, lon = 7.1500, knownDepthM = 46.5, datum = DepthDatum.LAT,
            source = "EMODnet REST survey S198700200-12 (avg −47.7 m)", toleranceM = 2.0
        )
    )
}
