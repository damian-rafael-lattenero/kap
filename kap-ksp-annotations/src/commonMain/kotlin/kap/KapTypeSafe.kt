package kap

/**
 * Generate a type-safe scoped builder for this class or function.
 *
 * KSP reads the parameter names and emits a scoped wrapper `${Type}Kap<F>` with
 * per-slot tag interfaces. At the call site, `.with { field from value }` exposes
 * the lambda's slot as an implicit receiver — the IDE shows exactly the field
 * expected at the current curry position. Swapping fields produces a crisp
 * compile error naming the expected tag.
 *
 * ```kotlin
 * @KapTypeSafe
 * data class User(val firstName: String, val lastName: String, val age: Int)
 *
 * // Usage — `.with { firstName from … }` is order-aware; type any other field
 * //         and the compiler rejects it with the slot's tag name.
 * kap(::User)
 *     .with { firstName from fetchFirstName() }
 *     .with { lastName from fetchLastName() }
 *     .with { age from fetchAge() }
 *     .evalGraph()
 * ```
 *
 * For **third-party classes** you can't annotate, use [KapBridge] instead.
 *
 * For **third-party functions**, create a one-line wrapper:
 * ```kotlin
 * @KapTypeSafe
 * fun buildDashboard(userName: String, cartSummary: String) =
 *     com.thirdparty.buildDashboard(userName, cartSummary)
 * ```
 *
 * ## Generic declarations
 *
 * Generic classes and functions are supported and mirror the non-generic
 * shape — the type argument pins the type variables at the entry:
 *
 * ```kotlin
 * @KapTypeSafe
 * data class Checkout2<T>(
 *     val user: String,
 *     val cart: String,
 *     val validated: Boolean,
 *     val total: T,
 * )
 *
 * val checkout = kap<Double>(::Checkout2)
 *     .with { user from fetchUser() }
 *     .with { cart from fetchCart() }
 *     .then { validated from validateOrder() }
 *     .with { total from 2.0 }
 *     .evalGraph()
 * ```
 *
 * Notes:
 *  - `kap<T>(::C)` is emitted whenever the declaration's parameter shape is
 *    unique across all `@KapTypeSafe`/`@KapBridge` declarations (JVM erasure
 *    ignores return types, so identical shapes collide). Colliding shapes
 *    fall back to a zero-arg, class-named entry: `kapCheckout2<Double>()`.
 *  - type parameter bounds are preserved;
 *  - type parameters are generalized by free-variable analysis: each generated
 *    declaration quantifies exactly the variables free in the positions it
 *    spans (its slot, plus the return type for last-slot operators);
 *  - internal generated binders (`E` for the error channel, `Rest` for the
 *    curried remainder) are α-converted on collision with your parameter
 *    names — a type parameter named `E` or `Rest` is fine;
 *  - `reified` type parameters are not supported.
 *
 * Use [prefix] to disambiguate generated **file names and tag class names** when
 * multiple `@KapTypeSafe` functions share parameter names. The call-site tag
 * names are always the original parameter names — the prefix only affects the
 * internal types so they don't collide across generated files.
 *
 * ```kotlin
 * @KapTypeSafe(prefix = "Dashboard")
 * fun buildDashboard(userName: String, cartSummary: String): Dashboard
 *
 * // Call site is unchanged — `userName`, `cartSummary` are the slot members:
 * kap(::buildDashboard).with { userName from … }.with { cartSummary from … }
 * ```
 *
 * @param prefix Optional prefix for generated file names + tag class names.
 *               Default is empty. Use when several functions share parameter
 *               names to avoid generated-type collisions.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KapTypeSafe(val prefix: String = "")
