package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
 * 1. Barrier failure propagation — followedBy throws, post-barrier with cancelled
 * 2. Memoize — caching, failure caching, and retry-on-failure
 * 3. Race + CancellationException — internal timeout in a racer
 * 4. PhaseBarrier signal lifecycle — chained barriers, signal on exception
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EdgeCaseTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. BARRIER FAILURE: followedBy throws → structured concurrency cleanup
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `followedBy failure propagates the barrier exception`() = runTest {
        val result = runCatching {
            Async {
                kap<String, String, String, String> { a, b, c -> "$a|$b|$c" }
                    .with { delay(10); "A" }
                    .followedBy(Computation<String> { throw RuntimeException("barrier failed") })
                    .with { delay(10); "C" }
            }
        }
        assertTrue(result.isFailure)
        assertEquals("barrier failed", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `followedBy failure with concurrent with - exception propagates cleanly`() = runTest {
        // Proves that when a barrier fails, the whole computation fails
        // even if there are concurrent with branches running.
        val result = runCatching {
            Async {
                kap<String, String, String, String> { a, b, c -> "$a|$b|$c" }
                    .with { delay(200); "A" }
                    .followedBy(Computation<String> { delay(10); throw RuntimeException("boom") })
                    .with { delay(100); "C" }
            }
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
        val comp: Computation<String> = Computation {
            callCount++
            delay(10)
            "result-$callCount"
        }.memoize()

        val first = Async { comp }
        assertEquals("result-1", first)
        assertEquals(1, callCount)

        val second = Async { comp }
        assertEquals("result-1", second)
        assertEquals(1, callCount, "Should not re-execute — result is cached")
    }

    @Test
    fun `memoizeOnSuccess - retries after failure, caches after success`() = runTest {
        var callCount = 0
        val comp: Computation<String> = Computation {
            callCount++
            if (callCount == 1) throw RuntimeException("transient failure")
            "success-$callCount"
        }.memoizeOnSuccess()

        // First call fails
        assertFailsWith<RuntimeException> { Async { comp }; Unit }
        assertEquals(1, callCount)

        // Second call retries and succeeds
        val result = Async { comp }
        assertEquals("success-2", result)
        assertEquals(2, callCount)

        // Third call returns cached success
        val cached = Async { comp }
        assertEquals("success-2", cached)
        assertEquals(2, callCount, "Should not re-execute — success is cached")
    }

    @Test
    fun `memoize caches failure - subsequent calls get same error`() = runTest {
        var callCount = 0
        val comp: Computation<Nothing> = Computation {
            callCount++
            throw RuntimeException("permanent failure #$callCount")
        }.memoize()

        val ex1 = assertFailsWith<RuntimeException> { Async { comp } }
        assertEquals("permanent failure #1", ex1.message)
        assertEquals(1, callCount)

        val ex2 = assertFailsWith<RuntimeException> { Async { comp } }
        assertEquals("permanent failure #1", ex2.message)
        assertEquals(1, callCount, "Should not retry — failure is cached in memoize()")
    }

    @Test
    fun `memoize used in parallel with branches executes only once`() = runTest {
        var callCount = 0
        val shared: Computation<String> = Computation {
            callCount++
            delay(50)
            "shared-$callCount"
        }.memoize()

        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with(shared)
                .with(shared)
        }
        assertEquals("shared-1|shared-1", result)
        assertEquals(1, callCount, "Memoized computation should execute only once even in parallel")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. RACE + CANCELLATION: internal timeout in a racer
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race - slow racer with internal timeout loses to fast racer`() = runTest {
        val result = Async {
            race(
                Computation {
                    withTimeout(10) { delay(100); "slow" }
                },
                Computation { delay(5); "fast" },
            )
        }
        assertEquals("fast", result)
        assertEquals(5, currentTime, "Fast racer wins at 5ms")
    }

    @Test
    fun `race - both fail, exception propagates`() = runTest {
        val result = runCatching {
            Async {
                race(
                    Computation { delay(10); throw RuntimeException("err-A") },
                    Computation { delay(20); throw RuntimeException("err-B") },
                )
            }
        }
        assertTrue(result.isFailure, "Race with all failures should fail")
        val ex = result.exceptionOrNull()!!
        assertTrue(ex is RuntimeException, "Should be RuntimeException, got ${ex::class}")
    }

    @Test
    fun `raceN - one succeeds among multiple failures`() = runTest {
        val result = Async {
            raceN(
                Computation { delay(10); throw RuntimeException("fail-1") },
                Computation { delay(20); throw RuntimeException("fail-2") },
                Computation { delay(15); "winner" },
            )
        }
        assertEquals("winner", result)
        assertEquals(15, currentTime, "Winner completes at 15ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. PHASE BARRIER SIGNAL LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `multiple followedBy barriers chain correctly`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String, d: String, e: String ->
                "$a|$b|$c|$d|$e"
            }
                .with { delay(20); "A" }
                .followedBy { delay(10); "B" }
                .followedBy { delay(10); "C" }
                .followedBy { delay(10); "D" }
                .followedBy { delay(10); "E" }
        }
        assertEquals("A|B|C|D|E", result)
        assertEquals(60, currentTime, "Sequential barriers: 20+10+10+10+10=60ms")
    }

    @Test
    fun `ap after multiple barriers launches only after last barrier`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
                .with { delay(20); "A" }
                .followedBy { delay(20); "B" }
                .followedBy { delay(20); "C" }
                .with { delay(20); "D" }
        }
        assertEquals("A|B|C|D", result)
        assertEquals(80, currentTime, "D waits for both barriers: 20+20+20+20=80ms")
    }

}
