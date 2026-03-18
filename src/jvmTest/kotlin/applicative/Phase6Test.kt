package applicative

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for Phase 6 improvements:
 * - Validated<E, A> type alias
 * - liftA6..liftA9
 * - traverse_ / sequence_ (fire-and-forget)
 * - memoize(null) edge case
 */
class Phase6Test {

    // ════════════════════════════════════════════════════════════════════════
    // Validated<E, A> TYPE ALIAS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Validated type alias works in function signatures`() = runTest {
        fun validateAge(age: Int): Validated<String, Int> =
            if (age >= 18) valid<String, Int>(age) else invalid("too young")

        // The alias resolves correctly
        val result: Validated<String, Int> = validateAge(20)
        val executed = Async { result }
        assertTrue(executed is Either.Right)
    }

    @Test
    fun `Validated alias composes with apV`() = runTest {
        fun validateName(n: String): Validated<String, String> =
            if (n.length >= 2) valid(n) else invalid("too short")

        fun validateEmail(e: String): Validated<String, String> =
            if ("@" in e) valid(e) else invalid("bad email")

        val result = Async {
            zipV(
                { Async { validateName("Al") } },
                { Async { validateEmail("bad") } },
            ) { name, email -> "$name:$email" }
        }
        assertTrue(result is Either.Left)
        assertEquals(1, (result as Either.Left).value.size)
    }

    // ════════════════════════════════════════════════════════════════════════
    // liftA6..liftA9
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `liftA6 runs 6 lambdas in parallel`() = runTest {
        val result = Async {
            liftA6(
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
    fun `liftA7 runs 7 lambdas in parallel`() = runTest {
        val result = Async {
            liftA7(
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
    fun `liftA8 runs 8 lambdas in parallel`() = runTest {
        val result = Async {
            liftA8(
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
    fun `liftA9 runs 9 lambdas in parallel`() = runTest {
        val result = Async {
            liftA9(
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
    fun `liftA9 propagates failure correctly`() = runTest {
        val result = runCatching {
            Async {
                liftA9(
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
            (1..5).toList().traverse_ { i ->
                Computation {
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
            (1..6).toList().traverse_(3) { i ->
                Computation {
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
        val computations: List<Computation<Unit>> = listOf(
            Computation { delay(50.milliseconds); synchronized(log) { log.add("a") }; Unit },
            Computation { delay(50.milliseconds); synchronized(log) { log.add("b") }; Unit },
            Computation { delay(50.milliseconds); synchronized(log) { log.add("c") }; Unit },
        )
        Async { computations.sequence_() }
        assertEquals(50, currentTime)
        assertEquals(3, log.size)
    }

    @Test
    fun `sequence_ with concurrency limit`() = runTest {
        val log = mutableListOf<String>()
        val computations: List<Computation<Unit>> = (1..4).map { i ->
            Computation { delay(50.milliseconds); synchronized(log) { log.add("$i") }; Unit }
        }
        Async { computations.sequence_(2) }
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
        val comp = Computation<String?> {
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
        val comp = Computation<String?> {
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
    fun `memoize with null in parallel ap`() = runTest {
        var callCount = 0
        val nullComp = Computation<String?> {
            callCount++
            delay(50.milliseconds)
            null
        }.memoize()

        val result = Async {
            lift2 { a: String?, b: String? -> "${a}|${b}" }
                .ap { nullComp.await() }
                .ap { nullComp.await() }
        }
        assertEquals("null|null", result)
        assertEquals(1, callCount)
        assertEquals(50, currentTime) // parallel
    }
}
