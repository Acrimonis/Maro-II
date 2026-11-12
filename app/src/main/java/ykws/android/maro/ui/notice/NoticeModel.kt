package ykws.android.maro.ui.notice

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import ykws.android.maro.config.AppConfig

/**
 * What a notice is, ranked: declared highest first, so [ordinal] is the rank — a lower ordinal outranks a
 * higher one. The arbiter reads nothing else about a class; its dwell is the notice's own.
 */
internal enum class NoticeClass {
    /** The "press back again" pill: pre-empts, never waits. */
    EXIT,

    /** Something went wrong, with a reason the user must read. */
    ERROR,

    /** A state that stands until its owner clears it — an operation running, a route arrived. */
    STATUS,

    /** The outcome of something the user asked for, with figures to read — an import count. */
    RESULT,

    /** A short acknowledgement — saved, started, locked. Drops if it waited past its own dwell. */
    CONFIRM
}

/**
 * A notice's text as data, resolved against resources only where it is drawn — so a locale change applies and
 * no `Context` enters a ViewModel for a string.
 */
internal sealed interface NoticeText {
    /** A string resource and the arguments its format takes. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : NoticeText

    /** A plurals resource for [count]; the format arguments default to the count itself. */
    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count)
    ) : NoticeText

    /** A format string whose arguments are themselves notice texts — the import result's composed line. */
    data class Joined(@StringRes val format: Int, val parts: List<NoticeText>) : NoticeText

    /**
     * Text a call site has already resolved from resources — the bridge for a path that formats on a
     * non-composable thread (a transfer's progress line). It is never a literal of the caller's own.
     */
    data class Resolved(val text: String) : NoticeText
}

/**
 * One notice: [key] names its slot (a post under a key showing or waiting replaces it), [dwellMs] is how long it
 * stands once shown, or null for a [NoticeClass.STATUS] that stands until cleared.
 */
internal data class Notice(
    val key: String,
    val text: NoticeText,
    val cls: NoticeClass,
    val dwellMs: Long?,
    /** Wears the danger border instead of the dashboard's — the exit pill while a recording would be stopped. */
    val danger: Boolean = false
) {
    init {
        require(dwellMs == null || dwellMs > 0) { "a dwell is positive or absent" }
    }

    companion object {
        /** A short acknowledgement on `notice.dwell.shortMs`. */
        fun confirm(key: String, text: NoticeText) =
            Notice(key, text, NoticeClass.CONFIRM, AppConfig.noticeDwellShortMs)

        /** A result the user has to read, on `notice.dwell.readableMs`. */
        fun result(key: String, text: NoticeText) =
            Notice(key, text, NoticeClass.RESULT, AppConfig.noticeDwellReadableMs)

        /** Something that went wrong, with a reason to read, on `notice.dwell.errorMs`. */
        fun error(key: String, text: NoticeText) =
            Notice(key, text, NoticeClass.ERROR, AppConfig.noticeDwellErrorMs)

        /**
         * The "press back again" pill: it stands exactly as long as the double-back window is open, so its
         * dwell is `map.exit.backWindowMs` itself, the one key both readers share.
         */
        fun exit(key: String, text: NoticeText, danger: Boolean) =
            Notice(key, text, NoticeClass.EXIT, AppConfig.exitBackWindowMs, danger)

        /** A state that stands until [NoticeCenter.clear] is called for its key. */
        fun status(key: String, text: NoticeText) =
            Notice(key, text, NoticeClass.STATUS, null)
    }
}

/** A notice on screen, with the instant its dwell began. */
internal data class ShownNotice(val notice: Notice, val shownAtMs: Long)

/** A notice waiting for a slot, with the instant it began to wait. */
internal data class QueuedNotice(val notice: Notice, val postedAtMs: Long)

/** The whole of the arbiter's state: [visible] in draw order (the newest last, on the band's edge) and the queue. */
internal data class NoticeBoard(
    val visible: List<ShownNotice> = emptyList(),
    val queued: List<QueuedNotice> = emptyList()
)
