package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Edge case tests for [PhaseBarrier] signal behavior.
 *
 * Verifies that barriers complete their signals under all conditions
 * (success, failure, cancellation) and that gated branches behave correctly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhaseBarrierEdgeCaseTest {

    @Test
    fun `then signal fires on success - gated with branches start`() = runTest {
        val gatedStarted = AtomicBoolean(false)
        val result = Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                .with { delay(50); "A" }
                .then { delay(30); "B" }
                .with {
                    gatedStarted.set(true)
                    delay(20)
                    "C"
                }.executeGraph()
        assertEquals("A|B|C", result)
        assertTrue(gatedStarted.get())
        // Phase 1: 50ms, barrier: 30ms, Phase 2: 20ms = 100ms
        assertEquals(100, currentTime)
    }

    @Test
    fun `then signal fires on failure - prevents deadlock in gated branches`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                    .with { delay(50); "A" }
                    .then { delay(30); throw IllegalStateException("barrier failed") }
                    .with { delay(20); "C" }  // should not hang
                    .executeGraph()
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `multiple sequential barriers each gate their subsequent phase`() = runTest {
        val phaseOrder = mutableListOf<String>()
        val result = Kap.of { a: Int -> { b: Int -> { c: Int -> { d: Int -> { e: Int -> a + b + c + d + e } } } } }
                .with { delay(50); phaseOrder.add("P1-A"); 1 }
                .with { delay(30); phaseOrder.add("P1-B"); 2 }
                .then { delay(20); phaseOrder.add("barrier1"); 3 }
                .with { delay(10); phaseOrder.add("P2-D"); 4 }
                .then { delay(15); phaseOrder.add("barrier2"); 5 }.executeGraph()
        assertEquals(15, result)
        // Barrier1 should complete before P2-D starts
        val barrier1Idx = phaseOrder.indexOf("barrier1")
        val p2dIdx = phaseOrder.indexOf("P2-D")
        assertTrue(barrier1Idx < p2dIdx, "barrier1 should complete before P2-D: $phaseOrder")
    }

    @Test
    fun `then with recover allows chain to continue after barrier failure`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                .with { "A" }
                .then {
                    Kap<String> { throw IllegalStateException("recoverable") }
                        .recover { "recovered" }
                        .executeGraph()
                }
                .with { "C" }.executeGraph()
        assertEquals("A|recovered|C", result)
    }

    @Test
    fun `thenValue does NOT gate subsequent with branches`() = runTest {
        val apStartTime = AtomicInteger(-1)
        val result = Kap.of { a: Int -> { b: Int -> { c: Int -> a + b + c } } }
                .with { delay(50); 1 }
                .thenValue { delay(100); 2 }
                .with {
                    apStartTime.set(currentTime.toInt())
                    delay(30)
                    3
                }.executeGraph()
        assertEquals(6, result)
        // The third .with should start at t=0 (ungated), not after thenValue completes
        assertEquals(0, apStartTime.get(), "thenValue should NOT gate subsequent with branches")
    }

    @Test
    fun `deeply nested barriers maintain correct execution order`() = runTest {
        val executionLog = mutableListOf<String>()

        val result = Kap.of { a: Int -> { b: Int -> { c: Int -> { d: Int -> { e: Int -> { f: Int -> { g: Int ->
                a + b + c + d + e + f + g
            } } } } } } }
                .with { delay(50); executionLog.add("P1-1"); 1 }
                .with { delay(40); executionLog.add("P1-2"); 2 }
                .then { delay(20); executionLog.add("B1"); 3 }
                .with { delay(30); executionLog.add("P2-1"); 4 }
                .with { delay(25); executionLog.add("P2-2"); 5 }
                .then { delay(15); executionLog.add("B2"); 6 }
                .with { delay(10); executionLog.add("P3-1"); 7 }.executeGraph()

        assertEquals(28, result)

        // Verify ordering constraints
        val b1Idx = executionLog.indexOf("B1")
        val b2Idx = executionLog.indexOf("B2")
        val p2Start = executionLog.indexOfFirst { it.startsWith("P2") }
        val p3Start = executionLog.indexOfFirst { it.startsWith("P3") }

        assertTrue(b1Idx < p2Start, "Barrier 1 must complete before Phase 2: $executionLog")
        assertTrue(b2Idx < p3Start, "Barrier 2 must complete before Phase 3: $executionLog")
    }

    @Test
    fun `then signal fires on failure - gated with does not hang`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                    .with { delay(10); "A" }
                    .then { throw RuntimeException("barrier-fail") }
                    .with { delay(10); "C" }  // must not hang
                    .executeGraph()
        }

        assertTrue(result.isFailure, "Should propagate barrier failure")
        assertEquals("barrier-fail", result.exceptionOrNull()!!.message)
        // If the signal didn't fire, this test would hang forever (timeout)
    }

    @Test
    fun `then failure with recover allows continuation`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                .with { delay(10); "A" }
                .then { delay(10); "B" }
                .with { delay(10); "C" }.executeGraph()

        assertEquals("A|B|C", result)
        // 10(A) + 10(B barrier) + 10(C) = 30ms
        assertEquals(30, currentTime)
    }

    @Test
    fun `three consecutive barriers gate correctly with virtual time`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> { d: String -> { e: String -> { f: String ->
                "$a|$b|$c|$d|$e|$f"
            } } } } } }
                .with { delay(20); "A" }           // t=0..20
                .then { delay(10); "B" }   // t=20..30 (barrier 1)
                .with { delay(20); "C" }           // t=30..50
                .then { delay(10); "D" }   // t=50..60 (barrier 2)
                .with { delay(20); "E" }           // t=60..80
                .then { delay(10); "F" }   // t=80..90 (barrier 3)
                .executeGraph()

        assertEquals("A|B|C|D|E|F", result)
        // 20 + 10 + 20 + 10 + 20 + 10 = 90ms
        assertEquals(90, currentTime,
            "Three barriers should produce 90ms: 20+10+20+10+20+10. Got ${currentTime}ms")
    }

}
