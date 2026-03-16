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
    // Flow.filterComputation
    // ════════════════════════════════════════════════════════════════════════

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
}
