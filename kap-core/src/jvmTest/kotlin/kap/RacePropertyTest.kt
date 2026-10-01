package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
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
 * Property-based tests for race combinators: random delays, random success
 * positions and random N — the success/failure matrix, timing invariants and
 * composition are verified with generated inputs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RacePropertyTest {

    // ── race: 2-way success/failure matrix ─────────────────────────────────

    @Test
    fun `race — both succeed, faster wins, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..200), Arb.int(10..200)) { d1, d2 ->
            val result = race(
                Kap { delay(d1.milliseconds); "a" },
                Kap { delay(d2.milliseconds); "b" },
            ).evalGraph()
            assertEquals(if (d1 <= d2) "a" else "b", result, "race($d1, $d2)")
        }
    }

    @Test
    fun `race — fast success beats slow failure, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { dFast, dSlow ->
            val result = race(
                Kap { delay(dFast.milliseconds); "fast success" },
                Kap { delay(dSlow.milliseconds); throw RuntimeException("slow fail") },
            ).evalGraph()
            assertEquals("fast success", result, "fast=$dFast slow=$dSlow")
        }
    }

    @Test
    fun `race — slow success survives fast failure, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { dFast, dSlow ->
            val result = race(
                Kap { delay(dFast.milliseconds); throw RuntimeException("fast fail") },
                Kap { delay(dSlow.milliseconds); "slow success" },
            ).evalGraph()
            assertEquals("slow success", result, "fast=$dFast slow=$dSlow")
        }
    }

    @Test
    fun `race — both fail, first error is primary, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { d1, d2 ->
            val graph: Kap<String> = race(
                Kap { delay(d1.milliseconds); throw RuntimeException("first") },
                Kap { delay(d2.milliseconds); throw IllegalStateException("second") },
            )
            val ex = assertFailsWith<RuntimeException> { graph.evalGraph() }
            assertEquals("first", ex.message, "race($d1, $d2)")
        }
    }

    // ── raceN: N-way combinatorial properties ──────────────────────────────

    @Test
    fun `raceN — the only successful racer wins at any position, for arbitrary N`() = runTest {
        checkAll(Arb.int(2..8), Arb.int(0..7), Arb.int(10..80), Arb.int(10..80)) { n, rawIdx, dWin, dFail ->
            val winnerIdx = rawIdx.coerceAtMost(n - 1)
            val racers = (0 until n).map { i ->
                if (i == winnerIdx) {
                    Kap { delay(dWin.milliseconds); "winner" }
                } else {
                    Kap<String> { delay(dFail.milliseconds); throw RuntimeException("f-$i") }
                }
            }
            val result = raceN(*racers.toTypedArray()).evalGraph()
            assertEquals("winner", result, "n=$n winnerIdx=$winnerIdx")
        }
    }

    @Test
    fun `raceN — all fail, first error propagates, for arbitrary delays`() = runTest {
        checkAll(Arb.int(2..6), Arb.int(10..50)) { n, step ->
            val racers = (0 until n).map { i ->
                Kap { delay(((i + 1) * step).milliseconds); throw RuntimeException("e$i") }
            }
            val ex = assertFailsWith<RuntimeException> { raceN(*racers.toTypedArray()).evalGraph() }
            assertEquals("e0", ex.message, "n=$n step=$step")
        }
    }

    // ── race timing verification ───────────────────────────────────────────

    @Test
    fun `race — total time is the fastest success, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { dFast, dSlow ->
            val start = currentTime
            race(
                Kap { delay(dSlow.milliseconds); "slow" },
                Kap { delay(dFast.milliseconds); "fast" },
            ).evalGraph()
            val elapsed = currentTime - start
            assertTrue(elapsed <= dFast + 10, "took ${elapsed}ms, fastest=$dFast")
        }
    }

    @Test
    fun `race — fast failure, slow success, total time is slow, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { dFast, dSlow ->
            val start = currentTime
            race(
                Kap { delay(dFast.milliseconds); throw RuntimeException("quick fail") },
                Kap { delay(dSlow.milliseconds); "slow winner" },
            ).evalGraph()
            val elapsed = currentTime - start
            assertTrue(elapsed in dSlow..(dSlow + 10), "took ${elapsed}ms, slow=$dSlow")
        }
    }

    // ── race inside with chains ────────────────────────────────────────────

    @Test
    fun `race composed with with — parallel branches with racing, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..50), Arb.int(100..300)) { dFast, dSlow ->
            val start = currentTime
            val result = Kap.of { a: String -> { b: String -> a to b } }
                .with {
                    race(
                        Kap { delay(dSlow.milliseconds); "slow-a" },
                        Kap { delay(dFast.milliseconds); "fast-a" },
                    ).evalGraph()
                }
                .with {
                    race(
                        Kap { delay(dFast.milliseconds); "fast-b" },
                        Kap { delay(dSlow.milliseconds); "slow-b" },
                    ).evalGraph()
                }.evalGraph()
            assertEquals("fast-a" to "fast-b", result)
            val elapsed = currentTime - start
            assertTrue(elapsed <= dFast + 10, "Both races should resolve at ~$dFast: ${elapsed}ms")
        }
    }

    // ── raceAll (iterable) ─────────────────────────────────────────────────

    @Test
    fun `raceAll — winner is the minimum-delay racer, for arbitrary delay lists`() = runTest {
        checkAll(Arb.list(Arb.int(10..200), 2..8)) { delays ->
            val expectedIdx = delays.indexOf(delays.min())
            val racers = delays.mapIndexed { i, d -> Kap { delay(d.milliseconds); "r$i" } }
            val result = racers.raceAll().evalGraph()
            assertEquals("r$expectedIdx", result, "delays=$delays")
        }
    }

    @Test
    fun `raceAll — single element list returns it, for arbitrary delays`() = runTest {
        checkAll(Arb.int(10..100)) { d ->
            assertEquals("only", listOf(Kap { delay(d.milliseconds); "only" }).raceAll().evalGraph())
        }
    }
}
