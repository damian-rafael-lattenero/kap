package kap

import kotlinx.coroutines.CoroutineScope

/**
 * Scope for the [computation] builder, providing [bind] for sequential
 * monadic composition inside a [Kap].
 *
 * This is the sequential counterpart to `kap+with` — use it when later
 * steps depend on earlier values:
 *
 * ```
 * val result = computation {
 *     val user = bind { fetchUser(id) }
 *     val cart = bind { fetchCart(user.cartId) }
 *     Dashboard(user, cart)
 * }.executeGraph()
 * ```
 */
class KapScope @PublishedApi internal constructor(
    @PublishedApi internal val scope: CoroutineScope,
) {
    /**
     * Executes this [Kap] within the current scope and returns the result.
     * Equivalent to chaining with [andThen], but with imperative syntax.
     */
    suspend fun <A> Kap<A>.bind(): A = with(this@bind) { scope.execute() }

    /**
     * Executes a suspend block as a [Kap] and returns the result.
     * Shorthand for `Kap { block() }.bind()`.
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
 * Builds a [Kap] using imperative syntax with [KapScope.bind].
 *
 * Each [bind] call executes its computation sequentially — use `kap+with`
 * when branches are independent and can run in parallel.
 */
inline fun <A> computation(crossinline block: suspend KapScope.() -> A): Kap<A> =
    Kap { KapScope(this).block() }
