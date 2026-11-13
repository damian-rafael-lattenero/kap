package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * The rejection names the breaker and its remaining window; the observation
 * never mutates. (The observability half of CircuitBreakerTest.)
 */
class CircuitBreakerObservabilityTest {

    @Test
    fun `the rejection carries the breaker name and the remaining window`() = runTest {
        val timeSource = TestTimeSource()
        val breaker = CircuitBreaker(
            maxFailures = 1,
            retryAfter = 30.seconds,
            name = "payment",
            timeSource = timeSource,
        )

        runCatching { Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker).evalGraph() }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        val e = assertFailsWith<CircuitBreakerOpenException> {
            Kap { "x" }.withCircuitBreaker(breaker).evalGraph()
        }
        assertEquals("payment", e.name)
        assertEquals(CircuitBreaker.State.Open, e.state)
        assertTrue(e.remaining != null && e.remaining > kotlin.time.Duration.ZERO)
        assertTrue(e.message!!.contains("payment"))
        assertTrue(e.message!!.contains("retry allowed in"))
    }

    @Test
    fun `the remaining window counts down inside the open state`() = runTest {
        val timeSource = TestTimeSource()
        val breaker = CircuitBreaker(
            maxFailures = 1,
            retryAfter = 30.seconds,
            name = "email",
            timeSource = timeSource,
        )

        runCatching { Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker).evalGraph() }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        val e1 = assertFailsWith<CircuitBreakerOpenException> {
            Kap { "x" }.withCircuitBreaker(breaker).evalGraph()
        }
        assertEquals(30.seconds, e1.remaining)

        timeSource += 10.seconds
        val e2 = assertFailsWith<CircuitBreakerOpenException> {
            Kap { "x" }.withCircuitBreaker(breaker).evalGraph()
        }
        assertEquals(20.seconds, e2.remaining)
        assertTrue(e2.message!!.contains("retry allowed in"))
    }

    @Test
    fun `a half-open rejection says a probe is in flight`() = runTest {
        val timeSource = TestTimeSource()
        val probeGate = CompletableDeferred<Unit>()
        val breaker = CircuitBreaker(
            maxFailures = 1,
            retryAfter = 100.milliseconds,
            name = "probe",
            timeSource = timeSource,
        )

        runCatching { Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker).evalGraph() }
        timeSource += 200.milliseconds

        // The in-flight probe occupies the HalfOpen slot (UNDISPATCHED: it must
        // reach its suspension before the main body's next call); the second call is rejected.
        val probe = launch(start = CoroutineStart.UNDISPATCHED) {
            Kap<Unit> { probeGate.await() }.withCircuitBreaker(breaker).evalGraph()
        }
        val e = assertFailsWith<CircuitBreakerOpenException> {
            Kap { "x" }.withCircuitBreaker(breaker).evalGraph()
        }
        assertEquals("probe", e.name)
        assertEquals(CircuitBreaker.State.HalfOpen, e.state)
        assertEquals(null, e.remaining)
        assertTrue(e.message!!.contains("half-open"))

        probeGate.complete(Unit)
        probe.join()
    }

    @Test
    fun `observedState reports HalfOpen after the window without admitting a probe`() = runTest {
        val timeSource = TestTimeSource()
        var transitions = 0
        val breaker = CircuitBreaker(
            maxFailures = 1,
            retryAfter = 400.milliseconds,
            name = "payment",
            timeSource = timeSource,
            onStateChange = { _, _ -> transitions++ },
        )

        assertEquals(CircuitBreaker.State.Closed, breaker.observedState)
        runCatching { Kap<String> { throw RuntimeException("fail") }.withCircuitBreaker(breaker).evalGraph() }
        assertEquals(CircuitBreaker.State.Open, breaker.observedState)

        timeSource += 500.milliseconds
        assertEquals(CircuitBreaker.State.HalfOpen, breaker.observedState)
        // The observation is read-only: no probe admitted, no transition fired.
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertEquals(1, transitions) // Closed -> Open only

        // A real call performs the lazy transition and admits the probe.
        assertTrue(Kap { "ok" }.withCircuitBreaker(breaker).evalGraph() == "ok")
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
        assertEquals(3, transitions) // Closed->Open, Open->HalfOpen, HalfOpen->Closed
    }

    @Test
    fun `the default name and toString still work`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, retryAfter = 1.seconds)
        assertEquals("circuit", breaker.name)
        assertTrue(breaker.toString().contains("circuit"))

        val e = CircuitBreakerOpenException()
        assertEquals(null, e.name)
        assertEquals("Circuit breaker is open", e.message)
    }

    @Test
    fun `the deprecated message constructor compiles`() {
        @Suppress("DEPRECATION_ERROR", "DEPRECATION")
        val legacy = CircuitBreakerOpenException("legacy message")
        assertEquals(null, legacy.name)
    }

    @Test
    fun `protect rejects with the enriched exception too`() = runTest {
        val timeSource = TestTimeSource()
        val breaker = CircuitBreaker(
            maxFailures = 1,
            retryAfter = 30.seconds,
            name = "inventory",
            timeSource = timeSource,
        )

        runCatching { breaker.protect { throw RuntimeException("fail") } }
        runCatching { breaker.protect { throw RuntimeException("fail") } }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        assertFalse(breaker.tryAcquire())

        val e = assertFailsWith<CircuitBreakerOpenException> { breaker.protect { "x" } }
        assertEquals("inventory", e.name)
        assertEquals(CircuitBreaker.State.Open, e.state)
    }
}
