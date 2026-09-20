package ykws.android.maro.spatial.mesh

import ykws.android.maro.data.model.RouteResult

/**
 * **The one home for what a route's dossier reads as when it carries none.**
 *
 * The mesh engine's counters are its own dossier ([RouteMeshDetails]); a result from another engine,
 * or from one that answered no readings at all, carries none of them. The rule stated here — and
 * stated nowhere else — is that such a result reads as the **empty** dossier, **every counter zero**,
 * because "not measured" and "measured as zero" are the same number and only one of them is worth a
 * failing assertion. The probe is the reader this is for: it prints counters, so an engine that
 * answered none should print zeros rather than stop the run.
 */
internal val RouteResult.Success.meshDetailsOrEmpty: RouteMeshDetails
    get() = details as? RouteMeshDetails ?: RouteMeshDetails()

/**
 * The same dossier, **read rather than defaulted** — for a test that means to count.
 *
 * [`meshDetailsOrEmpty`][meshDetailsOrEmpty]'s fallback is what lets a reading pass vacuously: a
 * change that stops producing a dossier at all still satisfies `assertEquals(0, …)` ("no corner was
 * sharp") without a dossier ever having been written, while an exact-count assertion fails on it. That
 * asymmetry is the whole reason this second accessor exists: a test that means to read a number asks
 * through this one, so a missing dossier is a failure and never a zero.
 */
internal val RouteResult.Success.meshDetailsRead: RouteMeshDetails
    get() = details as? RouteMeshDetails
        ?: error(
            "the engine answered with no dossier — a reading that means to count a " +
                "RouteMeshDetails must not pass on an empty one; use meshDetailsOrEmpty to want zeros"
        )
