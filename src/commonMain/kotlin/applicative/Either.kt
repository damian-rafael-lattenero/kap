package applicative

/**
 * A minimal Either type — the foundation for validated error accumulation.
 *
 * [Left] represents failure, [Right] represents success.
 * This is deliberately minimal: it exists to serve [Validated][apV],
 * not as a general-purpose Either library.
 */
sealed interface Either<out E, out A> {
    data class Left<out E>(val value: E) : Either<E, Nothing>
    data class Right<out A>(val value: A) : Either<Nothing, A>
}

// ── constructors ─────────────────────────────────────────────────────────

/** Wraps a success value. */
fun <A> right(a: A): Either<Nothing, A> = Either.Right(a)

/** Wraps a failure value. */
fun <E> left(e: E): Either<E, Nothing> = Either.Left(e)

// ── map / flatMap ────────────────────────────────────────────────────────

/** Transforms the success value. */
fun <E, A, B> Either<E, A>.map(f: (A) -> B): Either<E, B> = when (this) {
    is Either.Left -> this
    is Either.Right -> Either.Right(f(value))
}

/** Chains a computation that may fail. */
fun <E, A, B> Either<E, A>.flatMap(f: (A) -> Either<E, B>): Either<E, B> = when (this) {
    is Either.Left -> this
    is Either.Right -> f(value)
}

/** Transforms the failure value. */
fun <E, A, F> Either<E, A>.mapLeft(f: (E) -> F): Either<F, A> = when (this) {
    is Either.Left -> Either.Left(f(value))
    is Either.Right -> this
}

// ── fold / extract ───────────────────────────────────────────────────────

/** Eliminates the Either by handling both cases. */
fun <E, A, B> Either<E, A>.fold(onLeft: (E) -> B, onRight: (A) -> B): B = when (this) {
    is Either.Left -> onLeft(value)
    is Either.Right -> onRight(value)
}

/** Returns the success value or computes a default from the failure. */
fun <E, A> Either<E, A>.getOrElse(default: (E) -> A): A = when (this) {
    is Either.Left -> default(value)
    is Either.Right -> value
}

/** Returns the success value or null. */
fun <E, A> Either<E, A>.getOrNull(): A? = when (this) {
    is Either.Left -> null
    is Either.Right -> value
}

/** Returns true if this is a [Right]. */
val Either<*, *>.isRight: Boolean get() = this is Either.Right

/** Returns true if this is a [Left]. */
val Either<*, *>.isLeft: Boolean get() = this is Either.Left

// ── additional transformations ──────────────────────────────────────────

/** Swaps [Left] and [Right]. */
fun <E, A> Either<E, A>.swap(): Either<A, E> = when (this) {
    is Either.Left -> Either.Right(value)
    is Either.Right -> Either.Left(value)
}

/** Maps both sides simultaneously. */
inline fun <E, A, F, B> Either<E, A>.bimap(fe: (E) -> F, fa: (A) -> B): Either<F, B> = when (this) {
    is Either.Left -> Either.Left(fe(value))
    is Either.Right -> Either.Right(fa(value))
}

/** Executes a side-effect on [Left], returns this unchanged. */
inline fun <E, A> Either<E, A>.onLeft(f: (E) -> Unit): Either<E, A> {
    if (this is Either.Left) f(value)
    return this
}

/** Executes a side-effect on [Right], returns this unchanged. */
inline fun <E, A> Either<E, A>.onRight(f: (A) -> Unit): Either<E, A> {
    if (this is Either.Right) f(value)
    return this
}

/** Merges an [Either] where both sides are the same type. */
fun <A> Either<A, A>.merge(): A = when (this) {
    is Either.Left -> value
    is Either.Right -> value
}
