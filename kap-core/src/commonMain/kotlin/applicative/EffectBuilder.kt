package applicative

import kotlinx.coroutines.CoroutineScope

/**
 * Scope for the [computation] builder, providing [bind] for sequential
 * monadic composition inside a [Effect].
 *
 * This is the sequential counterpart to `kap+with` — use it when later
 * steps depend on earlier values:
 *
 * ```
 * val result = Async {
 *     computation {
 *         val user = bind { fetchUser(id) }
 *         val cart = bind { fetchCart(user.cartId) }
 *         Dashboard(user, cart)
 *     }
 * }
 * ```
 */
class EffectScope @PublishedApi internal constructor(
    @PublishedApi internal val scope: CoroutineScope,
) {
    /**
     * Executes this [Effect] within the current scope and returns the result.
     * Equivalent to chaining with [andThen], but with imperative syntax.
     */
    suspend fun <A> Effect<A>.bind(): A = with(this@bind) { scope.execute() }

    /**
     * Executes a suspend block as a [Effect] and returns the result.
     * Shorthand for `Effect { block() }.bind()`.
     *
     * ```
     * computation {
     *     val user = bind { fetchUser(id) }    // clean
     *     val cart = bind { fetchCart(user.id) } // value-dependent
     *     Dashboard(user, cart)
     * }
     * ```
     */
    suspend fun <A> bind(block: suspend () -> A): A = block()
}

/**
 * Builds a [Effect] using imperative syntax with [EffectScope.bind].
 *
 * Each [bind] call executes its computation sequentially — use `kap+with`
 * when branches are independent and can run in parallel.
 */
inline fun <A> computation(crossinline block: suspend EffectScope.() -> A): Effect<A> =
    Effect { EffectScope(this).block() }
