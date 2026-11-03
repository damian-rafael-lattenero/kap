package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The KapStream laws (SPEC-5.0 milestone 5.0), proven under virtual time:
 *
 * 1. **Exact strategies never drop.** [Backpressure.Suspend] (the default) and
 *    [Backpressure.Buffer] deliver every element, in order.
 * 2. **Lossy strategies drop only what they say.** Conflate/Latest keep the
 *    newest elements, DropOldest keeps the newest `n`, DropLatest keeps the
 *    oldest `n`; every strategy preserves upstream order among the elements
 *    it keeps, never duplicates, and never drops the element buffered at
 *    completion (except DropLatest, whose drop victim is the incoming one).
 * 3. **Order.** `mapKapOrdered` preserves upstream order for every
 *    concurrency; `mapKap(concurrency = 1)` and `filterKap` preserve it; the
 *    concurrent `mapKap` preserves the multiset.
 * 4. **Cancellation propagates** through every channel boundary to the source
 *    (proven in [KapStreamTest]).
 *
 * The slow-consumer scenario is deterministic: the consumer takes one element,
 * stalls for virtual time, and only then drains — the producer has the whole
 * stall to fill and drop per policy, so the observed lists are exact.
 */
class KapStreamLawsTest {

    private val size = 100

    private fun source(count: Int = size): KapStream<Int> = KapStream.from(
        flow {
            repeat(count) { emit(it) }
        },
    )

    // ── 1. Exact strategies ─────────────────────────────────────────────

    @Test
    fun `suspend and buffer never drop and preserve order`() = runTest {
        val expected = (0 until size).toList()
        assertEquals(expected, collectWithStall(source(), 1.seconds))
        assertEquals(expected, collectWithStall(source().buffer(1), 1.seconds))
        assertEquals(expected, collectWithStall(source().buffer(8), 1.seconds))
        assertEquals(expected, collectWithStall(source().withBackpressure(Backpressure.Buffer(3)), 1.seconds))
    }

    // ── 2. Lossy strategies ─────────────────────────────────────────────

    @Test
    fun `conflate keeps the latest element and drops the superseded ones`() = runTest {
        val received = collectWithStall(source().conflate(), 1.seconds)

        assertEquals(0, received.first(), "the first element was already consumed")
        assertEquals(size - 1, received.last(), "the element buffered at completion is never dropped")
        assertEquals(received.sorted(), received, "survivors keep upstream order")
        assertTrue(received.size < size, "superseded elements are gone: $received")
    }

    @Test
    fun `latest(n) keeps exactly the last n elements`() = runTest {
        checkAll(Arb.int(1..10)) { n ->
            val received = collectWithStall(source().latest(n), 1.seconds)

            assertEquals(0, received.first())
            assertEquals((size - n until size).toList(), received.takeLast(n), "latest($n) keeps the last $n")
            assertEquals(received.sorted(), received)
            assertEquals(received.distinct(), received, "no duplicates")
        }
    }

    @Test
    fun `dropOldest(n) keeps the newest n elements`() = runTest {
        checkAll(Arb.int(1..8)) { capacity ->
            val received = collectWithStall(source().dropOldest(capacity), 1.seconds)

            assertEquals(0, received.first())
            assertEquals((size - capacity until size).toList(), received.takeLast(capacity))
            assertTrue(received.size < size, "the producer never waited, so some elements were dropped")
        }
    }

    @Test
    fun `dropLatest(n) keeps a contiguous oldest prefix and drops the incoming elements`() = runTest {
        checkAll(Arb.int(1..8)) { capacity ->
            val received = collectWithStall(source().dropLatest(capacity), 1.seconds)

            assertEquals((0 until received.size).toList(), received, "a contiguous prefix: the newest are the victims")
            assertTrue(received.size < size, "the producer never waited, so the tail was dropped")
        }
    }

    @Test
    fun `conflate and latest(1) are observationally the same value`() = runTest {
        assertEquals(
            collectWithStall(source().conflate(), 1.seconds),
            collectWithStall(source().latest(1), 1.seconds),
        )
    }

    @Test
    fun `every strategy preserves order and never duplicates`() = runTest {
        val strategies = listOf(
            Backpressure.Suspend,
            Backpressure.Buffer(3),
            Backpressure.Conflate,
            Backpressure.DropOldest(3),
            Backpressure.DropLatest(3),
            Backpressure.Latest(3),
        )
        checkAll(Arb.int(1..30)) { count ->
            strategies.forEach { strategy ->
                val received = collectWithStall(source(count).withBackpressure(strategy), 1.seconds)
                assertEquals(received.sorted(), received, "$strategy reordered elements")
                assertEquals(received.distinct(), received, "$strategy duplicated elements")
                assertTrue(received.isNotEmpty(), "$strategy delivered nothing")
            }
        }
    }

    // ── 3. Ordering of stages ───────────────────────────────────────────

    @Test
    fun `mapKapOrdered preserves upstream order for every concurrency`() = runTest {
        checkAll(Arb.int(1..8), Arb.int(1..25)) { concurrency, count ->
            val input = (1..count).toList()
            val output = KapStream.from(input.asFlow())
                .mapKapOrdered(concurrency) { value ->
                    Kap { delay(((count + 1 - value) * 3).milliseconds); value }
                }
                .collectAsKap()
                .evalGraph()

            assertEquals(input, output, "mapKapOrdered(concurrency=$concurrency) reordered $count elements")
        }
    }

    @Test
    fun `mapKap with concurrency preserves the multiset and drops nothing under the suspend default`() = runTest {
        checkAll(Arb.int(1..8), Arb.int(1..25)) { concurrency, count ->
            val input = (1..count).toList()
            val output = KapStream.from(input.asFlow())
                .mapKap(concurrency) { value -> Kap { delay((value % 3).milliseconds); value } }
                .collectAsKap()
                .evalGraph()

            assertEquals(input.sorted(), output.sorted(), "mapKap(concurrency=$concurrency) lost or duplicated")
        }
    }

    @Test
    fun `filterKap preserves upstream order`() = runTest {
        checkAll(Arb.int(1..25)) { count ->
            val input = (1..count).toList()
            val output = KapStream.from(input.asFlow())
                .filterKap { value -> Kap.of(value % 2 == 0) }
                .collectAsKap()
                .evalGraph()

            assertEquals(input.filter { it % 2 == 0 }, output)
        }
    }

    // ── Helper: slow consumer ───────────────────────────────────────────

    /**
     * Collects [source] with a consumer that takes one element and then stalls
     * for [stall] before draining. The drain channel has one slot, so the
     * producer runs ahead and the stream's named strategy decides what
     * survives — deterministically, in virtual time.
     */
    private suspend fun collectWithStall(source: KapStream<Int>, stall: Duration): List<Int> {
        val received = mutableListOf<Int>()
        val channel = Channel<Int>(capacity = 1)
        coroutineScope {
            val production = launch {
                with(source.drainTo(channel)) { execute() }
                channel.close()
            }
            received += channel.receive()
            delay(stall)
            for (element in channel) received += element
            production.join()
        }
        return received
    }
}
