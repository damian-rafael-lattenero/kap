package kap

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
        val result = Kap { 42 }.discard().executeGraph()
        assertEquals(Unit, result)
    }

    @Test
    fun `void propagates failure`() = runTest {
        val result = runCatching {
            Kap<Int> { throw RuntimeException("boom") }.discard().executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // settled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `settled wraps success in Result success`() = runTest {
        val result = Kap { 42 }.settled().executeGraph()
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `settled wraps failure in Result failure`() = runTest {
        val result = Kap<Int> { throw IllegalStateException("bad") }.settled().executeGraph()
        assertTrue(result.isFailure)
        assertEquals("bad", result.exceptionOrNull()?.message)
    }

    @Test
    fun `settled does not catch CancellationException`() = runTest {
        val started = CompletableDeferred<Unit>()
        val comp = Kap<Int> {
            started.complete(Unit)
            awaitCancellation()
        }.settled()
        val job = launch { comp.executeGraph() }
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
        val sideKaps = mutableListOf<String>()
        val result = Kap { "hello" }
                .peek { sideKaps.add("saw: $it") }.executeGraph()
        assertEquals("hello", result)
        assertEquals(listOf("saw: hello"), sideKaps)
    }

    @Test
    fun `tap failure in side-effect propagates`() = runTest {
        val result = runCatching {
            Kap { "hello" }
                    .peek { throw RuntimeException("side-effect failed") }.executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals("side-effect failed", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // zipLeft / zipRight
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zipLeft runs both in parallel and returns left result`() = runTest {
        val result = Kap { delay(50); "left" }
                .keepFirst(Kap { delay(50); "right" }).executeGraph()
        assertEquals("left", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipRight runs both in parallel and returns right result`() = runTest {
        val result = Kap { delay(50); "left" }
                .keepSecond(Kap { delay(50); "right" }).executeGraph()
        assertEquals("right", result)
        assertEquals(50, currentTime, "Both should run in parallel (50ms, not 100ms)")
    }

    @Test
    fun `zipLeft propagates failure from right side`() = runTest {
        val result = runCatching {
            Kap { delay(100); "left" }
                    .keepFirst(Kap<String> { throw RuntimeException("right failed") }).executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals("right failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zipRight propagates failure from left side`() = runTest {
        val result = runCatching {
            Kap<String> { throw RuntimeException("left failed") }
                    .keepSecond(Kap { delay(100); "right" }).executeGraph()
        }
        assertTrue(result.isFailure)
        assertEquals("left failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `zipLeft cancels other on failure`() = runTest {
        val cancelled = CompletableDeferred<Boolean>()
        val result = runCatching {
            Kap<String> { delay(10); throw RuntimeException("boom") }
                    .keepFirst(Kap {
                        try { awaitCancellation() }
                        catch (e: CancellationException) { cancelled.complete(true); throw e }
                    }).executeGraph()
        }
        assertTrue(result.isFailure)
        assertTrue(cancelled.await(), "Right side should be cancelled when left fails")
    }
}
