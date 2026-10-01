package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Properties of the phase semantics — the execution model the DSL is named
 * after, verified with virtual time:
 *
 *  - `with` runs its right side in parallel with everything launched so far
 *  - `then` is a true barrier: later `with` launches are gated until it ends
 */
class PhasePropertyTest {

    @Test
    fun `with launches the right side immediately, without waiting for the left`() = runTest {
        checkAll(Arb.int(50..200)) { leftDelay ->
            val start = currentTime
            var rightLaunchTime: Long? = null
            Kap.of { a: Int -> { b: String -> "$a$b" } }
                .with {
                    delay(leftDelay.milliseconds)
                    1
                }
                .with {
                    rightLaunchTime = currentTime
                    "fast-right"
                }
                .evalGraph()

            val launchOffset = rightLaunchTime?.minus(start)
            assertTrue(launchOffset != null && launchOffset < leftDelay.toLong(),
                "second with must launch at t=0, launched at t=$launchOffset (left=$leftDelay)")
        }
    }

    @Test
    fun `then gates subsequent with launches until the barrier completes`() = runTest {
        checkAll(Arb.int(50..200), Arb.int(10..40)) { barrierDelay, gatedWork ->
            val start = currentTime
            var gatedLaunchTime: Long? = null
            val result = Kap.of { a: String -> { b: String -> a to b } }
                .then {
                    delay(barrierDelay.milliseconds)
                    "phase1"
                }
                .with {
                    gatedLaunchTime = currentTime
                    delay(gatedWork.milliseconds)
                    "phase2"
                }
                .evalGraph()

            assertEquals("phase1" to "phase2", result)

            val launchOffset = gatedLaunchTime?.minus(start)
            assertTrue(launchOffset != null && launchOffset >= barrierDelay.toLong(),
                "with after then must not launch before the barrier (launched t=$launchOffset, barrier=$barrierDelay)")
        }
    }

    @Test
    fun `with after then runs in parallel with its phase siblings once ungated`() = runTest {
        checkAll(Arb.int(50..150)) { barrierDelay ->
            val start = currentTime
            val result = Kap.of { a: String -> { b: Int -> { c: Int -> Triple(a, b, c) } } }
                .then {
                    delay(barrierDelay.milliseconds)
                    "phase1"
                }
                .with { delay(30.milliseconds); 1 }
                .with { delay(30.milliseconds); 2 }
                .evalGraph()

            assertEquals(Triple("phase1", 1, 2), result)

            val elapsed = currentTime - start
            // Ungated siblings overlap: barrier + one sibling window, not two.
            assertTrue(elapsed < (barrierDelay + 60).toLong(),
                "gated siblings must overlap (elapsed=$elapsed, barrier=$barrierDelay)")
        }
    }

    @Test
    fun `a chain of with calls preserves the phase start until evalGraph`() = runTest {
        checkAll(Arb.int(30..100)) { d1 ->
            checkAll(Arb.int(30..100)) { d2 ->
                val start = currentTime
                Kap.of { a: String -> { b: String -> a to b } }
                    .with { delay(d1.milliseconds); "a" }
                    .with { delay(d2.milliseconds); "b" }
                    .evalGraph()
                val elapsed = currentTime - start
                val expected = maxOf(d1, d2)
                assertTrue(elapsed in expected.toLong()..(expected + 5).toLong(),
                    "parallel with chain must take max(d1,d2)=$expected, took $elapsed (d1=$d1 d2=$d2)")
            }
        }
    }
}
