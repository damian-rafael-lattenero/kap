package kap

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.merge
import arrow.core.nonEmptyListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EitherTest {

    // ════════════════════════════════════════════════════════════════════════
    // FUNCTOR LAWS for Either.map
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map identity - map id equals id`() {
        val r: Either<String, Int> = Either.Right(42)
        assertEquals(r, r.map { it })
    }

    @Test
    fun `map identity on Left`() {
        val l: Either<String, Int> = Either.Left("err")
        assertEquals(l, l.map { it * 2 })
    }

    @Test
    fun `map composition - map f-compose-g equals map g then map f`() {
        val f: (String) -> Int = { it.length }
        val g: (Int) -> String = { "n=$it" }
        val v: Either<String, Int> = Either.Right(42)

        assertEquals(v.map { f(g(it)) }, v.map(g).map(f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS for Either.flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - right(a) flatMap f equals f(a)`() {
        val a = 42
        val f: (Int) -> Either<String, String> = { Either.Right("v=$it") }

        assertEquals(f(a), Either.Right(a).flatMap(f))
    }

    @Test
    fun `monad right identity - m flatMap right equals m`() {
        val m: Either<String, Int> = Either.Right(42)
        assertEquals(m, m.flatMap { Either.Right(it) })
    }

    @Test
    fun `monad right identity on Left`() {
        val m: Either<String, Int> = Either.Left("err")
        assertEquals(m, m.flatMap { Either.Right(it) })
    }

    @Test
    fun `monad associativity`() {
        val m: Either<String, Int> = Either.Right(10)
        val f: (Int) -> Either<String, String> = { Either.Right("n=$it") }
        val g: (String) -> Either<String, Int> = { Either.Right(it.length) }

        assertEquals(
            m.flatMap(f).flatMap(g),
            m.flatMap { a -> f(a).flatMap(g) }
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // mapLeft
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `mapLeft transforms Left value`() {
        val l: Either<Int, String> = Either.Left(42)
        assertEquals(Either.Left("err=42"), l.mapLeft { "err=$it" })
    }

    @Test
    fun `mapLeft is identity on Right`() {
        val r: Either<Int, String> = Either.Right("ok")
        assertEquals(r, r.mapLeft { it * 2 })
    }

    // ════════════════════════════════════════════════════════════════════════
    // fold / getOrElse / getOrNull / isRight / isLeft
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fold handles both cases`() {
        assertEquals("left=42", Either.Left(42).fold({ "left=$it" }, { "right=$it" }))
        assertEquals("right=ok", Either.Right("ok").fold({ "left=$it" }, { "right=$it" }))
    }

    @Test
    fun `getOrElse returns value or computes default`() {
        assertEquals(42, Either.Right(42).getOrElse { -1 })
        assertEquals(-1, Either.Left("err").getOrElse { -1 })
    }

    @Test
    fun `getOrNull returns value or null`() {
        assertEquals(42, Either.Right(42).getOrNull())
        assertNull(Either.Left("err").getOrNull())
    }

    @Test
    fun `isRight and isLeft`() {
        val r: Either<String, Int> = Either.Right(42)
        val l: Either<String, Int> = Either.Left("err")
        assertIs<Either.Right<Int>>(r)
        assertIs<Either.Left<String>>(l)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Structural equality
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Left and Right have structural equality`() {
        assertEquals(Either.Left("err"), Either.Left("err"))
        assertEquals(Either.Right(42), Either.Right(42))
        assertFalse(Either.Left("err") == Either.Right("err") as Any)
    }

    // ════════════════════════════════════════════════════════════════════════
    // swap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `swap turns Right into Left`() {
        assertEquals(Either.Left(42), Either.Right(42).swap())
    }

    @Test
    fun `swap turns Left into Right`() {
        assertEquals(Either.Right("err"), Either.Left("err").swap())
    }

    @Test
    fun `swap is its own inverse`() {
        val r: Either<String, Int> = Either.Right(42)
        val l: Either<String, Int> = Either.Left("err")
        assertEquals(r, r.swap().swap())
        assertEquals(l, l.swap().swap())
    }

    // ════════════════════════════════════════════════════════════════════════
    // map + mapLeft (replaces deprecated bimap)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `map + mapLeft transforms Right side`() {
        val r: Either<String, Int> = Either.Right(42)
        assertEquals(Either.Right("v=42"), r.mapLeft { "err:$it" }.map { "v=$it" })
    }

    @Test
    fun `map + mapLeft transforms Left side`() {
        val l: Either<String, Int> = Either.Left("oops")
        assertEquals(Either.Left("err:oops"), l.mapLeft { "err:$it" }.map { "v=$it" })
    }

    @Test
    fun `map + mapLeft identity is identity`() {
        val r: Either<String, Int> = Either.Right(42)
        val l: Either<String, Int> = Either.Left("err")
        assertEquals(r, r.mapLeft { it }.map { it })
        assertEquals(l, l.mapLeft { it }.map { it })
    }

    // ════════════════════════════════════════════════════════════════════════
    // onLeft / onRight
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `onLeft executes side-effect on Left`() {
        var captured: String? = null
        val l: Either<String, Int> = Either.Left("err")
        val result = l.onLeft { captured = it }
        assertEquals("err", captured)
        assertEquals(l, result)
    }

    @Test
    fun `onLeft does nothing on Right`() {
        var called = false
        val r: Either<String, Int> = Either.Right(42)
        val result = r.onLeft { called = true }
        assertFalse(called)
        assertEquals(r, result)
    }

    @Test
    fun `onRight executes side-effect on Right`() {
        var captured: Int? = null
        val r: Either<String, Int> = Either.Right(42)
        val result = r.onRight { captured = it }
        assertEquals(42, captured)
        assertEquals(r, result)
    }

    @Test
    fun `onRight does nothing on Left`() {
        var called = false
        val l: Either<String, Int> = Either.Left("err")
        val result = l.onRight { called = true }
        assertFalse(called)
        assertEquals(l, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // merge
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `merge extracts value from Right`() {
        assertEquals("ok", Either.Right("ok").merge())
    }

    @Test
    fun `merge extracts value from Left`() {
        assertEquals("err", Either.Left("err").merge())
    }

    // ════════════════════════════════════════════════════════════════════════
    // ensure (via flatMap, Arrow's native pattern)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `ensure passes when predicate holds`() {
        val result: Either<String, Int> = Either.Right(25).flatMap {
            if (it >= 21) Either.Right(it) else Either.Left("too young")
        }
        assertEquals(Either.Right(25), result)
    }

    @Test
    fun `ensure fails when predicate does not hold`() {
        val result: Either<String, Int> = Either.Right(18).flatMap {
            if (it >= 21) Either.Right(it) else Either.Left("too young")
        }
        assertEquals(Either.Left("too young"), result)
    }

    @Test
    fun `ensure is identity on Left`() {
        val l: Either<String, Int> = Either.Left("already bad")
        val result = l.flatMap {
            if (it >= 21) Either.Right(it) else Either.Left("too young")
        }
        assertEquals(l, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // filterOrElse (via flatMap)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `filterOrElse passes when predicate holds`() {
        val result: Either<String, Int> = Either.Right(25).flatMap {
            if (it >= 21) Either.Right(it) else Either.Left("too young")
        }
        assertEquals(Either.Right(25), result)
    }

    @Test
    fun `filterOrElse fails when predicate does not hold`() {
        val result: Either<String, Int> = Either.Right(18).flatMap {
            if (it >= 21) Either.Right(it) else Either.Left("too young")
        }
        assertEquals(Either.Left("too young"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // getOrElse as replacement for getOrHandle
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `getOrElse returns value on Right`() {
        assertEquals(42, Either.Right(42).getOrElse { -1 })
    }

    @Test
    fun `getOrElse applies handler on Left`() {
        assertEquals(3, Either.Left("err").getOrElse { it.length })
    }

    // ════════════════════════════════════════════════════════════════════════
    // handleErrorWith (via fold)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `handleErrorWith recovers from Left`() {
        val result = Either.Left("oops").fold({ Either.Right("fallback") }, { Either.Right(it) })
        assertEquals(Either.Right("fallback"), result)
    }

    @Test
    fun `handleErrorWith chains Left to Left`() {
        val result: Either<String, String> = Either.Left("oops").fold({ Either.Left("mapped") }, { Either.Right(it) })
        assertEquals(Either.Left("mapped"), result)
    }

    @Test
    fun `handleErrorWith is identity on Right`() {
        val result = Either.Right(42).fold({ Either.Right(-1) }, { Either.Right(it) })
        assertEquals(Either.Right(42), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // zip
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `zip combines two Rights`() {
        val a: Either<String, Int> = Either.Right(1)
        val b: Either<String, String> = Either.Right("a")
        val result = a.fold(
            ifLeft = { Either.Left(it) },
            ifRight = { n -> b.fold(ifLeft = { Either.Left(it) }, ifRight = { s -> Either.Right("$n$s") }) },
        )
        assertEquals(Either.Right("1a"), result)
    }

    @Test
    fun `zip short-circuits on first Left`() {
        val a: Either<String, String> = Either.Left("e1")
        val b: Either<String, String> = Either.Right("a")
        val result = a.fold(
            ifLeft = { Either.Left(it) },
            ifRight = { n -> b.fold(ifLeft = { Either.Left(it) }, ifRight = { s -> Either.Right("$n$s") }) },
        )
        assertEquals(Either.Left("e1"), result)
    }

    @Test
    fun `zip short-circuits on second Left`() {
        val a: Either<String, Int> = Either.Right(1)
        val b: Either<String, String> = Either.Left("e2")
        val result = a.fold(
            ifLeft = { Either.Left(it) },
            ifRight = { n -> b.fold(ifLeft = { Either.Left(it) }, ifRight = { s -> Either.Right("$n$s") }) },
        )
        assertEquals(Either.Left("e2"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // toValidatedNel (via mapLeft)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `toValidatedNel wraps Left in Nel`() {
        val result = Either.Left("err").mapLeft { nonEmptyListOf(it) }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(nonEmptyListOf("err"), result.value)
    }

    @Test
    fun `toValidatedNel preserves Right`() {
        val result: Either<NonEmptyList<String>, Int> =
            Either.Right(42).mapLeft { nonEmptyListOf(it) }
        assertEquals(Either.Right(42), result)
    }
}
