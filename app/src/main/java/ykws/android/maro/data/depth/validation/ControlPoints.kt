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
 * NOTE: the depth values below are **illustrative placeholders** pending confirmation
 * against the official SHOM chart (7287 — Golfe de la Napoule / Îles de Lérins) and
 * published dive-site references during the Litto3D bake. The harness and its thresholds
 * are exercised by synthetic fixtures in `DepthValidatorTest`; these are the runtime set.
 */
object ControlPoints {
    val NICE_FREJUS: List<ControlPoint> = listOf(
        ControlPoint(
            name = "Passe des Lérins (Ste-Marguerite ↔ St-Honorat)",
            lat = 43.5105, lon = 7.0465, knownDepthM = 3.0, datum = DepthDatum.LAT,
            source = "SHOM chart 7287 (TODO confirm)", toleranceM = 0.5
        ),
        ControlPoint(
            name = "Plateau du Milieu (Lérins)",
            lat = 43.5160, lon = 7.0520, knownDepthM = 6.0, datum = DepthDatum.LAT,
            source = "SHOM chart 7287 (TODO confirm)", toleranceM = 0.8
        ),
        ControlPoint(
            name = "Sec dive site off Cap d'Antibes",
            lat = 43.5500, lon = 7.1300, knownDepthM = 28.0, datum = DepthDatum.LAT,
            source = "dive guide (TODO confirm)", toleranceM = 2.0
        ),
        ControlPoint(
            name = "Open-water sounding S of Lérins",
            lat = 43.4800, lon = 7.0500, knownDepthM = 45.0, datum = DepthDatum.LAT,
            source = "SHOM sounding (TODO confirm)", toleranceM = 2.0
        )
    )
}
