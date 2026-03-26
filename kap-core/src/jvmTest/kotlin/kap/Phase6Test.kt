package kap

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)

/**
 * Tests for Phase 6 improvements:
 * - combine (6)..combine (9)
 * - traverse_ / sequence_ (fire-and-forget)
 * - memoize(null) edge case
 */
class Phase6Test {

    // ════════════════════════════════════════════════════════════════════════
    // combine (6)..combine (9)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `combine (6) runs 6 lambdas in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50.milliseconds); 1 },
                { delay(50.milliseconds); 2 },
                { delay(50.milliseconds); 3 },
                { delay(50.milliseconds); 4 },
                { delay(50.milliseconds); 5 },
                { delay(50.milliseconds); 6 },
            ) { a, b, c, d, e, f -> a + b + c + d + e + f }
        }
        assertEquals(21, result)
        assertEquals(50, currentTime)
    }

    @Test
    fun `combine (7) runs 7 lambdas in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50.milliseconds); 1 },
                { delay(50.milliseconds); 2 },
                { delay(50.milliseconds); 3 },
                { delay(50.milliseconds); 4 },
                { delay(50.milliseconds); 5 },
                { delay(50.milliseconds); 6 },
                { delay(50.milliseconds); 7 },
            ) { a, b, c, d, e, f, g -> a + b + c + d + e + f + g }
        }
        assertEquals(28, result)
        assertEquals(50, currentTime)
    }

    @Test
    fun `combine (8) runs 8 lambdas in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50.milliseconds); 1 },
                { delay(50.milliseconds); 2 },
                { delay(50.milliseconds); 3 },
                { delay(50.milliseconds); 4 },
                { delay(50.milliseconds); 5 },
                { delay(50.milliseconds); 6 },
                { delay(50.milliseconds); 7 },
                { delay(50.milliseconds); 8 },
            ) { a, b, c, d, e, f, g, h -> a + b + c + d + e + f + g + h }
        }
        assertEquals(36, result)
        assertEquals(50, currentTime)
    }

    @Test
    fun `combine (9) runs 9 lambdas in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50.milliseconds); 1 },
                { delay(50.milliseconds); 2 },
                { delay(50.milliseconds); 3 },
                { delay(50.milliseconds); 4 },
                { delay(50.milliseconds); 5 },
                { delay(50.milliseconds); 6 },
                { delay(50.milliseconds); 7 },
                { delay(50.milliseconds); 8 },
                { delay(50.milliseconds); 9 },
            ) { a, b, c, d, e, f, g, h, i -> a + b + c + d + e + f + g + h + i }
        }
        assertEquals(45, result)
        assertEquals(50, currentTime)
    }

    @Test
    fun `combine (9) propagates failure correctly`() = runTest {
        val result = runCatching {
            Async {
                combine(
                    { 1 },
                    { 2 },
                    { throw IllegalStateException("boom"); @Suppress("UNREACHABLE_CODE") 3 },
                    { 4 },
                    { 5 },
                    { 6 },
                    { 7 },
                    { 8 },
                    { 9 },
                ) { a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int, i: Int ->
                    a + b + c + d + e + f + g + h + i
                }
            }
        }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // traverse_ / sequence_ (FIRE-AND-FORGET)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `traverse_ executes all side-effects in parallel`() = runTest {
        val log = mutableListOf<Int>()
        Async {
            (1..5).toList().traverseDiscard { i ->
                Kap {
                    delay(50.milliseconds)
                    synchronized(log) { log.add(i) }
                }
            }
        }
        assertEquals(50, currentTime)
        assertEquals(5, log.size)
        assertEquals(setOf(1, 2, 3, 4, 5), log.toSet())
    }

    @Test
    fun `traverse_ with concurrency limit`() = runTest {
        val log = mutableListOf<Int>()
        Async {
            (1..6).toList().traverseDiscard(3) { i ->
                Kap {
                    delay(50.milliseconds)
                    synchronized(log) { log.add(i) }
                }
            }
        }
        // 6 items, concurrency 3 → 2 batches × 50ms = 100ms
        assertTrue(currentTime >= 100)
        assertEquals(6, log.size)
    }

    @Test
    fun `sequence_ executes all side-effects`() = runTest {
        val log = mutableListOf<String>()
        val computations: List<Kap<Unit>> = listOf(
            Kap { delay(50.milliseconds); synchronized(log) { log.add("a") }; Unit },
            Kap { delay(50.milliseconds); synchronized(log) { log.add("b") }; Unit },
            Kap { delay(50.milliseconds); synchronized(log) { log.add("c") }; Unit },
        )
        Async { computations.sequenceDiscard() }
        assertEquals(50, currentTime)
        assertEquals(3, log.size)
    }

    @Test
    fun `sequence_ with concurrency limit`() = runTest {
        val log = mutableListOf<String>()
        val computations: List<Kap<Unit>> = (1..4).map { i ->
            Kap { delay(50.milliseconds); synchronized(log) { log.add("$i") }; Unit }
        }
        Async { computations.sequenceDiscard(2) }
        // 4 items, concurrency 2 → 2 batches × 50ms = 100ms
        assertTrue(currentTime >= 100)
        assertEquals(4, log.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // MEMOIZE NULL EDGE CASE
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `memoize correctly caches null as a valid value`() = runTest {
        var callCount = 0
        val comp = Kap<String?> {
            callCount++
            null  // null is the actual result
        }.memoize()

        val first = Async { comp }
        val second = Async { comp }
        val third = Async { comp }

        assertEquals(null, first)
        assertEquals(null, second)
        assertEquals(null, third)
        assertEquals(1, callCount, "memoize should cache null and not re-execute")
    }

    @Test
    fun `memoizeOnSuccess correctly caches null as a valid value`() = runTest {
        var callCount = 0
        val comp = Kap<String?> {
            callCount++
            null
        }.memoizeOnSuccess()

        val first = Async { comp }
        val second = Async { comp }

        assertEquals(null, first)
        assertEquals(null, second)
        assertEquals(1, callCount, "memoizeOnSuccess should cache null and not re-execute")
    }

    @Test
    fun `memoize with null in parallel kap+with`() = runTest {
        var callCount = 0
        val nullComp = Kap<String?> {
            callCount++
            delay(50.milliseconds)
            null
        }.memoize()

        val result = Async {
            kap { a: String?, b: String? -> "${a}|${b}" }
                .with { nullComp.await() }
                .with { nullComp.await() }
        }
        assertEquals("null|null", result)
        assertEquals(1, callCount)
        assertEquals(50, currentTime) // parallel
    }
}
