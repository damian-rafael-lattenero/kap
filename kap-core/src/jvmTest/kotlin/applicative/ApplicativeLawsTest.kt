package applicative

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [Computation] satisfies the applicative functor laws
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
            val result = Async { Computation.of(x).map { it } }
            assertEquals(x, result)
        }
    }

    @Test
    fun `functor composition - map (g compose f) == map g compose map f`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val composed = Async { Computation.of(x).map { g(f(it)) } }
            val chained = Async { Computation.of(x).map(f).map(g) }
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
            val result = Async { Computation.of(id) with Computation.of(x) }
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative homomorphism - pure f with pure x == pure (f x)`() = runTest {
        val f: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val left = Async { Computation.of(f) with Computation.of(x) }
            val right = Async { Computation.of(f(x)) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative interchange - u with pure y == pure (apply y) with u`() = runTest {
        val u: Computation<(Int) -> String> = Computation.of { n: Int -> "v=$n" }

        checkAll(Arb.int()) { y ->
            val left = Async { u with Computation.of(y) }
            val applyY: ((Int) -> String) -> String = { fn -> fn(y) }
            val right = Async { Computation.of(applyY) with u }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative composition - pure compose with u with v with w == u with (v with w)`() = runTest {
        val u: Computation<(String) -> String> = Computation.of { s: String -> "[$s]" }
        val v: Computation<(Int) -> String> = Computation.of { n: Int -> "v=$n" }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val left = Async { Computation.of(compose) with u with v with Computation.of(x) }
            val right = Async { u with (v with Computation.of(x)) }
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS (for flatMap)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - pure a flatMap f == f a`() = runTest {
        val f: (Int) -> Computation<String> = { n -> Computation.of("v=$n") }

        checkAll(Arb.int()) { a ->
            val left = Async { Computation.of(a).flatMap(f) }
            val right = Async { f(a) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad right identity - m flatMap pure == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val left = Async { Computation.of(x).flatMap { Computation.of(it) } }
            val right = Async { Computation.of(x) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity - (m flatMap f) flatMap g == m flatMap (a - f(a) flatMap g)`() = runTest {
        val f: (Int) -> Computation<Int> = { n -> Computation.of(n + 1) }
        val g: (Int) -> Computation<String> = { n -> Computation.of("v=$n") }

        checkAll(Arb.int()) { x ->
            val left = Async { Computation.of(x).flatMap(f).flatMap(g) }
            val right = Async { Computation.of(x).flatMap { a -> f(a).flatMap(g) } }
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
            val effectful = Computation { kotlinx.coroutines.delay(1); x }
            val result = Async { Computation.of(id) with effectful }
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative composition with concurrent effectful computations`() = runTest {
        val u: Computation<(String) -> String> = Computation {
            kotlinx.coroutines.delay(1); { s: String -> "[$s]" }
        }
        val v: Computation<(Int) -> String> = Computation {
            kotlinx.coroutines.delay(1); { n: Int -> "v=$n" }
        }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val effectful = Computation { kotlinx.coroutines.delay(1); x }
            val left = Async { Computation.of(compose) with u with v with effectful }
            val right = Async { u with (v with effectful) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity with effectful computations`() = runTest {
        val f: (Int) -> Computation<Int> = { n -> Computation { kotlinx.coroutines.delay(1); n + 1 } }
        val g: (Int) -> Computation<String> = { n -> Computation { kotlinx.coroutines.delay(1); "v=$n" } }

        checkAll(Arb.int()) { x ->
            val m = Computation { kotlinx.coroutines.delay(1); x }
            val left = Async { m.flatMap(f).flatMap(g) }
            val right = Async { m.flatMap { a -> f(a).flatMap(g) } }
            assertEquals(left, right)
        }
    }

    @Test
    fun `functor composition with effectful computation`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val effectful = Computation { kotlinx.coroutines.delay(1); x }
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
            val viaLift = Async { kap(f).with(Computation.of(n)).with(Computation.of(s)) }
            val viaZip = Async { Computation.of(n).zip(Computation.of(s)) { a, b -> f(a, b) } }
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `kap with with with is consistent with nested zip`() = runTest {
        val f: (Int, Int, Int) -> Int = { a, b, c -> a + b + c }

        checkAll(Arb.int(), Arb.int(), Arb.int()) { a, b, c ->
            val viaLift = Async { kap(f) with Computation.of(a) with Computation.of(b) with Computation.of(c) }
            val viaZip = Async {
                Computation.of(a).zip(Computation.of(b)) { x, y -> x to y }.zip(Computation.of(c)) { (x, y), z -> f(x, y, z) }
            }
            assertEquals(viaLift, viaZip)
        }
    }

}
