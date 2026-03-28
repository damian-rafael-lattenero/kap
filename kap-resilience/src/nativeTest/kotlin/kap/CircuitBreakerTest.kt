package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class CircuitBreakerTest {

    @Test
    fun `stays closed on success`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds)
        repeat(10) {
            assertEquals("ok", Async { Kap { "ok" }.withCircuitBreaker(breaker) })
        }
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `opens after maxFailures consecutive failures`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds)
        val comp = Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker)

        repeat(3) { assertTrue(runCatching { Async { comp } }.isFailure) }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        // Next call fails fast with CircuitBreakerOpenException
        val r = runCatching { Async { comp } }
        assertTrue(r.exceptionOrNull() is CircuitBreakerOpenException)
    }

    @Test
    fun `success resets failure count`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds)
        var callCount = 0
        val comp = Kap {
            callCount++
            if (callCount % 3 == 0) "ok" else throw RuntimeException("fail")
        }.withCircuitBreaker(breaker)

        runCatching { Async { comp } } // fail
        runCatching { Async { comp } } // fail
        assertEquals("ok", Async { comp }) // success, resets
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)

        runCatching { Async { comp } } // fail
        runCatching { Async { comp } } // fail
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState) // still closed
    }

    @Test
    fun `transitions Open to HalfOpen after resetTimeout`() = runTest {
        val timeSource = TestTimeSource()
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 500.milliseconds, timeSource = timeSource)
        val comp = Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker)

        repeat(2) { runCatching { Async { comp } } }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        timeSource += 400.milliseconds
        assertTrue(runCatching { Async { comp } }.exceptionOrNull() is CircuitBreakerOpenException)

        timeSource += 200.milliseconds
        // Now allows probe (which fails → reopens)
        val probeResult = runCatching { Async { comp } }
        assertTrue(probeResult.exceptionOrNull() is RuntimeException)
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
    }

    @Test
    fun `HalfOpen probe success closes the circuit`() = runTest {
        val timeSource = TestTimeSource()
        var shouldFail = true
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 500.milliseconds, timeSource = timeSource)
        val comp = Kap {
            if (shouldFail) throw RuntimeException("fail"); "recovered"
        }.withCircuitBreaker(breaker)

        repeat(2) { runCatching { Async { comp } } }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        shouldFail = false
        timeSource += 600.milliseconds
        assertEquals("recovered", Async { comp })
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `onStateChange fires on transitions`() = runTest {
        val transitions = mutableListOf<Pair<CircuitBreaker.State, CircuitBreaker.State>>()
        val timeSource = TestTimeSource()
        val breaker = CircuitBreaker(
            maxFailures = 2, resetTimeout = 100.milliseconds, timeSource = timeSource,
            onStateChange = { from, to -> transitions.add(from to to) },
        )
        var shouldFail = true
        val comp = Kap {
            if (shouldFail) throw RuntimeException("fail"); "ok"
        }.withCircuitBreaker(breaker)

        repeat(2) { runCatching { Async { comp } } }
        assertEquals(listOf(CircuitBreaker.State.Closed to CircuitBreaker.State.Open), transitions)

        shouldFail = false
        timeSource += 200.milliseconds
        assertEquals("ok", Async { comp })
        assertEquals(3, transitions.size)
        assertEquals(CircuitBreaker.State.Open to CircuitBreaker.State.HalfOpen, transitions[1])
        assertEquals(CircuitBreaker.State.HalfOpen to CircuitBreaker.State.Closed, transitions[2])
    }

    @Test
    fun `parallel with branches — latch barrier proof`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 1.seconds)
        val latch1 = CompletableDeferred<Unit>()
        val latch2 = CompletableDeferred<Unit>()

        val compA = Kap {
            latch1.complete(Unit); latch2.await(); "A"
        }.withCircuitBreaker(breaker)
        val compB = Kap {
            latch2.complete(Unit); latch1.await(); "B"
        }.withCircuitBreaker(breaker)

        val graph = kap { a: String, b: String -> "$a|$b" }.with(compA).with(compB)
        assertEquals("A|B", Async { graph })
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `full resilience stack — retry + recover + circuit breaker`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 1.seconds)
        var callCount = 0
        val graph = Kap {
            callCount++
            if (callCount < 3) throw RuntimeException("transient"); "success"
        }.withCircuitBreaker(breaker).retry(3).recover { "fallback" }

        assertEquals("success", Async { graph })
        assertEquals(3, callCount)
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `shared breaker fast-fails when open`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 10.seconds)
        val fail = Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker)
        repeat(2) { runCatching { Async { fail } } }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        val compA = Kap<String> { error("nope") }.withCircuitBreaker(breaker).recover { "fast-a" }
        val compB = Kap<String> { error("nope") }.withCircuitBreaker(breaker).recover { "fast-b" }
        val graph = kap { a: String, b: String -> "$a|$b" }.with(compA).with(compB)
        assertEquals("fast-a|fast-b", Async { graph })
    }

    @Test
    fun `composes with timeout`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds)
        val graph = Kap { "fast" }.timeout(100.milliseconds).withCircuitBreaker(breaker)
        assertEquals("fast", Async { graph })
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `Schedule retry exhausts then opens circuit`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 1.seconds)
        var callCount = 0
        val graph = Kap<String> {
            callCount++; throw RuntimeException("always fails")
        }.withCircuitBreaker(breaker).retry(Schedule.times(4))

        assertTrue(runCatching { Async { graph } }.isFailure)
        assertEquals(5, callCount)
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
    }
}
