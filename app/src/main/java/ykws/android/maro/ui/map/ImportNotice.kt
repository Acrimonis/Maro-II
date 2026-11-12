package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.ui.notice.Notice
import ykws.android.maro.ui.notice.NoticeKeys
import ykws.android.maro.ui.notice.NoticeText

/**
 * An import's outcome as a notice: the counts as one composed line — imported, ignored and, when non-zero, the
 * unreadable files, which are a reason the user must see rather than a count folded into "ignored" — or the
 * failure line when nothing at all was imported, ignored or found unreadable.
 */
internal fun importNotice(banner: ImportBannerState): Notice {
    val text = when (banner) {
        ImportBannerState.Failed -> NoticeText.Res(R.string.import_failed)
        is ImportBannerState.Result ->
            if (banner.imported == 0 && banner.ignored == 0 && banner.invalid == 0) {
                NoticeText.Res(R.string.import_failed)
            } else {
                val (importedPlural, ignoredPlural) = when (banner.kind) {
                    TransferKind.MARKER ->
                        R.plurals.import_result_marker_imported to R.plurals.import_result_marker_ignored
                    TransferKind.TRACK ->
                        R.plurals.import_result_imported to R.plurals.import_result_ignored
                }
                val counts = NoticeText.Joined(
                    R.string.import_result_format,
                    listOf(
                        NoticeText.Plural(importedPlural, banner.imported),
                        NoticeText.Plural(ignoredPlural, banner.ignored)
                    )
                )
                if (banner.invalid > 0) {
                    NoticeText.Joined(
                        R.string.import_result_format,
                        listOf(counts, NoticeText.Plural(R.plurals.import_result_invalid, banner.invalid))
                    )
                } else counts
            }
    }
    return Notice.result(NoticeKeys.IMPORT, text)
}
