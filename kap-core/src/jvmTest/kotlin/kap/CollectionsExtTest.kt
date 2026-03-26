package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsExtTest {

    // ════════════════════════════════════════════════════════════════════════
    // 1. zip3 runs all 3 computations in parallel - timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip3 runs all 3 computations in parallel - timing proof`() = runTest {
        val result = Async {
            zip(
                Kap { delay(30); "A" },
                Kap { delay(30); "B" },
                Kap { delay(30); "C" },
            ) { a, b, c -> "$a|$b|$c" }
        }

        assertEquals("A|B|C", result)
        assertEquals(30, currentTime,
            "Expected 30ms (parallel). Got ${currentTime}ms (sequential would be 90ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. zip4 runs all 4 computations in parallel - timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip4 runs all 4 computations in parallel - timing proof`() = runTest {
        val result = Async {
            zip(
                Kap { delay(40); "A" },
                Kap { delay(40); "B" },
                Kap { delay(40); "C" },
                Kap { delay(40); "D" },
            ) { a, b, c, d -> "$a|$b|$c|$d" }
        }

        assertEquals("A|B|C|D", result)
        assertEquals(40, currentTime,
            "Expected 40ms (parallel). Got ${currentTime}ms (sequential would be 160ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. zip5 runs all 5 computations in parallel - timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip5 runs all 5 computations in parallel - timing proof`() = runTest {
        val result = Async {
            zip(
                Kap { delay(50); "A" },
                Kap { delay(50); "B" },
                Kap { delay(50); "C" },
                Kap { delay(50); "D" },
                Kap { delay(50); "E" },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }

        assertEquals("A|B|C|D|E", result)
        assertEquals(50, currentTime,
            "Expected 50ms (parallel). Got ${currentTime}ms (sequential would be 250ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. zip3 cancels siblings on failure
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip3 cancels siblings on failure`() = runTest {
        val cancelledA = CompletableDeferred<Boolean>()
        val cancelledC = CompletableDeferred<Boolean>()
        val allStarted = List(3) { CompletableDeferred<Unit>() }

        val result = runCatching {
            Async {
                zip(
                    Kap {
                        allStarted[0].complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            cancelledA.complete(true)
                            throw e
                        }
                    },
                    Kap<String> {
                        allStarted[1].complete(Unit)
                        allStarted.forEach { it.await() }
                        throw RuntimeException("boom")
                    },
                    Kap {
                        allStarted[2].complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            cancelledC.complete(true)
                            throw e
                        }
                    },
                ) { a, b, c -> "$a|$b|$c" }
            }
        }

        assertTrue(result.isFailure, "zip3 should propagate the exception")
        assertTrue(cancelledA.await(), "Sibling A should have been cancelled")
        assertTrue(cancelledC.await(), "Sibling C should have been cancelled")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. mapN2 is equivalent to zip
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapN2 is equivalent to zip`() = runTest {
        val fa = Kap { delay(30); "X" }
        val fb = Kap { delay(30); "Y" }

        val zipResult = Async { fa.zip(fb) { a, b -> "$a+$b" } }
        val zipTime = currentTime

        val mapNResult = Async { combine(fa, fb) { a, b -> "$a+$b" } }
        val mapNTime = currentTime - zipTime

        assertEquals(zipResult, mapNResult, "mapN2 and zip should produce the same result")
        assertEquals(zipTime, mapNTime, "mapN2 and zip should have the same timing")
        assertEquals(30, zipTime, "Both should complete in 30ms (parallel)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. mapN3 is equivalent to zip3
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapN3 is equivalent to zip3`() = runTest {
        val fa = Kap { delay(30); "A" }
        val fb = Kap { delay(30); "B" }
        val fc = Kap { delay(30); "C" }

        val zipResult = Async { zip(fa, fb, fc) { a, b, c -> "$a|$b|$c" } }
        val zipTime = currentTime

        val mapNResult = Async { combine(fa, fb, fc) { a, b, c -> "$a|$b|$c" } }
        val mapNTime = currentTime - zipTime

        assertEquals(zipResult, mapNResult, "mapN3 and zip3 should produce the same result")
        assertEquals(zipTime, mapNTime, "mapN3 and zip3 should have the same timing")
        assertEquals(30, zipTime, "Both should complete in 30ms (parallel)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. mapN4 is equivalent to zip4
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapN4 is equivalent to zip4`() = runTest {
        val fa = Kap { delay(40); "A" }
        val fb = Kap { delay(40); "B" }
        val fc = Kap { delay(40); "C" }
        val fd = Kap { delay(40); "D" }

        val zipResult = Async { zip(fa, fb, fc, fd) { a, b, c, d -> "$a|$b|$c|$d" } }
        val zipTime = currentTime

        val mapNResult = Async { combine(fa, fb, fc, fd) { a, b, c, d -> "$a|$b|$c|$d" } }
        val mapNTime = currentTime - zipTime

        assertEquals(zipResult, mapNResult, "mapN4 and zip4 should produce the same result")
        assertEquals(zipTime, mapNTime, "mapN4 and zip4 should have the same timing")
        assertEquals(40, zipTime, "Both should complete in 40ms (parallel)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. mapN5 is equivalent to zip5
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapN5 is equivalent to zip5`() = runTest {
        val fa = Kap { delay(50); "A" }
        val fb = Kap { delay(50); "B" }
        val fc = Kap { delay(50); "C" }
        val fd = Kap { delay(50); "D" }
        val fe = Kap { delay(50); "E" }

        val zipResult = Async { zip(fa, fb, fc, fd, fe) { a, b, c, d, e -> "$a|$b|$c|$d|$e" } }
        val zipTime = currentTime

        val mapNResult = Async { combine(fa, fb, fc, fd, fe) { a, b, c, d, e -> "$a|$b|$c|$d|$e" } }
        val mapNTime = currentTime - zipTime

        assertEquals(zipResult, mapNResult, "mapN5 and zip5 should produce the same result")
        assertEquals(zipTime, mapNTime, "mapN5 and zip5 should have the same timing")
        assertEquals(50, zipTime, "Both should complete in 50ms (parallel)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. traverse runs elements in parallel - timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverse runs elements in parallel - timing proof`() = runTest {
        val result = Async {
            listOf(1, 2, 3, 4, 5).traverse { i ->
                Kap { delay(30); "v$i" }
            }
        }

        assertEquals(listOf("v1", "v2", "v3", "v4", "v5"), result)
        assertEquals(30, currentTime,
            "Expected 30ms (all 5 in parallel). Got ${currentTime}ms (sequential would be 150ms)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. traverse with concurrency limit - timing proof
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverse with concurrency limit - timing proof`() = runTest {
        val result = Async {
            (1..9).toList().traverse(3) { i ->
                Kap { delay(30); "v$i" }
            }
        }

        assertEquals((1..9).map { "v$it" }, result)
        // 9 elements, concurrency=3, 30ms each: 3 batches x 30ms = 90ms
        assertEquals(90, currentTime,
            "Expected 90ms (3 batches of 3 at 30ms each). Got ${currentTime}ms")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. zip3 inside Async with other combinators
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip3 inside Async with other combinators`() = runTest {
        // Phase 1: zip3 runs 3 computations in parallel (30ms)
        // Phase 2: andThen uses the result to build a new computation
        // Phase 3: then gates a final parallel phase
        val result = Async {
            zip(
                Kap { delay(30); "user" },
                Kap { delay(30); "cart" },
                Kap { delay(30); "prefs" },
            ) { user, cart, prefs -> Triple(user, cart, prefs) }
                .andThen { (user, cart, prefs) ->
                    // Use the zip3 result to drive the next computation
                    val summary = "$user+$cart+$prefs"
                    kap { enriched: String, shipping: String, tax: String ->
                        "$enriched|$shipping|$tax"
                    }
                        .with(Kap { delay(20); "enriched($summary)" })
                        .then(Kap { delay(10); "shipping" })
                        .with(Kap { delay(20); "tax" })
                }
        }

        assertEquals("enriched(user+cart+prefs)|shipping|tax", result)
        // zip3: 30ms
        // andThen -> lift3.ap: 20ms (enriched) -> then: 10ms (shipping) -> ap: 20ms (tax)
        // Total: 30 + 20 + 10 + 20 = 80ms
        assertEquals(80, currentTime,
            "Expected 80ms: 30(zip3) + 20(enriched) + 10(shipping barrier) + 20(tax). Got ${currentTime}ms")
    }
}
