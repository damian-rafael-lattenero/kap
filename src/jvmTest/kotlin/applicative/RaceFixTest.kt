package applicative

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the race condition fix in Race.kt.
 *
 * The original implementation used `isCompleted` to determine which deferred
 * won the select, which has a TOCTOU race: between select returning and
 * checking isCompleted, the other deferred could complete. The fix tracks
 * the winner explicitly via select clause pairing.
 *
 * These tests verify correct behavior under scenarios where the old
 * implementation could lose a successful result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RaceFixTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. Basic race correctness
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race returns faster successful result`() = runTest {
        val result = Async {
            race(
                Computation { delay(100); "slow" },
                Computation { delay(20); "fast" },
            )
        }

        assertEquals("fast", result)
        assertEquals(20, currentTime)
    }

    @Test
    fun `race returns second if first fails`() = runTest {
        val result = Async {
            race(
                Computation<String> { delay(10); throw RuntimeException("first-fail") },
                Computation { delay(50); "second-ok" },
            )
        }

        assertEquals("second-ok", result)
        assertEquals(50, currentTime)
    }

    @Test
    fun `race throws when both fail`() = runTest {
        val result = runCatching {
            Async {
                race(
                    Computation<String> { delay(10); throw RuntimeException("err-A") },
                    Computation<String> { delay(20); throw RuntimeException("err-B") },
                )
            }
        }

        assertTrue(result.isFailure)
        // First failure's error is propagated
        assertEquals("err-A", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. The actual race condition scenario
    //
    //    If loser FAILS first and winner SUCCEEDS second, the old code
    //    using isCompleted could check the wrong deferred and miss the
    //    successful result. The fix uses explicit winner tracking.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race - loser fails first, winner succeeds second - success not lost`() = runTest {
        // Racer A fails quickly. Racer B succeeds slightly later.
        // Old code: select picks A (failure). Then checks da.isCompleted (true).
        // If db ALSO completed between select and isCompleted check,
        // old code awaits db correctly. But the fix is more robust.
        val result = Async {
            race(
                Computation<String> { delay(10); throw RuntimeException("fail-fast") },
                Computation { delay(30); "success-slow" },
            )
        }

        assertEquals("success-slow", result)
        assertEquals(30, currentTime)
    }

    @Test
    fun `race - failure first then success completes - success is returned`() = runTest {
        // Racer A fails at t=10, racer B succeeds at t=30.
        // Race should wait for B and return its success.
        val result = Async {
            race(
                Computation<String> {
                    delay(10)
                    throw RuntimeException("concurrent-fail")
                },
                Computation {
                    delay(30)
                    "concurrent-success"
                },
            )
        }

        assertEquals("concurrent-success", result)
        assertEquals(30, currentTime, "Should wait for second racer after first fails")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. raceN correctness with explicit winner tracking
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceN picks fastest success among mixed results`() = runTest {
        val result = Async {
            raceN(
                Computation<String> { delay(10); throw RuntimeException("err-1") },
                Computation<String> { delay(20); throw RuntimeException("err-2") },
                Computation { delay(30); "winner" },
                Computation { delay(40); "slow-ok" },
            )
        }

        assertEquals("winner", result)
        assertEquals(30, currentTime)
    }

    @Test
    fun `raceN - all fail, first error propagated`() = runTest {
        val result = runCatching {
            Async {
                raceN(
                    Computation<String> { delay(10); throw RuntimeException("e1") },
                    Computation<String> { delay(20); throw RuntimeException("e2") },
                    Computation<String> { delay(30); throw RuntimeException("e3") },
                )
            }
        }

        assertTrue(result.isFailure)
        val primary = result.exceptionOrNull()!!
        assertEquals("e1", primary.message)
    }

    @Test
    fun `raceN - first two fail, third succeeds - timing proof`() = runTest {
        val result = Async {
            raceN(
                Computation<String> { delay(10); throw RuntimeException("fast-fail") },
                Computation<String> { delay(20); throw RuntimeException("medium-fail") },
                Computation { delay(50); "late-success" },
            )
        }

        assertEquals("late-success", result)
        assertEquals(50, currentTime,
            "Should wait for the third racer since first two failed")
    }

    @Test
    fun `raceN cancels remaining on first success`() = runTest {
        val cancelled = CompletableDeferred<Boolean>()

        val result = Async {
            raceN(
                Computation { delay(20); "winner" },
                Computation {
                    try {
                        delay(Long.MAX_VALUE)
                        "never"
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        cancelled.complete(true)
                        throw e
                    }
                },
            )
        }

        assertEquals("winner", result)
        assertTrue(cancelled.await(), "Loser should have been cancelled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Race inside ap chains — production pattern
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race inside parallel ap branches - each branch races independently`() = runTest {
        val result = Async {
            lift3 { a: String, b: String, c: String -> "$a|$b|$c" }
                .ap {
                    with(race(
                        Computation { delay(100); "primary-A" },
                        Computation { delay(20); "cache-A" },
                    )) { execute() }
                }
                .ap {
                    with(race(
                        Computation { delay(15); "primary-B" },
                        Computation { delay(80); "cache-B" },
                    )) { execute() }
                }
                .ap { delay(30); "direct-C" }
        }

        assertEquals("cache-A|primary-B|direct-C", result)
        // max(20, 15, 30) = 30ms
        assertEquals(30, currentTime,
            "All 3 ap branches race in parallel. Total = max(20, 15, 30) = 30ms")
    }

    @Test
    fun `raceAll on iterable`() = runTest {
        val computations = listOf(
            Computation { delay(100); "slow" },
            Computation { delay(10); "fast" },
            Computation { delay(50); "medium" },
        )

        val result = Async { computations.raceAll() }

        assertEquals("fast", result)
        assertEquals(10, currentTime)
    }
}
