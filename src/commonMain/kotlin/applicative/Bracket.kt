package applicative

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

// ── bracket: resource-safe computation ──────────────────────────────────

/**
 * Acquires a resource, uses it in a [Computation], and guarantees [release]
 * runs even on failure or cancellation.
 *
 * [release] runs in [NonCancellable] context so it cannot be interrupted.
 * This mirrors Kotlin's `use {}` semantics.
 *
 * ```
 * val result = Async {
 *     bracket(
 *         acquire = { openConnection() },
 *         use = { conn ->
 *             lift2(::Result)
 *                 .ap { conn.fetchUser() }
 *                 .ap { conn.fetchCart() }
 *         },
 *         release = { conn -> conn.close() },
 *     )
 * }
 * ```
 *
 * @param acquire suspending function that acquires the resource.
 *        Runs once before [use]. If it throws, [release] is NOT called.
 * @param use function that builds a [Computation] from the acquired resource.
 * @param release suspending function that releases the resource.
 *        Called exactly once: on success, failure, or cancellation.
 */
fun <R, A> bracket(
    acquire: suspend () -> R,
    use: (R) -> Computation<A>,
    release: suspend (R) -> Unit,
): Computation<A> = Computation {
    val resource = acquire()
    try {
        with(use(resource)) { execute() }
    } finally {
        withContext(NonCancellable) { release(resource) }
    }
}

// ── guarantee: unconditional finalizer ──────────────────────────────────

/**
 * Guarantees that [finalizer] runs after this computation completes,
 * regardless of success, failure, or cancellation.
 *
 * [finalizer] runs in [NonCancellable] context so it cannot be interrupted.
 *
 * ```
 * Computation { fetchUser() }
 *     .guarantee { releaseConnection() }
 * ```
 */
fun <A> Computation<A>.guarantee(finalizer: suspend () -> Unit): Computation<A> = Computation {
    try {
        with(this@guarantee) { execute() }
    } finally {
        withContext(NonCancellable) { finalizer() }
    }
}

// ── guaranteeCase: finalizer with outcome ───────────────────────────────

/**
 * Outcome of a computation, passed to [guaranteeCase]'s finalizer.
 */
sealed class ExitCase {
    /** Computation completed successfully. */
    data class Completed<out A>(val value: A) : ExitCase()

    /** Computation failed with an exception. */
    data class Failed(val error: Throwable) : ExitCase()

    /** Computation was cancelled. */
    data object Cancelled : ExitCase()
}

/**
 * Like [guarantee] but the finalizer receives the [ExitCase] so it can
 * react differently to success, failure, or cancellation.
 *
 * ```
 * Computation { fetchUser() }
 *     .guaranteeCase { case ->
 *         when (case) {
 *             is ExitCase.Completed -> metrics.recordSuccess()
 *             is ExitCase.Failed -> metrics.recordError(case.error)
 *             is ExitCase.Cancelled -> metrics.recordCancellation()
 *         }
 *     }
 * ```
 */
fun <A> Computation<A>.guaranteeCase(finalizer: suspend (ExitCase) -> Unit): Computation<A> = Computation {
    try {
        val result = with(this@guaranteeCase) { execute() }
        withContext(NonCancellable) { finalizer(ExitCase.Completed(result)) }
        result
    } catch (e: CancellationException) {
        withContext(NonCancellable) { finalizer(ExitCase.Cancelled) }
        throw e
    } catch (e: Throwable) {
        withContext(NonCancellable) { finalizer(ExitCase.Failed(e)) }
        throw e
    }
}

// ── bracketCase: resource-safe computation with outcome ─────────────────

/**
 * Like [bracket] but [release] receives the [ExitCase] so it can react
 * differently to success, failure, or cancellation.
 *
 * ```
 * bracketCase(
 *     acquire = { openConnection() },
 *     use = { conn -> Computation { conn.query("SELECT ...") } },
 *     release = { conn, case ->
 *         when (case) {
 *             is ExitCase.Completed -> conn.commit()
 *             else -> conn.rollback()
 *         }
 *         conn.close()
 *     },
 * )
 * ```
 */
fun <R, A> bracketCase(
    acquire: suspend () -> R,
    use: (R) -> Computation<A>,
    release: suspend (R, ExitCase) -> Unit,
): Computation<A> = Computation {
    val resource = acquire()
    try {
        val result = with(use(resource)) { execute() }
        withContext(NonCancellable) { release(resource, ExitCase.Completed(result)) }
        result
    } catch (e: CancellationException) {
        withContext(NonCancellable) { release(resource, ExitCase.Cancelled) }
        throw e
    } catch (e: Throwable) {
        withContext(NonCancellable) { release(resource, ExitCase.Failed(e)) }
        throw e
    }
}
