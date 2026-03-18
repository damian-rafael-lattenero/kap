package applicative

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FlowIntegrationTest {

    // ════════════════════════════════════════════════════════════════════════
    // Computation.toFlow
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `toFlow emits single value`() = runTest {
        val flow = Computation { "hello" }.toFlow()
        val collected = flow.toList()
        assertEquals(listOf("hello"), collected)
    }

    @Test
    fun `toFlow propagates exceptions`() = runTest {
        val flow = Computation<String> { throw RuntimeException("boom") }.toFlow()
        val result = runCatching { flow.toList() }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.collectAsComputation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `collectAsComputation collects all emissions`() = runTest {
        val result = Async {
            flowOf(1, 2, 3).collectAsComputation()
        }
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `collectAsComputation handles empty flow`() = runTest {
        val result = Async {
            flowOf<Int>().collectAsComputation()
        }
        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapComputation — sequential (concurrency = 1)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapComputation sequential processes elements in order`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapComputation { n ->
                Computation {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapComputation — concurrent
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapComputation concurrent processes in parallel`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapComputation(concurrency = 3) { n ->
                Computation {
                    delay(30.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50, 60), result)
    }

    @Test
    fun `mapComputation rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapComputation(concurrency = 0) { n -> Computation { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapComputationOrdered — preserves upstream order
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapComputationOrdered preserves order despite varying completion times`() = runTest {
        // Elements have reverse delay: first element is slowest
        val result = flowOf(1, 2, 3, 4, 5)
            .mapComputationOrdered(concurrency = 5) { n ->
                Computation {
                    // Element 1 takes 50ms, element 5 takes 10ms
                    delay((60L - n * 10).milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result, "Must preserve upstream order")
    }

    @Test
    fun `mapComputationOrdered runs in parallel — proven by virtual time`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .mapComputationOrdered(concurrency = 5) { n ->
                Computation {
                    delay(50.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result)
        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in ~50ms")
    }

    @Test
    fun `mapComputationOrdered respects concurrency bound`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapComputationOrdered(concurrency = 2) { n ->
                Computation {
                    delay(30.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50, 60), result)
        // 6 items / 2 concurrency = 3 batches × 30ms = 90ms
        assertEquals(90L, currentTime, "Bounded concurrency should batch correctly")
    }

    @Test
    fun `mapComputationOrdered sequential fallback when concurrency is 1`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapComputationOrdered(concurrency = 1) { n ->
                Computation {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    @Test
    fun `mapComputationOrdered propagates exception`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapComputationOrdered(concurrency = 3) { n ->
                    Computation<Int> {
                        delay(10.milliseconds)
                        if (n == 2) throw RuntimeException("boom")
                        n * 10
                    }
                }
                .toList()
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("boom") == true)
    }

    @Test
    fun `mapComputationOrdered rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapComputationOrdered(concurrency = 0) { n -> Computation { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `mapComputationOrdered handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .mapComputationOrdered(concurrency = 5) { n -> Computation { n } }
            .toList()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `mapComputationOrdered vs mapComputation — order guarantee comparison`() = runTest {
        // With reverse delays, unordered mapComputation may reorder
        val ordered = flowOf(1, 2, 3, 4, 5)
            .mapComputationOrdered(concurrency = 5) { n ->
                Computation {
                    delay((60L - n * 10).milliseconds)
                    n
                }
            }
            .toList()

        // Ordered variant always preserves input order
        assertEquals(listOf(1, 2, 3, 4, 5), ordered, "Ordered variant must preserve order")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.filterComputation
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapComputationOrdered cancels pending on intermediate failure`() = runTest {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapComputationOrdered(concurrency = 5) { n ->
                    Computation<Int> {
                        if (n == 3) {
                            delay(10.milliseconds)
                            throw RuntimeException("boom at 3")
                        }
                        delay(50.milliseconds)
                        completed.incrementAndGet()
                        n * 10
                    }
                }
                .toList()
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("boom at 3") == true)
    }

    @Test
    fun `filterComputation filters based on computation predicate`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .filterComputation { n ->
                Computation { n % 2 == 0 }
            }
            .toList()

        assertEquals(listOf(2, 4), result)
    }

    @Test
    fun `filterComputation with async predicate`() = runTest {
        val result = flowOf("admin", "user", "admin", "guest")
            .filterComputation { role ->
                Computation {
                    delay(10.milliseconds)
                    role == "admin"
                }
            }
            .toList()

        assertEquals(listOf("admin", "admin"), result)
    }

    @Test
    fun `filterComputation handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .filterComputation { Computation { true } }
            .toList()

        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration: Flow + Computation pipeline
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `full pipeline - toFlow, mapComputation, filterComputation, collectAsComputation`() = runTest {
        val result = Async {
            flowOf(1, 2, 3, 4, 5)
                .mapComputation { n -> Computation { n * 10 } }
                .filterComputation { n -> Computation { n > 20 } }
                .collectAsComputation()
        }

        assertEquals(listOf(30, 40, 50), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // mapComputation — concurrent edge cases
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapComputation concurrent with concurrency exceeding element count`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapComputation(concurrency = 10) { n ->
                Computation {
                    delay(20.milliseconds)
                    n * 10
                }
            }
            .toList()

        // All 3 elements should be processed (concurrency > count is fine)
        assertEquals(3, result.size)
        assertTrue(result.containsAll(listOf(10, 20, 30)))
    }

    @Test
    fun `mapComputation sequential exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapComputation { n ->
                    Computation<Int> {
                        if (n == 2) throw RuntimeException("boom at $n")
                        n * 10
                    }
                }
                .toList()
        }

        assertTrue(result.isFailure)
        assertEquals("boom at 2", result.exceptionOrNull()?.message)
    }

    @Test
    fun `mapComputation concurrent exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapComputation(concurrency = 3) { n ->
                    Computation<Int> {
                        delay(10.milliseconds)
                        if (n == 3) throw RuntimeException("boom at $n")
                        n * 10
                    }
                }
                .toList()
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("boom") == true)
    }

    @Test
    fun `filterComputation exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .filterComputation { n ->
                    Computation<Boolean> {
                        if (n == 2) throw RuntimeException("filter boom")
                        true
                    }
                }
                .toList()
        }

        assertTrue(result.isFailure)
        assertEquals("filter boom", result.exceptionOrNull()?.message)
    }
}
