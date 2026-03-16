package applicative

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
        val r: Either<String, Int> = right(42)
        assertEquals(r, r.map { it })
    }

    @Test
    fun `map identity on Left`() {
        val l: Either<String, Int> = left("err")
        assertEquals(l, l.map { it * 2 })
    }

    @Test
    fun `map composition - map f-compose-g equals map g then map f`() {
        val f: (String) -> Int = { it.length }
        val g: (Int) -> String = { "n=$it" }
        val v: Either<String, Int> = right(42)

        assertEquals(v.map { f(g(it)) }, v.map(g).map(f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // MONAD LAWS for Either.flatMap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `monad left identity - right(a) flatMap f equals f(a)`() {
        val a = 42
        val f: (Int) -> Either<String, String> = { right("v=$it") }

        assertEquals(f(a), right(a).flatMap(f))
    }

    @Test
    fun `monad right identity - m flatMap right equals m`() {
        val m: Either<String, Int> = right(42)
        assertEquals(m, m.flatMap { right(it) })
    }

    @Test
    fun `monad right identity on Left`() {
        val m: Either<String, Int> = left("err")
        assertEquals(m, m.flatMap { right(it) })
    }

    @Test
    fun `monad associativity`() {
        val m: Either<String, Int> = right(10)
        val f: (Int) -> Either<String, String> = { right("n=$it") }
        val g: (String) -> Either<String, Int> = { right(it.length) }

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
        val l: Either<Int, String> = left(42)
        assertEquals(left("err=42"), l.mapLeft { "err=$it" })
    }

    @Test
    fun `mapLeft is identity on Right`() {
        val r: Either<Int, String> = right("ok")
        assertEquals(r, r.mapLeft { it * 2 })
    }

    // ════════════════════════════════════════════════════════════════════════
    // fold / getOrElse / getOrNull / isRight / isLeft
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fold handles both cases`() {
        assertEquals("left=42", left(42).fold({ "left=$it" }, { "right=$it" }))
        assertEquals("right=ok", right("ok").fold({ "left=$it" }, { "right=$it" }))
    }

    @Test
    fun `getOrElse returns value or computes default`() {
        assertEquals(42, right(42).getOrElse { -1 })
        assertEquals(-1, left("err").getOrElse { -1 })
    }

    @Test
    fun `getOrNull returns value or null`() {
        assertEquals(42, right(42).getOrNull())
        assertNull(left("err").getOrNull())
    }

    @Test
    fun `isRight and isLeft`() {
        assertTrue(right(42).isRight)
        assertFalse(right(42).isLeft)
        assertTrue(left("err").isLeft)
        assertFalse(left("err").isRight)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Structural equality
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Left and Right have structural equality`() {
        assertEquals(left("err"), left("err"))
        assertEquals(right(42), right(42))
        assertFalse(left("err") == right("err") as Any)
    }

    // ════════════════════════════════════════════════════════════════════════
    // swap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `swap turns Right into Left`() {
        assertEquals(left(42), right(42).swap())
    }

    @Test
    fun `swap turns Left into Right`() {
        assertEquals(right("err"), left("err").swap())
    }

    @Test
    fun `swap is its own inverse`() {
        val r: Either<String, Int> = right(42)
        val l: Either<String, Int> = left("err")
        assertEquals(r, r.swap().swap())
        assertEquals(l, l.swap().swap())
    }

    // ════════════════════════════════════════════════════════════════════════
    // bimap
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `bimap transforms Right side`() {
        val r: Either<String, Int> = right(42)
        assertEquals(right("v=42"), r.bimap({ "err:$it" }, { "v=$it" }))
    }

    @Test
    fun `bimap transforms Left side`() {
        val l: Either<String, Int> = left("oops")
        assertEquals(left("err:oops"), l.bimap({ "err:$it" }, { "v=$it" }))
    }

    @Test
    fun `bimap identity is identity`() {
        val r: Either<String, Int> = right(42)
        val l: Either<String, Int> = left("err")
        assertEquals(r, r.bimap({ it }, { it }))
        assertEquals(l, l.bimap({ it }, { it }))
    }

    // ════════════════════════════════════════════════════════════════════════
    // onLeft / onRight
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `onLeft executes side-effect on Left`() {
        var captured: String? = null
        val l: Either<String, Int> = left("err")
        val result = l.onLeft { captured = it }
        assertEquals("err", captured)
        assertEquals(l, result)
    }

    @Test
    fun `onLeft does nothing on Right`() {
        var called = false
        val r: Either<String, Int> = right(42)
        val result = r.onLeft { called = true }
        assertFalse(called)
        assertEquals(r, result)
    }

    @Test
    fun `onRight executes side-effect on Right`() {
        var captured: Int? = null
        val r: Either<String, Int> = right(42)
        val result = r.onRight { captured = it }
        assertEquals(42, captured)
        assertEquals(r, result)
    }

    @Test
    fun `onRight does nothing on Left`() {
        var called = false
        val l: Either<String, Int> = left("err")
        val result = l.onRight { called = true }
        assertFalse(called)
        assertEquals(l, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // merge
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `merge extracts value from Right`() {
        assertEquals("ok", right("ok").merge())
    }

    @Test
    fun `merge extracts value from Left`() {
        assertEquals("err", left("err").merge())
    }
}
