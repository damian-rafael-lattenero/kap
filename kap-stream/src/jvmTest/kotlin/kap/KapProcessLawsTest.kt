package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `KapProcess` laws (SPEC-5.0 — the typed mailbox actor), proven under
 * virtual time:
 *
 * 1. **Exact strategies never drop.** `Suspend` and `Buffer` deliver every item
 *    in offer order; the drop counter stays zero.
 * 2. **Lossy strategies drop only what they say — and count it.** Conflate /
 *    `Latest(n)` / `DropOldest(n)` keep the newest `n`, `DropLatest(n)` keeps
 *    the oldest `n`; survivors keep order, are never duplicated, and
 *    `delivered + dropped == offered` once a closed mailbox has drained.
 * 3. **At most one handler at a time** (the single sequential drain; a second
 *    consumer is rejected).
 *
 * The scenario is deterministic: the consumer is launched but not started
 * while the producer offers, so the lossy strategies see a full buffer and
 * their policy decides exactly what survives.
 */
class KapProcessLawsTest {

    @Test
    fun `exact strategies never drop and preserve offer order`() = runTest {
        checkAll(Arb.int(1..40)) { count ->
            listOf(
                Backpressure.Suspend,
                Backpressure.Buffer(1),
                Backpressure.Buffer(8),
            ).forEach { strategy ->
                val process = KapProcess<Int>("exact", strategy)
                val received = mutableListOf<Int>()
                val worker = launch { process.consume { received += it } }

                repeat(count) { process.offer(it) }
                process.close()
                worker.join()

                assertEquals((0 until count).toList(), received, "$strategy lost or reordered items")
                assertEquals(0L, process.dropped.value, "$strategy must never drop")
            }
        }
    }

    @Test
    fun `lossy strategies keep exactly the advertised survivors and count every drop`() = runTest {
        checkAll(Arb.int(1..30), Arb.int(1..8)) { count, n ->
            val cases = listOf<Pair<Backpressure, List<Int>>>(
                Backpressure.Conflate to listOf(count - 1),
                Backpressure.DropOldest(n) to (maxOf(0, count - n) until count).toList(),
                Backpressure.Latest(n) to (maxOf(0, count - n) until count).toList(),
                Backpressure.DropLatest(n) to (0 until minOf(n, count)).toList(),
            )
            cases.forEach { (strategy, expected) ->
                val process = KapProcess<Int>("lossy", strategy)
                val received = mutableListOf<Int>()
                val worker = launch { process.consume { received += it } }

                repeat(count) { process.offer(it) }
                process.close()
                worker.join()

                assertEquals(expected, received, "$strategy kept the wrong items (count=$count, n=$n)")
                assertEquals(
                    (count - expected.size).toLong(),
                    process.dropped.value,
                    "$strategy miscounted drops (count=$count, n=$n)",
                )
                assertEquals(
                    count.toLong(),
                    received.size + process.dropped.value,
                    "$strategy lost an item silently (count=$count, n=$n)",
                )
            }
        }
    }

    @Test
    fun `every lossy strategy preserves order and never duplicates survivors`() = runTest {
        checkAll(Arb.int(1..30), Arb.int(1..6)) { count, n ->
            listOf(
                Backpressure.Conflate,
                Backpressure.DropOldest(n),
                Backpressure.Latest(n),
                Backpressure.DropLatest(n),
            ).forEach { strategy ->
                val process = KapProcess<Int>("order", strategy)
                val received = mutableListOf<Int>()
                val worker = launch { process.consume { received += it } }

                repeat(count) { process.offer(it) }
                process.close()
                worker.join()

                assertEquals(received.sorted(), received, "$strategy reordered survivors")
                assertEquals(received.distinct(), received, "$strategy duplicated an item")
            }
        }
    }

    @Test
    fun `offers after close are dropped and counted`() = runTest {
        checkAll(Arb.int(1..20)) { extraOffers ->
            listOf(
                Backpressure.Suspend,
                Backpressure.Buffer(4),
                Backpressure.Conflate,
                Backpressure.DropLatest(2),
            ).forEach { strategy ->
                val process = KapProcess<Int>("closed", strategy)
                process.close()
                repeat(extraOffers) { process.offer(it) }

                assertEquals(extraOffers.toLong(), process.dropped.value, "$strategy forgot a post-close offer")
            }
        }
    }

    @Test
    fun `at most one handler runs at a time`() = runTest {
        checkAll(Arb.int(1..30)) { count ->
            val process = KapProcess<Int>("one-handler", Backpressure.Buffer(64))
            var active = 0
            var maxActive = 0
            val worker = launch {
                process.consume {
                    active += 1
                    maxActive = maxOf(maxActive, active)
                    yield()
                    active -= 1
                }
            }

            repeat(count) { process.offer(it) }
            process.close()
            worker.join()

            assertEquals(1, maxActive, "two handlers ran concurrently ($count items)")
            assertEquals(0L, process.dropped.value, "an exact strategy dropped with a live consumer")
        }
    }
}
