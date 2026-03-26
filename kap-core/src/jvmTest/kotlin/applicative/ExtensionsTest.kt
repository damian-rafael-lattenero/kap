package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionsTest {

    // ════════════════════════════════════════════════════════════════════════
    // void
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `void discards result and returns Unit`() = runTest {
        val result = Async { Effect { 42 }.discard() }
        assertEquals(Unit, result)
    }

    @Test
    fun `void propagates failure`() = runTest {
        val result = runCatching {
            Async { Effect<Int> { throw RuntimeException("boom") }.discard() }
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // settled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `settled wraps success in Result success`() = runTest {
        val result = Async { Effect { 42 }.settled() }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `settled wraps failure in Result failure`() = runTest {
        val result = Async {
            Effect<Int> { throw IllegalStateException("bad") }.settled()
        }
        assertTrue(result.isFailure)
        assertEquals("bad", result.exceptionOrNull()?.message)
    }

    @Test
    fun `settled does not catch CancellationException`() = runTest {
        val started = CompletableDeferred<Unit>()
        val comp = Effect<Int> {
            started.complete(Unit)
            awaitCancellation()
        }.settled()
        val job = launch { Async.invoke<Result<Int>> { comp } }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled, "Job should be cancelled, not caught by settled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // tap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `tap executes side-effect and returns original value`() = runTest {
        val sideEffects = mutableListOf<String>()
        val result = Async {
            Effect { "hello" }
                .peek { sideEffects.add("saw: $it") }
        }
        assertEquals("hello", result)
        assertEquals(listOf("saw: hello"), sideEffects)
    }

    @Test
    fun `tap failure in side-effect propagates`() = runTest {
        val result = runCatching {
            Async {
                Effect { "hello" }
                    .peek { throw RuntimeException("side-effect failed") }
            }
        }
        assertTrue(result.isFailure)
        assertEquals("side-effect failed", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipLeft / zipRight
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zipLeft runs both in parallel and returns left result`() = runTest {
        val result = Async {
            Effect { delay(50); "left" }
                .keepFirst(Effect { delay(50); "right" })
        }
        assertEquals("left", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipRight runs both in parallel and returns right result`() = runTest {
        val result = Async {
            Effect { delay(50); "left" }
                .keepSecond(Effect { delay(50); "right" })
        }
        assertEquals("right", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipLeft propagates failure from right side`() = runTest {
        val result = runCatching {
            Async {
                Effect { delay(100); "left" }
                    .keepFirst(Effect<String> { throw RuntimeException("right failed") })
            }
        }
        assertTrue(result.isFailure)
        assertEquals("right failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zipRight propagates failure from left side`() = runTest {
        val result = runCatching {
            Async {
                Effect<String> { throw RuntimeException("left failed") }
                    .keepSecond(Effect { delay(100); "right" })
            }
        }
        assertTrue(result.isFailure)
        assertEquals("left failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zipLeft cancels other on failure`() = runTest {
        val cancelled = CompletableDeferred<Boolean>()
        val result = runCatching {
            Async {
                Effect<String> { delay(10); throw RuntimeException("boom") }
                    .keepFirst(Effect {
                        try { awaitCancellation() }
                        catch (e: CancellationException) { cancelled.complete(true); throw e }
                    })
            }
        }
        assertTrue(result.isFailure)
        assertTrue(cancelled.await(), "Right side should be cancelled when left fails")
    }
}
