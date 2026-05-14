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
