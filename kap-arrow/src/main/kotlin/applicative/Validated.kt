package applicative

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ── entry points ─────────────────────────────────────────────────────────

/** Wraps a success value into a validated computation. */
fun <E, A> valid(a: A): Effect<Either<NonEmptyList<E>, A>> = Effect.of(Either.Right(a))

/** Wraps a single error into a validated computation. */
fun <E, A> invalid(e: E): Effect<Either<NonEmptyList<E>, A>> = Effect.of(Either.Left(nonEmptyListOf(e)))

/** Wraps multiple errors into a validated computation. */
fun <E, A> invalidAll(errors: NonEmptyList<E>): Effect<Either<NonEmptyList<E>, A>> = Effect.of(Either.Left(errors))

// ── withV: parallel applicative apply with error accumulation ────────────

/**
 * Validated parallel apply — runs both sides in parallel,
 * **accumulating** errors from both if both fail.
 */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.withV(
    fa: Effect<Either<NonEmptyList<E>, A>>,
): Effect<Either<NonEmptyList<E>, B>> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Effect {
            val deferredA = async {
                signal.await()
                with(fa) { execute() }
            }
            val ef = with(self) { execute() }
            val ea = deferredA.await()
            combineValidated(ef, ea)
        }, signal)
    } else {
        Effect {
            val deferredA = async { with(fa) { execute() } }
            val ef = with(self) { execute() }
            val ea = deferredA.await()
            combineValidated(ef, ea)
        }
    }
}

private fun <E, A, B> combineValidated(
    ef: Either<NonEmptyList<E>, (A) -> B>,
    ea: Either<NonEmptyList<E>, A>,
): Either<NonEmptyList<E>, B> = when {
    ef is Either.Right && ea is Either.Right -> Either.Right(ef.value(ea.value))
    ef is Either.Left && ea is Either.Left -> Either.Left(ef.value + ea.value)
    ef is Either.Left -> ef
    else -> @Suppress("UNCHECKED_CAST") (ea as Either.Left<NonEmptyList<E>>)
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.withV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Effect<Either<NonEmptyList<E>, B>> = withV(Effect { fa() })

// ── thenV: true phase barrier with short-circuit ──────────────────

/**
 * True phase barrier for validated computations — awaits the left side,
 * then runs [fa], and **gates** all subsequent [withV] calls until the
 * barrier completes.
 *
 * Short-circuits: if the left side is [Either.Left], the right side is **not** executed.
 */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.thenV(
    fa: Effect<Either<NonEmptyList<E>, A>>,
): Effect<Either<NonEmptyList<E>, B>> {
    val self = this
    val signal = CompletableDeferred<Unit>()
    return PhaseBarrier(Effect {
        when (val ef = with(self) { execute() }) {
            is Either.Left -> ef
            is Either.Right -> when (val ea = with(fa) { execute() }) {
                is Either.Left -> ea
                is Either.Right -> Either.Right(ef.value(ea.value))
            }
        }
    }, signal)
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.thenV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Effect<Either<NonEmptyList<E>, B>> = thenV(Effect { fa() })

// ── thenValueV: sequential value fill without barrier ────────────────────

/**
 * Sequential validated value fill — awaits the left side, then runs [fa].
 * Short-circuits on [Either.Left]. Does **not** create a phase barrier.
 */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.thenValueV(
    fa: Effect<Either<NonEmptyList<E>, A>>,
): Effect<Either<NonEmptyList<E>, B>> = Effect {
    when (val ef = with(this@thenValueV) { execute() }) {
        is Either.Left -> ef
        is Either.Right -> when (val ea = with(fa) { execute() }) {
            is Either.Left -> ea
            is Either.Right -> Either.Right(ef.value(ea.value))
        }
    }
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Effect<Either<NonEmptyList<E>, (A) -> B>>.thenValueV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Effect<Either<NonEmptyList<E>, B>> = thenValueV(Effect { fa() })

// ── catching: bridge from exception world to validated world ─────────────

/**
 * Converts a [Effect] into a validated computation by catching
 * non-cancellation exceptions and mapping them to errors via [toError].
 */
fun <E, A> Effect<A>.catching(toError: (Throwable) -> E): Effect<Either<NonEmptyList<E>, A>> =
    Effect {
        try {
            Either.Right(with(this@catching) { execute() })
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Either.Left(nonEmptyListOf(toError(e)))
        }
    }

// ── validate: predicate-based validation ─────────────────────────────────

/**
 * Validates the result of this computation with a predicate.
 * If [toError] returns null, the value passes validation.
 * If [toError] returns a non-null error, it becomes an [Either.Left].
 */
fun <E, A> Effect<A>.validate(toError: (A) -> E?): Effect<Either<NonEmptyList<E>, A>> =
    Effect {
        val a = with(this@validate) { execute() }
        val error = toError(a)
        if (error == null) Either.Right(a) else Either.Left(nonEmptyListOf(error))
    }

// ── ensureV: predicate-based validated guard ───────────────────────────

/**
 * Validates the result against [predicate].
 * If it passes, wraps in [Either.Right]. If it fails, wraps [error] in [Either.Left].
 */
fun <E, A> Effect<A>.ensureV(
    error: (A) -> E,
    predicate: (A) -> Boolean,
): Effect<Either<NonEmptyList<E>, A>> = Effect {
    val a = with(this@ensureV) { execute() }
    if (predicate(a)) Either.Right(a) else Either.Left(nonEmptyListOf(error(a)))
}

/**
 * Like [ensureV] but allows returning multiple errors when the predicate fails.
 */
fun <E, A> Effect<A>.ensureVAll(
    errors: (A) -> NonEmptyList<E>,
    predicate: (A) -> Boolean,
): Effect<Either<NonEmptyList<E>, A>> = Effect {
    val a = with(this@ensureVAll) { execute() }
    if (predicate(a)) Either.Right(a) else Either.Left(errors(a))
}

// ── traverseV: parallel traverse with error accumulation ─────────────────

/**
 * Applies [f] to each element in parallel, accumulating all errors.
 */
fun <E, A, B> Iterable<A>.traverseV(
    f: (A) -> Effect<Either<NonEmptyList<E>, B>>,
): Effect<Either<NonEmptyList<E>, List<B>>> = Effect {
    val results = map { a -> async { with(f(a)) { execute() } } }.awaitAll()
    val errors = results.filterIsInstance<Either.Left<NonEmptyList<E>>>()
    if (errors.isEmpty()) {
        Either.Right(results.map { (it as Either.Right).value })
    } else {
        Either.Left(errors.map { it.value }.reduce { acc, nel -> acc + nel })
    }
}

/**
 * Like [traverseV] but limits the number of concurrent computations.
 */
fun <E, A, B> Iterable<A>.traverseV(
    concurrency: Int,
    f: (A) -> Effect<Either<NonEmptyList<E>, B>>,
): Effect<Either<NonEmptyList<E>, List<B>>> = Effect {
    val semaphore = Semaphore(concurrency)
    val results = map { a ->
        async { semaphore.withPermit { with(f(a)) { execute() } } }
    }.awaitAll()
    val errors = results.filterIsInstance<Either.Left<NonEmptyList<E>>>()
    if (errors.isEmpty()) {
        Either.Right(results.map { (it as Either.Right).value })
    } else {
        Either.Left(errors.map { it.value }.reduce { acc, nel -> acc + nel })
    }
}

// ── sequenceV ─────────────────────────────────────────────────────────────

/**
 * Executes all validated computations in parallel, accumulating all errors.
 */
fun <E, A> Iterable<Effect<Either<NonEmptyList<E>, A>>>.sequenceV(): Effect<Either<NonEmptyList<E>, List<A>>> =
    traverseV { it }

/**
 * Like [sequenceV] but limits the number of concurrent computations.
 */
fun <E, A> Iterable<Effect<Either<NonEmptyList<E>, A>>>.sequenceV(
    concurrency: Int,
): Effect<Either<NonEmptyList<E>, List<A>>> =
    traverseV(concurrency) { it }

// ── andThenV: monadic bind for validated computations ────────────────────

/**
 * Monadic bind for validated computations — sequential, short-circuits on error.
 */
inline fun <E, A, B> Effect<Either<NonEmptyList<E>, A>>.andThenV(
    crossinline f: (A) -> Effect<Either<NonEmptyList<E>, B>>,
): Effect<Either<NonEmptyList<E>, B>> = Effect {
    when (val ea = with(this@andThenV) { execute() }) {
        is Either.Left -> ea
        is Either.Right -> with(f(ea.value)) { execute() }
    }
}

// ── recoverV ─────────────────────────────────────────────────────────────

/**
 * Catches non-cancellation exceptions thrown during a validated computation
 * and converts them into validation errors via [f].
 */
fun <E, A> Effect<Either<NonEmptyList<E>, A>>.recoverV(
    f: (Throwable) -> E,
): Effect<Either<NonEmptyList<E>, A>> = Effect {
    try {
        with(this@recoverV) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(nonEmptyListOf(f(e)))
    }
}

// ── unwrap ───────────────────────────────────────────────────────────────

/**
 * Exception thrown when a validated computation is unwrapped with [orThrow]
 * and contains errors.
 */
class ValidationException(val errors: NonEmptyList<*>) : RuntimeException(
    "Validation failed with ${errors.size} error(s): $errors"
)

/**
 * Unwraps a validated computation: returns the value on [Either.Right],
 * throws [ValidationException] on [Either.Left].
 */
fun <E, A> Effect<Either<NonEmptyList<E>, A>>.orThrow(): Effect<A> = map {
    when (it) {
        is Either.Right -> it.value
        is Either.Left -> throw ValidationException(it.value)
    }
}

// ── mapV: transform the success side ──────────────────────────────────────

/**
 * Transforms the success value inside a validated computation.
 */
fun <E, A, B> Effect<Either<NonEmptyList<E>, A>>.mapV(f: (A) -> B): Effect<Either<NonEmptyList<E>, B>> =
    map { it.map(f) }

// ── mapError: transform the error type ───────────────────────────────────

/**
 * Transforms the error type inside a validated computation.
 */
fun <E, F, A> Effect<Either<NonEmptyList<E>, A>>.mapError(f: (E) -> F): Effect<Either<NonEmptyList<F>, A>> =
    map { either ->
        when (either) {
            is Either.Right -> either
            is Either.Left -> Either.Left(NonEmptyList(f(either.value.head), either.value.tail.map(f)))
        }
    }

// ── validated { } builder: short-circuit DSL ────────────────────────────

@PublishedApi
internal class ValidatedShortCircuit(val errors: NonEmptyList<*>) : ControlFlowException()

/**
 * Scope for the [validated] builder, providing [bind] for short-circuit
 * sequential validation using Arrow types.
 */
class ValidatedScope<E> @PublishedApi internal constructor(
    @PublishedApi internal val scope: kotlinx.coroutines.CoroutineScope,
) {
    /**
     * Unwraps an [Either] — returns the [Right][Either.Right] value or
     * short-circuits the [validated] block with the [Left][Either.Left] errors.
     */
    fun <A> Either<NonEmptyList<E>, A>.bind(): A = when (this) {
        is Either.Right -> value
        is Either.Left -> throw ValidatedShortCircuit(value)
    }

    /**
     * Executes a validated [Effect] and unwraps the result — returns the
     * [Right][Either.Right] value or short-circuits with the [Left][Either.Left] errors.
     */
    suspend fun <A> Effect<Either<NonEmptyList<E>, A>>.bindV(): A =
        with(this@bindV) { scope.execute() }.bind()

    /**
     * Executes a non-validated suspend block inside a validated pipeline.
     */
    suspend fun <A> call(block: suspend () -> A): A = block()
}

/**
 * Short-circuit builder for validated computations using Arrow types.
 *
 * Provides [ValidatedScope.bind] to unwrap [Either.Right] values or
 * short-circuit on [Either.Left].
 */
@Suppress("UNCHECKED_CAST")
fun <E, A> validated(block: suspend ValidatedScope<E>.() -> A): Effect<Either<NonEmptyList<E>, A>> =
    Effect {
        try {
            Either.Right(ValidatedScope<E>(this).block())
        } catch (e: ValidatedShortCircuit) {
            Either.Left(e.errors as NonEmptyList<E>)
        }
    }

/**
 * Alias for [validated] — parallel error accumulation within phases,
 * sequential short-circuit between phases.
 */
@Suppress("UNCHECKED_CAST")
fun <E, A> accumulate(block: suspend ValidatedScope<E>.() -> A): Effect<Either<NonEmptyList<E>, A>> =
    validated(block)
