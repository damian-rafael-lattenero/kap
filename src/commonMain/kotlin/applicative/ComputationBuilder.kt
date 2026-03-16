package applicative

import kotlinx.coroutines.CoroutineScope

/**
 * Scope for the [computation] builder, providing [bind] for sequential
 * monadic composition inside a [Computation].
 *
 * This is the sequential counterpart to `lift+ap` — use it when later
 * steps depend on earlier values:
 *
 * ```
 * val result = Async {
 *     computation {
 *         val user = Computation { fetchUser(id) }.bind()
 *         val cart = Computation { fetchCart(user.cartId) }.bind()
 *         Dashboard(user, cart)
 *     }
 * }
 * ```
 */
class ComputationScope @PublishedApi internal constructor(
    @PublishedApi internal val scope: CoroutineScope,
) {
    /**
     * Executes this [Computation] within the current scope and returns the result.
     * Equivalent to chaining with [flatMap], but with imperative syntax.
     */
    suspend fun <A> Computation<A>.bind(): A = with(this@bind) { scope.execute() }
}

/**
 * Builds a [Computation] using imperative syntax with [ComputationScope.bind].
 *
 * Each [bind] call executes its computation sequentially — use `lift+ap`
 * when branches are independent and can run in parallel.
 */
inline fun <A> computation(crossinline block: suspend ComputationScope.() -> A): Computation<A> =
    Computation { ComputationScope(this).block() }
