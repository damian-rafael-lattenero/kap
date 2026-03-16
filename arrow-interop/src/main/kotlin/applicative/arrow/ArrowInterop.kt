package applicative.arrow

import applicative.Computation
import applicative.Either
import applicative.Nel
import applicative.pure

// ── Arrow Either ↔ Applicative Either ───────────────────────────────────

/**
 * Converts an Arrow [arrow.core.Either] to an applicative [Either].
 *
 * ```
 * val arrowEither: arrow.core.Either<String, Int> = arrow.core.Either.Right(42)
 * val applicativeEither: Either<String, Int> = arrowEither.toApplicativeEither()
 * ```
 */
fun <E, A> arrow.core.Either<E, A>.toApplicativeEither(): Either<E, A> = when (this) {
    is arrow.core.Either.Left -> Either.Left(value)
    is arrow.core.Either.Right -> Either.Right(value)
}

/**
 * Converts an applicative [Either] to an Arrow [arrow.core.Either].
 *
 * ```
 * val applicativeEither: Either<String, Int> = Either.Right(42)
 * val arrowEither: arrow.core.Either<String, Int> = applicativeEither.toArrowEither()
 * ```
 */
fun <E, A> Either<E, A>.toArrowEither(): arrow.core.Either<E, A> = when (this) {
    is Either.Left -> arrow.core.Either.Left(value)
    is Either.Right -> arrow.core.Either.Right(value)
}

// ── Arrow NonEmptyList ↔ Applicative Nel ────────────────────────────────

/**
 * Converts an Arrow [arrow.core.NonEmptyList] to an applicative [Nel].
 */
fun <A> arrow.core.NonEmptyList<A>.toApplicativeNel(): Nel<A> =
    Nel(head, tail)

/**
 * Converts an applicative [Nel] to an Arrow [arrow.core.NonEmptyList].
 */
fun <A> Nel<A>.toArrowNel(): arrow.core.NonEmptyList<A> =
    arrow.core.NonEmptyList(head, tail)

// ── Arrow Validated Either ↔ Applicative Validated Either ───────────────

/**
 * Converts an Arrow validated result (`arrow.core.Either<arrow.core.NonEmptyList<E>, A>`)
 * to an applicative validated result (`Either<Nel<E>, A>`).
 *
 * Use this to bridge results from Arrow's `zipOrAccumulate` into
 * applicative's `apV`/`zipV` chains.
 *
 * ```
 * val arrowResult: arrow.core.Either<NonEmptyList<Err>, User> = ...
 * val computation: Computation<Either<Nel<Err>, User>> = arrowResult.toValidatedComputation()
 * ```
 */
fun <E, A> arrow.core.Either<arrow.core.NonEmptyList<E>, A>.toValidatedComputation(): Computation<Either<Nel<E>, A>> =
    pure(
        when (this) {
            is arrow.core.Either.Left -> Either.Left(value.toApplicativeNel())
            is arrow.core.Either.Right -> Either.Right(value)
        }
    )

/**
 * Converts an applicative validated result to an Arrow validated result.
 *
 * Use this to pass results from `apV`/`zipV` chains into Arrow-based pipelines.
 */
fun <E, A> Either<Nel<E>, A>.toArrowValidated(): arrow.core.Either<arrow.core.NonEmptyList<E>, A> =
    when (this) {
        is Either.Left -> arrow.core.Either.Left(value.toArrowNel())
        is Either.Right -> arrow.core.Either.Right(value)
    }

// ── Arrow parZip result → Computation ───────────────────────────────────

/**
 * Wraps a suspend lambda (typically calling Arrow's `parZip` or `parMap`) into a [Computation].
 *
 * This is a convenience bridge for teams migrating from Arrow to this library,
 * or for mixing Arrow parallel primitives into an applicative chain.
 *
 * ```
 * val arrowPhase: Computation<Pair<User, Cart>> = fromArrow {
 *     parZip({ fetchUser() }, { fetchCart() }) { u, c -> u to c }
 * }
 *
 * lift3(::Dashboard)
 *     .ap(arrowPhase.map { it.first })
 *     .ap(arrowPhase.map { it.second })
 *     .ap { fetchExtra() }
 * ```
 */
fun <A> fromArrow(block: suspend () -> A): Computation<A> = Computation { block() }

// ── Computation → Arrow's suspend world ─────────────────────────────────

/**
 * Executes this [Computation] and returns the result as an Arrow [arrow.core.Either],
 * catching non-cancellation exceptions into [arrow.core.Either.Left].
 *
 * ```
 * val result: arrow.core.Either<Throwable, User> = myComputation.runCatchingArrow(scope)
 * ```
 */
suspend fun <A> Computation<A>.runCatchingArrow(
    scope: kotlinx.coroutines.CoroutineScope,
): arrow.core.Either<Throwable, A> =
    try {
        arrow.core.Either.Right(with(this) { scope.execute() })
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        arrow.core.Either.Left(e)
    }
