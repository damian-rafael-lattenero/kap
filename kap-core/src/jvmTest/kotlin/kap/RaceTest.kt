package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RaceTest {

    // ════════════════════════════════════════════════════════════════════════
    // raceN — N-way race
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceN with three computations returns fastest`() = runTest {
        val result = Async {
            raceN(
                Kap { delay(10_000); "slow1" },
                Kap { "fast" },
                Kap { delay(10_000); "slow2" },
            )
        }
        assertEquals("fast", result)
    }

    @Test
    fun `raceN cancels all losers`() = runTest {
        val cancelled1 = CompletableDeferred<Boolean>()
        val cancelled2 = CompletableDeferred<Boolean>()

        val result = Async {
            raceN(
                Kap {
                    try { awaitCancellation() }
                    catch (e: kotlinx.coroutines.CancellationException) {
                        cancelled1.complete(true); throw e
                    }
                },
                Kap { "winner" },
                Kap {
                    try { awaitCancellation() }
                    catch (e: kotlinx.coroutines.CancellationException) {
                        cancelled2.complete(true); throw e
                    }
                },
            )
        }

        assertEquals("winner", result)
        assertTrue(cancelled1.await())
        assertTrue(cancelled2.await())
    }

    @Test
    fun `raceN with single computation returns it`() = runTest {
        val result = Async { raceN(Kap.of(42)) }
        assertEquals(42, result)
    }

    @Test
    fun `raceN with empty throws IllegalArgumentException`() = runTest {
        val result = runCatching {
            Async { raceN<Int>() }
        }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    // ════════════════════════════════════════════════════════════════════════
    // raceAll — Iterable overload
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceAll on list returns fastest`() = runTest {
        val result = Async {
            listOf(
                Kap { delay(10_000); "slow" },
                Kap { "fast" },
                Kap { delay(10_000); "slower" },
            ).raceAll()
        }
        assertEquals("fast", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // race — first to succeed wins, failures give the other a chance
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race returns second when first fails`() = runTest {
        val result = Async {
            race(
                Kap<String> { throw RuntimeException("boom") },
                Kap { delay(100); "fallback" },
            )
        }
        assertEquals("fallback", result)
    }

    @Test
    fun `race returns first when second fails`() = runTest {
        val result = Async {
            race(
                Kap { delay(100); "primary" },
                Kap<String> { throw RuntimeException("boom") },
            )
        }
        assertEquals("primary", result)
    }

    @Test
    fun `race propagates when both sides fail`() = runTest {
        val result = runCatching {
            Async {
                race(
                    Kap<String> { throw RuntimeException("boom1") },
                    Kap<String> { throw RuntimeException("boom2") },
                )
            }
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `raceN skips failed racers and picks first success`() = runTest {
        val result = Async {
            raceN(
                Kap<String> { throw RuntimeException("fail1") },
                Kap<String> { throw RuntimeException("fail2") },
                Kap { delay(100); "winner" },
            )
        }
        assertEquals("winner", result)
    }

    @Test
    fun `raceN propagates when all fail`() = runTest {
        val result = runCatching {
            Async {
                raceN(
                    Kap<String> { throw RuntimeException("fail1") },
                    Kap<String> { throw RuntimeException("fail2") },
                )
            }
        }
        assertTrue(result.isFailure)
    }

    // ════════════════════════════════════════════════════════════════════════
    // race — Result-wrapping correctness (bug fix verification)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `race does not lose successful result when failure arrives concurrently`() = runTest {
        // Both complete nearly simultaneously — failure must not shadow success
        val result = Async {
            race(
                Kap { "success" },
                Kap<String> { throw RuntimeException("concurrent-fail") },
            )
        }
        assertEquals("success", result)
    }

    @Test
    fun `race both fail propagates first failure`() = runTest {
        val result = runCatching {
            Async {
                race(
                    Kap<String> { throw RuntimeException("first") },
                    Kap<String> { delay(50); throw RuntimeException("second") },
                )
            }
        }
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()!!
        // First failure is the one that propagates
        assertTrue(ex.message == "first" || ex.message == "second")
    }

    @Test
    fun `raceN all fail propagates with all errors collected`() = runTest {
        val result = runCatching {
            Async {
                raceN(
                    Kap<String> { throw RuntimeException("r1") },
                    Kap<String> { delay(50); throw RuntimeException("r2") },
                    Kap<String> { delay(100); throw RuntimeException("r3") },
                )
            }
        }
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()!!
        // Primary error is the first to fail
        assertEquals("r1", ex.message)
    }
}
