package kap

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Lifts a callback into a cancellable [Kap]: `resume` completes it, `cancel`
 * is invoked exactly once when the awaiting party is cancelled, so the source
 * can unregister. The callback is registered once, at execution time.
 *
 * ```
 * Kap.awaiting { resume, cancel ->
 *     transport.onClose { resume(Unit) }   // parks until the transport closes
 * }
 * ```
 */
fun <A> Kap.Companion.awaiting(
    register: (resume: (A) -> Unit, cancel: (cause: Throwable) -> Unit) -> Unit,
): Kap<A> = Kap {
    suspendCancellableCoroutine { cont ->
        register(
            { value -> cont.resume(value) },
            { cause -> cont.cancel(cause) },
        )
    }
}
