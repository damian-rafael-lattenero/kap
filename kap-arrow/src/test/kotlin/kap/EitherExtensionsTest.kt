package kap

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
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
        val result = Either.catch { 42 }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `catch wraps exception in Left`() {
        val ex = RuntimeException("boom")
        val result = Either.catch { throw ex }
        assertIs<Either.Left<Throwable>>(result)
        assertEquals(ex, result.value)
    }

    @Test
    fun `catch rethrows CancellationException`() {
        assertFailsWith<CancellationException> {
            Either.catch { throw CancellationException("cancelled") }
        }
    }

    // ── getOrNull ────────────────────────────────────────────────────────

    @Test
    fun `getOrNull returns value for Right`() {
        assertEquals(42, Either.Right(42).getOrNull())
    }

    @Test
    fun `getOrNull returns null for Left`() {
        assertNull(Either.Left("error").getOrNull())
    }

    // ── onLeft (replaces tapLeft) ────────────────────────────────────────

    @Test
    fun `onLeft executes side-effect on Left`() {
        var captured: String? = null
        val result = Either.Left("oops").onLeft { captured = it }
        assertEquals("oops", captured)
        assertEquals(Either.Left("oops"), result)
    }

    @Test
    fun `onLeft does nothing on Right`() {
        var called = false
        val result = Either.Right(42).onLeft { called = true }
        assertEquals(false, called)
        assertEquals(Either.Right(42), result)
    }

    // ── recover via fold ─────────────────────────────────────────────────

    @Test
    fun `recover maps Left to Right via fold`() {
        val result: Either<Nothing, Int> = Either.Left("err").fold(
            { Either.Right(it.length) },
            { Either.Right(it) }
        )
        assertEquals(Either.Right(3), result)
    }

    @Test
    fun `recover returns Right unchanged via getOrElse`() {
        val result = Either.Right(42).getOrElse { -1 }
        assertEquals(42, result)
    }

    // ── sequence (via fold) ──────────────────────────────────────────────

    @Test
    fun `sequence collects all Rights`() {
        val list: List<Either<String, Int>> = listOf(Either.Right(1), Either.Right(2), Either.Right(3))
        val result = list.fold<Either<String, Int>, Either<String, List<Int>>>(Either.Right(emptyList())) { acc, e ->
            acc.flatMap { lst -> e.map { lst + it } }
        }
        assertEquals(Either.Right(listOf(1, 2, 3)), result)
    }

    @Test
    fun `sequence short-circuits on first Left`() {
        val list: List<Either<String, Int>> = listOf(Either.Right(1), Either.Left("err"), Either.Right(3))
        val result = list.fold<Either<String, Int>, Either<String, List<Int>>>(Either.Right(emptyList())) { acc, e ->
            acc.flatMap { lst -> e.map { lst + it } }
        }
        assertEquals(Either.Left("err"), result)
    }

    // ── traverseEither (via map + fold) ──────────────────────────────────

    @Test
    fun `traverseEither maps and sequences`() {
        val result = listOf(1, 2, 3)
            .map { Either.Right(it * 2) as Either<String, Int> }
            .fold<Either<String, Int>, Either<String, List<Int>>>(Either.Right(emptyList())) { acc, e ->
                acc.flatMap { lst -> e.map { lst + it } }
            }
        assertEquals(Either.Right(listOf(2, 4, 6)), result)
    }

    @Test
    fun `traverseEither short-circuits on first Left`() {
        val mapped = listOf(1, 2, 3).map { n ->
            if (n == 2) Either.Left("fail at $n") else Either.Right(n) as Either<String, Int>
        }
        val result = mapped.fold<Either<String, Int>, Either<String, List<Int>>>(Either.Right(emptyList())) { acc, e ->
            acc.flatMap { lst -> e.map { lst + it } }
        }
        assertEquals(Either.Left("fail at 2"), result)
    }
}
