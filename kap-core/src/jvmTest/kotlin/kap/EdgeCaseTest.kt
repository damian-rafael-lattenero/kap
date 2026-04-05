package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Edge case tests for subtle concurrency scenarios that could break
 * in production but are easy to miss in happy-path testing.
 *
 * Categories:
 * 1. Barrier failure propagation — then throws, post-barrier with cancelled
 * 2. Memoize — caching, failure caching, and retry-on-failure
 * 3. Race + CancellationException — internal timeout in a racer
 * 4. PhaseBarrier signal lifecycle — chained barriers, signal on exception
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EdgeCaseTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. BARRIER FAILURE: then throws → structured concurrency cleanup
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `then failure propagates the barrier exception`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                    .with { delay(10); "A" }
                    .then(Kap<String> { throw RuntimeException("barrier failed") })
                    .with { delay(10); "C" }.evalGraph()
        }
        assertTrue(result.isFailure)
        assertEquals("barrier failed", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `then failure with concurrent with - exception propagates cleanly`() = runTest {
        // Proves that when a barrier fails, the whole computation fails
        // even if there are concurrent with branches running.
        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                    .with { delay(200); "A" }
                    .then(Kap<String> { delay(10); throw RuntimeException("boom") })
                    .with { delay(100); "C" }.evalGraph()
        }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()!!.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. MEMOIZE: caching, failure caching, retry-on-failure
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `memoize - second caller reuses cached result`() = runTest {
        var callCount = 0
        val comp: Kap<String> = Kap {
            callCount++
            delay(10)
            "result-$callCount"
        }.memoize()

        val first = comp.evalGraph()
        assertEquals("result-1", first)
        assertEquals(1, callCount)

        val second = comp.evalGraph()
        assertEquals("result-1", second)
        assertEquals(1, callCount, "Should not re-execute — result is cached")
    }

    @Test
    fun `memoizeOnSuccess - retries after failure, caches after success`() = runTest {
        var callCount = 0
        val comp: Kap<String> = Kap {
            callCount++
            if (callCount == 1) throw RuntimeException("transient failure")
            "success-$callCount"
        }.memoizeOnSuccess()

        // First call fails
        assertFailsWith<RuntimeException> { comp.evalGraph(); Unit }
        assertEquals(1, callCount)

        // Second call retries and succeeds
        val result = comp.evalGraph()
        assertEquals("success-2", result)
        assertEquals(2, callCount)

        // Third call returns cached success
        val cached = comp.evalGraph()
        assertEquals("success-2", cached)
        assertEquals(2, callCount, "Should not re-execute — success is cached")
    }

    @Test
    fun `memoize caches failure - subsequent calls get same error`() = runTest {
        var callCount = 0
        val comp: Kap<Nothing> = Kap {
            callCount++
            throw RuntimeException("permanent failure #$callCount")
        }.memoize()

        val ex1 = assertFailsWith<RuntimeException> { comp.evalGraph() }
        assertEquals("permanent failure #1", ex1.message)
        assertEquals(1, callCount)

        val ex2 = assertFailsWith<RuntimeException> { comp.evalGraph() }
        assertEquals("permanent failure #1", ex2.message)
        assertEquals(1, callCount, "Should not retry — failure is cached in memoize()")
    }

    @Test
    fun `memoize used in parallel with branches executes only once`() = runTest {
        var callCount = 0
        val shared: Kap<String> = Kap {
            callCount++
            delay(50)
            "shared-$callCount"
        }.memoize()

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with(shared)
                .with(shared).evalGraph()
        assertEquals("shared-1|shared-1", result)
        assertEquals(1, callCount, "Memoized computation should execute only once even in parallel")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. RACE + CANCELLATION: internal timeout in a racer
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race - slow racer with internal timeout loses to fast racer`() = runTest {
        val result = race(
                Kap {
                    withTimeout(10) { delay(100); "slow" }
                },
                Kap { delay(5); "fast" },
            ).evalGraph()
        assertEquals("fast", result)
        assertEquals(5, currentTime, "Fast racer wins at 5ms")
    }

    @Test
    fun `race - both fail, exception propagates`() = runTest {
        val result = runCatching {
            race(
                    Kap { delay(10); throw RuntimeException("err-A") },
                    Kap { delay(20); throw RuntimeException("err-B") },
                ).evalGraph()
        }
        assertTrue(result.isFailure, "Race with all failures should fail")
        val ex = result.exceptionOrNull()!!
        assertTrue(ex is RuntimeException, "Should be RuntimeException, got ${ex::class}")
    }

    @Test
    fun `raceN - one succeeds among multiple failures`() = runTest {
        val result = raceN(
                Kap { delay(10); throw RuntimeException("fail-1") },
                Kap { delay(20); throw RuntimeException("fail-2") },
                Kap { delay(15); "winner" },
            ).evalGraph()
        assertEquals("winner", result)
        assertEquals(15, currentTime, "Winner completes at 15ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. PHASE BARRIER SIGNAL LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple then barriers chain correctly`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String ->
                "$a|$b|$c|$d|$e"
            } } } } }
                .with { delay(20); "A" }
                .then { delay(10); "B" }
                .then { delay(10); "C" }
                .then { delay(10); "D" }
                .then { delay(10); "E" }.evalGraph()
        assertEquals("A|B|C|D|E", result)
        assertEquals(60, currentTime, "Sequential barriers: 20+10+10+10+10=60ms")
    }

    @Test
    fun `ap after multiple barriers launches only after last barrier`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> "$a|$b|$c|$d" } } } }
                .with { delay(20); "A" }
                .then { delay(20); "B" }
                .then { delay(20); "C" }
                .with { delay(20); "D" }.evalGraph()
        assertEquals("A|B|C|D", result)
        assertEquals(80, currentTime, "D waits for both barriers: 20+20+20+20=80ms")
    }

}
