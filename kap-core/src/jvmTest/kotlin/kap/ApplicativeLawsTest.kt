package kap

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [Kap] satisfies the functor, applicative and monad laws
 * using property-based testing.
 *
 * All functions involved in the laws are themselves derived from arbitrary
 * constants (e.g. `f(x) = x + k` with random `k`), so the laws are checked
 * across a space of functions, not just two fixed examples.
 */
class ApplicativeLawsTest {

    // ════════════════════════════════════════════════════════════════════════
    // FUNCTOR LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `functor identity - map id == id`() = runTest {
        checkAll(Arb.int()) { x ->
            val result = Kap.of(x).map { it }.evalGraph()
            assertEquals(x, result)
        }
    }

    @Test
    fun `functor composition - map (g compose f) == map g compose map f, arbitrary f and g`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000), Arb.int(-1000..1000)) { x, k, m ->
            val f: (Int) -> Int = { it + k }
            val g: (Int) -> String = { "v=${it * m}" }

            val composed = Kap.of(x).map { g(f(it)) }.evalGraph()
            val chained = Kap.of(x).map(f).map(g).evalGraph()
            assertEquals(composed, chained, "f(x)=x+$k, g(x)=v=x*$m")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // APPLICATIVE LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `applicative identity - pure id with v == v`() = runTest {
        val id: (Int) -> Int = { it }

        checkAll(Arb.int()) { x ->
            val result = (Kap.of(id) with Kap.of(x)).evalGraph()
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative homomorphism - pure f with pure x == pure (f x), arbitrary f`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k ->
            val f: (Int) -> String = { "v=${it + k}" }

            val left = (Kap.of(f) with Kap.of(x)).evalGraph()
            val right = Kap.of(f(x)).evalGraph()
            assertEquals(left, right, "f(x)=v=x+$k")
        }
    }

    @Test
    fun `applicative interchange - u with pure y == pure (apply y) with u, arbitrary u`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, k, m ->
            val u: Kap<(Int) -> String> = Kap.of { n: Int -> "v=${n * m + k}" }

            val left = (u with Kap.of(y)).evalGraph()
            val applyY: ((Int) -> String) -> String = { fn -> fn(y) }
            val right = (Kap.of(applyY) with u).evalGraph()
            assertEquals(left, right, "u(n)=v=n*$m+$k, y=$y")
        }
    }

    @Test
    fun `applicative composition - pure compose with u with v with w == u with (v with w), arbitrary v`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k2 ->
            val u: Kap<(String) -> String> = Kap.of { s: String -> "[$s]" }
            val v: Kap<(Int) -> String> = Kap.of { n: Int -> "v=${n + k2}" }

            val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
                { f -> { g -> { a -> f(g(a)) } } }

            val left = (Kap.of(compose) with u with v with Kap.of(x)).evalGraph()
            val right = (u with (v with Kap.of(x))).evalGraph()
            assertEquals(left, right, "v(n)=v=n+$k2")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS (for andThen)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - pure a andThen f == f a, arbitrary f`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { a, k ->
            val f: (Int) -> Kap<String> = { n -> Kap.of("v=${n + k}") }

            val left = Kap.of(a).andThen(f).evalGraph()
            val right = f(a).evalGraph()
            assertEquals(left, right, "f(n)=v=n+$k")
        }
    }

    @Test
    fun `monad right identity - m andThen pure == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val left = Kap.of(x).andThen { Kap.of(it) }.evalGraph()
            val right = Kap.of(x).evalGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity - (m andThen f) andThen g == m andThen (a - f(a) andThen g), arbitrary f and g`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000), Arb.int(-1000..1000)) { x, k1, k2 ->
            val f: (Int) -> Kap<Int> = { n -> Kap.of(n + k1) }
            val g: (Int) -> Kap<String> = { n -> Kap.of("v=${n * k2}") }

            val left = Kap.of(x).andThen(f).andThen(g).evalGraph()
            val right = Kap.of(x).andThen { a -> f(a).andThen(g) }.evalGraph()
            assertEquals(left, right, "f(n)=n+$k1, g(n)=v=n*$k2")
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
            val result = (Kap.of(id) with effectful).evalGraph()
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
            val left = (Kap.of(compose) with u with v with effectful).evalGraph()
            val right = (u with (v with effectful)).evalGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity with effectful computations`() = runTest {
        val f: (Int) -> Kap<Int> = { n -> Kap { kotlinx.coroutines.delay(1); n + 1 } }
        val g: (Int) -> Kap<String> = { n -> Kap { kotlinx.coroutines.delay(1); "v=$n" } }

        checkAll(Arb.int()) { x ->
            val m = Kap { kotlinx.coroutines.delay(1); x }
            val left = m.andThen(f).andThen(g).evalGraph()
            val right = m.andThen { a -> f(a).andThen(g) }.evalGraph()
            assertEquals(left, right)
        }
    }

    @Test
    fun `functor composition with effectful computation`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val effectful = Kap { kotlinx.coroutines.delay(1); x }
            val composed = effectful.map { g(f(it)) }.evalGraph()
            val chained = effectful.map(f).map(g).evalGraph()
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
            val viaLift = Kap.of { a: Int -> { b: String -> f(a, b) } }.with(Kap.of(n)).with(Kap.of(s)).evalGraph()
            val viaZip = Kap.of(n).zip(Kap.of(s)) { a, b -> f(a, b) }.evalGraph()
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `kap with with with is consistent with nested zip`() = runTest {
        val f: (Int, Int, Int) -> Int = { a, b, c -> a + b + c }

        checkAll(Arb.int(), Arb.int(), Arb.int()) { a, b, c ->
            val viaLift = (Kap.of { x: Int -> { y: Int -> { z: Int -> f(x, y, z) } } } with Kap.of(a) with Kap.of(b) with Kap.of(c)).evalGraph()
            val viaZip = Kap.of(a).zip(Kap.of(b)) { x, y -> x to y }.zip(Kap.of(c)) { (x, y), z -> f(x, y, z) }.evalGraph()
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `with with commutes for symmetric constructors - result independent of argument order`() = runTest {
        checkAll(Arb.int(), Arb.string()) { n, s ->
            val viaIntFirst = Kap.of { x: Int -> { y: String -> x to y } }
                .with(Kap.of(n)).with(Kap.of(s)).evalGraph()
            val viaStringFirst = Kap.of { y: String -> { x: Int -> x to y } }
                .with(Kap.of(s)).with(Kap.of(n)).evalGraph()
            assertEquals(viaIntFirst, viaStringFirst, "with-chain result must not depend on argument order")
        }
    }
}
