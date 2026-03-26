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
                Effect { delay(50.milliseconds); "slow" },
                Effect { delay(10.milliseconds); "fast" },
            )
        }
        assertEquals("fast", result)
    }

    @Test
    fun `race matrix — first succeeds fast, second fails slow`() = runTest {
        val result = Async {
            race(
                Effect { delay(10.milliseconds); "fast success" },
                Effect { delay(50.milliseconds); throw RuntimeException("slow fail") },
            )
        }
        assertEquals("fast success", result)
    }

    @Test
    fun `race matrix — first fails fast, second succeeds slow`() = runTest {
        val result = Async {
            race(
                Effect {
                    delay(10.milliseconds)
                    throw RuntimeException("fast fail")
                },
                Effect {
                    delay(50.milliseconds)
                    "slow success"
                },
            )
        }
        assertEquals("slow success", result)
    }

    @Test
    fun `race matrix — both fail, first error is primary`() = runTest {
        val graph: Effect<String> = race(
            Effect { delay(10.milliseconds); throw RuntimeException("first") },
            Effect { delay(50.milliseconds); throw IllegalStateException("second") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("first", ex.message)
    }

    // ── raceN: N-way combinatorial tests ───────────────────────────────────

    @Test
    fun `raceN — 5 racers, only one succeeds`() = runTest {
        val result = Async {
            raceN(
                Effect<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Effect<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Effect { delay(30.milliseconds); "winner" },
                Effect<String> { delay(40.milliseconds); throw RuntimeException("d") },
                Effect<String> { delay(50.milliseconds); throw RuntimeException("e") },
            )
        }
        assertEquals("winner", result)
    }

    @Test
    fun `raceN — first two fail instantly, third survives`() = runTest {
        val result = Async {
            raceN(
                Effect<String> { throw RuntimeException("instant-fail-1") },
                Effect<String> { throw RuntimeException("instant-fail-2") },
                Effect { delay(10.milliseconds); "survivor" },
                Effect { delay(20.milliseconds); "slow" },
                Effect { delay(30.milliseconds); "slower" },
            )
        }
        assertEquals("survivor", result)
    }

    @Test
    fun `raceN — all 4 fail, first error propagates`() = runTest {
        val graph: Effect<String> = raceN(
            Effect { delay(10.milliseconds); throw RuntimeException("e1") },
            Effect { delay(20.milliseconds); throw IllegalStateException("e2") },
            Effect { delay(30.milliseconds); throw UnsupportedOperationException("e3") },
            Effect { delay(40.milliseconds); throw ArithmeticException("e4") },
        )
        val ex = assertFailsWith<RuntimeException> { val r = Async { graph } }
        assertEquals("e1", ex.message)
    }

    @Test
    fun `raceN — success at position 0 (first)`() = runTest {
        val result = Async {
            raceN(
                Effect { "first" },
                Effect { delay(50.milliseconds); "second" },
                Effect { delay(100.milliseconds); "third" },
            )
        }
        assertEquals("first", result)
    }

    @Test
    fun `raceN — success at last position`() = runTest {
        val result = Async {
            raceN(
                Effect<String> { delay(10.milliseconds); throw RuntimeException("a") },
                Effect<String> { delay(20.milliseconds); throw RuntimeException("b") },
                Effect { delay(30.milliseconds); "last one standing" },
            )
        }
        assertEquals("last one standing", result)
    }

    // ── race timing verification ───────────────────────────────────────────

    @Test
    fun `race — total time is fastest success, not slowest`() = runTest {
        Async {
            race(
                Effect { delay(100.milliseconds); "slow" },
                Effect { delay(20.milliseconds); "fast" },
            )
        }
        assertTrue(currentTime <= 30, "Should complete in ~20ms, got ${currentTime}ms")
    }

    @Test
    fun `raceN — total time is fastest success`() = runTest {
        Async {
            raceN(
                Effect { delay(200.milliseconds); "very-slow" },
                Effect { delay(150.milliseconds); "slow" },
                Effect { delay(10.milliseconds); "fast" },
                Effect { delay(300.milliseconds); "very-very-slow" },
            )
        }
        assertTrue(currentTime <= 20, "Should complete in ~10ms, got ${currentTime}ms")
    }

    @Test
    fun `race — fast failure, slow success, total time is slow`() = runTest {
        val result = Async {
            race(
                Effect {
                    delay(10.milliseconds)
                    throw RuntimeException("quick fail")
                },
                Effect {
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
                        Effect { delay(100.milliseconds); "slow-a" },
                        Effect { delay(10.milliseconds); "fast-a" },
                    ).await()
                }
                .with {
                    race(
                        Effect { delay(10.milliseconds); "fast-b" },
                        Effect { delay(100.milliseconds); "slow-b" },
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
            Effect { delay(50.milliseconds); "slow" },
            Effect { delay(10.milliseconds); "fast" },
            Effect { delay(100.milliseconds); "very slow" },
        )
        val result = Async { computations.raceAll() }
        assertEquals("fast", result)
    }

    @Test
    fun `raceAll — single element list`() = runTest {
        val result = Async { listOf(Effect { "only" }).raceAll() }
        assertEquals("only", result)
    }
}
