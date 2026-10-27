package ykws.android.maro.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LoadCoordinationTest {
    @Test fun initializationJoinsActiveWorkAndDoesNotRepeatReadyWork() = runTest {
        val owner = RetryingInitializer()
        var ready = false
        var loads = 0
        val gate = CompletableDeferred<Unit>()
        val first = owner.start(this, { ready }) { loads++; gate.await(); ready = true }
        repeat(20) { assertSame(first, owner.start(this, { ready }) { loads++ }) }
        gate.complete(Unit)
        first!!.join()
        repeat(20) { assertNull(owner.start(this, { ready }) { loads++ }) }
        assertEquals(1, loads)
    }

    @Test fun unsuccessfulAndCancelledInitializationCanRetry() = runTest {
        val owner = RetryingInitializer()
        var ready = false
        owner.start(this, { ready }) { /* Repository returned Error, not Ready. */ }!!.join()
        val entered = CompletableDeferred<Unit>()
        val cancelled = owner.start(this, { ready }) { entered.complete(Unit); awaitCancellation() }!!
        entered.await()
        cancelled.cancelAndJoin()
        owner.start(this, { ready }) { ready = true }!!.join()
        assertTrue(ready)
    }

    @Test fun localWriterAndObserverJoinOnePublicationBeforeReconciliation() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        val events = mutableListOf<String>()
        val refresh = RevisionRefresh({ 1L }, {
            reads++; entered.complete(Unit); gate.await(); "latest"
        }, { events += it })
        val observer = async { refresh.refresh() }
        entered.await()
        val writer = async { refresh.refresh(); events += "reconcile" }
        gate.complete(Unit)
        observer.await(); writer.await()
        assertEquals(1, reads)
        assertEquals(listOf("latest", "reconcile"), events)
    }

    @Test fun aRevisionChangedDuringReadIsNotPublishedAsCurrent() = runTest {
        var revision = 0L
        var reads = 0
        val published = mutableListOf<String>()
        val refresh = RevisionRefresh({ revision }, {
            reads++
            if (reads == 1) { revision++; "stale" } else "current"
        }, { published += it })
        refresh.refresh()
        refresh.refresh()
        assertEquals(2, reads)
        assertEquals(listOf("current"), published)
    }

    @Test fun aFailedReadCanRetryTheSameRevision() = runTest {
        var attempts = 0
        val published = mutableListOf<Int>()
        val refresh = RevisionRefresh({ 1L }, {
            if (++attempts == 1) throw IOException("read failed")
            attempts
        }, { published += it })
        try { refresh.refresh(); fail("failure expected") } catch (_: IOException) { }
        refresh.refresh()
        assertEquals(listOf(2), published)
    }
}
