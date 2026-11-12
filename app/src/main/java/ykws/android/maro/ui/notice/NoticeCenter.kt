package ykws.android.maro.ui.notice

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import ykws.android.maro.config.AppConfig

/**
 * The one place a transient message is raised from: a thread-safe holder of the [NoticeBoard] that applies
 * [NoticeArbiter]'s reducers atomically and publishes the result for the host to draw.
 *
 * The clock and the slot count are injected so the class is testable without Android; [shared] is the
 * process-scoped instance every caller uses, so a notice can be posted before any screen or ViewModel exists.
 */
internal class NoticeCenter(
    private val clock: () -> Long,
    private val maxVisible: () -> Int
) {
    private val state = MutableStateFlow(NoticeBoard())

    /** What the host draws. */
    val board: StateFlow<NoticeBoard> = state.asStateFlow()

    /** Raises [notice]; one under the same key replaces it. */
    fun post(notice: Notice) {
        val now = clock()
        state.update { NoticeArbiter.post(it, notice, now, maxVisible()) }
    }

    /** Takes down the notice under [key], showing or waiting. */
    fun clear(key: String) {
        val now = clock()
        state.update { NoticeArbiter.clear(it, key, now, maxVisible()) }
    }

    /** Drops every notice whose dwell has run out; the host calls it when the earliest one does. */
    fun expire() {
        val now = clock()
        state.update { NoticeArbiter.expire(it, now, maxVisible()) }
    }

    private val hostSeen = AtomicBoolean(false)

    /**
     * The notice host calls this as it first composes: notices posted before it existed (the startup
     * diagnostics, raised before the first frame) have their dwell restarted, once per process, so they
     * stand for their full time instead of lapsing unseen. Later attaches (a rotation) change nothing.
     */
    fun hostAttached() {
        if (!hostSeen.compareAndSet(false, true)) return
        val now = clock()
        state.update { NoticeArbiter.restamp(it, now) }
    }

    /** The current instant on the clock the dwells are measured against. */
    fun now(): Long = clock()

    /** Sets or, for a null [text], clears the [NoticeClass.STATUS] notice under [key]. */
    fun setStatus(key: String, text: String?) {
        if (text == null) clear(key) else post(Notice.status(key, NoticeText.Resolved(text)))
    }

    companion object {
        /** The process-scoped instance, on the monotonic clock and `notice.pill.maxVisible`. */
        val shared: NoticeCenter by lazy {
            NoticeCenter({ SystemClock.elapsedRealtime() }, { AppConfig.noticePillMaxVisible })
        }
    }
}
