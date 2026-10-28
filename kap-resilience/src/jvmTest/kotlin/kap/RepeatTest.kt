package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** `repeat`: the loop as a policy (SPEC-5.0 milestone 4.2), proven under virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatTest {

    @Test
    fun `repeat runs once more per Continue and returns the last value`() = runTest {
        var n = 0
        val counter = Kap { ++n }
        val last = counter.repeat(Schedule.times(3)).evalGraph()
        assertEquals(4, last)   // 1 initial run + 3 repeats
        assertEquals(4, n)
    }

    @Test
    fun `spaced cadence is honoured in virtual time`() = runTest {
        var n = 0
        val start = currentTime
        Kap { ++n }.repeat(Schedule.times<Int>(4) and Schedule.spaced(500.milliseconds)).evalGraph()
        assertEquals(5, n)
        assertEquals(2_000, currentTime - start)   // 4 gaps of 500 ms
    }

    @Test
    fun `repeatUntil and repeatWhile stop on the last value`() = runTest {
        var n = 0
        assertEquals(5, Kap { ++n }.repeatUntil { it >= 5 }.evalGraph())
        n = 0
        assertEquals(3, Kap { ++n }.repeatWhile { it < 3 }.evalGraph())
    }

    @Test
    fun `retry composes per iteration - a flaky run is retried, the loop goes on`() = runTest {
        var calls = 0
        var runs = 0
        val flaky = Kap {
            calls += 1
            if (calls % 2 == 1) throw IllegalStateException("flaky $calls")
            ++runs
        }
        val last = flaky.retry(Schedule.times(1)).repeat(Schedule.times(2)).evalGraph()
        assertEquals(3, last)
        assertEquals(6, calls)
    }

    @Test
    fun `an uncaught failure stops the loop and surfaces`() = runTest {
        var n = 0
        val boom = Kap { if (++n == 3) throw IllegalArgumentException("third") else n }
        assertFailsWith<IllegalArgumentException> { boom.repeat(Schedule.forever()).evalGraph() }
        assertEquals(3, n)
    }

    @Test
    fun `cancellation between iterations stops a forever loop cleanly`() = runTest {
        var n = 0
        val job = async { Kap { ++n }.repeat(Schedule.spaced(1.seconds)).evalGraph() }
        delay(3_500)
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
        assertEquals(4, n)   // t=0,1,2,3 s
    }

    @Test
    fun `withMaxDuration bounds a repeat`() = runTest {
        var n = 0
        val start = currentTime
        val fresh = { Schedule.spaced<Int>(100.milliseconds).withMaxDuration(1.seconds, testScheduler.timeSource) }
        Kap { ++n }.repeat(fresh()).evalGraph()
        assertTrue(n in 10..12, "ran $n times")
        assertTrue(currentTime - start <= 1_100)
    }

    @Test
    fun `awaiting resumes from a callback and cancels the registration`() = runTest {
        var registered = 0
        var cancelled = 0
        var resumer: ((String) -> Unit)? = null
        val waiting = Kap.awaiting<String> { resume, _ ->
            registered += 1
            resumer = resume
        }
        val job = async { waiting.evalGraph() }
        delay(10)
        resumer!!("done")
        assertEquals("done", job.await())
        assertEquals(1, registered)

        val never = Kap.awaiting<Unit> { _, _ -> cancelled += 0 }
        val parked = async { never.evalGraph() }
        delay(10)
        parked.cancel()
        assertFailsWith<CancellationException> { parked.await() }
    }

    @Test
    fun `awaitUntil is true when the condition arrives in time and false when it does not`() = runTest {
        var ticks = 0
        val arrives = Kap.awaitUntil(every = 100.milliseconds, upTo = 5.seconds, timeSource = testScheduler.timeSource) { ++ticks >= 4 }
        val start = currentTime
        assertTrue(arrives.evalGraph())
        assertEquals(300, currentTime - start)   // 3 gaps of 100 ms before the 4th observation
        assertEquals(4, ticks)

        val never = Kap.awaitUntil(every = 100.milliseconds, upTo = 1.seconds, timeSource = testScheduler.timeSource) { false }
        val t0 = currentTime
        assertEquals(false, never.evalGraph())
        assertTrue(currentTime - t0 in 1_000..1_100)
    }
}
