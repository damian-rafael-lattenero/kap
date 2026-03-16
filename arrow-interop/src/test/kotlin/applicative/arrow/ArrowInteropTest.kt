package applicative.arrow

import applicative.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArrowInteropTest {

    // ════════════════════════════════════════════════════════════════════════
    // Either conversion
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Arrow Right converts to applicative Right`() {
        val arrow = arrow.core.Either.Right(42)
        val result = arrow.toApplicativeEither()
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Arrow Left converts to applicative Left`() {
        val arrow = arrow.core.Either.Left("error")
        val result = arrow.toApplicativeEither()
        assertEquals(Either.Left("error"), result)
    }

    @Test
    fun `applicative Right converts to Arrow Right`() {
        val either: Either<String, Int> = Either.Right(42)
        val result = either.toArrowEither()
        assertEquals(arrow.core.Either.Right(42), result)
    }

    @Test
    fun `applicative Left converts to Arrow Left`() {
        val either: Either<String, Int> = Either.Left("error")
        val result = either.toArrowEither()
        assertEquals(arrow.core.Either.Left("error"), result)
    }

    @Test
    fun `roundtrip Arrow Either - Right`() {
        val original = arrow.core.Either.Right(42)
        val result = original.toApplicativeEither().toArrowEither()
        assertEquals(original, result)
    }

    @Test
    fun `roundtrip Arrow Either - Left`() {
        val original: arrow.core.Either<String, Int> = arrow.core.Either.Left("err")
        val result = original.toApplicativeEither().toArrowEither()
        assertEquals(original, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Nel conversion
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Arrow NonEmptyList converts to applicative Nel`() {
        val arrowNel = arrow.core.NonEmptyList(1, listOf(2, 3))
        val result = arrowNel.toApplicativeNel()
        assertEquals(1, result.head)
        assertEquals(listOf(2, 3), result.tail)
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun `applicative Nel converts to Arrow NonEmptyList`() {
        val nel = Nel(1, listOf(2, 3))
        val result = nel.toArrowNel()
        assertEquals(1, result.head)
        assertEquals(listOf(2, 3), result.tail)
    }

    @Test
    fun `roundtrip Nel single element`() {
        val original = arrow.core.NonEmptyList(42, emptyList())
        val result = original.toApplicativeNel().toArrowNel()
        assertEquals(original, result)
    }

    @Test
    fun `roundtrip Nel multiple elements`() {
        val original = arrow.core.NonEmptyList("a", listOf("b", "c", "d"))
        val result = original.toApplicativeNel().toArrowNel()
        assertEquals(original, result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Validated conversion
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Arrow validated Right converts to applicative validated computation`() = runTest {
        val arrowResult: arrow.core.Either<arrow.core.NonEmptyList<String>, Int> =
            arrow.core.Either.Right(42)

        val result = Async { arrowResult.toValidatedComputation() }
        assertEquals(Either.Right(42), result)
    }

    @Test
    fun `Arrow validated Left converts to applicative validated computation`() = runTest {
        val arrowResult: arrow.core.Either<arrow.core.NonEmptyList<String>, Int> =
            arrow.core.Either.Left(arrow.core.NonEmptyList("e1", listOf("e2")))

        val result = Async { arrowResult.toValidatedComputation() }
        assertIs<Either.Left<Nel<String>>>(result)
        assertEquals(listOf("e1", "e2"), result.value.toList())
    }

    @Test
    fun `applicative validated result converts to Arrow validated`() {
        val right: Either<Nel<String>, Int> = Either.Right(42)
        val arrowRight = right.toArrowValidated()
        assertEquals(arrow.core.Either.Right(42), arrowRight)

        val left: Either<Nel<String>, Int> = Either.Left(Nel("e1", listOf("e2")))
        val arrowLeft = left.toArrowValidated()
        assertIs<arrow.core.Either.Left<arrow.core.NonEmptyList<String>>>(arrowLeft)
        assertEquals(listOf("e1", "e2"), arrowLeft.value.all)
    }

    // ════════════════════════════════════════════════════════════════════════
    // fromArrow bridge
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `fromArrow wraps suspend lambda into Computation`() = runTest {
        val result = Async {
            fromArrow { 42 }
        }
        assertEquals(42, result)
    }

    @Test
    fun `fromArrow composes with ap`() = runTest {
        val result = Async {
            lift2 { a: Int, b: String -> "$b=$a" }
                .ap(fromArrow { 42 })
                .ap(fromArrow { "answer" })
        }
        assertEquals("answer=42", result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Arrow validated result can be used in applicative zipV chains
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `Arrow zipOrAccumulate result feeds into applicative zipV chain`() = runTest {
        // Simulate an Arrow validation result
        val arrowPhase1: arrow.core.Either<arrow.core.NonEmptyList<String>, String> =
            arrow.core.Either.Right("Alice")

        val result = Async {
            zipV(
                { arrowPhase1.toApplicativeEither().let { e ->
                    when (e) {
                        is Either.Left -> Either.Left(e.value.let { err ->
                            @Suppress("UNCHECKED_CAST")
                            err as Nel<String>
                        })
                        is Either.Right -> Either.Right(e.value)
                    }
                }},
                { Either.Right("bob@test.com") as Either<Nel<String>, String> },
            ) { name, email -> "$name <$email>" }
        }

        assertEquals(Either.Right("Alice <bob@test.com>"), result)
    }

    // ════════════════════════════════════════════════════════════════════════
    // runCatchingArrow
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `runCatchingArrow returns Right on success`() = runTest {
        val computation = pure(42)
        val result = computation.runCatchingArrow(this)
        assertEquals(arrow.core.Either.Right(42), result)
    }

    @Test
    fun `runCatchingArrow returns Left on failure`() = runTest {
        val computation = Computation<Int> { throw RuntimeException("boom") }
        val result = computation.runCatchingArrow(this)
        assertIs<arrow.core.Either.Left<Throwable>>(result)
        assertEquals("boom", result.value.message)
    }
}
