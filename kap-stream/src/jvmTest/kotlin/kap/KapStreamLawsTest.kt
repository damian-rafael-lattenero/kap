package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kap.kotest.BackpressuredProducer
import kap.kotest.backpressureLaws
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
 *    it keeps and never duplicates one.
 * 3. **Order.** `mapKapOrdered` preserves upstream order for every
 *    concurrency; `mapKap(concurrency = 1)` and `filterKap` preserve it; the
 *    concurrent `mapKap` preserves the multiset.
 * 4. **Cancellation propagates** through every channel boundary to the source
 *    (proven in [KapStreamTest]).
 *
 * Laws 1-2 are the **reusable** suite from `kap-kotest`
 * ([kap.kotest.backpressureLaws]): this file only injects the `KapStream`
 * producer, so every downstream implementation is held to the same statements.
 * The slow-consumer scenario is deterministic: the consumer takes one element,
 * stalls for virtual time, and only then drains — the producer has the whole
 * stall to fill and drop per policy.
 */
class KapStreamLawsTest {

    private val size = 100

    private fun source(count: Int = size): KapStream<Int> = KapStream.from(
        flow {
            repeat(count) { emit(it) }
        },
    )

    // ── 1-2. Named backpressure strategies (the reusable suite) ─────────

    @Test
    fun `backpressure laws hold for every named strategy`() = runTest {
        backpressureLaws(KapStreamProducer(), (0 until size).toList()).verify()
    }

    @Test
    fun `conflate and latest(1) are observationally the same value`() = runTest {
        assertEquals(
            collectWithStall(source().conflate(), 1.seconds),
            collectWithStall(source().latest(1), 1.seconds),
        )
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

/** The `KapStream` producer the reusable backpressure suite drives. */
private class KapStreamProducer : BackpressuredProducer<Int> {
    override suspend fun produce(strategy: Backpressure, items: List<Int>, emit: suspend (Int) -> Unit) {
        KapStream.from(items.asFlow())
            .withBackpressure(strategy)
            .asFlow()
            .collect { element -> emit(element) }
    }
}
