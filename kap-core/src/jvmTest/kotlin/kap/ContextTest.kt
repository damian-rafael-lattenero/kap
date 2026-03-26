package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContextTest {

    // ════════════════════════════════════════════════════════════════════════
    // Async(context) — global context
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Async with context runs on specified dispatcher`() = runTest {
        val threadName = Async(Dispatchers.Default) {
            Kap { Thread.currentThread().name }
        }
        // Default dispatcher uses "DefaultDispatcher-worker-N" threads
        assertTrue(threadName.contains("DefaultDispatcher"), "Expected DefaultDispatcher thread, got: $threadName")
    }

    @Test
    fun `Async without context still works`() = runTest {
        val result = Async {
            Kap.of(42)
        }
        assertEquals(42, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .on(context) — per-computation context
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on switches computation to specified context`() = runTest {
        val threadName = Async {
            Kap { Thread.currentThread().name }.on(Dispatchers.Default)
        }
        assertTrue(threadName.contains("DefaultDispatcher"), "Expected DefaultDispatcher thread, got: $threadName")
    }

    @Test
    fun `on composes with kap+with - each branch can have different context`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val compA = Kap<String> {
            latchA.complete(Unit)
            latchB.await()
            "A"
        }.on(Dispatchers.Default)

        val compB = Kap<String> {
            latchB.complete(Unit)
            latchA.await()
            "B"
        }.on(Dispatchers.Default)

        val result = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { with(compA) { execute() } }
                .with { with(compB) { execute() } }
        }

        // Proves parallelism still works with .on()
        assertEquals("A|B", result)
    }

    @Test
    fun `on does not affect other computations in the chain`() = runTest {
        // One computation on Default, rest inherit parent context
        val compA = Kap { 21 }.on(Dispatchers.Default)
        val result = Async {
            kap { a: Int, b: Int -> a + b }
                .with { with(compA) { execute() } }
                .with { 21 }
        }
        assertEquals(42, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // context — reading CoroutineContext inside computations
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `context captures the current coroutine context`() = runTest {
        val result = Async(CoroutineName("test-context")) {
            context.map { ctx -> ctx[CoroutineName]?.name }
        }

        assertEquals("test-context", result)
    }

    @Test
    fun `context composes with andThen for trace propagation`() = runTest {
        val result = Async(CoroutineName("trace-123")) {
            context.andThen { ctx ->
                val traceName = ctx[CoroutineName]?.name ?: "unknown"
                kap { a: String, b: String -> "$a|$b|trace=$traceName" }
                    .with { "user" }
                    .with { "cart" }
            }
        }

        assertEquals("user|cart|trace=trace-123", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Async(context) — structured concurrency guarantee
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Async with context cancels siblings on failure`() = runTest {
        val siblingCancelled = CompletableDeferred<Boolean>()
        val siblingStarted = CompletableDeferred<Unit>()

        val result = runCatching {
            Async(CoroutineName("test-cancel")) {
                kap { a: String, b: String -> "$a|$b" }
                    .with {
                        try {
                            siblingStarted.complete(Unit)
                            kotlinx.coroutines.awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            siblingCancelled.complete(true)
                            throw e
                        }
                    }
                    .with {
                        siblingStarted.await()
                        throw RuntimeException("fast-fail")
                    }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(siblingCancelled.await(), "Sibling should be cancelled even with context override")
    }
}
