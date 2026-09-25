package ykws.android.maro.spatial

import ykws.android.maro.data.coastline.CoastlineSerializer
import ykws.android.maro.data.model.CoastlineData
import java.io.File

/**
 * **The one baked file the point-walk guard reads, and where it lives.**
 *
 * It exists because that guard reads the real coastline — its oracle is a brute force over every
 * segment of the region — and it must read the *same* file the app ships. It used to borrow the route
 * bake's own three-input loader; the bake was removed on 2026-09-22, and this is the coastline half of
 * it, kept where it belongs: beside the index it is read for, with no route machinery in sight.
 *
 * The rule the removed loader carried is kept: the caller owns the refusal. A reader that only wants a
 * number skips when the file is absent ([path] is exposed so it can test that before [load] reads it),
 * and nothing here decides that question.
 */
internal class PrebakedCoastline(val data: CoastlineData) {

    companion object {

        /** The path the app's own asset tree uses, so a guard and the app never disagree about the file. */
        fun path(repoDir: File, region: String): File =
            File(repoDir, "data/app-assets/coastlines/$region.bin")

        /** The coastline as the app reads it — the same serializer, off the same bytes. */
        fun load(repoDir: File, region: String): PrebakedCoastline =
            PrebakedCoastline(CoastlineSerializer.deserialize(path(repoDir, region).readBytes()))
    }
}
