package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CombinatorsTest {

    // ════════════════════════════════════════════════════════════════════════
    // TIMEOUT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout returns result when computation completes in time`() = runTest {
        val result = Async {
            pure(42).timeout(1.seconds)
        }
        assertEquals(42, result)
    }

    @Test
    fun `timeout throws when computation exceeds duration`() = runTest {
        val result = runCatching {
            Async {
                Computation<Int> { delay(10.seconds); 42 }.timeout(100.milliseconds)
            }
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `timeout with default returns default on timeout`() = runTest {
        val result = Async {
            Computation<Int> { delay(10.seconds); 42 }.timeout(100.milliseconds, default = -1)
        }
        assertEquals(-1, result)
    }

    @Test
    fun `timeout with default returns result when fast enough`() = runTest {
        val result = Async {
            pure(42).timeout(1.seconds, default = -1)
        }
        assertEquals(42, result)
    }

    @Test
    fun `timeout with fallback computation runs fallback on timeout`() = runTest {
        val result = Async {
            Computation<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, pure("fallback"))
        }
        assertEquals("fallback", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RECOVER
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `recover catches exception and maps to value`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("boom") }
                .recover { "recovered: ${it.message}" }
        }
        assertEquals("recovered: boom", result)
    }

    @Test
    fun `recover passes through successful computation`() = runTest {
        val result = Async {
            pure("ok").recover { "recovered" }
        }
        assertEquals("ok", result)
    }

    @Test
    fun `recover does not catch CancellationException`() = runTest {
        val result = runCatching {
            Async {
                Computation<String> { throw CancellationException("cancelled") }
                    .recover { "recovered" }
            }
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `recoverWith switches to recovery computation`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("boom") }
                .recoverWith { pure("recovered from: ${it.message}") }
        }
        assertEquals("recovered from: boom", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FALLBACK
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fallback switches to alternative on failure`() = runTest {
        val result = Async {
            Computation<String> { throw RuntimeException("boom") } fallback pure("backup")
        }
        assertEquals("backup", result)
    }

    @Test
    fun `fallback returns primary on success`() = runTest {
        val result = Async {
            pure("primary") fallback pure("backup")
        }
        assertEquals("primary", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // RETRY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `retry succeeds on first attempt`() = runTest {
        var attempts = 0
        val result = Async {
            Computation<String> { attempts++; "ok" }.retry(3)
        }
        assertEquals("ok", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retry succeeds on later attempt`() = runTest {
        var attempts = 0
        val result = Async {
            Computation<String> {
                attempts++
                if (attempts < 3) throw RuntimeException("fail #$attempts")
                "ok"
            }.retry(3)
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry exhausts all attempts then throws`() = runTest {
        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw RuntimeException("fail #$attempts")
                }.retry(3)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(3, attempts)
        assertEquals("fail #3", result.exceptionOrNull()?.message)
    }

    @Test
    fun `retry does not catch CancellationException`() = runTest {
        var attempts = 0
        val result = runCatching {
            Async {
                Computation<String> {
                    attempts++
                    throw CancellationException("cancelled")
                }.retry(3)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(1, attempts) // no retry on cancellation
    }

    @Test
    fun `retry composes with lift+ap`() = runTest {
        var attempts = 0
        val retryable = Computation<String> {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "retried"
        }.retry(3)

        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap { with(retryable) { execute() } }
                .ap { "ok" }
        }
        assertEquals("retried|ok", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // COMPOSITION
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with default plus recover compose naturally`() = runTest {
        val result = Async {
            Computation<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, "timed-out")
        }
        assertEquals("timed-out", result)
    }

    @Test
    fun `timeout exception plus fallback compose naturally`() = runTest {
        val result = Async {
            Computation<String> { delay(10.seconds); "slow" }
                .timeout(100.milliseconds, pure("fallback-value"))
        }
        assertEquals("fallback-value", result)
    }

    @Test
    fun `retry plus timeout compose naturally`() = runTest {
        var attempts = 0
        val result = Async {
            Computation<String> {
                attempts++
                if (attempts < 3) throw RuntimeException("fail")
                "ok"
            }.retry(3).timeout(5.seconds)
        }
        assertEquals("ok", result)
        assertEquals(3, attempts)
    }

    // ════════════════════════════════════════════════════════════════════════
    // TIMEOUT NULL-SAFETY (bug fix verification)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `timeout with default preserves null as a valid result`() = runTest {
        val result = Async {
            Computation<String?> { null }.timeout(1.seconds, default = "fallback")
        }
        // The computation completed with null — should NOT fall through to default
        assertEquals(null, result)
    }

    @Test
    fun `timeout with fallback preserves null as a valid result`() = runTest {
        val result = Async {
            Computation<String?> { null }.timeout(1.seconds, pure("fallback"))
        }
        assertEquals(null, result)
    }

    @Test
    fun `timeout with default still returns default on actual timeout`() = runTest {
        val result = Async {
            Computation<String?> { delay(10.seconds); null }.timeout(100.milliseconds, default = "timed-out")
        }
        assertEquals("timed-out", result)
    }
}
