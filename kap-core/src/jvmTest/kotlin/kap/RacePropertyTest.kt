package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Systematic tests for race combinators covering the full success/failure matrix,
 * timing properties, error propagation, and composition scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RacePropertyTest {

    // ── race: 2-way success/failure matrix ─────────────────────────────────

    @Test
    fun `race matrix — both succeed, faster wins`() = runTest {
        val result = race(
                Kap { delay(50.milliseconds); "slow" },
                Kap { delay(10.milliseconds); "fast" },
            ).evalGraph()
        assertEquals("fast", result)
    }

    @Test
    fun `race matrix — first succeeds fast, second fails slow`() = runTest {
        val result = race(
                Kap { delay(10.milliseconds); "fast success" },
                Kap { delay(50.milliseconds); throw RuntimeException("slow fail") },
            ).evalGraph()
        assertEquals("fast success", result)
    }

    @Test
    fun `race matrix — first fails fast, second succeeds slow`() = runTest {
        val result = race(
                Kap {
                    delay(10.milliseconds)
                    throw RuntimeException("fast fail")
                },
                Kap {
                    delay(50.milliseconds)
                    "slow success"
                },
            ).evalGraph()
        assertEquals("slow success", result)
    }

    @Test
    fun `race matrix — both fail, first error is primary`() = runTest {
        val graph: Kap<String> = race(
            Kap { delay(10.milliseconds); throw RuntimeException("first") },
            Kap { delay(50.milliseconds); throw IllegalStateException("second") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = graph.evalGraph() }
        assertEquals("first", ex.message)
    }

    // ── raceN: N-way combinatorial tests ───────────────────────────────────

    @Test
    fun `raceN — 5 racers, only one succeeds`() = runTest {
        val result = raceN(
                Kap<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Kap<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Kap { delay(30.milliseconds); "winner" },
                Kap<String> { delay(40.milliseconds); throw RuntimeException("d") },
                Kap<String> { delay(50.milliseconds); throw RuntimeException("e") },
            ).evalGraph()
        assertEquals("winner", result)
    }

    @Test
    fun `raceN — first two fail instantly, third survives`() = runTest {
        val result = raceN(
                Kap<String> { throw RuntimeException("instant-fail-1") },
                Kap<String> { throw RuntimeException("instant-fail-2") },
                Kap { delay(10.milliseconds); "survivor" },
                Kap { delay(20.milliseconds); "slow" },
                Kap { delay(30.milliseconds); "slower" },
            ).evalGraph()
        assertEquals("survivor", result)
    }

    @Test
    fun `raceN — all 4 fail, first error propagates`() = runTest {
        val graph: Kap<String> = raceN(
            Kap { delay(10.milliseconds); throw RuntimeException("e1") },
            Kap { delay(20.milliseconds); throw IllegalStateException("e2") },
            Kap { delay(30.milliseconds); throw UnsupportedOperationException("e3") },
            Kap { delay(40.milliseconds); throw ArithmeticException("e4") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = graph.evalGraph() }
        assertEquals("e1", ex.message)
    }

    @Test
    fun `raceN — success at position 0 (first)`() = runTest {
        val result = raceN(
                Kap { "first" },
                Kap { delay(50.milliseconds); "second" },
                Kap { delay(100.milliseconds); "third" },
            ).evalGraph()
        assertEquals("first", result)
    }

    @Test
    fun `raceN — success at last position`() = runTest {
        val result = raceN(
                Kap<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Kap<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Kap { delay(30.milliseconds); "last one standing" },
            ).evalGraph()
        assertEquals("last one standing", result)
    }

    // ── race timing verification ───────────────────────────────────────────

    @Test
    fun `race — total time is fastest success, not slowest`() = runTest {
        race(
                Kap { delay(100.milliseconds); "slow" },
                Kap { delay(20.milliseconds); "fast" },
            ).evalGraph()
        assertTrue(currentTime <= 30, "Should complete in ~20ms, got ${currentTime}ms")
    }

    @Test
    fun `raceN — total time is fastest success`() = runTest {
        raceN(
                Kap { delay(200.milliseconds); "very-slow" },
                Kap { delay(150.milliseconds); "slow" },
                Kap { delay(10.milliseconds); "fast" },
                Kap { delay(300.milliseconds); "very-very-slow" },
            ).evalGraph()
        assertTrue(currentTime <= 20, "Should complete in ~10ms, got ${currentTime}ms")
    }

    @Test
    fun `race — fast failure, slow success, total time is slow`() = runTest {
        val result = race(
                Kap {
                    delay(10.milliseconds)
                    throw RuntimeException("quick fail")
                },
                Kap {
                    delay(50.milliseconds)
                    "slow winner"
                },
            ).evalGraph()
        assertEquals("slow winner", result)
        assertTrue(currentTime in 40..60, "Should wait for slow success: ${currentTime}ms")
    }

    // ── race inside with chains ──────────────────────────────────────────────

    @Test
    fun `race composed with with — parallel branches with racing`() = runTest {
        data class Result(val a: String, val b: String)

        val result = Kap.of { a: String -> { b: String -> Result(a, b) } }
                .with {
                    race(
                        Kap { delay(100.milliseconds); "slow-a" },
                        Kap { delay(10.milliseconds); "fast-a" },
                    ).evalGraph()
                }
                .with {
                    race(
                        Kap { delay(10.milliseconds); "fast-b" },
                        Kap { delay(100.milliseconds); "slow-b" },
                    ).evalGraph()
                }.evalGraph()
        assertEquals(Result("fast-a", "fast-b"), result)
        assertTrue(currentTime <= 20, "Both races should resolve in ~10ms: ${currentTime}ms")
    }

    // ── raceAll (iterable) ─────────────────────────────────────────────────

    @Test
    fun `raceAll — list of computations`() = runTest {
        val computations = listOf(
            Kap { delay(50.milliseconds); "slow" },
            Kap { delay(10.milliseconds); "fast" },
            Kap { delay(100.milliseconds); "very slow" },
        )
        val result = computations.raceAll().evalGraph()
        assertEquals("fast", result)
    }

    @Test
    fun `raceAll — single element list`() = runTest {
        val result = listOf(Kap { "only" }).raceAll().evalGraph()
        assertEquals("only", result)
    }
}
