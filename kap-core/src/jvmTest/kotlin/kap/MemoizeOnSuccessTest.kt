package kap

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class MemoizeOnSuccessTest {

    @Test
    fun `caches successful result`() = runTest {
        var callCount = 0
        val comp = Kap { callCount++; "result" }.memoizeOnSuccess()
        assertEquals("result", Async { comp })
        assertEquals("result", Async { comp })
        assertEquals(1, callCount, "Should execute only once on success")
    }

    @Test
    fun `retries after failure`() = runTest {
        var callCount = 0
        val comp = Kap {
            callCount++
            if (callCount < 3) throw RuntimeException("transient #$callCount")
            "success"
        }.memoizeOnSuccess()

        // First two calls fail
        runCatching { Async { comp } }
        assertEquals(1, callCount)
        runCatching { Async { comp } }
        assertEquals(2, callCount)

        // Third call succeeds and gets cached
        assertEquals("success", Async { comp })
        assertEquals(3, callCount)
        assertEquals("success", Async { comp })
        assertEquals(3, callCount, "Should not execute again after success")
    }

    @Test
    fun `memoize caches failure forever vs memoizeOnSuccess retries`() = runTest {
        var c1 = 0
        val memoized = Kap { c1++; if (c1 == 1) throw RuntimeException("fail"); "ok" }.memoize()
        assertTrue(runCatching { Async { memoized } }.isFailure)
        assertTrue(runCatching { Async { memoized } }.isFailure) // cached failure
        assertEquals(1, c1)

        var c2 = 0
        val retryable = Kap { c2++; if (c2 == 1) throw RuntimeException("fail"); "ok" }.memoizeOnSuccess()
        assertTrue(runCatching { Async { retryable } }.isFailure)
        assertEquals("ok", Async { retryable }) // retried
        assertEquals(2, c2)
    }

    @Test
    fun `parallel branches share cached result`() = runTest {
        var callCount = 0
        val shared = Kap { callCount++; delay(50.milliseconds); "shared" }.memoizeOnSuccess()

        val a = shared
        val b = shared
        val graph = kap { x: String, y: String -> "$x|$y" }.with(a).with(b)
        val result = Async { graph }
        assertEquals("shared|shared", result)
        assertEquals(1, callCount, "Parallel branches should share single execution")
    }

    @Test
    fun `concurrent proof — latch barrier`() = runTest {
        var callCount = 0
        val shared = Kap { callCount++; "data" }.memoizeOnSuccess()
        val latch1 = CompletableDeferred<Unit>()
        val latch2 = CompletableDeferred<Unit>()

        val compA = Kap {
            latch1.complete(Unit); latch2.await()
            with(shared) { execute() }
        }
        val compB = Kap {
            latch2.complete(Unit); latch1.await()
            with(shared) { execute() }
        }

        val graph = kap { a: String, b: String -> "$a+$b" }.with(compA).with(compB)
        assertEquals("data+data", Async { graph })
        assertTrue(callCount <= 1)
    }

    @Test
    fun `with retry — transient failure then cached`() = runTest {
        var callCount = 0
        val comp = Kap {
            callCount++; if (callCount < 2) throw RuntimeException("transient"); "ok"
        }.memoizeOnSuccess()

        assertEquals("ok", Async { comp.retry(3) })
        assertEquals("ok", Async { comp }) // cached
        assertEquals(2, callCount)
    }
}
