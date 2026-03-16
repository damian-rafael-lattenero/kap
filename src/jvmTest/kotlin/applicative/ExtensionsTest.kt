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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionsTest {

    // ════════════════════════════════════════════════════════════════════════
    // void
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `void discards result and returns Unit`() = runTest {
        val result = Async { Computation { 42 }.void() }
        assertEquals(Unit, result)
    }

    @Test
    fun `void propagates failure`() = runTest {
        val result = runCatching {
            Async { Computation<Int> { throw RuntimeException("boom") }.void() }
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // attempt
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `attempt wraps success in Right`() = runTest {
        val result = Async { Computation { 42 }.attempt() }
        assertIs<Either.Right<Int>>(result)
        assertEquals(42, result.value)
    }

    @Test
    fun `attempt wraps failure in Left`() = runTest {
        val result = Async {
            Computation<Int> { throw IllegalStateException("bad") }.attempt()
        }
        assertIs<Either.Left<Throwable>>(result)
        assertEquals("bad", result.value.message)
    }

    @Test
    fun `attempt does not catch CancellationException`() = runTest {
        val started = CompletableDeferred<Unit>()
        val comp = Computation<Int> {
            started.complete(Unit)
            awaitCancellation()
        }.attempt()
        val job = launch { Async.invoke<Either<Throwable, Int>> { comp } }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled, "Job should be cancelled, not caught by attempt")
    }

    // ════════════════════════════════════════════════════════════════════════
    // tap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `tap executes side-effect and returns original value`() = runTest {
        val sideEffects = mutableListOf<String>()
        val result = Async {
            Computation { "hello" }
                .tap { sideEffects.add("saw: $it") }
        }
        assertEquals("hello", result)
        assertEquals(listOf("saw: hello"), sideEffects)
    }

    @Test
    fun `tap failure in side-effect propagates`() = runTest {
        val result = runCatching {
            Async {
                Computation { "hello" }
                    .tap { throw RuntimeException("side-effect failed") }
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
            Computation { delay(50); "left" }
                .zipLeft(Computation { delay(50); "right" })
        }
        assertEquals("left", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipRight runs both in parallel and returns right result`() = runTest {
        val result = Async {
            Computation { delay(50); "left" }
                .zipRight(Computation { delay(50); "right" })
        }
        assertEquals("right", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipLeft propagates failure from right side`() = runTest {
        val result = runCatching {
            Async {
                Computation { delay(100); "left" }
                    .zipLeft(Computation<String> { throw RuntimeException("right failed") })
            }
        }
        assertTrue(result.isFailure)
        assertEquals("right failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zipRight propagates failure from left side`() = runTest {
        val result = runCatching {
            Async {
                Computation<String> { throw RuntimeException("left failed") }
                    .zipRight(Computation { delay(100); "right" })
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
                Computation<String> { delay(10); throw RuntimeException("boom") }
                    .zipLeft(Computation {
                        try { awaitCancellation() }
                        catch (e: CancellationException) { cancelled.complete(true); throw e }
                    })
            }
        }
        assertTrue(result.isFailure)
        assertTrue(cancelled.await(), "Right side should be cancelled when left fails")
    }
}
