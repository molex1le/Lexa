package app.lexa.domain.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

/** Milliseconds from now to the next local midnight (at least 1). */
fun TimeProvider.millisUntilNextMidnight(): Long {
    val next = today().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (next - nowMillis()).coerceAtLeast(1)
}

/**
 * "Now" for screens that count by day: emits at once, again at every local midnight, and on every
 * [refresh] (the screen resumed, a session ended). A process left alive overnight sees the new day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TimeProvider.nowTicks(refresh: Flow<Unit> = emptyFlow()): Flow<Long> =
    merge(flowOf(Unit), refresh).flatMapLatest {
        flow {
            while (true) {
                emit(nowMillis())
                delay(millisUntilNextMidnight())
            }
        }
    }
