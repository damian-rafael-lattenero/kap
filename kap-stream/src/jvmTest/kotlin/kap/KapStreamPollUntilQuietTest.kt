package kap

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The `KapStream.pollUntilQuiet` laws — the drain source (D3's lawful home).
 *
 * 1. **Work-only emission.** Exactly the reports with `quiet = false` are
 *    emitted, in poll order, one element per poll; a quiet report is never
 *    emitted.
 * 2. **Quiet-driven termination.** With the canonical policy
 *    (`cadence and Schedule.debounce(n) { it.quiet }`) the stream completes
 *    exactly on the `n`-th consecutive quiet poll, and nothing is polled or
 *    emitted after it — a transient empty poll does not end the drain. A
 *    budget schedule is an explicit early stop.
 * 3. **Cadence.** The wait between polls is exactly the schedule's `Continue`
 *    delay (virtual time: exact); `Done` waits nothing.
 * 4. **Failure propagates.** A failing poll fails the stream.
 * 5. **Backpressure is named.** The source stage applies the strategy it is
 *    given; the default `Suspend` is exact, and a lossy strategy drops only
 *    what it documents.
 *
 * Every delay below is virtual; no test sleeps.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KapStreamPollUntilQuietTest {

    /** A scripted drain: one [Progress] per poll, recording how often it was asked. */
    private class ScriptedPoller(private val reports: List<Progress>) {
        private val script = reports.iterator()
        var calls: Int = 0
            private set

        suspend fun poll(): Progress {
            calls += 1
            return if (script.hasNext()) script.next() else Progress.Quiet
        }
    }

    /** A drain scripted as counts: a zero count is a quiet poll. */
    private fun counts(vararg counts: Int): List<Progress> =
        counts.map { Progress(total = it, quiet = it == 0) }

    /** The canonical policy: a cadence plus a quiet window of [window]. */
    private fun quietWindow(window: Int, cadence: kotlin.time.Duration = ZERO): Schedule<Progress> =
        Schedule.spaced<Progress>(cadence) and Schedule.debounce<Progress>(window) { it.quiet }

    // ── 1 + 2. Work-only emission and quiet-driven termination ──────────

    @Test
    fun `N batches then quiet emits N reports and completes`() = runTest {
        // The agent-brain drain scenario: polls [3, 4, 0, 2, 0, 0, 0, 99] with
        // a window of 3 stop after the 7th poll; the 99 is never seen.
        val poller = ScriptedPoller(counts(3, 4, 0, 2, 0, 0, 0, 99))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(3)).collectAsKap().evalGraph()

        assertEquals(listOf(3, 4, 2), emitted.map { it.total }, "exactly the polls that found work, in order")
        assertTrue(emitted.none { it.quiet }, "a quiet report carries nothing and is never emitted")
        assertEquals(9, emitted.sumOf { it.total }, "the drain's total is the sum of the emitted reports")
        assertEquals(7, poller.calls, "the loop stops at the filled quiet window; the 99 is never polled")
    }

    @Test
    fun `a transient empty poll does not end the drain — only the filled window does`() = runTest {
        val poller = ScriptedPoller(counts(5, 0, 7, 0, 0, 0, 11))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(3)).collectAsKap().evalGraph()

        assertEquals(listOf(5, 7), emitted.map { it.total }, "work after an empty poll is still drained and emitted")
        assertEquals(6, poller.calls, "the window fills on the sixth poll; nothing is polled after it")
    }

    @Test
    fun `a window of one stops at the first quiet poll`() = runTest {
        val poller = ScriptedPoller(counts(2, 0, 3))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(1)).collectAsKap().evalGraph()

        assertEquals(listOf(2), emitted.map { it.total })
        assertEquals(2, poller.calls)
    }

    @Test
    fun `a window of zero polls once and emits what it found`() = runTest {
        val poller = ScriptedPoller(counts(2, 1, 1))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(0)).collectAsKap().evalGraph()

        assertEquals(listOf(2), emitted.map { it.total })
        assertEquals(1, poller.calls, "an empty window stops immediately after the first poll")
    }

    @Test
    fun `a budget schedule is an explicit early stop`() = runTest {
        val poller = ScriptedPoller(counts(1, 2, 3, 4))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, Schedule.times<Progress>(2))
            .collectAsKap()
            .evalGraph()

        assertEquals(listOf(1, 2, 3), emitted.map { it.total }, "times(2) continues attempts 0 and 1, Done at 2")
        assertEquals(3, poller.calls)
    }

    @Test
    fun `the stream is cold — nothing is polled before collection`() = runTest {
        val poller = ScriptedPoller(counts(1, 0))

        val stream = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(1))
        assertEquals(0, poller.calls, "a stream is a description until it is collected")

        stream.collectAsKap().evalGraph()
        assertEquals(2, poller.calls)
    }

    // ── 3. Cadence ──────────────────────────────────────────────────────

    @Test
    fun `the cadence is exactly the schedule's in virtual time`() = runTest {
        val poller = ScriptedPoller(counts(1, 1, 0))
        val start = currentTime

        KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(1, 50.milliseconds))
            .collectAsKap()
            .evalGraph()

        assertEquals(100, currentTime - start, "two 50 ms waits before the quiet poll; Done waits nothing")
    }

    // ── 4. Failure ──────────────────────────────────────────────────────

    @Test
    fun `a failing poll fails the stream`() = runTest {
        var calls = 0

        val failure = assertFailsWith<IllegalStateException> {
            KapStream.pollUntilQuiet(
                progress = {
                    calls += 1
                    if (calls == 2) throw IllegalStateException("drain down") else Progress(1)
                },
                schedule = quietWindow(1),
            ).collectAsKap().evalGraph()
        }

        assertEquals("drain down", failure.message)
        assertEquals(2, calls, "the failure is not retried behind the caller's back")
    }

    @Test
    fun `cancelling the collection stops the polling`() = runTest {
        var calls = 0
        val gate = Channel<Unit>()
        val job = launch {
            KapStream.pollUntilQuiet(
                progress = {
                    calls += 1
                    gate.receive()
                    Progress(1)
                },
                schedule = quietWindow(1),
            ).collectAsKap().evalGraph()
        }

        gate.send(Unit) // let the first poll finish
        runCurrent() // the second poll is now parked on the gate
        assertEquals(2, calls)

        job.cancelAndJoin()
        runCurrent()
        assertEquals(2, calls, "a cancelled drain polls nothing more")
        gate.close()
    }

    // ── 5. Backpressure ─────────────────────────────────────────────────

    @Test
    fun `the default backpressure is exact — every work report is delivered`() = runTest {
        val poller = ScriptedPoller(counts(1, 2, 3, 0))

        val emitted = KapStream.pollUntilQuiet({ poller.poll() }, quietWindow(1)).collectAsKap().evalGraph()

        assertEquals(listOf(1, 2, 3), emitted.map { it.total })
    }

    @Test
    fun `a named lossy strategy drops only what it documents`() = runTest {
        val poller = ScriptedPoller(counts(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0))
        val received = mutableListOf<Int>()
        val channel = Channel<Progress>(capacity = 1)

        coroutineScope {
            val production = launch {
                with(
                    KapStream.pollUntilQuiet(
                        progress = { poller.poll() },
                        schedule = quietWindow(3),
                        backpressure = Backpressure.Conflate,
                    ).drainTo(channel),
                ) { execute() }
                channel.close()
            }
            received += channel.receive().total
            delay(1.seconds) // virtual: the producer runs ahead and conflates
            for (element in channel) received += element.total
            production.join()
        }

        assertEquals(1, received.first(), "the first report was already consumed")
        assertEquals(8, received.last(), "the element buffered at completion is never dropped")
        assertTrue(received.size < 8, "superseded reports were dropped by the named strategy: $received")
        assertEquals(received.sorted(), received, "survivors keep poll order")
    }
}
