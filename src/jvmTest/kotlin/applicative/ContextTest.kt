package applicative

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
            Computation { Thread.currentThread().name }
        }
        // Default dispatcher uses "DefaultDispatcher-worker-N" threads
        assertTrue(threadName.contains("DefaultDispatcher"), "Expected DefaultDispatcher thread, got: $threadName")
    }

    @Test
    fun `Async without context still works`() = runTest {
        val result = Async {
            pure(42)
        }
        assertEquals(42, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .on(context) — per-computation context
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on switches computation to specified context`() = runTest {
        val threadName = Async {
            Computation { Thread.currentThread().name }.on(Dispatchers.Default)
        }
        assertTrue(threadName.contains("DefaultDispatcher"), "Expected DefaultDispatcher thread, got: $threadName")
    }

    @Test
    fun `on composes with lift+ap - each branch can have different context`() = runTest {
        val latchA = CompletableDeferred<Unit>()
        val latchB = CompletableDeferred<Unit>()

        val compA = Computation<String> {
            latchA.complete(Unit)
            latchB.await()
            "A"
        }.on(Dispatchers.Default)

        val compB = Computation<String> {
            latchB.complete(Unit)
            latchA.await()
            "B"
        }.on(Dispatchers.Default)

        val result = Async {
            lift2 { a: String, b: String -> "$a|$b" }
                .ap { with(compA) { execute() } }
                .ap { with(compB) { execute() } }
        }

        // Proves parallelism still works with .on()
        assertEquals("A|B", result)
    }

    @Test
    fun `on does not affect other computations in the chain`() = runTest {
        // One computation on Default, rest inherit parent context
        val compA = Computation { 21 }.on(Dispatchers.Default)
        val result = Async {
            lift2 { a: Int, b: Int -> a + b }
                .ap { with(compA) { execute() } }
                .ap { 21 }
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
    fun `context composes with flatMap for trace propagation`() = runTest {
        val result = Async(CoroutineName("trace-123")) {
            context.flatMap { ctx ->
                val traceName = ctx[CoroutineName]?.name ?: "unknown"
                lift2 { a: String, b: String -> "$a|$b|trace=$traceName" }
                    .ap { "user" }
                    .ap { "cart" }
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
                lift2 { a: String, b: String -> "$a|$b" }
                    .ap {
                        try {
                            siblingStarted.complete(Unit)
                            kotlinx.coroutines.awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            siblingCancelled.complete(true)
                            throw e
                        }
                    }
                    .ap {
                        siblingStarted.await()
                        throw RuntimeException("fast-fail")
                    }
            }
        }

        assertTrue(result.isFailure)
        assertTrue(siblingCancelled.await(), "Sibling should be cancelled even with context override")
    }
}
