package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Algebraic laws over the FAILURE path — the dimension the happy-path law
 * suite does not cover. A coroutine-orchestration library is defined as much
 * by how failures propagate as by how values flow.
 */
class FailurePathLawsTest {

    // ── Functor over failure ────────────────────────────────────────────────

    @Test
    fun `map preserves the exact failure instance`() = runTest {
        checkAll(Arb.int()) { seed ->
            val err = IllegalStateException("boom-$seed")
            val outcome = runCatching { Kap.failed(err).map { n: Int -> n + 1 }.evalGraph() }
            assertSameFailure(err, outcome.exceptionOrNull(), "map must rethrow the original failure", seed)
        }
    }

    // ── Monad over failure ──────────────────────────────────────────────────

    @Test
    fun `andThen over failure never runs the continuation`() = runTest {
        checkAll(Arb.int()) { seed ->
            var continuationRan = false
            val outcome = runCatching {
                Kap.failed(err(seed))
                    .andThen { n: Int ->
                        continuationRan = true
                        Kap.of(n + 1)
                    }
                    .evalGraph()
            }
            assertTrue(outcome.isFailure, "seed=$seed")
            assertFalse(continuationRan, "bind must short-circuit on failure, seed=$seed")
        }
    }

    // ── Applicative over failure (with) ─────────────────────────────────────

    @Test
    fun `with propagates right-side failure and cancels the left side`() = runTest {
        checkAll(Arb.int(), Arb.int(50..200)) { seed, leftDelay ->
            var leftCompleted = false
            val err = IllegalStateException("right-$seed")
            val outcome = runCatching {
                Kap.of { a: Int -> { b: String -> "$a$b" } }
                    .with {
                        delay(leftDelay.milliseconds)
                        leftCompleted = true
                        1
                    }
                    .with { throw err }
                    .evalGraph()
            }
            assertSameFailure(err, outcome.exceptionOrNull(), "failure must be preserved", seed)
            assertFalse(leftCompleted, "failing sibling must cancel the other branch, seed=$seed")
        }
    }

    // ── settled / recover interplay ─────────────────────────────────────────

    @Test
    fun `settled converts failure into a failure Result with the original error`() = runTest {
        checkAll(Arb.int()) { seed ->
            val err = IllegalStateException("boom-$seed")
            val result = Kap.failed(err).settled().evalGraph()
            assertTrue(result.isFailure, "seed=$seed")
            assertSameFailure(err, result.exceptionOrNull(), "settled must capture the original failure", seed)
        }
    }

    @Test
    fun `settled commutes with map over both outcomes`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k ->
            val f: (Int) -> String = { "v=${it + k}" }

            val viaSettledFirst = Kap.of(x).settled().evalGraph().map { f(it) }
            val viaMapFirst = Kap.of(x).map(f).settled().evalGraph()
            assertEquals(viaSettledFirst, viaMapFirst, "k=$k")
        }
    }

    @Test
    fun `recover absorbs failure into an arbitrary fallback value`() = runTest {
        checkAll(Arb.int(), Arb.string()) { seed, fallback ->
            val result = Kap.failed(IllegalStateException("boom-$seed"))
                .recover { fallback }
                .evalGraph()
            assertEquals(fallback, result, "seed=$seed")
        }
    }

    @Test
    fun `orElse picks the fallback graph when the primary fails`() = runTest {
        checkAll(Arb.int(), Arb.string()) { seed, fallback ->
            val result = (Kap.failed(IllegalStateException("boom-$seed")) orElse Kap.of(fallback))
                .evalGraph()
            assertEquals(fallback, result, "seed=$seed")
        }
    }

    private fun err(seed: Int) = IllegalStateException("boom-$seed")

    /**
     * kotlinx-coroutines recovers stack traces by copying exceptions when they
     * cross coroutine boundaries — same class and message, different instance.
     * The law therefore asserts type and message, not JVM identity.
     */
    private fun assertSameFailure(
        expected: IllegalStateException,
        actual: Throwable?,
        description: String,
        seed: Int,
    ) {
        assertTrue(
            actual is IllegalStateException && actual.message == expected.message,
            "$description, seed=$seed — expected $expected but was $actual",
        )
    }
}
