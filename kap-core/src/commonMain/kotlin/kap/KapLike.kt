package kap

import kotlin.time.Duration

/**
 * Marker interface implemented by generated KSP wrappers (`${T}Kap<F>`).
 *
 * Exposes kap-core operators (.map, .recover, .timeout, .settled, .memoize,
 * .timed, .andThen, .evalGraph) directly on partial wrappers — without forcing
 * the wrapper to extend `Kap<F>` (which would trigger K2 overload-resolution
 * issues against the imported `Kap.with` extension).
 *
 * **Note**: `Kap<F>` deliberately does NOT implement `KapLike<F>` to avoid
 * double-resolution conflicts with the existing Kap extension operators.
 * Use `.asKap` to get the raw `Kap<F>` when an external API requires it.
 */
interface KapLike<F> {
    val asKap: Kap<F>
}

suspend fun <F> KapLike<F>.evalGraph(): F = asKap.evalGraph()

fun <F, A> KapLike<F>.map(f: (F) -> A): Kap<A> = asKap.map(f)

fun <F, A> KapLike<F>.andThen(f: (F) -> Kap<A>): Kap<A> = asKap.andThen(f)

fun <F> KapLike<F>.recover(f: suspend (Throwable) -> F): Kap<F> = asKap.recover(f)

fun <F> KapLike<F>.recoverWith(f: suspend (Throwable) -> Kap<F>): Kap<F> = asKap.recoverWith(f)

fun <F> KapLike<F>.timeout(duration: Duration): Kap<F> = asKap.timeout(duration)

fun <F> KapLike<F>.timeout(duration: Duration, default: F): Kap<F> = asKap.timeout(duration, default)

fun <F> KapLike<F>.settled(): Kap<Result<F>> = asKap.settled()

fun <F> KapLike<F>.memoize(): Kap<F> = asKap.memoize()

fun <F> KapLike<F>.timed(): Kap<TimedResult<F>> = asKap.timed()
