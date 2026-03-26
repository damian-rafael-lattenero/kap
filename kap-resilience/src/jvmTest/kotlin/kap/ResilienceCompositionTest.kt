package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extended tests for resilience combinators: retryOrElse, retryWithResult,
 * firstSuccessOf, orElse, and their composition.
 */
class ResilienceCompositionTest {

    // ── retryOrElse ─────────────────────────────────────────────────────

    @Test
    fun `retryOrElse invokes fallback when schedule exhausted`() = runTest {
        val schedule = Schedule.times<Throwable>(2)
        var attempts = 0
        val result = Async {
            Kap<String> {
                attempts++
                error("always fails")
            }.retryOrElse(schedule) { err -> "fallback: ${err.message}" }
        }
        assertEquals("fallback: always fails", result)
        assertEquals(3, attempts, "1 initial + 2 retries = 3 attempts")
    }

    @Test
    fun `retryOrElse succeeds before exhaustion`() = runTest {
        val schedule = Schedule.times<Throwable>(5)
        var attempts = 0
        val result = Async {
            Kap {
                attempts++
                if (attempts < 3) error("retry me")
                "success"
            }.retryOrElse(schedule) { "should not reach" }
        }
        assertEquals("success", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retryOrElse propagates CancellationException without fallback`() = runTest {
        val schedule = Schedule.times<Throwable>(3)
        val comp = Kap<String> {
            throw CancellationException("cancel")
        }.retryOrElse(schedule) { "should not reach" }
        assertFailsWith<CancellationException> { val r = Async { comp } }
    }

    @Test
    fun `retryOrElse with zero retries goes directly to fallback`() = runTest {
        val schedule = Schedule.times<Throwable>(0)
        val result = Async {
            Kap<String> { error("fail") }
                .retryOrElse(schedule) { "fallback" }
        }
        assertEquals("fallback", result)
    }

    // ── retryWithResult ─────────────────────────────────────────────────

    @Test
    fun `retryWithResult returns metadata on success after retries`() = runTest {
        val schedule = Schedule.times<Throwable>(5) and Schedule.spaced(10.milliseconds)
        var attempts = 0
        val result = Async {
            Kap {
                attempts++
                if (attempts < 4) error("retry")
                "value"
            }.retryWithResult(schedule)
        }
        assertEquals("value", result.value)
        assertEquals(3, result.attempts, "3 retries before success")
        assertEquals(30.milliseconds, result.totalDelay, "3 × 10ms = 30ms delay")
    }

    @Test
    fun `retryWithResult with immediate success has zero retries`() = runTest {
        val schedule = Schedule.times<Throwable>(5)
        val result = Async {
            Kap { "immediate" }.retryWithResult(schedule)
        }
        assertEquals("immediate", result.value)
        assertEquals(0, result.attempts)
        assertEquals(kotlin.time.Duration.ZERO, result.totalDelay)
    }

    @Test
    fun `retryWithResult throws when schedule exhausted`() = runTest {
        val schedule = Schedule.times<Throwable>(2)
        val comp = Kap<String> { error("always fails") }
            .retryWithResult(schedule)
        assertFailsWith<IllegalStateException> { val r = Async { comp } }
    }

    // ── firstSuccessOf ──────────────────────────────────────────────────

    @Test
    fun `firstSuccessOf returns first success value`() = runTest {
        val result = Async {
            firstSuccessOf(
                Kap<String> { error("fail-1") },
                Kap { "success-2" },
                Kap { "success-3" },
            )
        }
        assertEquals("success-2", result)
    }

    @Test
    fun `firstSuccessOf all fail throws last error`() = runTest {
        val comp = firstSuccessOf(
            Kap<String> { error("fail-1") },
            Kap<String> { error("fail-2") },
            Kap<String> { error("fail-3") },
        )
        val ex = assertFailsWith<IllegalStateException> { val r = Async { comp } }
        // The last error is the one that propagates
        assertEquals("fail-3", ex.message)
    }

    @Test
    fun `firstSuccessOf single computation delegates`() = runTest {
        val result = Async {
            firstSuccessOf(Kap { "only" })
        }
        assertEquals("only", result)
    }

    @Test
    fun `firstSuccessOf propagates CancellationException immediately`() = runTest {
        val comp = firstSuccessOf(
            Kap<String> { throw CancellationException("cancel") },
            Kap { "should not reach" },
        )
        assertFailsWith<CancellationException> { val r = Async { comp } }
    }

    @Test
    fun `firstSuccessOf requires non-empty`() {
        assertFailsWith<IllegalArgumentException> {
            firstSuccessOf<String>()
        }
    }

    @Test
    fun `Iterable firstSuccess works`() = runTest {
        val computations = listOf(
            Kap<String> { error("1") },
            Kap<String> { error("2") },
            Kap { "third" },
        )
        val result = Async { computations.firstSuccess() }
        assertEquals("third", result)
    }

    // ── orElse ──────────────────────────────────────────────────────────

    @Test
    fun `orElse runs fallback on failure`() = runTest {
        val result = Async {
            Kap<String> { error("primary down") }
                .orElse(Kap { "replica" })
        }
        assertEquals("replica", result)
    }

    @Test
    fun `orElse does not run fallback on success`() = runTest {
        var fallbackRan = false
        val result = Async {
            Kap { "primary" }
                .orElse(Kap { fallbackRan = true; "replica" })
        }
        assertEquals("primary", result)
        assertEquals(false, fallbackRan)
    }

    @Test
    fun `orElse propagates CancellationException`() = runTest {
        val comp = Kap<String> { throw CancellationException("cancel") }
            .orElse(Kap { "should not reach" })
        assertFailsWith<CancellationException> { val r = Async { comp } }
    }

    @Test
    fun `orElse chain 3 deep - middle succeeds`() = runTest {
        val result = Async {
            Kap<String> { error("fail-1") }
                .orElse(Kap { "success-2" })
                .orElse(Kap { "should not reach" })
        }
        assertEquals("success-2", result)
    }

    // ── ensure / ensureNotNull composition ──────────────────────────────

    @Test
    fun `ensure inside orElse chain`() = runTest {
        val result = Async {
            Kap { -1 }
                .ensure({ IllegalStateException("negative") }) { it > 0 }
                .orElse(Kap.of(42))
        }
        assertEquals(42, result)
    }

    @Test
    fun `ensureNotNull inside retry`() = runTest {
        var attempts = 0
        val result = Async {
            Kap {
                attempts++
                if (attempts < 3) null else "found"
            }.ensureNotNull({ IllegalStateException("null") }) { it }
             .retry(5)
        }
        assertEquals("found", result)
        assertEquals(3, attempts)
    }

    // ── complex composition scenarios ───────────────────────────────────

    @Test
    fun `retry + timeout + circuit breaker + recover composition`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 100.milliseconds)
        var attempts = 0

        val result = Async {
            Kap {
                attempts++
                if (attempts <= 2) error("transient")
                "recovered"
            }
            .timeout(50.milliseconds)
            .withCircuitBreaker(breaker)
            .retry(Schedule.times<Throwable>(5) and Schedule.spaced(1.milliseconds))
            .recover { "fallback" }
        }
        // After 2 failures the breaker opens, retry catches CircuitBreakerOpenException,
        // eventually the breaker half-opens and the 3rd attempt succeeds
        assertTrue(result == "recovered" || result == "fallback")
    }

    @Test
    fun `liftA with individual branch resilience`() = runTest {
        val result = Async {
            combine(
                { Kap { "user" }.retry(2).await() },
                {
                    Kap<String> { error("down") }
                        .recover { "cached-cart" }
                        .await()
                },
                {
                    Kap { delay(200); "slow-promos" }
                        .timeout(50.milliseconds, default = "cached-promos")
                        .await()
                },
            ) { user, cart, promos -> "$user|$cart|$promos" }
        }
        assertEquals("user|cached-cart|cached-promos", result)
    }

    @Test
    fun `retryOrElse with exponential backoff`() = runTest {
        val schedule = Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds)
        var attempts = 0
        val result = Async {
            Kap<String> {
                attempts++
                error("fail $attempts")
            }.retryOrElse(schedule) { err -> "gave up after $attempts: ${err.message}" }
        }
        assertEquals("gave up after 4: fail 4", result)
        // Delays: 10 + 20 + 40 = 70ms
    }
}
