package applicative

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Edge case tests covering scenarios identified during the deep stress-test analysis.
 *
 * These tests target subtle concurrency, cancellation, and statefulness edge cases
 * that are not covered by the main test suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EdgeCaseStressTest {

    // ── memoize() cancellation safety ───────────────────────────────────────

    @Test
    fun `memoize — cancellation of first caller does not poison cache`() = runTest {
        var executionCount = 0
        val computation = Effect<String> {
            executionCount++
            if (executionCount == 1) {
                throw CancellationException("first caller cancelled")
            }
            "success on attempt $executionCount"
        }.memoize()

        // First call: cancelled
        val firstResult = runCatching {
            coroutineScope { with(computation) { execute() } }
        }
        assertTrue(firstResult.isFailure)
        assertTrue(firstResult.exceptionOrNull() is CancellationException)

        // Second call: should retry the original, NOT return stale CancellationException
        val secondResult = coroutineScope { with(computation) { execute() } }
        assertEquals("success on attempt 2", secondResult)
    }

    @Test
    fun `memoize — non-cancellation failure IS cached`() = runTest {
        var executionCount = 0
        val computation = Effect<String> {
            executionCount++
            throw IllegalStateException("boom #$executionCount")
        }.memoize()

        val ex1 = assertFailsWith<IllegalStateException> {
            coroutineScope { with(computation) { execute() } }
        }
        assertEquals("boom #1", ex1.message)

        val ex2 = assertFailsWith<IllegalStateException> {
            coroutineScope { with(computation) { execute() } }
        }
        assertEquals("boom #1", ex2.message)
        assertEquals(1, executionCount)
    }

    @Test
    fun `memoize — success is cached across concurrent callers`() = runTest {
        var executionCount = 0
        val computation = Effect<Int> {
            executionCount++
            delay(50.milliseconds)
            42
        }.memoize()

        val graph = kap { a: Int, b: Int, c: Int -> listOf(a, b, c) }
            .with { computation.await() }
            .with { computation.await() }
            .with { computation.await() }

        val results = Async { graph }
        assertEquals(listOf(42, 42, 42), results)
        assertEquals(1, executionCount)
    }

    @Test
    fun `memoize — cancellation then success from another coroutine`() = runTest {
        var executionCount = 0
        val latch = CompletableDeferred<Unit>()
        val computation = Effect<String> {
            executionCount++
            latch.await()
            "result-$executionCount"
        }.memoize()

        val job = launch {
            coroutineScope { with(computation) { execute() } }
        }

        delay(10.milliseconds)
        job.cancel()
        job.join()

        latch.complete(Unit)
        val result = coroutineScope { with(computation) { execute() } }
        assertEquals("result-2", result)
    }

    // ── mapEffect concurrency fix ──────────────────────────────────────

    @Test
    fun `mapEffect — concurrency greater than 1 actually parallelizes`() = runTest {
        val results = (1..6).asFlow()
            .mapEffect(concurrency = 3) { i ->
                Effect {
                    delay(50.milliseconds)
                    i * 10
                }
            }
            .toList()

        assertEquals(6, results.size)
        assertEquals(setOf(10, 20, 30, 40, 50, 60), results.toSet())
        assertTrue(currentTime <= 150, "Expected ~100ms virtual time, got ${currentTime}ms")
    }

    @Test
    fun `mapEffect — concurrency 1 is sequential`() = runTest {
        val results = (1..3).asFlow()
            .mapEffect(concurrency = 1) { i ->
                Effect {
                    delay(50.milliseconds)
                    i * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), results)
        assertTrue(currentTime >= 150, "Expected sequential execution (>=150ms), got ${currentTime}ms")
    }

    @Test
    fun `mapEffect — concurrency bounds are respected`() = runTest {
        var maxConcurrent = 0
        var currentConcurrent = 0
        val lock = Mutex()

        val results = (1..10).asFlow()
            .mapEffect(concurrency = 3) { i ->
                Effect {
                    lock.withLock {
                        currentConcurrent++
                        if (currentConcurrent > maxConcurrent) maxConcurrent = currentConcurrent
                    }
                    delay(30.milliseconds)
                    lock.withLock { currentConcurrent-- }
                    i
                }
            }
            .toList()

        assertEquals(10, results.size)
        assertTrue(maxConcurrent <= 3, "Max concurrent exceeded bound: $maxConcurrent > 3")
    }

    // ── Race edge cases ────────────────────────────────────────────────────

    @Test
    fun `race — both fail, first error propagates`() = runTest {
        val graph: Effect<String> = race(
            Effect { delay(10.milliseconds); throw RuntimeException("first") },
            Effect { delay(20.milliseconds); throw IllegalStateException("second") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("first", ex.message)
    }

    @Test
    fun `raceN — all fail, first error propagates`() = runTest {
        val graph: Effect<String> = raceN(
            Effect { delay(10.milliseconds); throw RuntimeException("a") },
            Effect { delay(20.milliseconds); throw IllegalStateException("b") },
            Effect { delay(30.milliseconds); throw UnsupportedOperationException("c") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("a", ex.message)
    }

    @Test
    fun `race — fast failure, slow success wins`() = runTest {
        val result = Async {
            race(
                Effect {
                    delay(10.milliseconds)
                    throw RuntimeException("fast fail")
                },
                Effect {
                    delay(50.milliseconds)
                    "slow success"
                },
            )
        }
        assertEquals("slow success", result)
    }

    @Test
    fun `raceN — single computation returns directly`() = runTest {
        val result = Async { raceN(Effect { "only one" }) }
        assertEquals("only one", result)
    }

    @Test
    fun `raceN — many racers, fastest success wins`() = runTest {
        val result = Async {
            raceN(
                Effect { delay(200.milliseconds); "slow1" },
                Effect { delay(300.milliseconds); "slow2" },
                Effect { delay(10.milliseconds); "fast winner" },
                Effect { delay(400.milliseconds); "slow3" },
            )
        }
        assertEquals("fast winner", result)
    }

    @Test
    fun `race — CancellationException in one racer treated as failure, other racer wins`() = runTest {
        // In race, CancellationException from a racer is treated as a failure —
        // the other racer gets a chance to succeed. Only scope-level cancellation propagates.
        val result = Async {
            race(
                Effect<String> { throw CancellationException("one racer cancelled") },
                Effect { delay(10.milliseconds); "survivor" },
            )
        }
        assertEquals("survivor", result)
    }

    // ── PhaseBarrier edge cases ────────────────────────────────────────────

    @Test
    fun `then — barrier signal fires even on failure`() = runTest {
        val failing: Effect<Int> = Effect { throw RuntimeException("barrier failed") }
        val graph = kap { a: Int, b: Int, c: Int -> a + b + c }
            .with { 1 }
            .then(failing)
            .with { 3 }
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("barrier failed", ex.message)
    }

    @Test
    fun `then — multiple barriers in sequence`() = runTest {
        val order = mutableListOf<String>()
        val result = Async {
            kap { a: String, b: String, c: String, d: String, e: String ->
                "$a|$b|$c|$d|$e"
            }
                .with { order.add("a"); "a" }
                .then { order.add("b"); "b" }
                .with { order.add("c"); "c" }
                .then { order.add("d"); "d" }
                .with { order.add("e"); "e" }
        }
        assertEquals("a|b|c|d|e", result)
        assertTrue(order.indexOf("b") > order.indexOf("a"))
        assertTrue(order.indexOf("d") > order.indexOf("c"))
    }

    // ── orElse edge cases ──────────────────────────────────────────────────

    @Test
    fun `orElse — CancellationException propagates, does not fall through`() = runTest {
        val graph = Effect<String> { throw CancellationException("cancel") }
            .orElse(Effect { "fallback" })
        assertFailsWith<CancellationException> {
            coroutineScope { with(graph) { execute() } }
        }
    }

    @Test
    fun `orElse — chains three computations, first two fail`() = runTest {
        val calls = mutableListOf<String>()
        val result = Async {
            Effect { calls.add("primary"); throw RuntimeException("p") }
                .orElse(Effect { calls.add("secondary"); throw RuntimeException("s") })
                .orElse(Effect { calls.add("tertiary"); "success" })
        }
        assertEquals("success", result)
        assertEquals(listOf("primary", "secondary", "tertiary"), calls)
    }

    @Test
    fun `firstSuccessOf — all fail, last error thrown`() = runTest {
        val graph: Effect<String> = firstSuccessOf(
            Effect { throw RuntimeException("a") },
            Effect { throw IllegalStateException("b") },
            Effect { throw UnsupportedOperationException("c") },
        )
        val ex = assertFailsWith<UnsupportedOperationException> { val r = Async { graph } }
        assertEquals("c", ex.message)
    }

    // ── ensure / ensureNotNull edge cases ──────────────────────────────────

    @Test
    fun `ensure — predicate failure throws, success passes through`() = runTest {
        val passGraph = Effect.of(42).ensure({ IllegalStateException("too small") }) { it > 10 }
        assertEquals(42, Async { passGraph })

        val failGraph = Effect.of(5).ensure({ IllegalStateException("too small") }) { it > 10 }
        assertFailsWith<IllegalStateException> { val r = Async { failGraph } }
    }

    @Test
    fun `ensureNotNull — null extraction throws`() = runTest {
        data class Wrapper(val inner: String?)

        val passGraph = Effect.of(Wrapper("hello"))
            .ensureNotNull({ IllegalStateException("null!") }) { it.inner }
        assertEquals("hello", Async { passGraph })

        val failGraph = Effect.of(Wrapper(null))
            .ensureNotNull({ IllegalStateException("null!") }) { it.inner }
        assertFailsWith<IllegalStateException> { val r = Async { failGraph } }
    }

    // ── memoizeOnSuccess edge cases ────────────────────────────────────────

    @Test
    fun `memoizeOnSuccess — cancellation does not poison cache`() = runTest {
        var executionCount = 0
        val latch = CompletableDeferred<Unit>()
        val computation = Effect<String> {
            executionCount++
            latch.await()
            "result-$executionCount"
        }.memoizeOnSuccess()

        val job = launch {
            coroutineScope { with(computation) { execute() } }
        }
        delay(10.milliseconds)
        job.cancel()
        job.join()

        latch.complete(Unit)
        val result = coroutineScope { with(computation) { execute() } }
        assertEquals("result-2", result)
    }

    // ── Effect.defer edge cases ───────────────────────────────────────

    @Test
    fun `defer — lazy construction prevents eager stack overflow`() = runTest {
        fun countdown(n: Int): Effect<Int> =
            if (n <= 0) Effect.of(0)
            else Effect.defer { countdown(n - 1).map { it + 1 } }

        assertEquals(100, Async { countdown(100) })
    }

    // ── Effect.failed edge cases ──────────────────────────────────────

    @Test
    fun `failed — throws immediately, composes with recover`() = runTest {
        val graph = Effect.failed(RuntimeException("boom"))
            .recover { "recovered" }
        assertEquals("recovered", Async { graph })
    }

    // ── timeout edge cases ─────────────────────────────────────────────────

    @Test
    fun `timeout with default — null result not confused with timeout`() = runTest {
        val graph = Effect<String?> {
            delay(10.milliseconds)
            null
        }.timeout(100.milliseconds, "default")

        val result: String? = Async { graph }
        assertEquals(null, result)
    }

    @Test
    fun `timeout with computation fallback — fallback runs on timeout`() = runTest {
        val graph = Effect {
            delay(200.milliseconds)
            "slow"
        }.timeout(50.milliseconds, Effect { "fast fallback" })

        assertEquals("fast fallback", Async { graph })
    }
}
