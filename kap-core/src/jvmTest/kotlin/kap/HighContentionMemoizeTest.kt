package kap

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * High-contention tests for [memoize] and [memoizeOnSuccess].
 *
 * These tests verify correctness under concurrent access from many coroutines,
 * testing the double-checked locking pattern and cancellation safety.
 */
class HighContentionMemoizeTest {

    @Test
    fun `memoize executes original exactly once under 100 concurrent callers`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap {
            executionCount.incrementAndGet()
            delay(10)
            42
        }.memoize()

        val results = coroutineScope {
            (1..100).map {
                async { Async { comp } }
            }.awaitAll()
        }

        assertEquals(1, executionCount.get(), "Original should execute exactly once")
        assertTrue(results.all { it == 42 }, "All callers should get the same result")
    }

    @Test
    fun `memoizeOnSuccess executes original exactly once under 100 concurrent callers`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap {
            executionCount.incrementAndGet()
            delay(10)
            "result"
        }.memoizeOnSuccess()

        val results = coroutineScope {
            (1..100).map {
                async { Async { comp } }
            }.awaitAll()
        }

        assertEquals(1, executionCount.get())
        assertTrue(results.all { it == "result" })
    }

    @Test
    fun `memoize caches failure and rethrows to all concurrent callers`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap<String> {
            executionCount.incrementAndGet()
            delay(10)
            throw IllegalStateException("boom")
        }.memoize()

        val results = coroutineScope {
            (1..50).map {
                async {
                    try {
                        Async { comp }
                        "success"
                    } catch (e: IllegalStateException) {
                        "caught: ${e.message}"
                    }
                }
            }.awaitAll()
        }

        assertEquals(1, executionCount.get(), "Original should execute exactly once even on failure")
        assertTrue(results.all { it == "caught: boom" })
    }

    @Test
    fun `memoizeOnSuccess retries after failure under contention`() = runTest {
        val attemptCount = AtomicInteger(0)
        val comp = Kap {
            val attempt = attemptCount.incrementAndGet()
            delay(5)
            if (attempt == 1) throw IllegalStateException("transient")
            "success-$attempt"
        }.memoizeOnSuccess()

        // First call fails
        val firstResult = try {
            Async { comp }
        } catch (e: IllegalStateException) {
            "failed"
        }
        assertEquals("failed", firstResult)

        // Subsequent calls retry and succeed
        val results = coroutineScope {
            (1..50).map {
                async { Async { comp } }
            }.awaitAll()
        }

        // Should have executed the original again (attempt 2) and cached it
        assertTrue(attemptCount.get() >= 2)
        assertTrue(results.all { it.startsWith("success-") })
        // All should return the same cached result
        val uniqueResults = results.toSet()
        assertEquals(1, uniqueResults.size, "All callers should get the same cached success")
    }

    @Test
    fun `memoize with null value correctly caches null`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap<String?> {
            executionCount.incrementAndGet()
            delay(10)
            null
        }.memoize()

        val results = coroutineScope {
            (1..50).map {
                async { Async { comp } }
            }.awaitAll()
        }

        assertEquals(1, executionCount.get())
        assertTrue(results.all { it == null }, "All callers should get null")
    }

    @Test
    fun `memoize survives cancellation of first caller — second caller executes successfully`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap {
            executionCount.incrementAndGet()
            delay(100)
            "result"
        }.memoize()

        // First caller: cancel before the memoized computation completes
        val job = launch { Async.invoke<String> { comp } }
        delay(10) // Let it start but not finish
        job.cancel()
        delay(10) // Let cancellation propagate

        // First execution was cancelled — Mutex should be released, cache not poisoned
        assertEquals(1, executionCount.get(), "First execution started")

        // Second caller should be able to execute successfully
        val result = withTimeout(5.seconds) { Async.invoke<String> { comp } }
        assertEquals("result", result)
        assertEquals(2, executionCount.get(), "Should have re-executed after cancellation")
    }

    @Test
    fun `memoizeOnSuccess survives cancellation of first caller`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap {
            executionCount.incrementAndGet()
            delay(100)
            "result"
        }.memoizeOnSuccess()

        val job = launch { Async.invoke<String> { comp } }
        delay(10)
        job.cancel()
        delay(10)

        val result = withTimeout(5.seconds) { Async.invoke<String> { comp } }
        assertEquals("result", result)
        assertTrue(executionCount.get() >= 2, "Should have re-executed after cancellation")
    }

    @Test
    fun `memoize concurrent access after cache is populated uses fast path`() = runTest {
        val executionCount = AtomicInteger(0)
        val comp = Kap {
            executionCount.incrementAndGet()
            "cached"
        }.memoize()

        // Populate cache
        Async { comp }
        assertEquals(1, executionCount.get())

        // 200 concurrent reads should all hit the fast path
        val results = coroutineScope {
            (1..200).map {
                async { Async { comp } }
            }.awaitAll()
        }

        assertEquals(1, executionCount.get(), "No additional executions after cache populated")
        assertTrue(results.all { it == "cached" })
    }
}
