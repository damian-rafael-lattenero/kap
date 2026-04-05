package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTest {

    // ════════════════════════════════════════════════════════════════════════
    // Async(context) — global context
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Async with context runs on specified dispatcher`() = runTest {
        val threadName = withContext(Dispatchers.Default) { Kap { Thread.currentThread().name }.evalGraph() }
        // Default dispatcher uses "DefaultDispatcher-worker-N" threads
        assertTrue(threadName.contains("DefaultDispatcher"), "Expected DefaultDispatcher thread, got: $threadName")
    }

    @Test
    fun `Async without context still works`() = runTest {
        val result = Kap.of(42).evalGraph()
        assertEquals(42, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // .on(context) — per-computation context
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `on switches computation to specified context`() = runTest {
        val threadName = Kap { Thread.currentThread().name }.on(Dispatchers.Default).evalGraph()
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

        val result = Kap.of { a: String -> { b: String -> "$a|$b" } }
                .with { with(compA) { execute() } }
                .with { with(compB) { execute() } }.evalGraph()

        // Proves parallelism still works with .on()
        assertEquals("A|B", result)
    }

    @Test
    fun `on does not affect other computations in the chain`() = runTest {
        // One computation on Default, rest inherit parent context
        val compA = Kap { 21 }.on(Dispatchers.Default)
        val result = Kap.of { a: Int -> { b: Int -> a + b } }
                .with { with(compA) { execute() } }
                .with { 21 }.evalGraph()
        assertEquals(42, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // context — reading CoroutineContext inside computations
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `context captures the current coroutine context`() = runTest {
        val result = withContext(CoroutineName("test-context")) {
            context.map { ctx -> ctx[CoroutineName]?.name }.evalGraph()
        }

        assertEquals("test-context", result)
    }

    @Test
    fun `context composes with andThen for trace propagation`() = runTest {
        val result = withContext(CoroutineName("trace-123")) {
            context.andThen { ctx ->
                val traceName = ctx[CoroutineName]?.name ?: "unknown"
                Kap.of { a: String -> { b: String -> "$a|$b|trace=$traceName" } }
                    .with { "user" }
                    .with { "cart" }
            }.evalGraph()
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
            withContext(CoroutineName("test-cancel")) {
                Kap.of { a: String -> { b: String -> "$a|$b" } }
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
                    }.evalGraph()
            }
        }

        assertTrue(result.isFailure)
        assertTrue(siblingCancelled.await(), "Sibling should be cancelled even with context override")
    }
}
