package applicative

import kotlinx.coroutines.CancellationException

/**
 * Executes [block] and wraps the result in [Right], or catches non-cancellation
 * exceptions in [Left]. [CancellationException] always propagates.
 */
inline fun <A> Either.Companion.`catch`(block: () -> A): Either<Throwable, A> =
    try {
        Either.Right(block())
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(e)
    }

/** Alias for [catch] — catches non-fatal exceptions. [CancellationException] always propagates. */
inline fun <A> Either.Companion.catchNonFatal(block: () -> A): Either<Throwable, A> = `catch`(block)
