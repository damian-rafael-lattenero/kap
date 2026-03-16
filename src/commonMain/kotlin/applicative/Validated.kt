package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import applicative.internal.curried

// ── entry points ─────────────────────────────────────────────────────────

/** Wraps a success value into a validated computation. */
fun <E, A> valid(a: A): Computation<Either<Nel<E>, A>> = pure(Either.Right(a))

/** Wraps a single error into a validated computation. */
fun <E, A> invalid(e: E): Computation<Either<Nel<E>, A>> = pure(Either.Left(e.nel()))

/** Wraps multiple errors into a validated computation. */
fun <E, A> invalidAll(errors: Nel<E>): Computation<Either<Nel<E>, A>> = pure(Either.Left(errors))

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
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.apV(
    fa: Computation<Either<Nel<E>, A>>,
): Computation<Either<Nel<E>, B>> {
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
                else -> @Suppress("UNCHECKED_CAST") (ea as Either.Left<Nel<E>>)
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
                else -> @Suppress("UNCHECKED_CAST") (ea as Either.Left<Nel<E>>)
            }
        }
    }
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.apV(
    fa: suspend () -> Either<Nel<E>, A>,
): Computation<Either<Nel<E>, B>> = apV(Computation { fa() })

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
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.followedByV(
    fa: Computation<Either<Nel<E>, A>>,
): Computation<Either<Nel<E>, B>> {
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
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.followedByV(
    fa: suspend () -> Either<Nel<E>, A>,
): Computation<Either<Nel<E>, B>> = followedByV(Computation { fa() })

// ── thenValueV: sequential value fill without barrier ────────────────────

/**
 * Sequential validated value fill — awaits the left side, then runs [fa].
 * Short-circuits on [Either.Left]. Does **not** create a phase barrier.
 *
 * Subsequent [apV] calls will still launch eagerly.
 */
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.thenValueV(
    fa: Computation<Either<Nel<E>, A>>,
): Computation<Either<Nel<E>, B>> = Computation {
    when (val ef = with(this@thenValueV) { execute() }) {
        is Either.Left -> ef
        is Either.Right -> when (val ea = with(fa) { execute() }) {
            is Either.Left -> ea
            is Either.Right -> Either.Right(ef.value(ea.value))
        }
    }
}

/** Convenience overload that wraps a suspend lambda returning [Either]. */
infix fun <E, A, B> Computation<Either<Nel<E>, (A) -> B>>.thenValueV(
    fa: suspend () -> Either<Nel<E>, A>,
): Computation<Either<Nel<E>, B>> = thenValueV(Computation { fa() })

// ── catching: bridge from exception world to validated world ─────────────

/**
 * Converts a [Computation] into a validated computation by catching
 * non-cancellation exceptions and mapping them to errors via [toError].
 *
 * [CancellationException] is never caught — structured concurrency
 * cancellation always propagates.
 */
fun <E, A> Computation<A>.catching(toError: (Throwable) -> E): Computation<Either<Nel<E>, A>> =
    Computation {
        try {
            Either.Right(with(this@catching) { execute() })
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Either.Left(toError(e).nel())
        }
    }

// ── validate: predicate-based validation ─────────────────────────────────

/**
 * Validates the result of this computation with a predicate.
 *
 * If [toError] returns null, the value passes validation.
 * If [toError] returns a non-null error, it becomes an [Either.Left].
 */
fun <E, A> Computation<A>.validate(toError: (A) -> E?): Computation<Either<Nel<E>, A>> =
    Computation {
        val a = with(this@validate) { execute() }
        val error = toError(a)
        if (error == null) Either.Right(a) else Either.Left(error.nel())
    }

// ── traverseV: parallel traverse with error accumulation ─────────────────

/**
 * Applies [f] to each element in parallel, accumulating all errors.
 */
fun <E, A, B> Iterable<A>.traverseV(
    f: (A) -> Computation<Either<Nel<E>, B>>,
): Computation<Either<Nel<E>, List<B>>> = Computation {
    val results = map { a -> async { with(f(a)) { execute() } } }.awaitAll()
    val errors = results.filterIsInstance<Either.Left<Nel<E>>>()
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
    f: (A) -> Computation<Either<Nel<E>, B>>,
): Computation<Either<Nel<E>, List<B>>> = Computation {
    val semaphore = Semaphore(concurrency)
    val results = map { a ->
        async { semaphore.withPermit { with(f(a)) { execute() } } }
    }.awaitAll()
    val errors = results.filterIsInstance<Either.Left<Nel<E>>>()
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
fun <E, A> Iterable<Computation<Either<Nel<E>, A>>>.sequenceV(): Computation<Either<Nel<E>, List<A>>> =
    traverseV { it }

/**
 * Like [sequenceV] but limits the number of concurrent computations.
 */
fun <E, A> Iterable<Computation<Either<Nel<E>, A>>>.sequenceV(
    concurrency: Int,
): Computation<Either<Nel<E>, List<A>>> =
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
inline fun <E, A, B> Computation<Either<Nel<E>, A>>.flatMapV(
    crossinline f: (A) -> Computation<Either<Nel<E>, B>>,
): Computation<Either<Nel<E>, B>> = Computation {
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
fun <E, A> Computation<Either<Nel<E>, A>>.recoverV(
    f: (Throwable) -> E,
): Computation<Either<Nel<E>, A>> = Computation {
    try {
        with(this@recoverV) { execute() }
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Either.Left(f(e).nel())
    }
}

// ── unwrap ───────────────────────────────────────────────────────────────

/**
 * Exception thrown when a validated computation is unwrapped with [orThrow]
 * and contains errors.
 */
class ValidationException(val errors: Nel<*>) : RuntimeException(
    "Validation failed with ${errors.size} error(s): $errors"
)

/**
 * Unwraps a validated computation: returns the value on [Either.Right],
 * throws [ValidationException] on [Either.Left].
 */
fun <E, A> Computation<Either<Nel<E>, A>>.orThrow(): Computation<A> = map {
    when (it) {
        is Either.Right -> it.value
        is Either.Left -> throw ValidationException(it.errors)
    }
}

/** The errors from a [Left] result, preserving the type. */
val <E> Either.Left<Nel<E>>.errors: Nel<E> get() = value

// ── mapV: transform the success side of a validated computation ──────────

/**
 * Transforms the success value inside a validated computation.
 */
fun <E, A, B> Computation<Either<Nel<E>, A>>.mapV(f: (A) -> B): Computation<Either<Nel<E>, B>> =
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
fun <E, F, A> Computation<Either<Nel<E>, A>>.mapError(f: (E) -> F): Computation<Either<Nel<F>, A>> =
    map { either ->
        when (either) {
            is Either.Right -> either
            is Either.Left -> Either.Left(Nel(f(either.value.head), either.value.tail.map(f)))
        }
    }

// ── zipV: parallel validation with full type inference ───────────────────

/**
 * Runs all validated computations in parallel, accumulating errors, and combines
 * successes with [combine]. Unlike [liftV2]+[apV], all type parameters are inferred.
 *
 * Overloads cover arities 2-22.
 *
 * ```
 * val result = Async {
 *     zipV(
 *         { validateName("A") },
 *         { validateEmail("bad") },
 *         { validateAge(5) },
 *     ) { name, email, age -> Registration(name, email, age) }
 * }
 * ```
 */
fun <E, A, B, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    combine: (A, B) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val ea = da.await()
    val eb = db.await()
    if (ea is Either.Right && eb is Either.Right)
        Either.Right(combine(ea.value, eb.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    combine: (A, B, C) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    combine: (A, B, C, D) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>,
    fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>,
    fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>,
    combine: (A, B, C, D, F) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }
    val db = async { fb() }
    val dc = async { fc() }
    val dd = async { fd() }
    val df = async { ff() }
    val ea = da.await()
    val eb = db.await()
    val ec = dc.await()
    val ed = dd.await()
    val ef = df.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value))
    else {
        val errors = buildList {
            if (ea is Either.Left) add(ea.value)
            if (eb is Either.Left) add(eb.value)
            if (ec is Either.Left) add(ec.value)
            if (ed is Either.Left) add(ed.value)
            if (ef is Either.Left) add(ef.value)
        }
        Either.Left(errors.reduce { acc, nel -> acc + nel })
    }
}

fun <E, A, B, C, D, F, G, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    combine: (A, B, C, D, F, G) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }
    val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await()
    val ed = dd.await(); val ef = df.await(); val eg = dg.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>,
    combine: (A, B, C, D, F, G, H) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }
    val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await()
    val ef = df.await(); val eg = dg.await(); val eh = dh.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    combine: (A, B, C, D, F, G, H, I) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }
    val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await()
    val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>,
    combine: (A, B, C, D, F, G, H, I, J) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }
    val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await()
    val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>,
    combine: (A, B, C, D, F, G, H, I, J, K) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }
    val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await()
    val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>,
    combine: (A, B, C, D, F, G, H, I, J, K, L) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }
    val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await()
    val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await(); val el = dl.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>,
    fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>,
    fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>,
    fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }
    val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await()
    val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }
    val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await()
    val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }
    val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await()
    val ei = di.await(); val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

// zipV arities 15–22 follow the same smart-cast pattern

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }
    val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await()
    val ei = di.await(); val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }
    val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await()
    val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }
    val dj = async { fj() }; val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }; val ds = async { fs() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await()
    val ej = dj.await(); val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await(); val es = ds.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value); if (es is Either.Left) add(es.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>, ft: suspend () -> Either<Nel<E>, T>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }
    val dk = async { fk() }; val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }; val ds = async { fs() }; val dt = async { ft() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await()
    val ek = dk.await(); val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await(); val es = ds.await(); val et = dt.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value); if (es is Either.Left) add(es.value); if (et is Either.Left) add(et.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>, ft: suspend () -> Either<Nel<E>, T>, fu: suspend () -> Either<Nel<E>, U>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }
    val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }; val ds = async { fs() }; val dt = async { ft() }; val du = async { fu() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await()
    val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await(); val es = ds.await(); val et = dt.await(); val eu = du.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value); if (es is Either.Left) add(es.value); if (et is Either.Left) add(et.value); if (eu is Either.Left) add(eu.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>, ft: suspend () -> Either<Nel<E>, T>, fu: suspend () -> Either<Nel<E>, U>, fv: suspend () -> Either<Nel<E>, V>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }
    val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }; val ds = async { fs() }; val dt = async { ft() }; val du = async { fu() }; val dv = async { fv() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await()
    val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await(); val es = ds.await(); val et = dt.await(); val eu = du.await(); val ev = dv.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right && ev is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value, ev.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value); if (es is Either.Left) add(es.value); if (et is Either.Left) add(et.value); if (eu is Either.Left) add(eu.value); if (ev is Either.Left) add(ev.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

fun <E, A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W, R> zipV(
    fa: suspend () -> Either<Nel<E>, A>, fb: suspend () -> Either<Nel<E>, B>, fc: suspend () -> Either<Nel<E>, C>, fd: suspend () -> Either<Nel<E>, D>,
    ff: suspend () -> Either<Nel<E>, F>, fg: suspend () -> Either<Nel<E>, G>, fh: suspend () -> Either<Nel<E>, H>, fi: suspend () -> Either<Nel<E>, I>,
    fj: suspend () -> Either<Nel<E>, J>, fk: suspend () -> Either<Nel<E>, K>, fl: suspend () -> Either<Nel<E>, L>, fm: suspend () -> Either<Nel<E>, M>,
    fn: suspend () -> Either<Nel<E>, N>, fo: suspend () -> Either<Nel<E>, O>, fp: suspend () -> Either<Nel<E>, P>, fq: suspend () -> Either<Nel<E>, Q>,
    fs: suspend () -> Either<Nel<E>, S>, ft: suspend () -> Either<Nel<E>, T>, fu: suspend () -> Either<Nel<E>, U>, fv: suspend () -> Either<Nel<E>, V>,
    fw: suspend () -> Either<Nel<E>, W>,
    combine: (A, B, C, D, F, G, H, I, J, K, L, M, N, O, P, Q, S, T, U, V, W) -> R,
): Computation<Either<Nel<E>, R>> = Computation {
    val da = async { fa() }; val db = async { fb() }; val dc = async { fc() }; val dd = async { fd() }; val df = async { ff() }; val dg = async { fg() }; val dh = async { fh() }; val di = async { fi() }; val dj = async { fj() }; val dk = async { fk() }
    val dl = async { fl() }; val dm = async { fm() }; val dn = async { fn() }; val dO = async { fo() }; val dp = async { fp() }; val dq = async { fq() }; val ds = async { fs() }; val dt = async { ft() }; val du = async { fu() }; val dv = async { fv() }; val dw = async { fw() }
    val ea = da.await(); val eb = db.await(); val ec = dc.await(); val ed = dd.await(); val ef = df.await(); val eg = dg.await(); val eh = dh.await(); val ei = di.await(); val ej = dj.await(); val ek = dk.await()
    val el = dl.await(); val em = dm.await(); val en = dn.await(); val eO = dO.await(); val ep = dp.await(); val eq = dq.await(); val es = ds.await(); val et = dt.await(); val eu = du.await(); val ev = dv.await(); val ew = dw.await()
    if (ea is Either.Right && eb is Either.Right && ec is Either.Right && ed is Either.Right && ef is Either.Right && eg is Either.Right && eh is Either.Right && ei is Either.Right && ej is Either.Right && ek is Either.Right && el is Either.Right && em is Either.Right && en is Either.Right && eO is Either.Right && ep is Either.Right && eq is Either.Right && es is Either.Right && et is Either.Right && eu is Either.Right && ev is Either.Right && ew is Either.Right)
        Either.Right(combine(ea.value, eb.value, ec.value, ed.value, ef.value, eg.value, eh.value, ei.value, ej.value, ek.value, el.value, em.value, en.value, eO.value, ep.value, eq.value, es.value, et.value, eu.value, ev.value, ew.value))
    else { val errors = buildList { if (ea is Either.Left) add(ea.value); if (eb is Either.Left) add(eb.value); if (ec is Either.Left) add(ec.value); if (ed is Either.Left) add(ed.value); if (ef is Either.Left) add(ef.value); if (eg is Either.Left) add(eg.value); if (eh is Either.Left) add(eh.value); if (ei is Either.Left) add(ei.value); if (ej is Either.Left) add(ej.value); if (ek is Either.Left) add(ek.value); if (el is Either.Left) add(el.value); if (em is Either.Left) add(em.value); if (en is Either.Left) add(en.value); if (eO is Either.Left) add(eO.value); if (ep is Either.Left) add(ep.value); if (eq is Either.Left) add(eq.value); if (es is Either.Left) add(es.value); if (et is Either.Left) add(et.value); if (eu is Either.Left) add(eu.value); if (ev is Either.Left) add(ev.value); if (ew is Either.Left) add(ew.value) }; Either.Left(errors.reduce { acc, nel -> acc + nel }) }
}

// ── liftV: curry + pure into the validated world ─────────────────────────

fun <E, P1, P2, R> liftV2(f: (P1, P2) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, R> liftV3(f: (P1, P2, P3) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, R> liftV4(f: (P1, P2, P3, P4) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, R> liftV5(f: (P1, P2, P3, P4, P5) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, R> liftV6(f: (P1, P2, P3, P4, P5, P6) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, R> liftV7(f: (P1, P2, P3, P4, P5, P6, P7) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, R> liftV8(f: (P1, P2, P3, P4, P5, P6, P7, P8) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, R> liftV9(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, R> liftV10(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, R> liftV11(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, R> liftV12(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, R> liftV13(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, R> liftV14(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, R> liftV15(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, R> liftV16(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, R> liftV17(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, R> liftV18(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, R> liftV19(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, R> liftV20(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, R> liftV21(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> R>> =
    pure(Either.Right(f.curried()))

fun <E, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, R> liftV22(f: (P1, P2, P3, P4, P5, P6, P7, P8, P9, P10, P11, P12, P13, P14, P15, P16, P17, P18, P19, P20, P21, P22) -> R): Computation<Either<Nel<E>, (P1) -> (P2) -> (P3) -> (P4) -> (P5) -> (P6) -> (P7) -> (P8) -> (P9) -> (P10) -> (P11) -> (P12) -> (P13) -> (P14) -> (P15) -> (P16) -> (P17) -> (P18) -> (P19) -> (P20) -> (P21) -> (P22) -> R>> =
    pure(Either.Right(f.curried()))
