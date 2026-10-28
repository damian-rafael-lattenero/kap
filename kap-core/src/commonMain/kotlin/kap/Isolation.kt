package kap

import kotlinx.coroutines.coroutineScope

/**
 * Runs this computation inside its own [coroutineScope].
 *
 * `with`, `zip` and `traverse` launch their children on the CALLER's scope.
 * Inside a CONVERT or RETRY operator that caller is the operator's own
 * coroutine, so a failing grandchild used to cancel the operator through
 * structured concurrency before the operator could convert the failure:
 * `settled`, `recover`, `traverseSettled` and every `retry` leaked the
 * exception when their operand had parallel children. A scope boundary
 * turns that cancellation into an ordinary thrown exception at the
 * operator, which is the **Isolation law** (docs/design/error-semantics.md).
 *
 * Every CONVERT and RETRY operator applies it to its operand; user code can
 * apply it to any graph it hands to a third party.
 */
fun <A> Kap<A>.isolated(): Kap<A> = Kap { this@isolated.executeIsolated() }

@PublishedApi
@Suppress("RedundantSuspendModifier") // coroutineScope is inline-suspend: detekt cannot see the suspension
internal suspend fun <A> Kap<A>.executeIsolated(): A =
    coroutineScope { with(this@executeIsolated) { execute() } }
