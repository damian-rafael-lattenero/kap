package applicative

/**
 * A minimal Either type — the foundation for validated error accumulation.
 *
 * [Left] represents failure, [Right] represents success.
 * This is deliberately minimal: it exists to serve [Validated][apV],
 * not as a general-purpose Either library.
 */
sealed interface Either<out E, out A> {
    companion object {}
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

// ── guards & recovery ─────────────────────────────────────────────────

/**
 * Validates the [Right] value against a [predicate].
 * If the predicate fails, returns [Left] with the result of [error].
 *
 * ```
 * right(18).ensure({ "too young" }) { it >= 21 } // Left("too young")
 * right(25).ensure({ "too young" }) { it >= 21 } // Right(25)
 * ```
 */
inline fun <E, A> Either<E, A>.ensure(error: () -> E, predicate: (A) -> Boolean): Either<E, A> =
    when (this) {
        is Either.Left -> this
        is Either.Right -> if (predicate(value)) this else Either.Left(error())
    }

/**
 * Like [ensure] but with predicate first — matches the `filter` convention.
 *
 * ```
 * right(18).filterOrElse({ it >= 21 }) { "too young" } // Left("too young")
 * ```
 */
inline fun <E, A> Either<E, A>.filterOrElse(predicate: (A) -> Boolean, default: () -> E): Either<E, A> =
    ensure(default, predicate)

/**
 * Recovers from a [Left] by applying [handler] to produce a success value.
 * More ergonomic than [fold] when you only need the error case.
 *
 * ```
 * left("oops").getOrHandle { it.uppercase() } // "OOPS"
 * right(42).getOrHandle { -1 }                // 42
 * ```
 */
inline fun <E, A> Either<E, A>.getOrHandle(handler: (E) -> A): A = when (this) {
    is Either.Left -> handler(value)
    is Either.Right -> value
}

/**
 * Recovers from a [Left] by producing a new [Either].
 * Analogous to [Computation.recoverWith].
 *
 * ```
 * left("primary").handleErrorWith { right("fallback") } // Right("fallback")
 * ```
 */
inline fun <E, A> Either<E, A>.handleErrorWith(handler: (E) -> Either<E, A>): Either<E, A> =
    when (this) {
        is Either.Left -> handler(value)
        is Either.Right -> this
    }

// ── zip: combine two Eithers ─────────────────────────────────────────

/**
 * Combines two [Either] values with [combine] if both are [Right].
 * Short-circuits on the first [Left].
 *
 * ```
 * right(1).zip(right("a")) { n, s -> "$n$s" } // Right("1a")
 * left("e1").zip(right("a")) { n, s -> "$n$s" } // Left("e1")
 * ```
 */
inline fun <E, A, B, C> Either<E, A>.zip(other: Either<E, B>, crossinline combine: (A, B) -> C): Either<E, C> =
    flatMap { a -> other.map { b -> combine(a, b) } }

// ── bridge to validated ───────────────────────────────────────────────

/**
 * Lifts a single-error [Either] into the [NonEmptyList]-based validated world.
 *
 * ```
 * left("bad").toValidatedNel()  // Left(Nel("bad"))
 * right(42).toValidatedNel()    // Right(42)
 * ```
 */
fun <E, A> Either<E, A>.toValidatedNel(): Either<NonEmptyList<E>, A> = when (this) {
    is Either.Left -> Either.Left(value.toNonEmptyList())
    is Either.Right -> this
}

// ── aliases & taps ──────────────────────────────────────────────────

/** Alias for [getOrNull]. Arrow naming compatibility. */
fun <E, A> Either<E, A>.orNull(): A? = getOrNull()

/** Executes [f] on the [Left] value, returns this unchanged. Alias for [onLeft]. */
inline fun <E, A> Either<E, A>.tapLeft(f: (E) -> Unit): Either<E, A> = onLeft(f)

// ── recover ─────────────────────────────────────────────────────────

/**
 * Recovers from [Left] by mapping the error to a success value.
 * Unlike [getOrHandle] which extracts the value, this returns an [Either] for continued chaining.
 */
inline fun <E, A> Either<E, A>.recover(f: (E) -> A): Either<Nothing, A> = when (this) {
    is Either.Left -> Either.Right(f(value))
    is Either.Right -> @Suppress("UNCHECKED_CAST") (this as Either<Nothing, A>)
}

// ── sequence & traverse ─────────────────────────────────────────────

/**
 * Sequences a list of [Either] values, short-circuiting on the first [Left].
 * Returns [Right] with all values if all are [Right].
 */
fun <E, A> Iterable<Either<E, A>>.sequence(): Either<E, List<A>> {
    val results = mutableListOf<A>()
    for (either in this) {
        when (either) {
            is Either.Left -> return either
            is Either.Right -> results.add(either.value)
        }
    }
    return Either.Right(results)
}

/**
 * Applies [f] to each element and sequences the results, short-circuiting on the first [Left].
 */
inline fun <E, A, B> Iterable<A>.traverseEither(f: (A) -> Either<E, B>): Either<E, List<B>> {
    val results = mutableListOf<B>()
    for (a in this) {
        when (val either = f(a)) {
            is Either.Left -> return either
            is Either.Right -> results.add(either.value)
        }
    }
    return Either.Right(results)
}
