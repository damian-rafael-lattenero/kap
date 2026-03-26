package applicative

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [Effect] satisfies the applicative functor laws
 * using property-based testing.
 *
 * These are the algebraic laws that any lawful applicative must obey.
 * Failing any of these means the abstraction is broken at a fundamental level.
 *
 * Properties are verified with random inputs via Kotest's [checkAll].
 */
class ApplicativeLawsTest {

    // ════════════════════════════════════════════════════════════════════════
    // FUNCTOR LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `functor identity - map id == id`() = runTest {
        checkAll(Arb.int()) { x ->
            val result = Async { Effect.of(x).map { it } }
            assertEquals(x, result)
        }
    }

    @Test
    fun `functor composition - map (g compose f) == map g compose map f`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val composed = Async { Effect.of(x).map { g(f(it)) } }
            val chained = Async { Effect.of(x).map(f).map(g) }
            assertEquals(composed, chained)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // APPLICATIVE LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `applicative identity - pure id with v == v`() = runTest {
        val id: (Int) -> Int = { it }

        checkAll(Arb.int()) { x ->
            val result = Async { Effect.of(id) with Effect.of(x) }
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative homomorphism - pure f with pure x == pure (f x)`() = runTest {
        val f: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val left = Async { Effect.of(f) with Effect.of(x) }
            val right = Async { Effect.of(f(x)) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative interchange - u with pure y == pure (apply y) with u`() = runTest {
        val u: Effect<(Int) -> String> = Effect.of { n: Int -> "v=$n" }

        checkAll(Arb.int()) { y ->
            val left = Async { u with Effect.of(y) }
            val applyY: ((Int) -> String) -> String = { fn -> fn(y) }
            val right = Async { Effect.of(applyY) with u }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative composition - pure compose with u with v with w == u with (v with w)`() = runTest {
        val u: Effect<(String) -> String> = Effect.of { s: String -> "[$s]" }
        val v: Effect<(Int) -> String> = Effect.of { n: Int -> "v=$n" }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val left = Async { Effect.of(compose) with u with v with Effect.of(x) }
            val right = Async { u with (v with Effect.of(x)) }
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS (for andThen)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - pure a andThen f == f a`() = runTest {
        val f: (Int) -> Effect<String> = { n -> Effect.of("v=$n") }

        checkAll(Arb.int()) { a ->
            val left = Async { Effect.of(a).andThen(f) }
            val right = Async { f(a) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad right identity - m andThen pure == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val left = Async { Effect.of(x).andThen { Effect.of(it) } }
            val right = Async { Effect.of(x) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity - (m andThen f) andThen g == m andThen (a - f(a) andThen g)`() = runTest {
        val f: (Int) -> Effect<Int> = { n -> Effect.of(n + 1) }
        val g: (Int) -> Effect<String> = { n -> Effect.of("v=$n") }

        checkAll(Arb.int()) { x ->
            val left = Async { Effect.of(x).andThen(f).andThen(g) }
            val right = Async { Effect.of(x).andThen { a -> f(a).andThen(g) } }
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // APPLICATIVE LAWS WITH REAL EFFECTS (delay + side effects)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `applicative identity with effectful computation`() = runTest {
        val id: (Int) -> Int = { it }

        checkAll(Arb.int()) { x ->
            val effectful = Effect { kotlinx.coroutines.delay(1); x }
            val result = Async { Effect.of(id) with effectful }
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative composition with concurrent effectful computations`() = runTest {
        val u: Effect<(String) -> String> = Effect {
            kotlinx.coroutines.delay(1); { s: String -> "[$s]" }
        }
        val v: Effect<(Int) -> String> = Effect {
            kotlinx.coroutines.delay(1); { n: Int -> "v=$n" }
        }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val effectful = Effect { kotlinx.coroutines.delay(1); x }
            val left = Async { Effect.of(compose) with u with v with effectful }
            val right = Async { u with (v with effectful) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity with effectful computations`() = runTest {
        val f: (Int) -> Effect<Int> = { n -> Effect { kotlinx.coroutines.delay(1); n + 1 } }
        val g: (Int) -> Effect<String> = { n -> Effect { kotlinx.coroutines.delay(1); "v=$n" } }

        checkAll(Arb.int()) { x ->
            val m = Effect { kotlinx.coroutines.delay(1); x }
            val left = Async { m.andThen(f).andThen(g) }
            val right = Async { m.andThen { a -> f(a).andThen(g) } }
            assertEquals(left, right)
        }
    }

    @Test
    fun `functor composition with effectful computation`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val effectful = Effect { kotlinx.coroutines.delay(1); x }
            val composed = Async { effectful.map { g(f(it)) } }
            val chained = Async { effectful.map(f).map(g) }
            assertEquals(composed, chained)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // KAP + WITH CONSISTENCY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `kap with with == zip for binary function`() = runTest {
        val f: (Int, String) -> String = { n, s -> "$s=$n" }

        checkAll(Arb.int(), Arb.string()) { n, s ->
            val viaLift = Async { kap(f).with(Effect.of(n)).with(Effect.of(s)) }
            val viaZip = Async { Effect.of(n).zip(Effect.of(s)) { a, b -> f(a, b) } }
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `kap with with with is consistent with nested zip`() = runTest {
        val f: (Int, Int, Int) -> Int = { a, b, c -> a + b + c }

        checkAll(Arb.int(), Arb.int(), Arb.int()) { a, b, c ->
            val viaLift = Async { kap(f) with Effect.of(a) with Effect.of(b) with Effect.of(c) }
            val viaZip = Async {
                Effect.of(a).zip(Effect.of(b)) { x, y -> x to y }.zip(Effect.of(c)) { (x, y), z -> f(x, y, z) }
            }
            assertEquals(viaLift, viaZip)
        }
    }

}
