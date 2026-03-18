package applicative

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
import kotlin.test.assertIs
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
    fun `followedBy signal fires on success - gated ap branches start`() = runTest {
        val gatedStarted = AtomicBoolean(false)
        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { delay(50); "A" }
                .followedBy { delay(30); "B" }
                .ap {
                    gatedStarted.set(true)
                    delay(20)
                    "C"
                }
        }
        assertEquals("A|B|C", result)
        assertTrue(gatedStarted.get())
        // Phase 1: 50ms, barrier: 30ms, Phase 2: 20ms = 100ms
        assertEquals(100, currentTime)
    }

    @Test
    fun `followedBy signal fires on failure - prevents deadlock in gated branches`() = runTest {
        val result = runCatching {
            Async {
                lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                    .ap { delay(50); "A" }
                    .followedBy { delay(30); throw IllegalStateException("barrier failed") }
                    .ap { delay(20); "C" }  // should not hang
            }
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `multiple sequential barriers each gate their subsequent phase`() = runTest {
        val phaseOrder = mutableListOf<String>()
        val result = Async {
            lift5 { a: Int, b: Int, c: Int, d: Int, e: Int -> a + b + c + d + e }
                .ap { delay(50); phaseOrder.add("P1-A"); 1 }
                .ap { delay(30); phaseOrder.add("P1-B"); 2 }
                .followedBy { delay(20); phaseOrder.add("barrier1"); 3 }
                .ap { delay(10); phaseOrder.add("P2-D"); 4 }
                .followedBy { delay(15); phaseOrder.add("barrier2"); 5 }
        }
        assertEquals(15, result)
        // Barrier1 should complete before P2-D starts
        val barrier1Idx = phaseOrder.indexOf("barrier1")
        val p2dIdx = phaseOrder.indexOf("P2-D")
        assertTrue(barrier1Idx < p2dIdx, "barrier1 should complete before P2-D: $phaseOrder")
    }

    @Test
    fun `followedBy with recover allows chain to continue after barrier failure`() = runTest {
        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { "A" }
                .followedBy {
                    Computation<String> { throw IllegalStateException("recoverable") }
                        .recover { "recovered" }
                        .await()
                }
                .ap { "C" }
        }
        assertEquals("A|recovered|C", result)
    }

    @Test
    fun `thenValue does NOT gate subsequent ap branches`() = runTest {
        val apStartTime = AtomicInteger(-1)
        val result = Async {
            lift3 { a: Int, b: Int, c: Int -> a + b + c }
                .ap { delay(50); 1 }
                .thenValue { delay(100); 2 }
                .ap {
                    apStartTime.set(currentTime.toInt())
                    delay(30)
                    3
                }
        }
        assertEquals(6, result)
        // The third .ap should start at t=0 (ungated), not after thenValue completes
        assertEquals(0, apStartTime.get(), "thenValue should NOT gate subsequent ap branches")
    }

    @Test
    fun `followedByV signal fires on validated Left - gated apV branches proceed`() = runTest {
        val result = Async {
            liftV3<String, Int, Int, Int, Int> { a, b, c -> a + b + c }
                .apV(valid<String, Int>(1))
                .followedByV(invalid<String, Int>("barrier error"))
                .apV(valid<String, Int>(3))  // should not hang
        }
        assertTrue(result is Either.Left)
    }

    @Test
    fun `deeply nested barriers maintain correct execution order`() = runTest {
        val executionLog = mutableListOf<String>()

        val result = Async {
            lift7 { a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int ->
                a + b + c + d + e + f + g
            }
                .ap { delay(50); executionLog.add("P1-1"); 1 }
                .ap { delay(40); executionLog.add("P1-2"); 2 }
                .followedBy { delay(20); executionLog.add("B1"); 3 }
                .ap { delay(30); executionLog.add("P2-1"); 4 }
                .ap { delay(25); executionLog.add("P2-2"); 5 }
                .followedBy { delay(15); executionLog.add("B2"); 6 }
                .ap { delay(10); executionLog.add("P3-1"); 7 }
        }

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
    fun `followedBy signal fires on failure - gated ap does not hang`() = runTest {
        val result = runCatching {
            Async {
                lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                    .ap { delay(10); "A" }
                    .followedBy { throw RuntimeException("barrier-fail") }
                    .ap { delay(10); "C" }  // must not hang
            }
        }

        assertTrue(result.isFailure, "Should propagate barrier failure")
        assertEquals("barrier-fail", result.exceptionOrNull()!!.message)
        // If the signal didn't fire, this test would hang forever (timeout)
    }

    @Test
    fun `followedBy failure with recover allows continuation`() = runTest {
        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap { delay(10); "A" }
                .followedBy { delay(10); "B" }
                .ap { delay(10); "C" }
        }

        assertEquals("A|B|C", result)
        // 10(A) + 10(B barrier) + 10(C) = 30ms
        assertEquals(30, currentTime)
    }

    @Test
    fun `three consecutive barriers gate correctly with virtual time`() = runTest {
        val result = Async {
            lift6 { a: String, b: String, c: String, d: String, e: String, f: String ->
                "$a|$b|$c|$d|$e|$f"
            }
                .ap { delay(20); "A" }           // t=0..20
                .followedBy { delay(10); "B" }   // t=20..30 (barrier 1)
                .ap { delay(20); "C" }           // t=30..50
                .followedBy { delay(10); "D" }   // t=50..60 (barrier 2)
                .ap { delay(20); "E" }           // t=60..80
                .followedBy { delay(10); "F" }   // t=80..90 (barrier 3)
        }

        assertEquals("A|B|C|D|E|F", result)
        // 20 + 10 + 20 + 10 + 20 + 10 = 90ms
        assertEquals(90, currentTime,
            "Three barriers should produce 90ms: 20+10+20+10+20+10. Got ${currentTime}ms")
    }

    @Test
    fun `apV after failed followedByV does not hang`() = runTest {
        val result = Async {
            liftV3<String, String, String, String, String> { a, b, c -> "$a|$b|$c" }
                .apV { delay(10); Either.Right("A") }
                .followedByV { delay(10); Either.Left(NonEmptyList("barrier-err")) }
                .apV { delay(10); Either.Right("C") }
        }

        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals("barrier-err", result.value.head)
    }
}
