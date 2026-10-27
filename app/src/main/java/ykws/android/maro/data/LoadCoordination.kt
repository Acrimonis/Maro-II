package ykws.android.maro.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Called on the owning UI lane. Concurrent requests share work; failed or cancelled loads can retry. */
internal class RetryingInitializer {
    private var running: Job? = null

    fun start(scope: CoroutineScope, ready: () -> Boolean = { false }, load: suspend () -> Unit): Job? {
        if (ready()) return null
        running?.takeIf { it.isActive }?.let { return it }
        return scope.launch { load() }.also { running = it }
    }
}

/** A local writer and the change observer join the same refresh before either reconciles its UI. */
internal class RevisionRefresh<T>(
    private val revision: () -> Long,
    private val load: suspend () -> T,
    private val publish: (T) -> Unit
) {
    private val mutex = Mutex()
    private var appliedRevision: Long? = null

    suspend fun refresh() = mutex.withLock {
        while (appliedRevision != revision()) {
            val reading = revision()
            val value = load()
            if (reading != revision()) continue
            publish(value)
            appliedRevision = reading
        }
    }
}
