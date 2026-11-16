package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** `KapProcess`: the typed mailbox actor (SPEC-5.0), proven under virtual time. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KapProcessTest {

    @Test
    fun `a suspend offer waits for a consumer`() = runTest {
        val process = KapProcess<Int>("rendezvous")
        var offered = false
        val offer = launch { process.offer(1); offered = true }

        runCurrent()
        assertFalse(offered, "a rendezvous offer must not complete without a consumer")

        val worker = launch { process.consume { } }
        runCurrent()
        assertTrue(offered, "the consumer must release the suspended offer")
        worker.cancelAndJoin()
    }

    @Test
    fun `a buffer offer suspends only when the buffer is full`() = runTest {
        val process = KapProcess<Int>("buffered", Backpressure.Buffer(1))
        var firstAccepted = false
        var secondAccepted = false

        val first = launch { process.offer(1); firstAccepted = true }
        runCurrent()
        assertTrue(firstAccepted, "the first item fits the one-slot buffer")

        val second = launch { process.offer(2); secondAccepted = true }
        runCurrent()
        assertFalse(secondAccepted, "the second item must wait for room")

        val worker = launch { process.consume { } }
        runCurrent()
        assertTrue(secondAccepted, "the consumer frees the slot")
        worker.cancelAndJoin()
        first.join()
        second.join()
    }

    @Test
    fun `a lossy offer never suspends even when the buffer is full`() = runTest {
        val process = KapProcess<Int>("lossy", Backpressure.Conflate)
        var done = false
        val offers = launch { repeat(100) { process.offer(it) }; done = true }

        runCurrent()
        assertTrue(done, "a conflating offer must never suspend")
        assertEquals(99L, process.dropped.value)
    }

    @Test
    fun `conflate keeps the newest while the consumer is busy and counts the superseded one`() = runTest {
        val process = KapProcess<Int>("conflate", Backpressure.Conflate)
        val received = mutableListOf<Int>()
        val worker = launch {
            process.consume {
                received += it
                delay(1.seconds)
            }
        }

        runCurrent()
        process.offer(0)          // handed straight to the waiting consumer
        runCurrent()              // the handler is now busy
        process.offer(1)          // buffered
        process.offer(2)          // supersedes 1
        process.close()
        advanceUntilIdle()

        assertEquals(listOf(0, 2), received)
        assertEquals(1L, process.dropped.value)
        worker.join()
    }

    @Test
    fun `an item exceeding its budget is abandoned at exactly the budget and the drain continues`() = runTest {
        val events = mutableListOf<MailboxEvent<String>>()
        val process = KapProcess<String>(
            name = "budgeted",
            backpressure = Backpressure.Buffer(4),
            itemBudget = 1.seconds,
            observe = { events += it },
        )
        val received = mutableListOf<String>()
        var slowFinally = false
        val worker = launch {
            process.consume { item ->
                if (item == "slow") {
                    try {
                        delay(10.seconds)
                    } finally {
                        slowFinally = true
                    }
                }
                received += item
            }
        }

        process.offer("slow")
        process.offer("fast")
        process.close()
        val start = currentTime
        advanceUntilIdle()

        assertEquals(start + 1_000, currentTime, "the abandonment must land exactly at the budget")
        assertEquals(listOf("fast"), received, "the next item must run after the abandoned one")
        assertTrue(slowFinally, "the abandoned handler's finally must run")
        val timeout = events.filterIsInstance<MailboxEvent.TimedOut<String>>().single()
        assertEquals("slow", timeout.item)
        assertEquals(1.seconds, timeout.budget)
        worker.join()
    }

    @Test
    fun `a failing item is reported, isolated, and never restarts the process`() = runTest {
        val events = mutableListOf<MailboxEvent<String>>()
        val process = KapProcess<String>("isolated", observe = { events += it })
        val received = mutableListOf<String>()
        val handle = process.asProcess(
            handler = { item ->
                if (item == "bad") throw IllegalStateException("boom")
                received += item
            },
            restart = RestartPolicy.constant(10.milliseconds),
            heartbeatEvery = ZERO,
        ).startIn(this)

        process.offer("bad")
        process.offer("good")
        process.close()

        assertEquals(Unit, handle.await())
        assertEquals(listOf("good"), received)
        assertEquals(0, handle.restarts.value, "an item failure must not restart the drain")
        val failed = events.filterIsInstance<MailboxEvent.Failed<String>>().single()
        assertEquals("bad", failed.item)
        assertEquals("boom", failed.error.message)
    }

    @Test
    fun `the heartbeat beats while the drain runs and stops with the process`() = runTest {
        val process = KapProcess<String>("heartbeat")
        var beats = 0
        val handle = process.asProcess(
            handler = { delay(3.5.seconds) },
            restart = RestartPolicy.constant(1.seconds),
            heartbeatEvery = 1.seconds,
            onHeartbeat = { beats += 1 },
        ).startIn(this)

        process.offer("one")   // the handler is now mid-cycle
        process.close()
        assertEquals(Unit, handle.await())
        assertEquals(4, beats, "beats at t = 0, 1, 2, 3 — never waiting for the handler")

        advanceTimeBy(10.seconds)
        assertEquals(4, beats, "no beat after the process finished")
    }

    @Test
    fun `close completes the process normally after the buffered items`() = runTest {
        val process = KapProcess<Int>("drain", Backpressure.Buffer(8))
        val received = mutableListOf<Int>()
        val handle = process.asProcess(
            handler = { received += it },
            heartbeatEvery = ZERO,
        ).startIn(this)

        repeat(3) { process.offer(it) }
        process.close()

        assertEquals(Unit, handle.await())
        assertEquals(listOf(0, 1, 2), received)
        assertEquals(0, handle.restarts.value)
        assertFalse(handle.isAlive)
    }

    @Test
    fun `stop during a handler cancels it and is not a failure`() = runTest {
        val process = KapProcess<Int>("stopped")
        var finallyRan = false
        val handle = process.asProcess(
            handler = {
                try {
                    delay(60.seconds)
                } finally {
                    finallyRan = true
                }
            },
            restart = RestartPolicy.constant(1.seconds),
            heartbeatEvery = ZERO,
        ).startIn(this)

        process.offer(1)   // the handler is now mid-cycle
        runCurrent()
        handle.stop()
        handle.stop()      // idempotent

        assertFailsWith<ProcessStoppedException> { handle.await() }
        assertTrue(finallyRan, "the in-flight handler must be cancelled, not abandoned")
        assertEquals(0, handle.restarts.value)
        assertFalse(handle.isAlive)
    }

    @Test
    fun `a CancellationException from the handler propagates and is not an item failure`() = runTest {
        val events = mutableListOf<MailboxEvent<Int>>()
        val process = KapProcess<Int>("ce", observe = { events += it })
        val handle = process.asProcess(
            handler = { throw CancellationException("handler says stop") },
            restart = RestartPolicy.constant(1.seconds),
            heartbeatEvery = ZERO,
        ).startIn(this)

        process.offer(1)

        assertFailsWith<CancellationException> { handle.await() }
        assertTrue(events.none { it is MailboxEvent.Failed<*> }, "cancellation is never an item failure")
        assertEquals(0, handle.restarts.value)
    }

    @Test
    fun `a fatal drain error restarts the drain under the policy`() = runTest {
        var calls = 0
        val received = mutableListOf<Int>()
        val process = KapProcess<Int>("fatal")
        val handle = process.asProcess(
            handler = { item ->
                calls += 1
                if (calls == 1) throw AssertionError("fatal")
                received += item
            },
            restart = RestartPolicy.constant(10.milliseconds, maxRestarts = 2),
            heartbeatEvery = ZERO,
        ).startIn(this)

        process.offer(1)   // consumed, then the Error kills the drain
        process.offer(2)   // waits out the restart delay
        process.close()

        assertEquals(Unit, handle.await())
        assertEquals(listOf(2), received)
        assertEquals(1, handle.restarts.value)
    }

    @Test
    fun `a second consumer is rejected`() = runTest {
        val process = KapProcess<Int>("single-consumer")
        val first = launch { process.consume { } }
        runCurrent()

        assertFailsWith<IllegalStateException> { process.consume { } }
        first.cancelAndJoin()
    }

    @Test
    fun `an unlawful item budget cannot be expressed`() {
        assertFailsWith<IllegalArgumentException> { KapProcess<Int>("zero-budget", itemBudget = ZERO) }
        assertFailsWith<IllegalArgumentException> { KapProcess<Int>("negative-budget", itemBudget = (-1).seconds) }
    }
}
