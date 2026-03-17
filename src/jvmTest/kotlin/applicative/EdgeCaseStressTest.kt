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
import kotlin.time.TestTimeSource

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
        val computation = Computation<String> {
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
        val computation = Computation<String> {
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
        val computation = Computation<Int> {
            executionCount++
            delay(50.milliseconds)
            42
        }.memoize()

        val graph = lift3 { a: Int, b: Int, c: Int -> listOf(a, b, c) }
            .ap { computation.await() }
            .ap { computation.await() }
            .ap { computation.await() }

        val results = Async { graph }
        assertEquals(listOf(42, 42, 42), results)
        assertEquals(1, executionCount)
    }

    @Test
    fun `memoize — cancellation then success from another coroutine`() = runTest {
        var executionCount = 0
        val latch = CompletableDeferred<Unit>()
        val computation = Computation<String> {
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

    // ── mapComputation concurrency fix ──────────────────────────────────────

    @Test
    fun `mapComputation — concurrency greater than 1 actually parallelizes`() = runTest {
        val results = (1..6).asFlow()
            .mapComputation(concurrency = 3) { i ->
                Computation {
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
    fun `mapComputation — concurrency 1 is sequential`() = runTest {
        val results = (1..3).asFlow()
            .mapComputation(concurrency = 1) { i ->
                Computation {
                    delay(50.milliseconds)
                    i * 10
                }
            }
            .toList()

        assertEquals(listOf(10, 20, 30), results)
        assertTrue(currentTime >= 150, "Expected sequential execution (>=150ms), got ${currentTime}ms")
    }

    @Test
    fun `mapComputation — concurrency bounds are respected`() = runTest {
        var maxConcurrent = 0
        var currentConcurrent = 0
        val lock = Mutex()

        val results = (1..10).asFlow()
            .mapComputation(concurrency = 3) { i ->
                Computation {
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

    // ── Schedule.withMaxDuration statefulness ──────────────────────────────

    @Test
    fun `withMaxDuration — reused schedule leaks state across retries`() = runTest {
        val timeSource = TestTimeSource()

        val statefulSchedule = Schedule.recurs<Throwable>(10)
            .and(Schedule.spaced(100.milliseconds))
            .withMaxDuration(500.milliseconds, timeSource)

        var attempt1Count = 0
        try {
            coroutineScope {
                with(Computation<String> {
                    attempt1Count++
                    timeSource += 100.milliseconds
                    throw RuntimeException("fail")
                }.retry(statefulSchedule)) { execute() }
            }
        } catch (_: RuntimeException) {}

        val firstAttempts = attempt1Count

        var attempt2Count = 0
        try {
            coroutineScope {
                with(Computation<String> {
                    attempt2Count++
                    timeSource += 100.milliseconds
                    throw RuntimeException("fail")
                }.retry(statefulSchedule)) { execute() }
            }
        } catch (_: RuntimeException) {}

        assertTrue(
            attempt2Count < firstAttempts,
            "Reused schedule should have fewer retries: first=$firstAttempts, second=$attempt2Count"
        )
    }

    @Test
    fun `retry with schedule factory — fresh state each invocation`() = runTest {
        val timeSource = TestTimeSource()

        val scheduleFactory = {
            Schedule.recurs<Throwable>(10)
                .and(Schedule.spaced(100.milliseconds))
                .withMaxDuration(500.milliseconds, timeSource)
        }

        var attempt1Count = 0
        try {
            coroutineScope {
                with(Computation<String> {
                    attempt1Count++
                    timeSource += 100.milliseconds
                    throw RuntimeException("fail")
                }.retry(scheduleFactory)) { execute() }
            }
        } catch (_: RuntimeException) {}

        val timeSource2 = TestTimeSource()
        val scheduleFactory2 = {
            Schedule.recurs<Throwable>(10)
                .and(Schedule.spaced(100.milliseconds))
                .withMaxDuration(500.milliseconds, timeSource2)
        }

        var attempt2Count = 0
        try {
            coroutineScope {
                with(Computation<String> {
                    attempt2Count++
                    timeSource2 += 100.milliseconds
                    throw RuntimeException("fail")
                }.retry(scheduleFactory2)) { execute() }
            }
        } catch (_: RuntimeException) {}

        assertEquals(attempt1Count, attempt2Count, "Factory should give fresh state each time")
    }

    // ── Race edge cases ────────────────────────────────────────────────────

    @Test
    fun `race — both fail, first error propagates`() = runTest {
        val graph: Computation<String> = race(
            Computation { delay(10.milliseconds); throw RuntimeException("first") },
            Computation { delay(20.milliseconds); throw IllegalStateException("second") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("first", ex.message)
    }

    @Test
    fun `raceN — all fail, first error propagates`() = runTest {
        val graph: Computation<String> = raceN(
            Computation { delay(10.milliseconds); throw RuntimeException("a") },
            Computation { delay(20.milliseconds); throw IllegalStateException("b") },
            Computation { delay(30.milliseconds); throw UnsupportedOperationException("c") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("a", ex.message)
    }

    @Test
    fun `race — fast failure, slow success wins`() = runTest {
        val result = Async {
            race(
                Computation {
                    delay(10.milliseconds)
                    throw RuntimeException("fast fail")
                },
                Computation {
                    delay(50.milliseconds)
                    "slow success"
                },
            )
        }
        assertEquals("slow success", result)
    }

    @Test
    fun `raceN — single computation returns directly`() = runTest {
        val result = Async { raceN(Computation { "only one" }) }
        assertEquals("only one", result)
    }

    @Test
    fun `raceN — many racers, fastest success wins`() = runTest {
        val result = Async {
            raceN(
                Computation { delay(200.milliseconds); "slow1" },
                Computation { delay(300.milliseconds); "slow2" },
                Computation { delay(10.milliseconds); "fast winner" },
                Computation { delay(400.milliseconds); "slow3" },
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
                Computation<String> { throw CancellationException("one racer cancelled") },
                Computation { delay(10.milliseconds); "survivor" },
            )
        }
        assertEquals("survivor", result)
    }

    @Test
    fun `raceEither — different types, faster wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Computation { delay(100.milliseconds); 42 },
                fb = Computation { delay(10.milliseconds); "fast" },
            )
        }
        assertTrue(result is Either.Right)
        assertEquals("fast", (result as Either.Right).value)
    }

    @Test
    fun `raceEither — first fails, second wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Computation<Int> { throw RuntimeException("fail") },
                fb = Computation { delay(10.milliseconds); "ok" },
            )
        }
        assertTrue(result is Either.Right)
        assertEquals("ok", (result as Either.Right).value)
    }

    // ── PhaseBarrier edge cases ────────────────────────────────────────────

    @Test
    fun `followedBy — barrier signal fires even on failure`() = runTest {
        val failing: Computation<Int> = Computation { throw RuntimeException("barrier failed") }
        val graph = lift3 { a: Int, b: Int, c: Int -> a + b + c }
            .ap { 1 }
            .followedBy(failing)
            .ap { 3 }
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("barrier failed", ex.message)
    }

    @Test
    fun `followedBy — multiple barriers in sequence`() = runTest {
        val order = mutableListOf<String>()
        val result = Async {
            lift5 { a: String, b: String, c: String, d: String, e: String ->
                "$a|$b|$c|$d|$e"
            }
                .ap { order.add("a"); "a" }
                .followedBy { order.add("b"); "b" }
                .ap { order.add("c"); "c" }
                .followedBy { order.add("d"); "d" }
                .ap { order.add("e"); "e" }
        }
        assertEquals("a|b|c|d|e", result)
        assertTrue(order.indexOf("b") > order.indexOf("a"))
        assertTrue(order.indexOf("d") > order.indexOf("c"))
    }

    // ── Validated edge cases ───────────────────────────────────────────────

    @Test
    fun `apV — all three branches fail, all errors accumulated`() = runTest {
        val result = Async {
            zipV(
                { Either.Left("err1".toNonEmptyList()) as Either<NonEmptyList<String>, Int> },
                { Either.Left("err2".toNonEmptyList()) as Either<NonEmptyList<String>, Int> },
                { Either.Left("err3".toNonEmptyList()) as Either<NonEmptyList<String>, Int> },
            ) { a, b, c -> a + b + c }
        }
        assertTrue(result is Either.Left)
        val errors = (result as Either.Left).value
        assertEquals(3, errors.size)
        assertTrue(errors.contains("err1"))
        assertTrue(errors.contains("err2"))
        assertTrue(errors.contains("err3"))
    }

    @Test
    fun `traverseV — all succeed`() = runTest {
        val result = Async {
            listOf(1, 2, 3).traverseV { i ->
                Computation { Either.Right(i * 10) as Either<NonEmptyList<String>, Int> }
            }
        }
        assertTrue(result is Either.Right)
        assertEquals(listOf(10, 20, 30), (result as Either.Right).value)
    }

    @Test
    fun `traverseV — multiple failures accumulated`() = runTest {
        val result = Async {
            listOf(1, 2, 3, 4).traverseV { i ->
                Computation {
                    if (i % 2 == 0) Either.Left(NonEmptyList("err$i"))
                    else Either.Right(i * 10)
                }
            }
        }
        assertTrue(result is Either.Left)
        val errors = (result as Either.Left).value
        assertEquals(2, errors.size)
        assertTrue(errors.contains("err2"))
        assertTrue(errors.contains("err4"))
    }

    // ── Bracket edge cases ─────────────────────────────────────────────────

    @Test
    fun `bracket — release runs in NonCancellable even when parent is cancelled`() = runTest {
        var released = false
        val started = CompletableDeferred<Unit>()

        val job = launch {
            coroutineScope {
                with(bracket(
                    acquire = { "resource" },
                    use = { _ ->
                        Computation<String> {
                            started.complete(Unit)
                            delay(5.seconds)
                            "done"
                        }
                    },
                    release = { _ ->
                        delay(10.milliseconds)
                        released = true
                    },
                )) { execute() }
            }
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue(released, "Release should have run even after cancellation")
    }

    @Test
    fun `guaranteeCase — reports Cancelled on cancellation`() = runTest {
        var exitCase: ExitCase? = null
        val started = CompletableDeferred<Unit>()

        val graph = Computation<String> {
            started.complete(Unit)
            delay(5.seconds)
            "result"
        }.guaranteeCase { exitCase = it }

        val job = launch {
            coroutineScope { with(graph) { execute() } }
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue(exitCase is ExitCase.Cancelled)
    }

    // ── orElse edge cases ──────────────────────────────────────────────────

    @Test
    fun `orElse — CancellationException propagates, does not fall through`() = runTest {
        val graph = Computation<String> { throw CancellationException("cancel") }
            .orElse(Computation { "fallback" })
        assertFailsWith<CancellationException> {
            coroutineScope { with(graph) { execute() } }
        }
    }

    @Test
    fun `orElse — chains three computations, first two fail`() = runTest {
        val calls = mutableListOf<String>()
        val result = Async {
            Computation { calls.add("primary"); throw RuntimeException("p") }
                .orElse(Computation { calls.add("secondary"); throw RuntimeException("s") })
                .orElse(Computation { calls.add("tertiary"); "success" })
        }
        assertEquals("success", result)
        assertEquals(listOf("primary", "secondary", "tertiary"), calls)
    }

    @Test
    fun `firstSuccessOf — all fail, last error thrown`() = runTest {
        val graph: Computation<String> = firstSuccessOf(
            Computation { throw RuntimeException("a") },
            Computation { throw IllegalStateException("b") },
            Computation { throw UnsupportedOperationException("c") },
        )
        val ex = assertFailsWith<UnsupportedOperationException> { val r = Async { graph } }
        assertEquals("c", ex.message)
    }

    // ── ensure / ensureNotNull edge cases ──────────────────────────────────

    @Test
    fun `ensure — predicate failure throws, success passes through`() = runTest {
        val passGraph = pure(42).ensure({ IllegalStateException("too small") }) { it > 10 }
        assertEquals(42, Async { passGraph })

        val failGraph = pure(5).ensure({ IllegalStateException("too small") }) { it > 10 }
        assertFailsWith<IllegalStateException> { val r = Async { failGraph } }
    }

    @Test
    fun `ensureNotNull — null extraction throws`() = runTest {
        data class Wrapper(val inner: String?)

        val passGraph = pure(Wrapper("hello"))
            .ensureNotNull({ IllegalStateException("null!") }) { it.inner }
        assertEquals("hello", Async { passGraph })

        val failGraph = pure(Wrapper(null))
            .ensureNotNull({ IllegalStateException("null!") }) { it.inner }
        assertFailsWith<IllegalStateException> { val r = Async { failGraph } }
    }

    // ── memoizeOnSuccess edge cases ────────────────────────────────────────

    @Test
    fun `memoizeOnSuccess — cancellation does not poison cache`() = runTest {
        var executionCount = 0
        val latch = CompletableDeferred<Unit>()
        val computation = Computation<String> {
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

    // ── Computation.defer edge cases ───────────────────────────────────────

    @Test
    fun `defer — lazy construction prevents eager stack overflow`() = runTest {
        fun countdown(n: Int): Computation<Int> =
            if (n <= 0) pure(0)
            else Computation.defer { countdown(n - 1).map { it + 1 } }

        assertEquals(100, Async { countdown(100) })
    }

    // ── Computation.failed edge cases ──────────────────────────────────────

    @Test
    fun `failed — throws immediately, composes with recover`() = runTest {
        val graph = Computation.failed(RuntimeException("boom"))
            .recover { "recovered" }
        assertEquals("recovered", Async { graph })
    }

    // ── timeout edge cases ─────────────────────────────────────────────────

    @Test
    fun `timeout with default — null result not confused with timeout`() = runTest {
        val graph = Computation<String?> {
            delay(10.milliseconds)
            null
        }.timeout(100.milliseconds, "default")

        val result: String? = Async { graph }
        assertEquals(null, result)
    }

    @Test
    fun `timeout with computation fallback — fallback runs on timeout`() = runTest {
        val graph = Computation {
            delay(200.milliseconds)
            "slow"
        }.timeout(50.milliseconds, Computation { "fast fallback" })

        assertEquals("fast fallback", Async { graph })
    }
}
