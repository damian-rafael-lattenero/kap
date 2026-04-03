package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [Kap] satisfies the applicative functor laws
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
            val result = Kap.of(x).map { it }.executeGraph()
            assertEquals(x, result)
        }
    }

    @Test
    fun `functor composition - map (g compose f) == map g compose map f`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val composed = Kap.of(x).map { g(f(it)) }.executeGraph()
            val chained = Kap.of(x).map(f).map(g).executeGraph()
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
            val result = (Kap.of(id) with Kap.of(x)).executeGraph()
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative homomorphism - pure f with pure x == pure (f x)`() = runTest {
        val f: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val left = (Kap.of(f) with Kap.of(x)).executeGraph()
            val right = Kap.of(f(x)).executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative interchange - u with pure y == pure (apply y) with u`() = runTest {
        val u: Kap<(Int) -> String> = Kap.of { n: Int -> "v=$n" }

        checkAll(Arb.int()) { y ->
            val left = (u with Kap.of(y)).executeGraph()
            val applyY: ((Int) -> String) -> String = { fn -> fn(y) }
            val right = (Kap.of(applyY) with u).executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative composition - pure compose with u with v with w == u with (v with w)`() = runTest {
        val u: Kap<(String) -> String> = Kap.of { s: String -> "[$s]" }
        val v: Kap<(Int) -> String> = Kap.of { n: Int -> "v=$n" }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val left = (Kap.of(compose) with u with v with Kap.of(x)).executeGraph()
            val right = (u with (v with Kap.of(x))).executeGraph()
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS (for andThen)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - pure a andThen f == f a`() = runTest {
        val f: (Int) -> Kap<String> = { n -> Kap.of("v=$n") }

        checkAll(Arb.int()) { a ->
            val left = Kap.of(a).andThen(f).executeGraph()
            val right = f(a).executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad right identity - m andThen pure == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val left = Kap.of(x).andThen { Kap.of(it) }.executeGraph()
            val right = Kap.of(x).executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity - (m andThen f) andThen g == m andThen (a - f(a) andThen g)`() = runTest {
        val f: (Int) -> Kap<Int> = { n -> Kap.of(n + 1) }
        val g: (Int) -> Kap<String> = { n -> Kap.of("v=$n") }

        checkAll(Arb.int()) { x ->
            val left = Kap.of(x).andThen(f).andThen(g).executeGraph()
            val right = Kap.of(x).andThen { a -> f(a).andThen(g) }.executeGraph()
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
            val effectful = Kap { kotlinx.coroutines.delay(1); x }
            val result = (Kap.of(id) with effectful).executeGraph()
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative composition with concurrent effectful computations`() = runTest {
        val u: Kap<(String) -> String> = Kap {
            kotlinx.coroutines.delay(1); { s: String -> "[$s]" }
        }
        val v: Kap<(Int) -> String> = Kap {
            kotlinx.coroutines.delay(1); { n: Int -> "v=$n" }
        }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val effectful = Kap { kotlinx.coroutines.delay(1); x }
            val left = (Kap.of(compose) with u with v with effectful).executeGraph()
            val right = (u with (v with effectful)).executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity with effectful computations`() = runTest {
        val f: (Int) -> Kap<Int> = { n -> Kap { kotlinx.coroutines.delay(1); n + 1 } }
        val g: (Int) -> Kap<String> = { n -> Kap { kotlinx.coroutines.delay(1); "v=$n" } }

        checkAll(Arb.int()) { x ->
            val m = Kap { kotlinx.coroutines.delay(1); x }
            val left = m.andThen(f).andThen(g).executeGraph()
            val right = m.andThen { a -> f(a).andThen(g) }.executeGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `functor composition with effectful computation`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val effectful = Kap { kotlinx.coroutines.delay(1); x }
            val composed = effectful.map { g(f(it)) }.executeGraph()
            val chained = effectful.map(f).map(g).executeGraph()
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
            val viaLift = Kap.of { a: Int -> { b: String -> f(a, b) } }.with(Kap.of(n)).with(Kap.of(s)).executeGraph()
            val viaZip = Kap.of(n).zip(Kap.of(s)) { a, b -> f(a, b) }.executeGraph()
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `kap with with with is consistent with nested zip`() = runTest {
        val f: (Int, Int, Int) -> Int = { a, b, c -> a + b + c }

        checkAll(Arb.int(), Arb.int(), Arb.int()) { a, b, c ->
            val viaLift = (Kap.of { x: Int -> { y: Int -> { z: Int -> f(x, y, z) } } } with Kap.of(a) with Kap.of(b) with Kap.of(c)).executeGraph()
            val viaZip = Kap.of(a).zip(Kap.of(b)) { x, y -> x to y }.zip(Kap.of(c)) { (x, y), z -> f(x, y, z) }.executeGraph()
            assertEquals(viaLift, viaZip)
        }
    }

}
