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
        assertEquals("result", comp.executeGraph())
        assertEquals("result", comp.executeGraph())
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
        runCatching { comp.executeGraph() }
        assertEquals(1, callCount)
        runCatching { comp.executeGraph() }
        assertEquals(2, callCount)

        // Third call succeeds and gets cached
        assertEquals("success", comp.executeGraph())
        assertEquals(3, callCount)
        assertEquals("success", comp.executeGraph())
        assertEquals(3, callCount, "Should not execute again after success")
    }

    @Test
    fun `memoize caches failure forever vs memoizeOnSuccess retries`() = runTest {
        var c1 = 0
        val memoized = Kap { c1++; if (c1 == 1) throw RuntimeException("fail"); "ok" }.memoize()
        assertTrue(runCatching { memoized.executeGraph() }.isFailure)
        assertTrue(runCatching { memoized.executeGraph() }.isFailure) // cached failure
        assertEquals(1, c1)

        var c2 = 0
        val retryable = Kap { c2++; if (c2 == 1) throw RuntimeException("fail"); "ok" }.memoizeOnSuccess()
        assertTrue(runCatching { retryable.executeGraph() }.isFailure)
        assertEquals("ok", retryable.executeGraph()) // retried
        assertEquals(2, c2)
    }

    @Test
    fun `parallel branches share cached result`() = runTest {
        var callCount = 0
        val shared = Kap { callCount++; delay(50.milliseconds); "shared" }.memoizeOnSuccess()

        val a = shared
        val b = shared
        val graph = Kap.of { x: String -> { y: String -> "$x|$y" } }.with(a).with(b)
        val result = graph.executeGraph()
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

        val graph = Kap.of { a: String -> { b: String -> "$a+$b" } }.with(compA).with(compB)
        assertEquals("data+data", graph.executeGraph())
        assertTrue(callCount <= 1)
    }

    @Test
    fun `with retry — transient failure then cached`() = runTest {
        var callCount = 0
        val comp = Kap {
            callCount++; if (callCount < 2) throw RuntimeException("transient"); "ok"
        }.memoizeOnSuccess()

        assertEquals("ok", comp.retry(3).executeGraph())
        assertEquals("ok", comp.executeGraph()) // cached
        assertEquals(2, callCount)
    }
}
