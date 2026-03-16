package applicative

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class EitherExtensionsTest {

    // ── Either.catch ────────────────────────────────────────────────────

    @Test
    fun `catch wraps success in Right`() {
        val result = Either.`catch` { 42 }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `catch wraps exception in Left`() {
        val ex = RuntimeException("boom")
        val result = Either.`catch` { throw ex }
        assertIs<Either.Left<Throwable>>(result)
        assertEquals(ex, result.value)
    }

    @Test
    fun `catch rethrows CancellationException`() {
        assertFailsWith<CancellationException> {
            Either.`catch` { throw CancellationException("cancelled") }
        }
    }

    // ── catchNonFatal ───────────────────────────────────────────────────

    @Test
    fun `catchNonFatal behaves like catch for success`() {
        assertEquals(Either.Right("ok"), Either.catchNonFatal { "ok" })
    }

    @Test
    fun `catchNonFatal behaves like catch for failure`() {
        val ex = IllegalStateException("fail")
        val result = Either.catchNonFatal { throw ex }
        assertIs<Either.Left<Throwable>>(result)
        assertEquals(ex, result.value)
    }

    // ── orNull ──────────────────────────────────────────────────────────

    @Test
    fun `orNull returns value for Right`() {
        assertEquals(42, right(42).orNull())
    }

    @Test
    fun `orNull returns null for Left`() {
        assertNull(left("error").orNull())
    }

    // ── tapLeft ─────────────────────────────────────────────────────────

    @Test
    fun `tapLeft executes side-effect on Left`() {
        var captured: String? = null
        val result = left("oops").tapLeft { captured = it }
        assertEquals("oops", captured)
        assertEquals(Either.Left("oops"), result)
    }

    @Test
    fun `tapLeft does nothing on Right`() {
        var called = false
        val result = right(42).tapLeft { called = true }
        assertEquals(false, called)
        assertEquals(Either.Right(42), result)
    }

    // ── recover ─────────────────────────────────────────────────────────

    @Test
    fun `recover maps Left to Right`() {
        val result: Either<Nothing, Int> = left("err").recover { it.length }
        assertEquals(Either.Right(3), result)
    }

    @Test
    fun `recover returns Right unchanged`() {
        val result: Either<Nothing, Int> = right(42).recover { -1 }
        assertEquals(Either.Right(42), result)
    }

    // ── sequence ────────────────────────────────────────────────────────

    @Test
    fun `sequence collects all Rights`() {
        val list = listOf(right(1), right(2), right(3))
        assertEquals(Either.Right(listOf(1, 2, 3)), list.sequence())
    }

    @Test
    fun `sequence short-circuits on first Left`() {
        val list = listOf(right(1), left("err"), right(3))
        assertEquals(Either.Left("err"), list.sequence())
    }

    // ── traverseEither ──────────────────────────────────────────────────

    @Test
    fun `traverseEither maps and sequences`() {
        val result = listOf(1, 2, 3).traverseEither { right(it * 2) }
        assertEquals(Either.Right(listOf(2, 4, 6)), result)
    }

    @Test
    fun `traverseEither short-circuits on first Left`() {
        val result = listOf(1, 2, 3).traverseEither { n ->
            if (n == 2) left("fail at $n") else right(n)
        }
        assertEquals(Either.Left("fail at 2"), result)
    }
}
