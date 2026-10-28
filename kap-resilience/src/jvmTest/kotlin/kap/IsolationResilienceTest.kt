package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Isolation law for the resilience operators + the 4.1.0 public breaker API (SPEC F-01, F-03, F-06). */
@OptIn(ExperimentalCoroutinesApi::class)
class IsolationResilienceTest {

    private class ChildFailed : RuntimeException("child failed")

    private fun nested(): Kap<List<Int>> = listOf(1, 2).traverse { n ->
        Kap { if (n == 2) throw ChildFailed() else n }
    }

    @Test
    fun `schedule retry retries a nested parallel failure and surfaces the original error`() = runTest {
        var attempts = 0
        val flaky = listOf(1, 2).traverse { n -> Kap { if (n == 2 && attempts++ < 1) throw ChildFailed() else n } }
        assertEquals(listOf(1, 2), flaky.retry(Schedule.times(2)).evalGraph())
        assertFailsWith<ChildFailed> { nested().retry(Schedule.times(1)).evalGraph() }
    }

    @Test
    fun `retryOrElse and retryWithResult convert a nested parallel failure`() = runTest {
        assertEquals(listOf(-1), nested().retryOrElse(Schedule.times(1)) { listOf(-1) }.evalGraph())
        var attempts = 0
        val flaky = listOf(1, 2).traverse { n -> Kap { if (n == 2 && attempts++ < 1) throw ChildFailed() else n } }
        val r = flaky.retryWithResult(Schedule.times(2)).evalGraph()
        assertEquals(listOf(1, 2), r.value)
        assertEquals(1, r.attempts) // retries, not total calls (KDoc: 0 = succeeded on first try)
    }

    @Test
    fun `withCircuitBreaker records a nested parallel failure and opens`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 1, resetTimeout = 1.minutes)
        assertFailsWith<ChildFailed> { nested().withCircuitBreaker(breaker).evalGraph() }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertFailsWith<CircuitBreakerOpenException> { Kap.of(1).withCircuitBreaker(breaker).evalGraph() }
    }

    @Test
    fun `the public breaker API guards plain suspend code`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 1.minutes)
        assertTrue(breaker.tryAcquire())
        assertEquals("ok", breaker.protect { "ok" })
        repeat(2) { assertFailsWith<IllegalStateException> { breaker.protect { error("boom") } } }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertFailsWith<CircuitBreakerOpenException> { breaker.protect { "unreachable" } }
        assertTrue(!breaker.tryAcquire())
        breaker.onSuccess()
        breaker.onFailure()
    }

    @Test
    fun `KapLike wrappers can apply resilience mid-chain`() = runTest {
        val wrapper = object : KapLike<Int> {
            override val asKap: Kap<Int> = Kap { 21 }
        }
        assertEquals(21, wrapper.retry(Schedule.times(1)).evalGraph())
        assertEquals(21, wrapper.withCircuitBreaker(CircuitBreaker(1, 1.seconds)).evalGraph())
        assertEquals(21, wrapper.timeoutRace(100.milliseconds, Kap.of(0)).evalGraph())
        assertEquals(21, wrapper.timeoutAsFailure(1.seconds).evalGraph())
        assertEquals(21, (wrapper orElse Kap.of(0)).evalGraph())
    }
}
