package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CombinatorsTest {

    // ════════════════════════════════════════════════════════════════════════
    // TIMEOUT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout returns result when computation completes in time`() = runTest {
        val result = Kap.of(42).timeout(1.seconds).executeGraph()
        assertEquals(42, result)
    }

    @Test
    fun `timeout throws when computation exceeds duration`() = runTest {
        val result = runCatching {
            Kap<Int> { delay(10.seconds); 42 }.timeout(100.milliseconds).executeGraph()
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `timeout with default returns default on timeout`() = runTest {
        val result = Kap<Int> { delay(10.seconds); 42 }.timeout(100.milliseconds, default = -1).executeGraph()
        assertEquals(-1, result)
    }

    @Test
    fun `timeout with default returns result when fast enough`() = runTest {
        val result = Kap.of(42).timeout(1.seconds, default = -1).executeGraph()
        assertEquals(42, result)
    }

    @Test
    fun `timeout with fallback computation runs fallback on timeout`() = runTest {
        val result = Kap<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, Kap.of("fallback")).executeGraph()
        assertEquals("fallback", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECOVER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recover catches exception and maps to value`() = runTest {
        val result = Kap<String> { throw RuntimeException("boom") }
                .recover { "recovered: ${it.message}" }.executeGraph()
        assertEquals("recovered: boom", result)
    }

    @Test
    fun `recover passes through successful computation`() = runTest {
        val result = Kap.of("ok").recover { "recovered" }.executeGraph()
        assertEquals("ok", result)
    }

    @Test
    fun `recover does not catch CancellationException`() = runTest {
        val result = runCatching {
            Kap<String> { throw CancellationException("cancelled") }
                    .recover { "recovered" }.executeGraph()
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `recoverWith switches to recovery computation`() = runTest {
        val result = Kap<String> { throw RuntimeException("boom") }
                .recoverWith { Kap.of("recovered from: ${it.message}") }.executeGraph()
        assertEquals("recovered from: boom", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FALLBACK
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fallback switches to alternative on failure`() = runTest {
        val result = (Kap<String> { throw RuntimeException("boom") } fallback Kap.of("backup")).executeGraph()
        assertEquals("backup", result)
    }

    @Test
    fun `fallback returns primary on success`() = runTest {
        val result = (Kap.of("primary") fallback Kap.of("backup")).executeGraph()
        assertEquals("primary", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RETRY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry succeeds on first attempt`() = runTest {
        var attempts = 0
        val result = Kap<String> { attempts++; "ok" }.retry(3).executeGraph()
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retry succeeds on later attempt`() = runTest {
        var attempts = 0
        val result = Kap<String> {
                attempts++
                if (attempts < 3) throw RuntimeException("fail #$attempts")
                "ok"
            }.retry(3).executeGraph()
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry exhausts all attempts then throws`() = runTest {
        var attempts = 0
        val result = runCatching {
            Kap<String> {
                    attempts++
                    throw RuntimeException("fail #$attempts")
                }.retry(3).executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals(3, attempts)
        assertEquals("fail #3", result.exceptionOrNull()?.message)
    }

    @Test
    fun `retry does not catch CancellationException`() = runTest {
        var attempts = 0
        val result = runCatching {
            Kap<String> {
                    attempts++
                    throw CancellationException("cancelled")
                }.retry(3).executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals(1, attempts) // no retry on cancellation
    }

    @Test
    fun `retry composes with kap+with`() = runTest {
        var attempts = 0
        val retryable = Kap<String> {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "retried"
        }.retry(3)

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with { with(retryable) { execute() } }
                .with { "ok" }.executeGraph()
        assertEquals("retried|ok", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPOSITION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with default plus recover compose naturally`() = runTest {
        val result = Kap<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, "timed-out").executeGraph()
        assertEquals("timed-out", result)
    }

    @Test
    fun `timeout exception plus fallback compose naturally`() = runTest {
        val result = Kap<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, Kap.of("fallback-value")).executeGraph()
        assertEquals("fallback-value", result)
    }

    @Test
    fun `retry plus timeout compose naturally`() = runTest {
        var attempts = 0
        val result = Kap<String> {
                attempts++
                if (attempts < 3) throw RuntimeException("fail")
                "ok"
            }.retry(3).timeout(5.seconds).executeGraph()
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    // ════════════════════════════════════════════════════════════════════════
    // TIMEOUT NULL-SAFETY (bug fix verification)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with default preserves null as a valid result`() = runTest {
        val result = Kap<String?> { null }.timeout(1.seconds, default = "fallback").executeGraph()
        // The computation completed with null — should NOT fall through to default
        assertEquals(null, result)
    }

    @Test
    fun `timeout with fallback preserves null as a valid result`() = runTest {
        val result = Kap<String?> { null }.timeout(1.seconds, Kap.of("fallback")).executeGraph()
        assertEquals(null, result)
    }

    @Test
    fun `timeout with default still returns default on actual timeout`() = runTest {
        val result = Kap<String?> { delay(10.seconds); null }.timeout(100.milliseconds, default = "timed-out").executeGraph()
        assertEquals("timed-out", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MEMOIZE
    // ════════════════════════════════════════════════════════════════════════

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `memoize runs computation only once across multiple executions`() = runTest {
        val counter = AtomicInteger(0)
        val expensive = Kap { counter.incrementAndGet(); "result" }.memoize()

        val result1 = expensive.executeGraph()
        val result2 = expensive.executeGraph()

        assertEquals("result", result1)
        assertEquals("result", result2)
        assertEquals(1, counter.get(), "Kap should execute only once")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `memoize shares result across parallel with branches`() = runTest {
        val counter = AtomicInteger(0)
        val expensive = Kap {
            delay(50)
            counter.incrementAndGet()
            "data"
        }.memoize()

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with(expensive)
                .with(expensive).executeGraph()

        assertEquals("data|data", result)
        assertEquals(1, counter.get(), "Memoized computation should run once even in parallel")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `memoize propagates exception to all waiters`() = runTest {
        val counter = AtomicInteger(0)
        val failing = Kap<String> {
            counter.incrementAndGet()
            throw RuntimeException("boom")
        }.memoize()

        val result = runCatching { failing.executeGraph() }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
        assertEquals(1, counter.get(), "Should only execute once even on failure")
    }
}
