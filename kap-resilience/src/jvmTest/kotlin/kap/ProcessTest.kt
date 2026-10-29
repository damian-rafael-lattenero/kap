package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** `process`: the supervised loop (SPEC-5.0 milestone 4.2), proven under virtual time. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProcessTest {

    @Test
    fun `normal completion finishes the process without restarts`() = runTest {
        var runs = 0
        val handle = Kap { runs += 1; "done" }
            .process("once", heartbeatEvery = ZERO)
            .startIn(this)
        assertEquals("done", handle.await())
        assertEquals(1, runs)
        assertEquals(0, handle.restarts.value)
        assertFalse(handle.isAlive)
    }

    @Test
    fun `a failure restarts on the policy cadence in virtual time`() = runTest {
        var runs = 0
        val start = currentTime
        val handle = Kap { runs += 1; throw IllegalStateException("boom-$runs") }
            .process(
                "flaky",
                restart = RestartPolicy.exponential(base = 100.milliseconds, maxRestarts = 2),
                heartbeatEvery = ZERO,
            )
            .startIn(this)
        val exhausted = assertFailsWith<ProcessExhaustedException> { handle.await() }
        assertEquals(3, runs)                    // initial run + 2 restarts
        assertEquals(2, handle.restarts.value)
        assertEquals(300, currentTime - start)   // delays: 100 ms + 200 ms
        assertEquals("boom-3", exhausted.cause?.message)
    }

    @Test
    fun `a recovering body restarts and completes with the value`() = runTest {
        var runs = 0
        val handle = Kap { runs += 1; if (runs == 1) throw IllegalStateException("first") else "recovered" }
            .process("recovery", restart = RestartPolicy.constant(50.milliseconds), heartbeatEvery = ZERO)
            .startIn(this)
        assertEquals("recovered", handle.await())
        assertEquals(2, runs)
        assertEquals(1, handle.restarts.value)
    }

    @Test
    fun `stop is prompt, idempotent and not a failure`() = runTest {
        var runs = 0
        var bodyFinallyRan = 0
        val handle = Kap {
            runs += 1
            try {
                delay(60.seconds)
            } finally {
                bodyFinallyRan += 1
            }
            "never"
        }.process(
            "stopped",
            restart = RestartPolicy.constant(1.seconds),
            heartbeatEvery = ZERO,
        ).startIn(this)

        delay(1.seconds)   // the body is mid-run
        handle.stop()
        handle.stop()      // idempotent
        assertFailsWith<ProcessStoppedException> { handle.await() }
        assertEquals(1, runs)
        assertEquals(0, handle.restarts.value)   // stopping never restarts
        assertEquals(1, bodyFinallyRan)          // the body was cancelled, not abandoned
        assertFalse(handle.isAlive)
    }

    @Test
    fun `stop during the restart delay prevents the next run`() = runTest {
        var runs = 0
        val handle = Kap { runs += 1; throw IllegalStateException("x") }
            .process(
                "mid-delay",
                restart = RestartPolicy.constant(1.seconds, maxRestarts = 10),
                heartbeatEvery = ZERO,
            )
            .startIn(this)

        delay(500.milliseconds)   // run 0 failed at t=0; the restart delay runs until t=1 s
        handle.stop()
        delay(5.seconds)          // nothing may restart now
        assertEquals(1, runs)
        assertEquals(0, handle.restarts.value)
        assertFailsWith<ProcessStoppedException> { handle.await() }
    }

    @Test
    fun `cancellation of the owning scope ends the process without restarts`() = runTest {
        var runs = 0
        val scopeJob = Job()
        val processScope = CoroutineScope(coroutineContext + scopeJob)
        val handle = Kap { runs += 1; throw IllegalStateException("x") }
            .process("scoped", restart = RestartPolicy.constant(1.seconds), heartbeatEvery = ZERO)
            .startIn(processScope)

        delay(100.milliseconds)   // run 0 failed; inside the restart delay
        scopeJob.cancel()
        delay(2.seconds)
        assertEquals(1, runs)
        assertFailsWith<CancellationException> { handle.await() }
    }

    @Test
    fun `the heartbeat beats at its period while the body is mid-cycle and stops with the process`() = runTest {
        var beats = 0
        val handle = Kap { delay(3.5.seconds); "slow" }
            .process(
                "slow-beat",
                restart = RestartPolicy.constant(1.seconds),
                heartbeatEvery = 1.seconds,
                onHeartbeat = { beats += 1 },
            )
            .startIn(this)

        assertEquals("slow", handle.await())
        assertEquals(4, beats)   // t = 0, 1, 2, 3 — the beat never waits for body progress
        advanceTimeBy(10.seconds)
        assertEquals(4, beats)   // no beat after the process finished
    }

    @Test
    fun `a disabled heartbeat reports INFINITE age`() = runTest {
        var beats = 0
        val handle = Kap { "quick" }
            .process("no-hb", heartbeatEvery = ZERO, onHeartbeat = { beats += 1 })
            .startIn(this)
        assertEquals("quick", handle.await())
        assertEquals(0, beats)
        assertEquals(Duration.INFINITE, handle.heartbeatAge())
    }

    @Test
    fun `heartbeatAge reflects the last beat`() = runTest {
        val handle = Kap { delay(3.seconds); "x" }
            .process("aged", heartbeatEvery = 1.seconds, timeSource = testScheduler.timeSource)
            .startIn(this)
        delay(2_500.milliseconds)
        assertEquals(500.milliseconds, handle.heartbeatAge())   // beat at t=2 s, now t=2.5 s
        handle.await()
    }

    @Test
    fun `observability hooks are transparent`() = runTest {
        var runs = 0
        var beats = 0
        val handle = Kap { runs += 1; if (runs == 1) throw IllegalStateException("first") else "ok" }
            .process(
                "transparent",
                restart = RestartPolicy.constant(10.milliseconds),
                onRestart = { _, _, _ -> throw IllegalStateException("restart observer must not matter") },
                onHeartbeat = { beats += 1; throw IllegalStateException("beat observer must not matter") },
            )
            .startIn(this)
        assertEquals("ok", handle.await())
        assertEquals(2, runs)
        assertTrue(beats >= 1, "the beat kept beating despite its observer throwing")
    }
}
