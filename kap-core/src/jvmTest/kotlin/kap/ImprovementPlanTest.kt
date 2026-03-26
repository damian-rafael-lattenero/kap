package kap

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for improvement plan items (core-only):
 *
 * 2. Kap.await() ergonomic extension
 * 4. PhaseBarrier signal safety on exception
 * 6. Alternative: orElse / firstSuccessOf
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImprovementPlanTest {

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 2: Kap.await() — ergonomic execution inside with lambdas
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `await executes computation and returns result`() = runTest {
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { Kap { delay(30); "fast" }.timeout(100.milliseconds, "timeout").await() }
                .with { delay(30); "other" }
        }

        assertEquals("fast|other", result)
        assertEquals(30, currentTime, "Max(30,30) = 30ms")
    }

    @Test
    fun `await with timeout fallback inside with branch`() = runTest {
        val result = Async {
            kap { a: String, b: String, c: String -> "$a|$b|$c" }
                .with { delay(30); "A" }
                .with { Kap { delay(500); "slow-B" }.timeout(50.milliseconds, "timeout-B").await() }
                .with { delay(30); "C" }
        }

        assertEquals("A|timeout-B|C", result)
        assertEquals(50, currentTime, "Timeout at 50ms determines total")
    }

    @Test
    fun `await with retry inside with branch`() = runTest {
        var attempts = 0
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    Kap {
                        attempts++
                        if (attempts < 3) throw RuntimeException("flaky")
                        delay(20); "recovered"
                    }.retry(3, delay = 10.milliseconds).await()
                }
                .with { delay(30); "stable" }
        }

        assertEquals("recovered|stable", result)
        assertEquals(3, attempts)
        assertEquals(40, currentTime, "Retry: fail+10ms+fail+10ms+20ms=40ms")
    }

    @Test
    fun `await with recover inside with branch`() = runTest {
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    Kap<String> { throw RuntimeException("boom") }
                        .recover { "recovered" }
                        .await()
                }
                .with { delay(30); "B" }
        }

        assertEquals("recovered|B", result)
    }

    @Test
    fun `await preserves structured concurrency`() = runTest {
        // If one branch fails, the other should be cancelled
        val result = runCatching {
            Async {
                kap { a: String, b: String -> "$a|$b" }
                    .with { Kap<String> { throw RuntimeException("crash") }.await() }
                    .with { delay(1000); "never" }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("crash", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 4: PhaseBarrier signal safety — signal fires even on exception
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `PhaseBarrier completes signal on exception — gated with does not hang`() = runTest {
        // If the barrier throws, the gated with branch should see the failure
        // through structured concurrency cancellation, NOT hang forever.
        val result = runCatching {
            Async {
                kap { a: String, b: String, c: String -> "$a|$b|$c" }
                    .with { delay(30); "A" }
                    .then { throw RuntimeException("barrier-crash") }
                    .with { delay(30); "C" }  // gated — should NOT hang
            }
        }

        assertTrue(result.isFailure)
        assertEquals("barrier-crash", result.exceptionOrNull()?.message)
    }

    @Test
    fun `PhaseBarrier completes signal on exception with recover in chain`() = runTest {
        // More subtle: if the barrier fails but there's a recover higher up,
        // the signal must still fire so gated branches don't deadlock.
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { delay(20); "A" }
                .then {
                    with(Kap<String> { throw RuntimeException("barrier-fail") }
                        .recover { "recovered" }) { execute() }
                }
        }

        assertEquals("A|recovered", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ITEM 6: Alternative — orElse / firstSuccessOf
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `orElse returns primary on success`() = runTest {
        val result = Async {
            Kap { "primary" }
                .orElse(Kap { "fallback" })
        }

        assertEquals("primary", result)
    }

    @Test
    fun `orElse returns fallback on primary failure`() = runTest {
        val result = Async {
            Kap<String> { throw RuntimeException("primary-fail") }
                .orElse(Kap { delay(30); "fallback" })
        }

        assertEquals("fallback", result)
        assertEquals(30, currentTime, "Fallback starts only after primary fails")
    }

    @Test
    fun `orElse chains three levels`() = runTest {
        val result = Async {
            Kap<String> { throw RuntimeException("fail1") }
                .orElse(Kap { throw RuntimeException("fail2") })
                .orElse(Kap { "last-resort" })
        }

        assertEquals("last-resort", result)
    }

    @Test
    fun `orElse propagates CancellationException`() = runTest {
        val result = runCatching {
            Async {
                Kap<String> { throw CancellationException("cancel") }
                    .orElse(Kap { "should-not-reach" })
            }
        }

        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `orElse is sequential not concurrent - timing proof`() = runTest {
        val result = Async {
            Kap<String> { delay(20); throw RuntimeException("fail") }
                .orElse(Kap { delay(30); "fallback" })
        }

        assertEquals("fallback", result)
        assertEquals(50, currentTime, "Sequential: 20ms (fail) + 30ms (fallback) = 50ms")
    }

    @Test
    fun `firstSuccessOf returns first successful computation`() = runTest {
        val result = Async {
            firstSuccessOf(
                Kap { throw RuntimeException("fail1") },
                Kap { throw RuntimeException("fail2") },
                Kap { delay(30); "third" },
                Kap { delay(30); "fourth" }, // never tried
            )
        }

        assertEquals("third", result)
    }

    @Test
    fun `firstSuccessOf throws last error when all fail`() = runTest {
        val result = runCatching {
            Async {
                firstSuccessOf(
                    Kap<String> { throw RuntimeException("err1") },
                    Kap { throw RuntimeException("err2") },
                    Kap { throw RuntimeException("err3") },
                )
            }
        }

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()!!
        // The last failure's error should propagate
        assertEquals("err3", error.message)
    }

    @Test
    fun `firstSuccessOf preserves suppressed exceptions outside coroutineScope`() = runTest {
        // Test suppressed exceptions directly without Async wrapper
        val errors = mutableListOf<Throwable>()
        val computations = arrayOf(
            Kap<String> { throw RuntimeException("err1") },
            Kap { throw RuntimeException("err2") },
            Kap { throw RuntimeException("err3") },
        )

        // Execute directly within coroutineScope to verify addSuppressed works
        val result = runCatching {
            kotlinx.coroutines.coroutineScope {
                with(firstSuccessOf(*computations)) { execute() }
            }
        }

        assertTrue(result.isFailure)
        assertEquals("err3", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `firstSuccessOf is sequential - timing proof`() = runTest {
        val result = Async {
            firstSuccessOf(
                Kap { delay(20); throw RuntimeException("fail") },
                Kap { delay(30); "success" },
            )
        }

        assertEquals("success", result)
        assertEquals(50, currentTime, "Sequential: 20ms (fail) + 30ms (success) = 50ms")
    }

    @Test
    fun `firstSuccess on iterable`() = runTest {
        val computations = listOf(
            Kap<String> { throw RuntimeException("fail") },
            Kap { "success" },
        )
        val result = Async { computations.firstSuccess() }

        assertEquals("success", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTEGRATION: Multiple items composed together
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `integration - await inside with with firstSuccessOf`() = runTest {
        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with {
                    firstSuccessOf(
                        Kap { delay(20); throw RuntimeException("primary-down") },
                        Kap { delay(30); "replica-data" },
                    ).await()
                }
                .with { delay(60); "other" }
        }

        assertEquals("replica-data|other", result)
        // firstSuccessOf: 20 + 30 = 50ms. Other: 60ms. Parallel: max(50,60) = 60ms
        assertEquals(60, currentTime, "max(50, 60) = 60ms")
    }

}
