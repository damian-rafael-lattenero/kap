package applicative

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the kap-arrow extensions that bridge between kap-core Effects
 * and Arrow types (Either, NonEmptyList).
 */
class ArrowInteropTest {

    // ════════════════════════════════════════════════════════════════════════
    // attempt — Effect<A> → Effect<Either<Throwable, A>>
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `attempt wraps success in Right`() = runTest {
        val result = Async {
            Effect.of(42).attempt()
        }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `attempt wraps exception in Left`() = runTest {
        val result = Async {
            Effect<Int> { throw RuntimeException("boom") }.attempt()
        }
        assertIs<Either.Left<Throwable>>(result)
        assertEquals("boom", result.value.message)
    }

    @Test
    fun `attempt does not catch CancellationException`() = runTest {
        val result = runCatching {
            Async {
                Effect<Int> { throw CancellationException("cancelled") }.attempt()
            }
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun `attempt composes with validated operations via catching`() = runTest {
        val result = Async {
            kapV<String, Int, Int, Int> { a, b -> a + b }
                .withV(Effect { 1 }.catching { it.message ?: "unknown" })
                .withV(Effect { 2 }.catching { it.message ?: "unknown" })
        }
        assertEquals(Either.Right(3), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // raceEither — race two computations with different result types
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `raceEither returns Left when first computation wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Effect.of("fast"),
                fb = Effect { delay(10_000); 42 },
            )
        }
        assertEquals(Either.Left("fast"), result)
    }

    @Test
    fun `raceEither returns Right when second computation wins`() = runTest {
        val result = Async {
            raceEither(
                fa = Effect { delay(10_000); "slow" },
                fb = Effect.of(42),
            )
        }
        assertEquals(Either.Right(42), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // toEither — Result<A> → Either<Throwable, A>
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Result success converts to Either Right`() {
        val result = Result.success(42).toEither()
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Result failure converts to Either Left`() {
        val ex = RuntimeException("boom")
        val result = Result.failure<Int>(ex).toEither()
        assertIs<Either.Left<Throwable>>(result)
        assertEquals("boom", result.value.message)
    }

    // ════════════════════════════════════════════════════════════════════════
    // toResult — Either<Throwable, A> → Result<A>
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Either Right converts to Result success`() {
        val either: Either<Throwable, Int> = Either.Right(42)
        val result = either.toResult()
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `Either Left converts to Result failure`() {
        val either: Either<Throwable, Int> = Either.Left(RuntimeException("boom"))
        val result = either.toResult()
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Either Left with custom error type converts to Result failure via mapError`() {
        val either: Either<String, Int> = Either.Left("bad input")
        val result = either.toResult { IllegalArgumentException(it) }
        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun `roundtrip Result to Either and back`() {
        val original = Result.success("hello")
        val roundtripped = original.toEither().toResult()
        assertEquals(original.getOrNull(), roundtripped.getOrNull())
    }

    // ════════════════════════════════════════════════════════════════════════
    // toValidated — Result<A> → Validated<E, A>
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Result success converts to valid computation`() = runTest {
        val result = Async {
            Result.success(42).toValidated { "error: ${it.message}" }
        }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Result failure converts to invalid computation`() = runTest {
        val result = Async {
            Result.failure<Int>(RuntimeException("boom")).toValidated { "error: ${it.message}" }
        }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals("error: boom", result.value.head)
    }

    // ════════════════════════════════════════════════════════════════════════
    // fromArrow — wraps suspend lambda into Effect
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fromArrow wraps suspend lambda into Effect`() = runTest {
        val result = Async {
            fromArrow { 42 }
        }
        assertEquals(42, result)
    }

    @Test
    fun `fromArrow composes with ap`() = runTest {
        val result = Async {
            kap { a: Int, b: String -> "$b=$a" }
                .with(fromArrow { 42 })
                .with(fromArrow { "answer" })
        }
        assertEquals("answer=42", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // runCatchingArrow — execute Effect and return Arrow Either
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `runCatchingArrow returns Right on success`() = runTest {
        val computation = Effect.of(42)
        val result = computation.runCatchingArrow(this)
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `runCatchingArrow returns Left on failure`() = runTest {
        val computation = Effect<Int> { throw RuntimeException("boom") }
        val result = computation.runCatchingArrow(this)
        assertIs<Either.Left<Throwable>>(result)
        assertEquals("boom", result.value.message)
    }

    @Test
    fun `runCatchingArrow does not catch CancellationException`() = runTest {
        val computation = Effect<Int> { throw CancellationException("cancelled") }
        val result = runCatching { computation.runCatchingArrow(this@runTest) }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    // ════════════════════════════════════════════════════════════════════════
    // Nel typealias
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Nel typealias works as NonEmptyList`() {
        val nel: Nel<String> = nonEmptyListOf("a", "b", "c")
        assertEquals(3, nel.size)
        assertEquals("a", nel.head)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Validated typealias
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Validated typealias works for valid computations`() = runTest {
        val v: Validated<String, Int> = valid(42)
        val result = Async { v }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Validated typealias works for invalid computations`() = runTest {
        val v: Validated<String, Int> = invalid("oops")
        val result = Async { v }
        assertIs<Either.Left<NonEmptyList<String>>>(result)
        assertEquals(nonEmptyListOf("oops"), result.value)
    }
}
