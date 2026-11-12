package ykws.android.maro.ui.notice

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import ykws.android.maro.config.AppConfig

/**
 * The one place a transient message is raised from: a thread-safe holder of the [NoticeBoard] that applies
 * [NoticeArbiter]'s reducers atomically and publishes the result for the host to draw.
 *
 * The clock and the slot count are injected so the class is testable without Android; [shared] is the
 * process-scoped instance every caller uses, so a notice can be posted before any screen or ViewModel exists.
 *
 * It also knows whether a host is drawing it: a notice posted while none is (a startup diagnostic, raised in
 * `onCreate`) has its dwell restarted when the next host attaches, and a status posted while none is — a late
 * progress callback after the screen went — is dropped, since nothing would ever clear it.
 */
internal class NoticeCenter(
    private val clock: () -> Long,
    private val maxVisible: () -> Int
) {
    private val state = MutableStateFlow(NoticeBoard())

    /** Hosts currently attached; the screen's one `NoticeHost` while it is composed. */
    private val hosts = AtomicInteger(0)

    /** Keys posted while no host was attached, whose dwell restarts when one is. */
    private val awaitingHost: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** What the host draws. */
    val board: StateFlow<NoticeBoard> = state.asStateFlow()

    /** Raises [notice]; one under the same key replaces it. */
    fun post(notice: Notice) {
        if (hosts.get() == 0) awaitingHost.add(notice.key)
        val now = clock()
        state.update { NoticeArbiter.post(it, notice, now, maxVisible()) }
    }

    /** Takes down the notice under [key], showing or waiting. */
    fun clear(key: String) {
        awaitingHost.remove(key)
        val now = clock()
        state.update { NoticeArbiter.clear(it, key, now, maxVisible()) }
    }

    /** Drops every notice whose dwell has run out; the host calls it when the earliest one does. */
    fun expire() {
        val now = clock()
        state.update { NoticeArbiter.expire(it, now, maxVisible()) }
    }

    /**
     * A host started drawing: whatever was posted while none was has its dwell restarted, so it stands for its
     * full time instead of lapsing unseen. A notice posted while a host was already attached is untouched, so
     * a rotation changes nothing.
     */
    fun hostAttached() {
        hosts.incrementAndGet()
        val waiting = awaitingHost.toSet()
        if (waiting.isEmpty()) return
        awaitingHost.removeAll(waiting)
        val now = clock()
        state.update { NoticeArbiter.restamp(it, waiting, now) }
    }

    /** The host stopped drawing (the screen left). */
    fun hostDetached() {
        hosts.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** The current instant on the clock the dwells are measured against. */
    fun now(): Long = clock()

    /**
     * Sets or, for a null [text], clears the [NoticeClass.STATUS] notice under [key]. A status is owned by a
     * screen that clears it, so one set while no host is attached is dropped rather than left standing.
     */
    fun setStatus(key: String, text: String?) {
        if (text == null) clear(key)
        else if (hosts.get() > 0) post(Notice.status(key, NoticeText.Resolved(text)))
    }

    companion object {
        /** The process-scoped instance, on the monotonic clock and `notice.pill.maxVisible`. */
        val shared: NoticeCenter by lazy {
            NoticeCenter({ SystemClock.elapsedRealtime() }, { AppConfig.noticePillMaxVisible })
        }
    }
}
