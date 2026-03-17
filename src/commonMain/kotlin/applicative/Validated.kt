package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// ── entry points ─────────────────────────────────────────────────────────

/** Wraps a success value into a validated computation. */
fun <E, A> valid(a: A): Computation<Either<NonEmptyList<E>, A>> = pure(Either.Right(a))

/** Wraps a single error into a validated computation. */
fun <E, A> invalid(e: E): Computation<Either<NonEmptyList<E>, A>> = pure(Either.Left(e.toNonEmptyList()))

/** Wraps multiple errors into a validated computation. */
fun <E, A> invalidAll(errors: NonEmptyList<E>): Computation<Either<NonEmptyList<E>, A>> = pure(Either.Left(errors))

// ── apV: parallel applicative apply with error accumulation ──────────────

/**
 * Validated applicative apply — runs both sides in parallel,
 * **accumulating** errors from both if both fail.
 *
 * ```
 * liftV3<Err, Card, Stock, Addr, Checkout>(::Checkout)
 *     .apV { validateCard(card) }    // parallel, accumulates
 *     .apV { checkStock(items) }     // parallel, accumulates
 *     .apV { validateAddr(addr) }    // parallel, accumulates
 * // If card AND stock fail → Either.Left(Nel(cardErr, stockErr))
 * ```
 *
 * **Why not `inline`:** Same rationale as [ap] — the [Computation] SAM constructor
 * stores the lambda, preventing `inline` usage.
 */
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.apV(
    fa: Computation<Either<NonEmptyList<E>, A>>,
): Computation<Either<NonEmptyList<E>, B>> {
    val self = this
    return if (self is PhaseBarrier) {
        val signal = self.signal
        PhaseBarrier(Computation {
            val deferredA = async {
                signal.await()
                with(fa) { execute() }
            }
            val ef = with(self) { execute() }
            val ea = deferredA.await()
            when {
                ef is Either.Right && ea is Either.Right -> Either.Right(ef.value(ea.value))
                ef is Either.Left && ea is Either.Left -> Either.Left(ef.value + ea.value)
                ef is Either.Left -> ef
                else -> @Suppress("UNCHECKED_CAST") (ea as Either.Left<NonEmptyList<E>>)
            }
        }, signal)
    } else {
        Computation {
            val deferredA = async { with(fa) { execute() } }
            val ef = with(self) { execute() }
            val ea = deferredA.await()
            when {
                ef is Either.Right && ea is Either.Right -> Either.Right(ef.value(ea.value))
                ef is Either.Left && ea is Either.Left -> Either.Left(ef.value + ea.value)
                ef is Either.Left -> ef
                else -> @Suppress("UNCHECKED_CAST") (ea as Either.Left<NonEmptyList<E>>)
            }
        }
    }
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.apV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Computation<Either<NonEmptyList<E>, B>> = apV(Computation { fa() })

// ── followedByV: true phase barrier with short-circuit ──────────────────

/**
 * True phase barrier for validated computations — awaits the left side,
 * then runs [fa], and **gates** all subsequent [apV] calls until the
 * barrier completes.
 *
 * Short-circuits: if the left side is [Either.Left], the right side is **not** executed.
 * The signal is still fired so gated [apV] calls proceed (they will see the Left
 * value propagated through the chain).
 *
 * For parallel error accumulation within a phase, use [apV] instead.
 */
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.followedByV(
    fa: Computation<Either<NonEmptyList<E>, A>>,
): Computation<Either<NonEmptyList<E>, B>> {
    val self = this
    val signal = CompletableDeferred<Unit>()
    return PhaseBarrier(Computation {
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
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.followedByV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Computation<Either<NonEmptyList<E>, B>> = followedByV(Computation { fa() })

// ── thenValueV: sequential value fill without barrier ────────────────────

/**
 * Sequential validated value fill — awaits the left side, then runs [fa].
 * Short-circuits on [Either.Left]. Does **not** create a phase barrier.
 *
 * Subsequent [apV] calls will still launch eagerly.
 */
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.thenValueV(
    fa: Computation<Either<NonEmptyList<E>, A>>,
): Computation<Either<NonEmptyList<E>, B>> = Computation {
    when (val ef = with(this@thenValueV) { execute() }) {
        is Either.Left -> ef
        is Either.Right -> when (val ea = with(fa) { execute() }) {
            is Either.Left -> ea
            is Either.Right -> Either.Right(ef.value(ea.value))
        }
    }
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Computation<Either<NonEmptyList<E>, (A) -> B>>.thenValueV(
    fa: suspend () -> Either<NonEmptyList<E>, A>,
): Computation<Either<NonEmptyList<E>, B>> = thenValueV(Computation { fa() })

// ── catching: bridge from exception world to validated world ─────────────

/**
 * Converts a [Computation] into a validated computation by catching
 * non-cancellation exceptions and mapping them to errors via [toError].
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 */
fun <E, A> Computation<A>.catching(toError: (Throwable) -> E): Computation<Either<NonEmptyList<E>, A>> =
    Computation {
        try {
            Either.Right(with(this@catching) { execute() })
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Either.Left(toError(e).toNonEmptyList())
        }
    }

// ── validate: predicate-based validation ─────────────────────────────────

/**
 * Validates the result of this computation with a predicate.
 *
 * If [toError] returns null, the value passes validation.
 * If [toError] returns a non-null error, it becomes an [Either.Left].
 */
fun <E, A> Computation<A>.validate(toError: (A) -> E?): Computation<Either<NonEmptyList<E>, A>> =
    Computation {
        val a = with(this@validate) { execute() }
        val error = toError(a)
        if (error == null) Either.Right(a) else Either.Left(error.toNonEmptyList())
    }

// ── traverseV: parallel traverse with error accumulation ─────────────────

/**
 * Applies [f] to each element in parallel, accumulating all errors.
 */
fun <E, A, B> Iterable<A>.traverseV(
    f: (A) -> Computation<Either<NonEmptyList<E>, B>>,
): Computation<Either<NonEmptyList<E>, List<B>>> = Computation {
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
    f: (A) -> Computation<Either<NonEmptyList<E>, B>>,
): Computation<Either<NonEmptyList<E>, List<B>>> = Computation {
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

// ── sequenceV: parallel execution with error accumulation ────────────────

/**
 * Executes all validated computations in parallel, accumulating all errors.
 */
fun <E, A> Iterable<Computation<Either<NonEmptyList<E>, A>>>.sequenceV(): Computation<Either<NonEmptyList<E>, List<A>>> =
    traverseV { it }

/**
 * Like [sequenceV] but limits the number of concurrent computations.
 */
fun <E, A> Iterable<Computation<Either<NonEmptyList<E>, A>>>.sequenceV(
    concurrency: Int,
): Computation<Either<NonEmptyList<E>, List<A>>> =
    traverseV(concurrency) { it }

// ── flatMapV: monadic bind for validated computations ────────────────────

/**
 * Monadic bind for validated computations — sequential, short-circuits on error.
 *
 * Unlike [apV] which accumulates errors from parallel branches, [flatMapV]
 * short-circuits: if the left side is [Either.Left], the right side is never executed.
 * Use this when the next validation step depends on the previous value.
 *
 * ```
 * val result = Async {
 *     validateEmail(input)
 *         .flatMapV { email -> checkEmailNotTaken(email) }
 *         .flatMapV { email -> registerUser(email) }
 * }
 * ```
 */
inline fun <E, A, B> Computation<Either<NonEmptyList<E>, A>>.flatMapV(
    crossinline f: (A) -> Computation<Either<NonEmptyList<E>, B>>,
): Computation<Either<NonEmptyList<E>, B>> = Computation {
    when (val ea = with(this@flatMapV) { execute() }) {
        is Either.Left -> ea
        is Either.Right -> with(f(ea.value)) { execute() }
    }
}

// ── recoverV: bridge exceptions into validated error channel ─────────

/**
 * Catches non-cancellation exceptions thrown during a validated computation
 * and converts them into validation errors via [f].
 *
 * Without this, an exception inside a [zipV] branch would cancel siblings
 * and bypass error accumulation. With [recoverV], the exception becomes
 * a normal [Either.Left] that participates in accumulation.
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 *
 * ```
 * zipV(
 *     { validateName(input).recoverV { FormError.Unexpected(it.message) } },
 *     { externalCheck(input).recoverV { FormError.ServiceDown(it) } },
 * ) { name, check -> Registration(name, check) }
 * ```
 */
fun <E, A> Computation<Either<NonEmptyList<E>, A>>.recoverV(
    f: (Throwable) -> E,
): Computation<Either<NonEmptyList<E>, A>> = Computation {
    try {
        with(this@recoverV) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(f(e).toNonEmptyList())
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
fun <E, A> Computation<Either<NonEmptyList<E>, A>>.orThrow(): Computation<A> = map {
    when (it) {
        is Either.Right -> it.value
        is Either.Left -> throw ValidationException(it.errors)
    }
}

/** The errors from a [Left] result, preserving the type. */
val <E> Either.Left<NonEmptyList<E>>.errors: NonEmptyList<E> get() = value

// ── mapV: transform the success side of a validated computation ──────────

/**
 * Transforms the success value inside a validated computation.
 */
fun <E, A, B> Computation<Either<NonEmptyList<E>, A>>.mapV(f: (A) -> B): Computation<Either<NonEmptyList<E>, B>> =
    map { it.map(f) }

// ── mapError: transform the error type of a validated computation ────────

/**
 * Transforms the error type inside a validated computation.
 *
 * Useful for unifying error types when combining validations from different domains:
 * ```
 * val userValidation: Computation<Either<Nel<UserError>, User>> = ...
 * val cartValidation: Computation<Either<Nel<CartError>, Cart>> = ...
 *
 * liftV2<AppError, User, Cart, Checkout>(::Checkout)
 *     .apV { userValidation.mapError { AppError.User(it) } }
 *     .apV { cartValidation.mapError { AppError.Cart(it) } }
 * ```
 */
fun <E, F, A> Computation<Either<NonEmptyList<E>, A>>.mapError(f: (E) -> F): Computation<Either<NonEmptyList<F>, A>> =
    map { either ->
        when (either) {
            is Either.Right -> either
            is Either.Left -> Either.Left(NonEmptyList(f(either.value.head), either.value.tail.map(f)))
        }
    }

// ── validated { } builder: short-circuit DSL ────────────────────────────

/**
 * Internal exception for short-circuit control flow in [validated].
 * Never leaks outside the builder.
 */
@PublishedApi
internal class ValidatedShortCircuit(val errors: NonEmptyList<*>) : Exception()

/**
 * Scope for the [validated] builder, providing [bind] for short-circuit
 * sequential validation.
 *
 * Use [bind] to unwrap [Either.Right] values or short-circuit on [Either.Left]:
 *
 * ```
 * val result = Async {
 *     validated<String> {
 *         val name = validateName(input).bind()
 *         val email = validateEmail(input).bind()
 *         UserData(name, email)
 *     }
 * }
 * ```
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
     * Executes a validated [Computation] and unwraps the result — returns the
     * [Right][Either.Right] value or short-circuits with the [Left][Either.Left] errors.
     *
     * Eliminates the need for nested [Async] blocks inside [validated]:
     *
     * ```
     * // Before:
     * validated<String> {
     *     val identity = Async {
     *         zipV(
     *             { validateName(input) },
     *             { validateEmail(input) },
     *         ) { name, email -> Identity(name, email) }
     *     }.bind()
     * }
     *
     * // After:
     * validated<String> {
     *     val identity = zipV(
     *         { validateName(input) },
     *         { validateEmail(input) },
     *     ) { name, email -> Identity(name, email) }
     *         .bindV()
     * }
     * ```
     */
    suspend fun <A> Computation<Either<NonEmptyList<E>, A>>.bindV(): A =
        with(this@bindV) { scope.execute() }.bind()
}

/**
 * Short-circuit builder for validated computations.
 *
 * Provides [ValidatedScope.bind] to unwrap [Either.Right] values or
 * short-circuit on [Either.Left]. Useful for sequential validation
 * where later steps depend on earlier results.
 *
 * For parallel validation (accumulating **all** errors), use [zipV] or [apV] instead.
 *
 * ```
 * val result = Async {
 *     validated<String> {
 *         val identity = zipV(
 *             { validateName(input) },
 *             { validateEmail(input) },
 *         ) { name, email -> Identity(name, email) }
 *             .bindV()  // executes + unwraps — no nested Async needed
 *
 *         val cleared = zipV(
 *             { checkNotBlocked(identity) },
 *             { checkUsernameAvailable(identity.email) },
 *         ) { a, b -> Clearance(a, b) }
 *             .bindV()
 *
 *         Registration(identity, cleared)
 *     }
 * }
 * ```
 *
 * [CancellationException] always propagates — never caught by this builder.
 */
@Suppress("UNCHECKED_CAST")
fun <E, A> validated(block: suspend ValidatedScope<E>.() -> A): Computation<Either<NonEmptyList<E>, A>> =
    Computation {
        try {
            Either.Right(ValidatedScope<E>(this).block())
        } catch (e: ValidatedShortCircuit) {
            Either.Left(e.errors as NonEmptyList<E>)
        }
    }

// ── zipV + liftV: see ValidatedOverloads.kt (auto-generated) ─────────────
