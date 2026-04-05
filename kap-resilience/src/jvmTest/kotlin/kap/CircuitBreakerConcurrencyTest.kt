package kap

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Concurrent state transition tests for [CircuitBreaker].
 *
 * Tests that the circuit breaker correctly handles multiple threads
 * hitting state transitions simultaneously.
 */
class CircuitBreakerConcurrencyTest {

    @Test
    fun `concurrent failures correctly trip the breaker`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 1.seconds)
        val failingComp = Kap<String> { throw IllegalStateException("fail") }
            .withCircuitBreaker(breaker)

        // Send many concurrent failures
        val results = coroutineScope {
            (1..20).map {
                async {
                    try {
                        failingComp.evalGraph()
                        "success"
                    } catch (e: CircuitBreakerOpenException) {
                        "open"
                    } catch (e: IllegalStateException) {
                        "failed"
                    }
                }
            }.awaitAll()
        }

        // After 5 failures, the breaker should be open
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
        // Some should have gotten the actual exception, others should get CircuitBreakerOpenException
        assertTrue(results.contains("failed"), "Some calls should fail with the actual exception")
        assertTrue(results.contains("open"), "Some calls should be rejected by the open breaker")
    }

    @Test
    fun `breaker transitions from open to half-open after timeout`() = runTest {
        // Use a custom TimeSource so runTest's virtual time doesn't interfere
        val testTimeSource = object : TimeSource {
            var now = 0L
            override fun markNow() = object : TimeMark {
                val snapshot = now
                override fun elapsedNow(): kotlin.time.Duration = (now - snapshot).milliseconds
            }
        }
        val breaker = CircuitBreaker(
            maxFailures = 2,
            resetTimeout = 100.milliseconds,
            timeSource = testTimeSource,
        )

        val failingComp = Kap<String> {
            throw IllegalStateException("fail")
        }.withCircuitBreaker(breaker)

        // Trip the breaker
        repeat(2) {
            try { failingComp.evalGraph() } catch (_: Exception) {}
        }
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)

        // Advance test time past reset timeout
        testTimeSource.now = 150

        // Next call should trigger half-open probe
        val succeedingComp = Kap { "recovered" }.withCircuitBreaker(breaker)

        val result = succeedingComp.evalGraph()
        assertEquals("recovered", result)
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `successful calls reset failure count`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 3, resetTimeout = 1.seconds)

        // 2 failures (below threshold)
        val failComp = Kap<String> { throw IllegalStateException("fail") }
            .withCircuitBreaker(breaker)
        repeat(2) {
            try { failComp.evalGraph() } catch (_: Exception) {}
        }
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)

        // 1 success resets the counter
        val successComp = Kap { "ok" }.withCircuitBreaker(breaker)
        successComp.evalGraph()
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)

        // 2 more failures should NOT trip the breaker (counter was reset)
        repeat(2) {
            try { failComp.evalGraph() } catch (_: Exception) {}
        }
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun `state change callback is invoked on transitions`() = runTest {
        val transitions = mutableListOf<Pair<CircuitBreaker.State, CircuitBreaker.State>>()
        val testTimeSource = object : TimeSource {
            var now = 0L
            override fun markNow() = object : TimeMark {
                val snapshot = now
                override fun elapsedNow(): kotlin.time.Duration = (now - snapshot).milliseconds
            }
        }
        val breaker = CircuitBreaker(
            maxFailures = 2,
            resetTimeout = 100.milliseconds,
            onStateChange = { old, new -> transitions.add(old to new) },
            timeSource = testTimeSource,
        )

        val failComp = Kap<String> { throw IllegalStateException("fail") }
            .withCircuitBreaker(breaker)

        // Trip the breaker
        repeat(2) {
            try { failComp.evalGraph() } catch (_: Exception) {}
        }

        assertTrue(transitions.contains(CircuitBreaker.State.Closed to CircuitBreaker.State.Open))

        // Advance past reset timeout and probe
        testTimeSource.now = 150
        val successComp = Kap { "ok" }.withCircuitBreaker(breaker)
        successComp.evalGraph()

        assertTrue(transitions.contains(CircuitBreaker.State.Open to CircuitBreaker.State.HalfOpen))
        assertTrue(transitions.contains(CircuitBreaker.State.HalfOpen to CircuitBreaker.State.Closed))
    }

    @Test
    fun `cancellation does not count as failure`() = runTest {
        val breaker = CircuitBreaker(maxFailures = 2, resetTimeout = 1.seconds)
        val callCount = AtomicInteger(0)

        val slowComp = Kap {
            callCount.incrementAndGet()
            delay(1.seconds)
            "done"
        }.withCircuitBreaker(breaker)

        // Cancel via timeout — should NOT increment failure count
        repeat(3) {
            try {
                slowComp.timeout(10.milliseconds).evalGraph()
            } catch (_: Exception) {}
        }

        // Breaker should still be closed
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }
}
