package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for race semantics and cancellation guarantees.
 *
 * Verifies that race always picks the fastest racer, losers are cancelled,
 * and error handling is correct under random timing conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceCancellationPropertyTest {

    // ════════════════════════════════════════════════════════════════════════
    // race: winner is always the fastest
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race always returns the result of the faster computation`() = runTest {
        checkAll(Arb.int(10..200), Arb.int(10..200)) { d1, d2 ->
            val result = race(
                    Kap { delay(d1.toLong()); "A" },
                    Kap { delay(d2.toLong()); "B" },
                ).evalGraph()
            val expected = if (d1 <= d2) "A" else "B"
            assertEquals(expected, result, "race($d1, $d2) should return $expected")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // raceN: winner is always the fastest among N racers
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceN always returns the fastest of N racers`() = runTest {
        checkAll(Arb.int(2..8)) { n ->
            val delays = (1..n).map { it * 10L } // 10, 20, 30, ...
            val computations = delays.mapIndexed { i, d ->
                Kap { delay(d); "racer-$i" }
            }
            val result = raceN(*computations.toTypedArray()).evalGraph()
            assertEquals("racer-0", result, "fastest racer (10ms) should win")
        }
    }

    @Test
    fun `raceN cancels all losers`() = runTest {
        val running = AtomicInteger(0)
        val completed = AtomicInteger(0)

        val n = 5
        val computations = (0 until n).map { i ->
            Kap {
                running.incrementAndGet()
                try {
                    delay(if (i == 0) 10 else 1000)
                    completed.incrementAndGet()
                    "racer-$i"
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                }
            }
        }

        val result = raceN(*computations.toTypedArray()).evalGraph()
        assertEquals("racer-0", result)
        assertEquals(1, completed.get(), "Only the winner should complete")
    }

    // ════════════════════════════════════════════════════════════════════════
    // race with failures: first success wins even if others fail
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race succeeds if at least one computation succeeds`() = runTest {
        val result = race(
                Kap<String> { delay(10); throw RuntimeException("fail-A") },
                Kap { delay(50); "B-wins" },
            ).evalGraph()
        assertEquals("B-wins", result)
    }

    @Test
    fun `race fails only when all computations fail`() = runTest {
        val fa = Kap<String> { delay(10); throw RuntimeException("fail-A") }
        val fb = Kap<String> { delay(20); throw RuntimeException("fail-B") }
        val result = runCatching { race(fa, fb).evalGraph() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `raceN with mixed success and failure returns first success`() = runTest {
        checkAll(Arb.int(2..6)) { n ->
            // All fail except the last one
            val computations = (0 until n).map { i ->
                Kap {
                    delay((i + 1) * 10L)
                    if (i < n - 1) throw RuntimeException("fail-$i")
                    "survivor"
                }
            }
            val result = raceN(*computations.toTypedArray()).evalGraph()
            assertEquals("survivor", result)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // orElse: sequential fallback chain
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `orElse chain returns first success`() = runTest {
        checkAll(Arb.int(1..8)) { successIdx ->
            val computations = (0 until 10).map { i ->
                Kap<String> {
                    if (i < successIdx) throw RuntimeException("fail-$i")
                    "success-$i"
                }
            }
            var chain = computations[0]
            for (i in 1 until computations.size) {
                chain = chain.orElse(computations[i])
            }
            val result = chain.evalGraph()
            assertEquals("success-$successIdx", result)
        }
    }

    @Test
    fun `firstSuccessOf returns first success from vararg`() = runTest {
        checkAll(Arb.int(0..7)) { successIdx ->
            val computations = (0..7).map { i ->
                Kap<String> {
                    if (i < successIdx) throw RuntimeException("fail-$i")
                    "success-$i"
                }
            }
            val result = firstSuccessOf(*computations.toTypedArray()).evalGraph()
            assertEquals("success-$successIdx", result)
        }
    }

    @Test
    fun `firstSuccessOf with all failures throws last error`() = runTest {
        val c1 = Kap<String> { throw RuntimeException("e1") }
        val c2 = Kap<String> { throw RuntimeException("e2") }
        val c3 = Kap<String> { throw RuntimeException("e3") }
        val result = runCatching { firstSuccessOf(c1, c2, c3).evalGraph() }
        assertTrue(result.isFailure)
        // The last error is the primary exception
        assertTrue(result.exceptionOrNull()?.message == "e3" ||
                result.exceptionOrNull()?.cause?.message == "e3",
            "Last error should be e3, got: ${result.exceptionOrNull()}")
    }
}
