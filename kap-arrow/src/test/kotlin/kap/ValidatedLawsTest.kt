package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Algebraic laws for the VALIDATED applicative — `F<A> = Kap<Either<NonEmptyList<E>, A>>`
 * with `valid`/`invalid` as `pure` and [withV] as `apply`.
 *
 * This is the surface `kapV`/`.withV` is built on; failing any law here breaks
 * parallel error-accumulating validation at a fundamental level.
 */
class ValidatedLawsTest {

    // ── Applicative laws ────────────────────────────────────────────────────

    @Test
    fun `applicative identity - valid(id) withV valid(x) == valid(x)`() = runTest {
        val id: (Int) -> Int = { it }

        checkAll(Arb.int()) { x ->
            val result = (valid<String, (Int) -> Int>(id) withV valid<String, Int>(x)).evalGraph()
            assertEquals(Either.Right(x), result)
        }
    }

    @Test
    fun `applicative homomorphism - valid(f) withV valid(x) == valid(f x), arbitrary f`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k ->
            val f: (Int) -> String = { "v=${it + k}" }

            val left = (valid<String, (Int) -> String>(f) withV valid<String, Int>(x)).evalGraph()
            assertEquals(Either.Right(f(x)), left, "f(x)=v=x+$k")
        }
    }

    @Test
    fun `applicative interchange - u withV valid(y) == valid(applyY) withV u, arbitrary u`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000), Arb.int(-1000..1000)) { y, k, m ->
            val u: Kap<Either<NonEmptyList<String>, (Int) -> String>> = valid { n: Int -> "v=${n * m + k}" }

            val left = (u withV valid(y)).evalGraph()
            val applyY: (((Int) -> String)) -> String = { fn -> fn(y) }
            val right = (valid<String, ((Int) -> String) -> String>(applyY) withV u).evalGraph()
            assertEquals(left, right, "u(n)=v=n*$m+$k, y=$y")
        }
    }

    @Test
    fun `applicative composition - compose withV u withV v withV w == u withV (v withV w), arbitrary v`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k ->
            val u: Kap<Either<NonEmptyList<String>, (String) -> String>> = valid { s: String -> "[$s]" }
            val v: Kap<Either<NonEmptyList<String>, (Int) -> String>> = valid { n: Int -> "v=${n + k}" }

            val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
                { f -> { g -> { a -> f(g(a)) } } }

            val left = (valid<String, ((String) -> String) -> ((Int) -> String) -> (Int) -> String>(compose) withV u withV v withV valid<String, Int>(x)).evalGraph()
            val right = (u withV (v withV valid<String, Int>(x))).evalGraph()
            assertEquals(left, right, "v(n)=v=n+$k")
        }
    }

    // ── Error-accumulation laws (the NEL semigroup) ─────────────────────────

    @Test
    fun `withV accumulates errors from both sides, order-preserving`() = runTest {
        checkAll(Arb.int(), Arb.int()) { e1, e2 ->
            val left = (invalid<String, (Int) -> Int>("e$e1") withV invalid<String, Int>("e$e2")).evalGraph()
            assertEquals(Either.Left(nonEmptyListOf("e$e1", "e$e2")), left)
        }
    }

    @Test
    fun `chained withV accumulates three failures in left-to-right order`() = runTest {
        checkAll(Arb.int(), Arb.int(), Arb.int()) { e1, e2, e3 ->
            val f: Kap<Either<NonEmptyList<String>, (Int) -> (Int) -> Int>> =
                invalid("e$e1") // the function slot itself is invalid
            val b: Kap<Either<NonEmptyList<String>, Int>> = invalid("e$e2")
            val c: Kap<Either<NonEmptyList<String>, Int>> = invalid("e$e3")

            val result = ((f withV b) withV c).evalGraph()
            assertEquals(Either.Left(nonEmptyListOf("e$e1", "e$e2", "e$e3")), result)
        }
    }

    // ── thenV short-circuit ────────────────────────────────────────────────

    @Test
    fun `thenV does not execute the right side when the left side is invalid`() = runTest {
        checkAll(Arb.int(), Arb.int()) { e1, work ->
            var rightRan = false
            val result = (invalid<String, (Int) -> Int>("e$e1") thenV Kap {
                rightRan = true
                Either.Right(work)
            }).evalGraph()

            assertEquals(Either.Left(nonEmptyListOf("e$e1")), result)
            assertFalse(rightRan, "thenV must short-circuit on Left")
        }
    }

    // ── Monad laws for andThenV ─────────────────────────────────────────────

    @Test
    fun `monad left identity - valid(a) andThenV f == f(a), arbitrary f`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { a, k ->
            val f: (Int) -> Kap<Either<NonEmptyList<String>, String>> = { n -> valid("v=${n + k}") }

            val left = valid<String, Int>(a).andThenV(f).evalGraph()
            assertEquals(f(a).evalGraph(), left, "f(n)=v=n+$k")
        }
    }

    @Test
    fun `monad right identity - m andThenV valid == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val m = valid<String, Int>(x)
            assertEquals(m.evalGraph(), m.andThenV { valid(it) }.evalGraph())
        }
    }

    @Test
    fun `monad associativity - (m andThenV f) andThenV g == m andThenV (a - f(a) andThenV g), arbitrary f and g`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000), Arb.int(-1000..1000)) { x, k1, k2 ->
            val m = valid<String, Int>(x)
            val f: (Int) -> Kap<Either<NonEmptyList<String>, Int>> = { n -> valid(n + k1) }
            val g: (Int) -> Kap<Either<NonEmptyList<String>, String>> = { n -> valid("v=${n * k2}") }

            val left = m.andThenV(f).andThenV(g).evalGraph()
            val right = m.andThenV { a -> f(a).andThenV(g) }.evalGraph()
            assertEquals(left, right, "f(n)=n+$k1, g(n)=v=n*$k2")
        }
    }

    @Test
    fun `andThenV over invalid short-circuits the continuation`() = runTest {
        checkAll(Arb.int()) { e1 ->
            var continuationRan = false
            val result = invalid<String, Int>("e$e1").andThenV { n ->
                continuationRan = true
                valid(n + 1)
            }.evalGraph()

            assertEquals(Either.Left(nonEmptyListOf("e$e1")), result)
            assertFalse(continuationRan, "andThenV must short-circuit on Left")
        }
    }

    // ── traverseV accumulation ──────────────────────────────────────────────

    @Test
    fun `traverseV collects every error in order, for arbitrary mixed lists`() = runTest {
        checkAll(Arb.list(Arb.int(-10..10), 0..12)) { xs ->
            val computations = xs.map { n ->
                if (n < 0) invalid<String, Int>("neg-$n") else valid<String, Int>(n)
            }
            val result = computations.traverseV { it }.evalGraph()

            val expectedErrors = xs.filter { it < 0 }.map { "neg-$it" }
            when {
                expectedErrors.isEmpty() ->
                    assertEquals(Either.Right(xs), result, "all non-negative must collect values")
                else -> {
                    val errors = (result as Either.Left).value.all
                    assertEquals(expectedErrors, errors, "xs=$xs")
                }
            }
        }
    }

    // ── validated builder vs applicative consistency ────────────────────────

    @Test
    fun `validated builder agrees with withV accumulation, arbitrary inputs`() = runTest {
        checkAll(Arb.int(), Arb.int()) { ok, bad ->
            val viaBuilder = validated<String, Int> {
                val r: Either<NonEmptyList<String>, Int> = Either.Right(ok)
                val l: Either<NonEmptyList<String>, Int> = Either.Left(nonEmptyListOf("e$bad"))
                val a = r.bind()
                val b = l.bind()
                a + b
            }.evalGraph()

            assertEquals(Either.Left(nonEmptyListOf("e$bad")), viaBuilder, "first Left must short-circuit the builder")
        }
    }

    @Test
    fun `mapV transforms only the success side`() = runTest {
        checkAll(Arb.int(), Arb.int(-1000..1000)) { x, k ->
            assertEquals(Either.Right("v=${x + k}"), (valid<String, Int>(x).mapV { "v=${it + k}" }).evalGraph())
            assertEquals(
                Either.Left(nonEmptyListOf("e$x")),
                (invalid<String, Int>("e$x").mapV { "v=${it + k}" }).evalGraph(),
            )
        }
    }
}
