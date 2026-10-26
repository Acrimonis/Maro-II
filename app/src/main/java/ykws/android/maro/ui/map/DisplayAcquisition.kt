package ykws.android.maro.ui.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/** Consume the service's existing fixes only where display acquisition is suspended. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> recordingFixesWhileDisplayPaused(
    fixes: Flow<T>, gpsMode: Flow<Boolean>, displayActive: Flow<Boolean>, recording: Flow<Boolean>
): Flow<T> = combine(gpsMode, displayActive, recording) { gps, active, on -> gps && !active && on }
    .distinctUntilChanged()
    .flatMapLatest { paused -> if (paused) fixes else emptyFlow() }
