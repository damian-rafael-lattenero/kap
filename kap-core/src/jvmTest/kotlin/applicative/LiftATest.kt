package applicative

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for [combine] (suspend lambda variant) and [pair]/[triple] — Haskell-style applicative lifting
 * with suspend lambda inputs (parZip-like ergonomics).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiftATest {

    // ── combine (2) ─────────────────────────────────────────────────────

    @Test
    fun `combine (2) runs both lambdas in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50); "A" },
                { delay(50); "B" },
            ) { a, b -> "$a|$b" }
        }
        assertEquals("A|B", result)
        assertEquals(50, currentTime, "Both should run in parallel → 50ms, not 100ms")
    }

    @Test
    fun `combine (2) with different types`() = runTest {
        val result = Async {
            combine(
                { delay(10); 42 },
                { delay(10); "hello" },
            ) { n, s -> "$s-$n" }
        }
        assertEquals("hello-42", result)
    }

    @Test
    fun `combine (2) propagates first exception`() = runTest {
        val comp = combine(
            { error("boom") },
            { delay(100); "B" },
        ) { a: String, b: String -> "$a|$b" }
        assertFailsWith<IllegalStateException> { val r = Async { comp } }
    }

    // ── combine (3) ─────────────────────────────────────────────────────

    @Test
    fun `combine (3) runs all three in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50); "user" },
                { delay(50); "cart" },
                { delay(50); "promos" },
            ) { u, c, p -> "$u|$c|$p" }
        }
        assertEquals("user|cart|promos", result)
        assertEquals(50, currentTime, "All 3 should run in parallel → 50ms, not 150ms")
    }

    @Test
    fun `combine (3) with constructor reference`() = runTest {
        data class Dashboard(val user: String, val cart: String, val promos: String)

        val result = Async {
            combine(
                { delay(10); "Alice" },
                { delay(10); "3 items" },
                { delay(10); "SAVE20" },
            ) { u, c, p -> Dashboard(u, c, p) }
        }
        assertEquals("Alice", result.user)
        assertEquals("3 items", result.cart)
        assertEquals("SAVE20", result.promos)
    }

    // ── combine (4) ─────────────────────────────────────────────────────

    @Test
    fun `combine (4) runs all four in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50); "A" },
                { delay(50); "B" },
                { delay(50); "C" },
                { delay(50); "D" },
            ) { a, b, c, d -> "$a|$b|$c|$d" }
        }
        assertEquals("A|B|C|D", result)
        assertEquals(50, currentTime, "All 4 should run in parallel → 50ms, not 200ms")
    }

    @Test
    fun `combine (4) cancels siblings on failure`() = runTest {
        val started = mutableListOf<String>()
        val comp = combine(
            { started.add("A"); delay(10); error("boom") },
            { started.add("B"); delay(200); "B" },
            { started.add("C"); delay(200); "C" },
            { started.add("D"); delay(200); "D" },
        ) { a: String, b: String, c: String, d: String -> "$a|$b|$c|$d" }
        assertFailsWith<IllegalStateException> { val r = Async { comp } }
        // All should have started (parallel), but the scope cancels siblings on error
        assertEquals(4, started.size, "All 4 should start in parallel")
    }

    // ── combine (5) ─────────────────────────────────────────────────────

    @Test
    fun `combine (5) runs all five in parallel`() = runTest {
        val result = Async {
            combine(
                { delay(50); "A" },
                { delay(50); "B" },
                { delay(50); "C" },
                { delay(50); "D" },
                { delay(50); "E" },
            ) { a, b, c, d, e -> "$a|$b|$c|$d|$e" }
        }
        assertEquals("A|B|C|D|E", result)
        assertEquals(50, currentTime, "All 5 should run in parallel → 50ms, not 250ms")
    }

    @Test
    fun `combine (5) with heterogeneous types`() = runTest {
        val result = Async {
            combine(
                { delay(10); "user" },
                { delay(10); 42 },
                { delay(10); true },
                { delay(10); listOf("a", "b") },
                { delay(10); 3.14 },
            ) { s, n, b, l, d -> "$s|$n|$b|${l.size}|$d" }
        }
        assertEquals("user|42|true|2|3.14", result)
    }

    // ── pair / triple ───────────────────────────────────────────────────

    @Test
    fun `pair returns Pair in parallel`() = runTest {
        val (a, b) = Async {
            pair(
                { delay(50); "user" },
                { delay(50); 42 },
            )
        }
        assertEquals("user", a)
        assertEquals(42, b)
        assertEquals(50, currentTime)
    }

    @Test
    fun `triple returns Triple in parallel`() = runTest {
        val (a, b, c) = Async {
            triple(
                { delay(50); "user" },
                { delay(50); 42 },
                { delay(50); true },
            )
        }
        assertEquals("user", a)
        assertEquals(42, b)
        assertEquals(true, c)
        assertEquals(50, currentTime)
    }

    // ── composition with other combinators ──────────────────────────────

    @Test
    fun `combine (3) composes with andThen for phased execution`() = runTest {
        val result = Async {
            combine(
                { delay(50); "user" },
                { delay(50); "prefs" },
                { delay(50); "tier" },
            ) { u, p, t -> Triple(u, p, t) }
            .andThen { (user, prefs, tier) ->
                combine(
                    { delay(50); "recs for $user" },
                    { delay(50); "promos for $tier" },
                ) { recs, promos -> "$user|$prefs|$tier|$recs|$promos" }
            }
        }
        assertEquals("user|prefs|tier|recs for user|promos for tier", result)
        assertEquals(100, currentTime, "Phase 1 (50ms) + Phase 2 (50ms) = 100ms")
    }

    @Test
    fun `combine (2) with individual branch retry`() = runTest {
        var attempts = 0
        val result = Async {
            combine(
                { "stable" },
                {
                    // Retry inside the branch, not outside combine (2)
                    Effect {
                        attempts++
                        if (attempts < 3) error("flaky")
                        "recovered"
                    }.retry(3).await()
                },
            ) { a, b -> "$a|$b" }
        }
        assertEquals("stable|recovered", result)
    }

    @Test
    fun `combine (2) composes with timeout`() = runTest {
        val result = Async {
            combine(
                { delay(10); "fast" },
                { delay(10); "also fast" },
            ) { a, b -> "$a|$b" }
            .timeout(kotlin.time.Duration.parse("1s"))
        }
        assertEquals("fast|also fast", result)
    }

    // ── law verification ────────────────────────────────────────────────

    @Test
    fun `combine (2) identity - combine id fa fb == pair fa fb`() = runTest {
        val a = Async { combine({ 1 }, { 2 }) { x, y -> Pair(x, y) } }
        val b = Async { pair({ 1 }, { 2 }) }
        assertEquals(a, b)
    }

    @Test
    fun `combine (2) agrees with kap+with`() = runTest {
        val viaLiftA = Async {
            combine({ delay(10); "A" }, { delay(10); "B" }) { a, b -> "$a|$b" }
        }
        val viaLiftAp = Async {
            kap { a: String, b: String -> "$a|$b" }
                .with { delay(10); "A" }
                .with { delay(10); "B" }
        }
        assertEquals(viaLiftA, viaLiftAp)
    }

    @Test
    fun `combine (3) agrees with kap+with`() = runTest {
        val viaLiftA = Async {
            combine({ 1 }, { 2 }, { 3 }) { a, b, c -> a + b + c }
        }
        val viaLiftAp = Async {
            kap { a: Int, b: Int, c: Int -> a + b + c }
                .with { 1 }.with { 2 }.with { 3 }
        }
        assertEquals(viaLiftA, viaLiftAp)
    }

    @Test
    fun `combine (5) agrees with kap+with`() = runTest {
        val viaLiftA = Async {
            combine({ 1 }, { 2 }, { 3 }, { 4 }, { 5 }) { a, b, c, d, e -> a + b + c + d + e }
        }
        val viaLiftAp = Async {
            kap { a: Int, b: Int, c: Int, d: Int, e: Int -> a + b + c + d + e }
                .with { 1 }.with { 2 }.with { 3 }.with { 4 }.with { 5 }
        }
        assertEquals(viaLiftA, viaLiftAp)
    }

    // ── deadlock detection (barrier proof) ──────────────────────────────

    @Test
    fun `combine (3) does not deadlock with barrier from follow-up andThen`() = runTest {
        val result = Async {
            combine(
                { delay(30); 1 },
                { delay(30); 2 },
                { delay(30); 3 },
            ) { a, b, c -> a + b + c }
            .andThen { sum ->
                Effect.of(sum * 10)
            }
        }
        assertEquals(60, result)
    }
}
