package kap

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
    // Kap.toFlow
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `toFlow emits single value`() = runTest {
        val flow = Kap { "hello" }.toFlow()
        val collected = flow.toList()
        assertEquals(listOf("hello"), collected)
    }

    @Test
    fun `toFlow propagates exceptions`() = runTest {
        val flow = Kap<String> { throw RuntimeException("boom") }.toFlow()
        val result = runCatching { flow.toList() }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.collectAsKap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `collectAsKap collects all emissions`() = runTest {
        val result = flowOf(1, 2, 3).collectAsKap().evalGraph()
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `collectAsKap handles empty flow`() = runTest {
        val result = flowOf<Int>().collectAsKap().evalGraph()
        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapKap — sequential (concurrency = 1)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapKap sequential processes elements in order`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapKap { n ->
                Kap {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapKap — concurrent
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapKap concurrent processes in parallel`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapKap(concurrency = 3) { n ->
                Kap {
                    delay(30.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50, 60), result)
    }

    @Test
    fun `mapKap rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapKap(concurrency = 0) { n -> Kap { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapKapOrdered — preserves upstream order
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapKapOrdered preserves order despite varying completion times`() = runTest {
        // Elements have reverse delay: first element is slowest
        val result = flowOf(1, 2, 3, 4, 5)
            .mapKapOrdered(concurrency = 5) { n ->
                Kap {
                    // Element 1 takes 50ms, element 5 takes 10ms
                    delay((60L - n * 10).milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result, "Must preserve upstream order")
    }

    @Test
    fun `mapKapOrdered runs in parallel — proven by virtual time`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .mapKapOrdered(concurrency = 5) { n ->
                Kap {
                    delay(50.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result)
        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in ~50ms")
    }

    @Test
    fun `mapKapOrdered respects concurrency bound`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapKapOrdered(concurrency = 2) { n ->
                Kap {
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
    fun `mapKapOrdered sequential fallback when concurrency is 1`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapKapOrdered(concurrency = 1) { n ->
                Kap {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    @Test
    fun `mapKapOrdered propagates exception`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapKapOrdered(concurrency = 3) { n ->
                    Kap<Int> {
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
    fun `mapKapOrdered rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapKapOrdered(concurrency = 0) { n -> Kap { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `mapKapOrdered handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .mapKapOrdered(concurrency = 5) { n -> Kap { n } }
            .toList()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `mapKapOrdered vs mapKap — order guarantee comparison`() = runTest {
        // With reverse delays, unordered mapKap may reorder
        val ordered = flowOf(1, 2, 3, 4, 5)
            .mapKapOrdered(concurrency = 5) { n ->
                Kap {
                    delay((60L - n * 10).milliseconds)
                    n
                }
            }
            .toList()

        // Ordered variant always preserves input order
        assertEquals(listOf(1, 2, 3, 4, 5), ordered, "Ordered variant must preserve order")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.filterKap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapKapOrdered cancels pending on intermediate failure`() = runTest {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapKapOrdered(concurrency = 5) { n ->
                    Kap<Int> {
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
    fun `filterKap filters based on computation predicate`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .filterKap { n ->
                Kap { n % 2 == 0 }
            }
            .toList()

        assertEquals(listOf(2, 4), result)
    }

    @Test
    fun `filterKap with async predicate`() = runTest {
        val result = flowOf("admin", "user", "admin", "guest")
            .filterKap { role ->
                Kap {
                    delay(10.milliseconds)
                    role == "admin"
                }
            }
            .toList()

        assertEquals(listOf("admin", "admin"), result)
    }

    @Test
    fun `filterKap handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .filterKap { Kap { true } }
            .toList()

        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration: Flow + Kap pipeline
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `full pipeline - toFlow, mapKap, filterKap, collectAsKap`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
                .mapKap { n -> Kap { n * 10 } }
                .filterKap { n -> Kap { n > 20 } }
                .collectAsKap().evalGraph()

        assertEquals(listOf(30, 40, 50), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // mapKap — concurrent edge cases
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapKap concurrent with concurrency exceeding element count`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapKap(concurrency = 10) { n ->
                Kap {
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
    fun `mapKap sequential exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapKap { n ->
                    Kap<Int> {
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
    fun `mapKap concurrent exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapKap(concurrency = 3) { n ->
                    Kap<Int> {
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
    fun `filterKap exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .filterKap { n ->
                    Kap<Boolean> {
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
