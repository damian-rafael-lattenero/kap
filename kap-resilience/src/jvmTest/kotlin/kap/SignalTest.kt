package kap

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** `Signal`/`Beat`: the hot wake-up primitive (SPEC-5.0), proven under virtual time. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SignalTest {

    @Test
    fun `a wake inside the window returns Signalled at the signal's time, not the window's`() = runTest {
        val signal = Signal()
        val start = currentTime
        val sender = launch {
            delay(750.milliseconds)
            signal.send()
        }
        assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(5.seconds)))
        assertEquals(start + 750, currentTime)
        sender.join()
    }

    @Test
    fun `a Done schedule waits nothing - TimedOut immediately without a wake`() = runTest {
        val signal = Signal()
        val start = currentTime
        assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.never()))
        assertEquals(start, currentTime)
        assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.immediate()))
        assertEquals(start, currentTime)
    }

    @Test
    fun `a Done schedule still yields to a pending wake`() = runTest {
        val signal = Signal()
        signal.send()
        assertEquals(Beat.Signalled, signal.awaitOr(Schedule.never()))
    }

    @Test
    fun `a single-delay schedule is a single exact window`() = runTest {
        val signal = Signal()
        val start = currentTime
        assertEquals(
            Beat.TimedOut,
            signal.awaitOr(Schedule.delays(listOf(2.seconds))),
        )
        assertEquals(start + 2_000, currentTime)
    }

    @Test
    fun `the hot loop - a send wakes the cadence now, silence waits the cadence`() = runTest {
        val signal = Signal()
        val draws = mutableListOf<Long>()
        val loop = launch {
            repeat(3) {
                draws += currentTime
                signal.awaitOr(Schedule.spaced(1.seconds))
            }
        }

        runCurrent()          // first draw at t=0, then awaiting
        signal.send()         // a tap: wake NOW
        runCurrent()          // second draw at t=0, awaiting again
        delay(1.seconds)      // no tap: the cadence elapses
        loop.join()

        assertEquals(listOf(0L, 0L, 1_000L), draws)
    }

    @Test
    fun `every await consumes at most one wake`() = runTest {
        val signal = Signal()
        signal.send()
        assertEquals(Beat.Signalled, signal.awaitOr(Schedule.spaced(1.seconds)))
        assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.spaced(1.seconds)))
        assertEquals(Beat.TimedOut, signal.awaitOr(Schedule.spaced(1.seconds)))
    }
}
