package kap

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `KapStream`: the stream algebra (SPEC-5.0 milestone 5.0), proven under
 * virtual time. Every delay below is virtual; no test sleeps.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KapStreamTest {

    // ── Sources ─────────────────────────────────────────────────────────

    @Test
    fun `from lifts a flow unchanged`() = runTest {
        assertEquals(listOf(1, 2, 3), KapStream.from(flowOf(1, 2, 3)).collectAsKap().evalGraph())
    }

    @Test
    fun `emitted every times runs the kap on the schedule cadence in virtual time`() = runTest {
        val start = currentTime
        var ticks = 0
        val values = KapStream.emitted(Kap { ticks++ })
            .every(Schedule.spaced<Int>(1.seconds) and Schedule.doUntil { it == 2 })
            .collectAsKap()
            .evalGraph()

        assertEquals(listOf(0, 1, 2), values)
        assertEquals(3, ticks)   // three polls: values 0, 1 and 2
        assertEquals(2_000, currentTime - start)   // two 1 s waits, the third value ended the poll
    }

    @Test
    fun `emitted every respects a bounded count schedule`() = runTest {
        var ticks = 0
        val values = KapStream.emitted(Kap { ticks++ })
            .every(Schedule.times<Int>(2))   // initial poll + 2 more = 3 emissions, zero delay
            .collectAsKap()
            .evalGraph()

        assertEquals(listOf(0, 1, 2), values)
        assertEquals(3, ticks)
    }

    @Test
    fun `emitted propagates a failing poll as a stream failure`() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            KapStream.emitted(Kap<Int> { throw IllegalStateException("sensor down") })
                .every(Schedule.spaced(1.milliseconds))
                .collectAsKap()
                .evalGraph()
        }
        assertEquals("sensor down", failure.message)
    }

    // ── Stages ──────────────────────────────────────────────────────────

    @Test
    fun `mapKap runs every element through its Kap`() = runTest {
        val values = KapStream.from((1..20).asFlow())
            .mapKap { value -> Kap.of(value * 2) }
            .collectAsKap()
            .evalGraph()
        assertEquals((1..20).map { it * 2 }, values)
    }

    @Test
    fun `mapKapOrdered preserves upstream order under inverted completion order`() = runTest {
        val input = (1..40).toList()
        val output = KapStream.from(input.asFlow())
            .mapKapOrdered(concurrency = 8) { value ->
                Kap { delay(((41 - value) * 5).milliseconds); value }
            }
            .collectAsKap()
            .evalGraph()

        assertEquals(input, output, "mapKapOrdered must emit in upstream order")
    }

    @Test
    fun `mapKap with concurrency emits in completion order, not upstream order`() = runTest {
        val input = (1..40).toList()
        val output = KapStream.from(input.asFlow())
            .mapKap(concurrency = 8) { value ->
                Kap { delay(((41 - value) * 5).milliseconds); value }
            }
            .collectAsKap()
            .evalGraph()

        assertEquals(input.sorted(), output.sorted(), "no value lost or duplicated")
        assertNotEquals(input, output, "a concurrent map does not promise upstream order")
    }

    @Test
    fun `filterKap keeps only the elements its Kap predicate accepts`() = runTest {
        val values = KapStream.from((1..10).asFlow())
            .filterKap { value -> Kap.of(value % 2 == 0) }
            .collectAsKap()
            .evalGraph()
        assertEquals(listOf(2, 4, 6, 8, 10), values)
    }

    @Test
    fun `a failing stage Kap fails the stream`() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            KapStream.from((1..5).asFlow())
                .mapKap { value -> if (value == 3) Kap.failed(IllegalStateException("bad-$value")) else Kap.of(value) }
                .collectAsKap()
                .evalGraph()
        }
        assertEquals("bad-3", failure.message)
    }

    @Test
    fun `a stage Kap with parallel children fails the stream cleanly`() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            KapStream.from((1..3).asFlow())
                .mapKap { value ->
                    Kap {
                        val left = async { value }
                        val right = async {
                            if (value == 2) throw IllegalStateException("child-$value") else value
                        }
                        left.await() + right.await()
                    }
                }
                .collectAsKap()
                .evalGraph()
        }
        assertEquals("child-2", failure.message)
    }

    @Test
    fun `stage backpressure defaults to suspend and never drops`() = runTest {
        val values = KapStream.from((0 until 100).asFlow())
            .mapKap(concurrency = 4) { value -> Kap.of(value) }
            .collectAsKap()
            .evalGraph()
        assertEquals((0 until 100).toList(), values.sorted(), "the suspend default is exact: nothing dropped")
        assertEquals(100, values.size)
    }

    @Test
    fun `flatMapKap merges inner streams sequentially and in order at concurrency 1`() = runTest {
        val values = KapStream.from((1..5).asFlow())
            .flatMapKap(concurrency = 1) { value -> KapStream.from(flowOf(value, value * 10)) }
            .collectAsKap()
            .evalGraph()
        assertEquals(listOf(1, 10, 2, 20, 3, 30, 4, 40, 5, 50), values)
    }

    @Test
    fun `flatMapKap with concurrency preserves the merged multiset`() = runTest {
        val values = KapStream.from((1..20).asFlow())
            .flatMapKap(concurrency = 4) { value ->
                KapStream.from(
                    flow {
                        delay((value % 3).milliseconds)
                        emit(value)
                        emit(value * 10)
                    },
                )
            }
            .collectAsKap()
            .evalGraph()
        val expected = (1..20).flatMap { listOf(it, it * 10) }
        assertEquals(expected.sorted(), values.sorted(), "no inner element lost or duplicated")
    }

    // ── Sinks ───────────────────────────────────────────────────────────

    @Test
    fun `drainTo suspends on the channel's own capacity`() = runTest {
        var produced = 0
        val channel = Channel<Int>(capacity = 1)
        val source = KapStream.from(
            flow {
                repeat(10) {
                    produced += 1
                    emit(it)
                }
            },
        )

        val received = mutableListOf<Int>()
        coroutineScope {
            val drain = launch {
                with(source.drainTo(channel)) { execute() }
                channel.close()
            }
            received += channel.receive()
            delay(1.seconds)                    // the sink cannot take more: the producer must wait
            assertTrue(produced < 10, "drainTo must respect the channel capacity, produced=$produced")
            for (element in channel) received += element
            drain.join()
        }

        assertEquals(10, produced)
        assertEquals((0 until 10).toList(), received)
    }

    @Test
    fun `drainTo fails when the consumer closed the channel`() = runTest {
        val channel = Channel<Int>(capacity = 1)
        channel.close()
        assertFailsWith<ClosedSendChannelException> {
            KapStream.from(flowOf(1, 2, 3)).drainTo(channel).evalGraph()
        }
    }

    // ── Cancellation ────────────────────────────────────────────────────

    @Test
    fun `cancelling the collection cancels the source through every channel boundary`() = runTest {
        var ticks = 0
        var sourceClosed = 0
        val source = KapStream.from(
            flow {
                try {
                    coroutineScope {
                        while (isActive) {
                            emit(ticks++)
                            delay(100.milliseconds)
                        }
                    }
                } finally {
                    sourceClosed += 1
                }
            },
        )

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(350.milliseconds) {
                with(
                    source
                        .conflate()
                        .mapKap { value -> Kap.of(value) }
                        .collectAsKap(),
                ) { execute() }
            }
        }

        assertEquals(1, sourceClosed, "the source was cancelled, not left running")
        assertEquals(4, ticks, "emissions at 0, 100, 200 and 300 ms")
    }

    // ── Backpressure validation and escape hatch ────────────────────────

    @Test
    fun `unlawful backpressure values are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { Backpressure.Buffer(0) }
        assertFailsWith<IllegalArgumentException> { Backpressure.DropOldest(0) }
        assertFailsWith<IllegalArgumentException> { Backpressure.DropLatest(0) }
        assertFailsWith<IllegalArgumentException> { Backpressure.Latest(0) }
    }

    @Test
    fun `asFlow exposes the same elements`() = runTest {
        val values = KapStream.from(flowOf("a", "b"))
            .mapKap { value -> Kap.of(value.uppercase()) }
            .asFlow()
            .toList()
        assertEquals(listOf("A", "B"), values)
    }
}
