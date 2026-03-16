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
            val result = Async { pure(x).map { it } }
            assertEquals(x, result)
        }
    }

    @Test
    fun `functor composition - map (g compose f) == map g compose map f`() = runTest {
        val f: (Int) -> Int = { it + 1 }
        val g: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val composed = Async { pure(x).map { g(f(it)) } }
            val chained = Async { pure(x).map(f).map(g) }
            assertEquals(composed, chained)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // APPLICATIVE LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `applicative identity - pure id ap v == v`() = runTest {
        val id: (Int) -> Int = { it }

        checkAll(Arb.int()) { x ->
            val result = Async { pure(id) ap pure(x) }
            assertEquals(x, result)
        }
    }

    @Test
    fun `applicative homomorphism - pure f ap pure x == pure (f x)`() = runTest {
        val f: (Int) -> String = { "v=$it" }

        checkAll(Arb.int()) { x ->
            val left = Async { pure(f) ap pure(x) }
            val right = Async { pure(f(x)) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative interchange - u ap pure y == pure (apply y) ap u`() = runTest {
        val u: Computation<(Int) -> String> = pure { n: Int -> "v=$n" }

        checkAll(Arb.int()) { y ->
            val left = Async { u ap pure(y) }
            val applyY: ((Int) -> String) -> String = { fn -> fn(y) }
            val right = Async { pure(applyY) ap u }
            assertEquals(left, right)
        }
    }

    @Test
    fun `applicative composition - pure compose ap u ap v ap w == u ap (v ap w)`() = runTest {
        val u: Computation<(String) -> String> = pure { s: String -> "[$s]" }
        val v: Computation<(Int) -> String> = pure { n: Int -> "v=$n" }

        val compose: ((String) -> String) -> ((Int) -> String) -> (Int) -> String =
            { f -> { g -> { a -> f(g(a)) } } }

        checkAll(Arb.int()) { x ->
            val left = Async { pure(compose) ap u ap v ap pure(x) }
            val right = Async { u ap (v ap pure(x)) }
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS (for flatMap)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - pure a flatMap f == f a`() = runTest {
        val f: (Int) -> Computation<String> = { n -> pure("v=$n") }

        checkAll(Arb.int()) { a ->
            val left = Async { pure(a).flatMap(f) }
            val right = Async { f(a) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad right identity - m flatMap pure == m`() = runTest {
        checkAll(Arb.int()) { x ->
            val left = Async { pure(x).flatMap { pure(it) } }
            val right = Async { pure(x) }
            assertEquals(left, right)
        }
    }

    @Test
    fun `monad associativity - (m flatMap f) flatMap g == m flatMap (a - f(a) flatMap g)`() = runTest {
        val f: (Int) -> Computation<Int> = { n -> pure(n + 1) }
        val g: (Int) -> Computation<String> = { n -> pure("v=$n") }

        checkAll(Arb.int()) { x ->
            val left = Async { pure(x).flatMap(f).flatMap(g) }
            val right = Async { pure(x).flatMap { a -> f(a).flatMap(g) } }
            assertEquals(left, right)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // LIFT + AP CONSISTENCY
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `lift2 ap ap == zip for binary function`() = runTest {
        val f: (Int, String) -> String = { n, s -> "$s=$n" }

        checkAll(Arb.int(), Arb.string()) { n, s ->
            val viaLift = Async { lift2(f).ap(pure(n)).ap(pure(s)) }
            val viaZip = Async { pure(n).zip(pure(s)) { a, b -> f(a, b) } }
            assertEquals(viaLift, viaZip)
        }
    }

    @Test
    fun `lift3 ap ap ap is consistent with nested zip`() = runTest {
        val f: (Int, Int, Int) -> Int = { a, b, c -> a + b + c }

        checkAll(Arb.int(), Arb.int(), Arb.int()) { a, b, c ->
            val viaLift = Async { lift3(f) ap pure(a) ap pure(b) ap pure(c) }
            val viaZip = Async {
                pure(a).zip(pure(b)) { x, y -> x to y }.zip(pure(c)) { (x, y), z -> f(x, y, z) }
            }
            assertEquals(viaLift, viaZip)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // VALIDATED APPLICATIVE LAWS
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `validated identity - liftV with valid inputs produces Right`() = runTest {
        checkAll(Arb.int(), Arb.int()) { a, b ->
            val result = Async {
                liftV2<String, Int, Int, Int> { x, y -> x + y }
                    .apV(valid<String, Int>(a))
                    .apV(valid<String, Int>(b))
            }
            assertEquals(Either.Right(a + b), result)
        }
    }

    @Test
    fun `validated accumulates errors from all branches`() = runTest {
        checkAll(Arb.string(), Arb.string(), Arb.string()) { e1, e2, e3 ->
            val result = Async {
                liftV3<String, Int, Int, Int, Int> { a, b, c -> a + b + c }
                    .apV(invalid<String, Int>(e1))
                    .apV(invalid<String, Int>(e2))
                    .apV(invalid<String, Int>(e3))
            }

            val errors = (result as Either.Left).value
            assertEquals(listOf(e1, e2, e3), errors.toList())
        }
    }

    @Test
    fun `mapError transforms error type preserving structure`() = runTest {
        checkAll(Arb.string()) { err ->
            val result = Async {
                invalid<String, Int>(err)
                    .mapError { it.length }
            }
            assertEquals(Either.Left(Nel(err.length)), result)
        }
    }

    @Test
    fun `mapError on Right is identity`() = runTest {
        checkAll(Arb.int()) { x ->
            val result = Async {
                valid<String, Int>(x)
                    .mapError { it.length }
            }
            assertEquals(Either.Right(x), result)
        }
    }

    @Test
    fun `mapError allows unifying different error types in apV chains`() = runTest {
        checkAll(Arb.string(), Arb.string()) { nameErr, ageErr ->
            val nameCheck: Computation<Either<Nel<String>, String>> = invalid(nameErr)
            val ageCheck: Computation<Either<Nel<String>, Int>> = invalid(ageErr)

            val result = Async {
                liftV2<Pair<String, String>, String, Int, String> { name, age -> "$name:$age" }
                    .apV(nameCheck.mapError { "name" to it })
                    .apV(ageCheck.mapError { "age" to it })
            }

            val errors = (result as Either.Left).value
            assertEquals(2, errors.size)
            assertEquals("name" to nameErr, errors[0])
            assertEquals("age" to ageErr, errors[1])
        }
    }
}
