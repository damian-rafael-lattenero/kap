package kap.kotest

import kap.Backpressure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The producer half of a backpressure law run: produce [items] through your
 * implementation's **named** backpressure boundary, handing every surviving
 * element to [emit].
 *
 * The law suite supplies a slow consumer ([emit] blocks while it stalls for
 * virtual time), so the named strategy decides what survives — deterministically.
 * Inject your implementation once:
 *
 * ```
 * class ChannelProducer : BackpressuredProducer<Int> {
 *     override suspend fun produce(strategy: Backpressure, items: List<Int>, emit: suspend (Int) -> Unit) {
 *         val channel = Channel<Int>(capacityOf(strategy), overflowOf(strategy))
 *         coroutineScope {
 *             val producer = launch { items.forEach { channel.send(it) }; channel.close() }
 *             try { for (element in channel) emit(element) } finally { producer.cancel() }
 *         }
 *     }
 * }
 *
 * @Test
 * fun `backpressure laws`() = runTest {
 *     backpressureLaws(ChannelProducer(), items = (1..100).toList()).verify()
 * }
 * ```
 */
fun interface BackpressuredProducer<A> {
    suspend fun produce(strategy: Backpressure, items: List<A>, emit: suspend (A) -> Unit)
}

/**
 * Reusable laws of a named [Backpressure] strategy, proven under virtual time
 * (no sleeps, no wall clock). Obtain one from
 * [`backpressureLaws`][backpressureLaws] inside `runTest`.
 *
 * 1. **Exact strategies never drop.** `Suspend` and `Buffer(n)` deliver every
 *    element, in upstream order.
 * 2. **Lossy strategies drop only what they name.** `Conflate`/`Latest(n)`
 *    keep the newest, `DropOldest(n)` the newest `n`, `DropLatest(n)` the
 *    already-buffered prefix; survivors keep upstream order.
 * 3. **Order is preserved.** Every strategy delivers its survivors as an
 *    ordered subsequence of the input.
 * 4. **Nothing is duplicated.** Every element is delivered at most once.
 */
class BackpressureLaws<A> internal constructor(
    private val producer: BackpressuredProducer<A>,
    private val items: List<A>,
    private val strategies: List<Backpressure>,
    private val stall: Duration,
) {

    /**
     * Law 1 — exact strategies (`Suspend`, `Buffer(n)`) deliver every element
     * in upstream order: the producer suspends instead of discarding.
     */
    suspend fun exactStrategiesNeverDrop() {
        strategies.filter { it.isExactStrategy() }.forEach { strategy ->
            assertEquals(items, collect(strategy), "$strategy dropped or reordered elements")
        }
    }

    /**
     * Law 2 — every lossy strategy drops exactly the elements it names:
     * `Conflate`/`Latest(n)` keep the newest, `DropOldest(n)` keeps the newest
     * `n`, `DropLatest(n)` keeps the already-buffered (oldest) prefix.
     */
    suspend fun lossyStrategiesDropOnlyWhatTheyName() {
        strategies.filterNot { it.isExactStrategy() }.forEach { strategy ->
            val received = collect(strategy)

            assertTrue(received.isNotEmpty(), "$strategy delivered nothing")
            assertEquals(items.first(), received.first(), "$strategy dropped the already-consumed element")
            assertTrue(
                received.isOrderedSubsequenceOf(items),
                "$strategy reordered its survivors: $received is not a subsequence of $items",
            )

            when (strategy) {
                Backpressure.Conflate -> {
                    assertEquals(items.last(), received.last(), "Conflate must keep the newest element")
                    assertTrue(
                        received.size < items.size,
                        "Conflate should have dropped superseded elements: $received",
                    )
                }
                is Backpressure.Latest -> {
                    assertEquals(
                        items.takeLast(strategy.n),
                        received.takeLast(strategy.n),
                        "Latest(${strategy.n}) must keep the newest ${strategy.n}",
                    )
                    assertTrue(received.size < items.size, "Latest(${strategy.n}) should have dropped older elements")
                }
                is Backpressure.DropOldest -> {
                    assertEquals(
                        items.takeLast(strategy.capacity),
                        received.takeLast(strategy.capacity),
                        "DropOldest(${strategy.capacity}) must keep the newest ${strategy.capacity}",
                    )
                    assertTrue(received.size < items.size, "DropOldest should have dropped older elements")
                }
                is Backpressure.DropLatest -> {
                    assertEquals(
                        items.take(received.size),
                        received,
                        "DropLatest(${strategy.capacity}) must keep the already-buffered prefix",
                    )
                    assertTrue(received.size < items.size, "DropLatest should have dropped incoming elements")
                }
                Backpressure.Suspend, is Backpressure.Buffer -> error("exact strategy in the lossy law: $strategy")
            }
        }
    }

    /** Law 3 — survivors keep upstream order as an ordered subsequence of the input. */
    suspend fun orderingIsPreserved() {
        strategies.forEach { strategy ->
            val received = collect(strategy)
            assertTrue(
                received.isOrderedSubsequenceOf(items),
                "$strategy reordered its survivors: $received is not a subsequence of $items",
            )
        }
    }

    /** Law 4 — no element is delivered twice. */
    suspend fun noDuplication() {
        strategies.forEach { strategy ->
            val received = collect(strategy)
            assertEquals(received.distinct(), received, "$strategy duplicated elements")
        }
    }

    /** Runs all four laws. The one-line entry point downstream projects call. */
    suspend fun verify() {
        exactStrategiesNeverDrop()
        lossyStrategiesDropOnlyWhatTheyName()
        orderingIsPreserved()
        noDuplication()
    }

    /**
     * Runs [producer] into a consumer that takes one element, stalls for
     * [stall] of virtual time, then drains — the deterministic slow-consumer
     * scenario that lets the named strategy fill and drop.
     */
    private suspend fun collect(strategy: Backpressure): List<A> {
        val received = mutableListOf<A>()
        val channel = Channel<A>(capacity = 1)
        coroutineScope {
            val production = launch {
                producer.produce(strategy, items) { element -> channel.send(element) }
                channel.close()
            }
            received += channel.receive()
            delay(stall)
            for (element in channel) received += element
            production.join()
        }
        return received
    }

    companion object {
        private const val DEFAULT_BUFFER = 3

        /** The named strategies every law ranges over by default. */
        fun defaultStrategies(): List<Backpressure> = listOf(
            Backpressure.Suspend,
            Backpressure.Buffer(DEFAULT_BUFFER),
            Backpressure.Conflate,
            Backpressure.DropOldest(DEFAULT_BUFFER),
            Backpressure.DropLatest(DEFAULT_BUFFER),
            Backpressure.Latest(DEFAULT_BUFFER),
        )
    }
}

/**
 * Builds the reusable backpressure law suite for [producer]. Call it inside
 * `runTest`; the laws are virtual-time only.
 *
 * [items] must contain at least two **distinct** elements and must be longer
 * than the largest named buffer plus one, so the lossy laws can observe real
 * drops. [strategies] defaults to [BackpressureLaws.defaultStrategies].
 *
 * ```
 * @Test
 * fun `my producer obeys backpressure`() = runTest {
 *     backpressureLaws(MyProducer(), (1..100).toList()).verify()
 * }
 * ```
 */
fun <A> TestScope.backpressureLaws(
    producer: BackpressuredProducer<A>,
    items: List<A>,
    strategies: List<Backpressure> = BackpressureLaws.defaultStrategies(),
    stall: Duration = 1.seconds,
): BackpressureLaws<A> {
    require(items.size >= 2) { "backpressure laws need at least two elements" }
    require(items.distinct().size == items.size) { "the sample items must be distinct (ordering is index-based)" }
    require(strategies.isNotEmpty()) { "the law suite needs at least one strategy" }
    val largestBuffer = strategies.maxOf { it.capacityOrOne() }
    require(items.size > largestBuffer + 1) {
        "the sample must be longer than the largest buffer plus one ($largestBuffer) so losses are observable"
    }
    return BackpressureLaws(producer, items, strategies, stall)
}

/** True for the strategies that never drop (the default `Suspend` included). */
internal fun Backpressure.isExactStrategy(): Boolean =
    this is Backpressure.Suspend || this is Backpressure.Buffer

private fun Backpressure.capacityOrOne(): Int = when (this) {
    Backpressure.Suspend, Backpressure.Conflate -> 1
    is Backpressure.Buffer -> capacity
    is Backpressure.DropOldest -> capacity
    is Backpressure.DropLatest -> capacity
    is Backpressure.Latest -> n
}

private fun <A> List<A>.isOrderedSubsequenceOf(source: List<A>): Boolean {
    var index = 0
    for (element in this) {
        while (index < source.size && source[index] != element) index += 1
        if (index == source.size) return false
        index += 1
    }
    return true
}
