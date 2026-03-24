package applicative

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
        val result = Async {
            race(
                Computation { delay(50.milliseconds); "slow" },
                Computation { delay(10.milliseconds); "fast" },
            )
        }
        assertEquals("fast", result)
    }

    @Test
    fun `race matrix — first succeeds fast, second fails slow`() = runTest {
        val result = Async {
            race(
                Computation { delay(10.milliseconds); "fast success" },
                Computation { delay(50.milliseconds); throw RuntimeException("slow fail") },
            )
        }
        assertEquals("fast success", result)
    }

    @Test
    fun `race matrix — first fails fast, second succeeds slow`() = runTest {
        val result = Async {
            race(
                Computation {
                    delay(10.milliseconds)
                    throw RuntimeException("fast fail")
                },
                Computation {
                    delay(50.milliseconds)
                    "slow success"
                },
            )
        }
        assertEquals("slow success", result)
    }

    @Test
    fun `race matrix — both fail, first error is primary`() = runTest {
        val graph: Computation<String> = race(
            Computation { delay(10.milliseconds); throw RuntimeException("first") },
            Computation { delay(50.milliseconds); throw IllegalStateException("second") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("first", ex.message)
    }

    // ── raceN: N-way combinatorial tests ───────────────────────────────────

    @Test
    fun `raceN — 5 racers, only one succeeds`() = runTest {
        val result = Async {
            raceN(
                Computation<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Computation<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Computation { delay(30.milliseconds); "winner" },
                Computation<String> { delay(40.milliseconds); throw RuntimeException("d") },
                Computation<String> { delay(50.milliseconds); throw RuntimeException("e") },
            )
        }
        assertEquals("winner", result)
    }

    @Test
    fun `raceN — first two fail instantly, third survives`() = runTest {
        val result = Async {
            raceN(
                Computation<String> { throw RuntimeException("instant-fail-1") },
                Computation<String> { throw RuntimeException("instant-fail-2") },
                Computation { delay(10.milliseconds); "survivor" },
                Computation { delay(20.milliseconds); "slow" },
                Computation { delay(30.milliseconds); "slower" },
            )
        }
        assertEquals("survivor", result)
    }

    @Test
    fun `raceN — all 4 fail, first error propagates`() = runTest {
        val graph: Computation<String> = raceN(
            Computation { delay(10.milliseconds); throw RuntimeException("e1") },
            Computation { delay(20.milliseconds); throw IllegalStateException("e2") },
            Computation { delay(30.milliseconds); throw UnsupportedOperationException("e3") },
            Computation { delay(40.milliseconds); throw ArithmeticException("e4") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("e1", ex.message)
    }

    @Test
    fun `raceN — success at position 0 (first)`() = runTest {
        val result = Async {
            raceN(
                Computation { "first" },
                Computation { delay(50.milliseconds); "second" },
                Computation { delay(100.milliseconds); "third" },
            )
        }
        assertEquals("first", result)
    }

    @Test
    fun `raceN — success at last position`() = runTest {
        val result = Async {
            raceN(
                Computation<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Computation<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Computation { delay(30.milliseconds); "last one standing" },
            )
        }
        assertEquals("last one standing", result)
    }

    // ── race timing verification ───────────────────────────────────────────

    @Test
    fun `race — total time is fastest success, not slowest`() = runTest {
        Async {
            race(
                Computation { delay(100.milliseconds); "slow" },
                Computation { delay(20.milliseconds); "fast" },
            )
        }
        assertTrue(currentTime <= 30, "Should complete in ~20ms, got ${currentTime}ms")
    }

    @Test
    fun `raceN — total time is fastest success`() = runTest {
        Async {
            raceN(
                Computation { delay(200.milliseconds); "very-slow" },
                Computation { delay(150.milliseconds); "slow" },
                Computation { delay(10.milliseconds); "fast" },
                Computation { delay(300.milliseconds); "very-very-slow" },
            )
        }
        assertTrue(currentTime <= 20, "Should complete in ~10ms, got ${currentTime}ms")
    }

    @Test
    fun `race — fast failure, slow success, total time is slow`() = runTest {
        val result = Async {
            race(
                Computation {
                    delay(10.milliseconds)
                    throw RuntimeException("quick fail")
                },
                Computation {
                    delay(50.milliseconds)
                    "slow winner"
                },
            )
        }
        assertEquals("slow winner", result)
        assertTrue(currentTime in 40..60, "Should wait for slow success: ${currentTime}ms")
    }

    // ── race inside with chains ──────────────────────────────────────────────

    @Test
    fun `race composed with with — parallel branches with racing`() = runTest {
        data class Result(val a: String, val b: String)

        val result = Async {
            kap(::Result)
                .with {
                    race(
                        Computation { delay(100.milliseconds); "slow-a" },
                        Computation { delay(10.milliseconds); "fast-a" },
                    ).await()
                }
                .with {
                    race(
                        Computation { delay(10.milliseconds); "fast-b" },
                        Computation { delay(100.milliseconds); "slow-b" },
                    ).await()
                }
        }
        assertEquals(Result("fast-a", "fast-b"), result)
        assertTrue(currentTime <= 20, "Both races should resolve in ~10ms: ${currentTime}ms")
    }

    // ── raceAll (iterable) ─────────────────────────────────────────────────

    @Test
    fun `raceAll — list of computations`() = runTest {
        val computations = listOf(
            Computation { delay(50.milliseconds); "slow" },
            Computation { delay(10.milliseconds); "fast" },
            Computation { delay(100.milliseconds); "very slow" },
        )
        val result = Async { computations.raceAll() }
        assertEquals("fast", result)
    }

    @Test
    fun `raceAll — single element list`() = runTest {
        val result = Async { listOf(Computation { "only" }).raceAll() }
        assertEquals("only", result)
    }
}
