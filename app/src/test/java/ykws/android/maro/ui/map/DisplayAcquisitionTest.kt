package ykws.android.maro.ui.map

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DisplayAcquisitionTest {
    @Test fun backgroundWaterGetsExistingFixesWithoutChangingTheServiceStream() = runTest {
        val fixes=MutableSharedFlow<Int>()
        val gps=MutableStateFlow(true); val active=MutableStateFlow(true); val recording=MutableStateFlow(true)
        val service=mutableListOf<Int>(); val background=mutableListOf<Int>()
        val recorder=launch { fixes.collect { service+=it } }
        val observer=launch { recordingFixesWhileDisplayPaused(fixes,gps,active,recording).collect { background+=it } }
        runCurrent(); fixes.emit(1)
        active.value=false; runCurrent(); fixes.emit(2); fixes.emit(3)
        active.value=true; runCurrent(); fixes.emit(4)
        active.value=false; recording.value=false; runCurrent(); fixes.emit(5)
        recording.value=true; gps.value=false; runCurrent(); fixes.emit(6)
        gps.value=true; runCurrent(); fixes.emit(7); runCurrent()
        observer.cancelAndJoin(); recorder.cancelAndJoin()
        assertEquals((1..7).toList(),service)
        assertEquals(listOf(2,3,7),background) // No stale replay on either subscription.
    }
}
