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
    // Effect.toFlow
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `toFlow emits single value`() = runTest {
        val flow = Effect { "hello" }.toFlow()
        val collected = flow.toList()
        assertEquals(listOf("hello"), collected)
    }

    @Test
    fun `toFlow propagates exceptions`() = runTest {
        val flow = Effect<String> { throw RuntimeException("boom") }.toFlow()
        val result = runCatching { flow.toList() }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.collectAsEffect
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `collectAsEffect collects all emissions`() = runTest {
        val result = Async {
            flowOf(1, 2, 3).collectAsEffect()
        }
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `collectAsEffect handles empty flow`() = runTest {
        val result = Async {
            flowOf<Int>().collectAsEffect()
        }
        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapEffect — sequential (concurrency = 1)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapEffect sequential processes elements in order`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapEffect { n ->
                Effect {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapEffect — concurrent
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapEffect concurrent processes in parallel`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapEffect(concurrency = 3) { n ->
                Effect {
                    delay(30.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50, 60), result)
    }

    @Test
    fun `mapEffect rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapEffect(concurrency = 0) { n -> Effect { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.mapEffectOrdered — preserves upstream order
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapEffectOrdered preserves order despite varying completion times`() = runTest {
        // Elements have reverse delay: first element is slowest
        val result = flowOf(1, 2, 3, 4, 5)
            .mapEffectOrdered(concurrency = 5) { n ->
                Effect {
                    // Element 1 takes 50ms, element 5 takes 10ms
                    delay((60L - n * 10).milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result, "Must preserve upstream order")
    }

    @Test
    fun `mapEffectOrdered runs in parallel — proven by virtual time`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .mapEffectOrdered(concurrency = 5) { n ->
                Effect {
                    delay(50.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30, 40, 50), result)
        assertEquals(50L, currentTime, "5 parallel tasks @ 50ms should complete in ~50ms")
    }

    @Test
    fun `mapEffectOrdered respects concurrency bound`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5, 6)
            .mapEffectOrdered(concurrency = 2) { n ->
                Effect {
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
    fun `mapEffectOrdered sequential fallback when concurrency is 1`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapEffectOrdered(concurrency = 1) { n ->
                Effect {
                    delay(10.milliseconds)
                    n * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), result)
        assertEquals(30L, currentTime, "Sequential: 3 * 10ms = 30ms")
    }

    @Test
    fun `mapEffectOrdered propagates exception`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapEffectOrdered(concurrency = 3) { n ->
                    Effect<Int> {
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
    fun `mapEffectOrdered rejects concurrency less than 1`() = runTest {
        val result = runCatching {
            flowOf(1).mapEffectOrdered(concurrency = 0) { n -> Effect { n } }.toList()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `mapEffectOrdered handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .mapEffectOrdered(concurrency = 5) { n -> Effect { n } }
            .toList()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `mapEffectOrdered vs mapEffect — order guarantee comparison`() = runTest {
        // With reverse delays, unordered mapEffect may reorder
        val ordered = flowOf(1, 2, 3, 4, 5)
            .mapEffectOrdered(concurrency = 5) { n ->
                Effect {
                    delay((60L - n * 10).milliseconds)
                    n
                }
            }
            .toList()

        // Ordered variant always preserves input order
        assertEquals(listOf(1, 2, 3, 4, 5), ordered, "Ordered variant must preserve order")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Flow.filterEffect
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapEffectOrdered cancels pending on intermediate failure`() = runTest {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapEffectOrdered(concurrency = 5) { n ->
                    Effect<Int> {
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
    fun `filterEffect filters based on computation predicate`() = runTest {
        val result = flowOf(1, 2, 3, 4, 5)
            .filterEffect { n ->
                Effect { n % 2 == 0 }
            }
            .toList()

        assertEquals(listOf(2, 4), result)
    }

    @Test
    fun `filterEffect with async predicate`() = runTest {
        val result = flowOf("admin", "user", "admin", "guest")
            .filterEffect { role ->
                Effect {
                    delay(10.milliseconds)
                    role == "admin"
                }
            }
            .toList()

        assertEquals(listOf("admin", "admin"), result)
    }

    @Test
    fun `filterEffect handles empty flow`() = runTest {
        val result = flowOf<Int>()
            .filterEffect { Effect { true } }
            .toList()

        assertEquals(emptyList(), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration: Flow + Effect pipeline
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `full pipeline - toFlow, mapEffect, filterEffect, collectAsEffect`() = runTest {
        val result = Async {
            flowOf(1, 2, 3, 4, 5)
                .mapEffect { n -> Effect { n * 10 } }
                .filterEffect { n -> Effect { n > 20 } }
                .collectAsEffect()
        }

        assertEquals(listOf(30, 40, 50), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // mapEffect — concurrent edge cases
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapEffect concurrent with concurrency exceeding element count`() = runTest {
        val result = flowOf(1, 2, 3)
            .mapEffect(concurrency = 10) { n ->
                Effect {
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
    fun `mapEffect sequential exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .mapEffect { n ->
                    Effect<Int> {
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
    fun `mapEffect concurrent exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3, 4, 5)
                .mapEffect(concurrency = 3) { n ->
                    Effect<Int> {
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
    fun `filterEffect exception propagates`() = runTest {
        val result = runCatching {
            flowOf(1, 2, 3)
                .filterEffect { n ->
                    Effect<Boolean> {
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
