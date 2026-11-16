package kap.kotest

import kap.Backpressure
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Proves the reusable [backpressureLaws] against a channel-backed producer —
 * the same `Channel(capacity, overflow)` translation `KapStream` uses — and
 * proves the exact law bites when a producer drops under `Suspend`.
 */
class BackpressureLawsTest {

    private val items = (1..100).toList()

    @Test
    fun `a channel-backed producer obeys every backpressure law`() = runTest {
        backpressureLaws(ChannelProducer(), items).verify()
    }

    @Test
    fun `the exact law catches a producer that drops under Suspend`() = runTest {
        assertFailsWith<AssertionError> {
            backpressureLaws(DroppingProducer(), items, listOf(Backpressure.Suspend)).exactStrategiesNeverDrop()
        }
    }
}

/** The named strategies as real channel boundaries, mirroring kap-stream's translation. */
private class ChannelProducer : BackpressuredProducer<Int> {

    override suspend fun produce(strategy: Backpressure, items: List<Int>, emit: suspend (Int) -> Unit) {
        val channel = when (strategy) {
            Backpressure.Suspend -> Channel<Int>(capacity = 1)
            is Backpressure.Buffer -> Channel<Int>(strategy.capacity)
            Backpressure.Conflate -> Channel<Int>(1, BufferOverflow.DROP_OLDEST)
            is Backpressure.DropOldest -> Channel<Int>(strategy.capacity, BufferOverflow.DROP_OLDEST)
            is Backpressure.DropLatest -> Channel<Int>(strategy.capacity, BufferOverflow.DROP_LATEST)
            is Backpressure.Latest -> Channel<Int>(strategy.n, BufferOverflow.DROP_OLDEST)
        }
        coroutineScope {
            val production = launch {
                items.forEach { channel.send(it) }
                channel.close()
            }
            try {
                for (element in channel) emit(element)
            } finally {
                production.cancel()
            }
        }
    }
}

/** Deliberately unlawful: drops half the elements even under the exact `Suspend` strategy. */
private class DroppingProducer : BackpressuredProducer<Int> {
    override suspend fun produce(strategy: Backpressure, items: List<Int>, emit: suspend (Int) -> Unit) {
        items.filterIndexed { index, _ -> index % 2 == 0 }.forEach { emit(it) }
    }
}
