package kap

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Proves that KAP is fully debuggable and produces clean, readable errors.
 *
 * Each test corresponds to a concrete debuggability claim:
 * 1. Exceptions propagate with original type, message, and stack trace — no wrapping
 * 2. Each parallel branch can be named — visible in IntelliJ's coroutine debugger
 * 3. Each branch can be traced with lifecycle hooks — you know exactly what failed and when
 * 4. Parallel tracing: when multiple branches run, each reports independently
 */
class DebuggabilityTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. Exception type and message are preserved exactly — no wrapping
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `exception in with branch preserves original type and message`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> "$a-$b" } }
                .with { "ok" }
                .with { throw IllegalStateException("user service returned 503") }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()!!
        // Original type — not wrapped in ExecutionException, CompletionException, or anything else
        assertIs<IllegalStateException>(ex)
        // Original message — not prefixed, suffixed, or modified
        assertEquals("user service returned 503", ex.message)
    }

    @Test
    fun `exception in then barrier preserves original type and message`() = runTest {
        val result = runCatching {
            Kap.of { a: String -> { b: String -> { c: String -> "$a|$b|$c" } } }
                .with { "phase-1" }
                .then(Kap<String> { throw RuntimeException("stock validation failed") })
                .with { "phase-3" }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()!!
        assertIs<RuntimeException>(ex)
        assertEquals("stock validation failed", ex.message)
    }

    @Test
    fun `custom exception types propagate unchanged`() = runTest {
        class PaymentDeclinedException(msg: String) : Exception(msg)

        val result = runCatching {
            Kap.of { a: String -> { b: String -> "$a-$b" } }
                .with { "order-123" }
                .with { throw PaymentDeclinedException("card expired") }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<PaymentDeclinedException>(result.exceptionOrNull())
        assertEquals("card expired", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `failure in one branch cancels siblings — structured concurrency intact`() = runTest {
        var siblingCompleted = false

        val result = runCatching {
            Kap.of { a: String -> { b: String -> "$a-$b" } }
                .with { delay(500); siblingCompleted = true; "slow" }
                .with { delay(10); throw IllegalArgumentException("fast failure") }
                .evalGraph()
        }

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals("fast failure", result.exceptionOrNull()!!.message)
        // The slow sibling was cancelled, not abandoned
        assertTrue(!siblingCompleted, "Sibling should have been cancelled, not left running")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Named branches — visible in IntelliJ coroutine debugger
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `named sets CoroutineName visible in debugger`() = runTest {
        val result = Kap {
            coroutineContext[CoroutineName]?.name ?: "missing"
        }.named("fetch-user").evalGraph()

        assertEquals("fetch-user", result)
    }

    @Test
    fun `each parallel branch gets its own CoroutineName`() = runTest {
        val result = Kap.of { a: String -> { b: String -> { c: String -> listOf(a, b, c) } } }
            .with(Kap<String> {
                coroutineContext[CoroutineName]?.name ?: "missing"
            }.named("fetch-user"))
            .with(Kap<String> {
                coroutineContext[CoroutineName]?.name ?: "missing"
            }.named("fetch-cart"))
            .with(Kap<String> {
                coroutineContext[CoroutineName]?.name ?: "missing"
            }.named("fetch-promos"))
            .evalGraph()

        // Each branch reports its own name — no cross-contamination
        assertEquals(listOf("fetch-user", "fetch-cart", "fetch-promos"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Traced lifecycle — you know exactly what failed, when, and how long it ran
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traced reports which branch failed and the exact error`() = runTest {
        val events = mutableListOf<String>()

        val result = runCatching {
            Kap<String> { throw RuntimeException("connection refused") }
                .traced(
                    name = "payment-service",
                    onStart = { events += "START: $it" },
                    onSuccess = { name, duration -> events += "OK: $name (${duration.inWholeMilliseconds}ms)" },
                    onError = { name, duration, ex -> events += "FAIL: $name (${duration.inWholeMilliseconds}ms) — ${ex.message}" },
                ).evalGraph()
        }

        assertTrue(result.isFailure)
        // You get: which service, how long it ran, and the exact error message
        assertEquals("START: payment-service", events[0])
        assertTrue(events[1].startsWith("FAIL: payment-service"))
        assertTrue(events[1].contains("connection refused"))
    }

    @Test
    fun `traced on success reports service name and duration`() = runTest {
        val events = mutableListOf<String>()

        Kap { delay(50); "user-data" }
            .traced(
                name = "user-service",
                onStart = { events += "START: $it" },
                onSuccess = { name, _ -> events += "OK: $name" },
                onError = { name, _, ex -> events += "FAIL: $name — ${ex.message}" },
            ).evalGraph()

        assertEquals(listOf("START: user-service", "OK: user-service"), events)
    }

    @Test
    fun `KapTracer interface receives structured events with error details`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = KapTracer { events += it }

        val result = runCatching {
            Kap<String> { throw IllegalStateException("timeout after 5s") }
                .traced("inventory-check", tracer).evalGraph()
        }

        assertTrue(result.isFailure)
        assertEquals(2, events.size)
        // Event 1: started
        assertIs<TraceEvent.Started>(events[0])
        assertEquals("inventory-check", events[0].name)
        // Event 2: failed — with the exact exception
        val failed = events[1] as TraceEvent.Failed
        assertEquals("inventory-check", failed.name)
        assertEquals("timeout after 5s", failed.error.message)
        assertIs<IllegalStateException>(failed.error)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Parallel tracing — each branch reports independently
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `parallel branches each trace independently — you see exactly which succeeded and which failed`() = runTest {
        val events = mutableListOf<String>()

        val result = runCatching {
            Kap.of { a: String -> { b: String -> "$a-$b" } }
                .with(
                    Kap { delay(20); "user-data" }
                        .traced("user-service",
                            onStart = { events += "START: $it" },
                            onSuccess = { name, _ -> events += "OK: $name" },
                            onError = { name, _, ex -> events += "FAIL: $name — ${ex.message}" })
                )
                .with(
                    Kap<String> { delay(10); throw RuntimeException("503 Service Unavailable") }
                        .traced("cart-service",
                            onStart = { events += "START: $it" },
                            onSuccess = { name, _ -> events += "OK: $name" },
                            onError = { name, _, ex -> events += "FAIL: $name — ${ex.message}" })
                )
                .evalGraph()
        }

        assertTrue(result.isFailure)
        // Both branches started
        assertTrue(events.contains("START: user-service"))
        assertTrue(events.contains("START: cart-service"))
        // Cart failed — you know exactly which one and why
        assertTrue(events.contains("FAIL: cart-service — 503 Service Unavailable"))
    }

    @Test
    fun `named plus traced — full observability stack`() = runTest {
        val events = mutableListOf<TraceEvent>()
        val tracer = KapTracer { events += it }

        val result = Kap.of { a: String -> { b: Int -> "$a=$b" } }
            .with(Kap { delay(10); "Alice" }.named("fetch-user").traced("fetch-user", tracer))
            .with(Kap { delay(10); 42 }.named("count-notifications").traced("count-notifications", tracer))
            .evalGraph()

        assertEquals("Alice=42", result)
        // Both branches traced with structured events
        val names = events.map { it.name }.toSet()
        assertTrue(names.contains("fetch-user"))
        assertTrue(names.contains("count-notifications"))
        // All succeeded
        assertTrue(events.filterIsInstance<TraceEvent.Succeeded>().size == 2)
    }
}
