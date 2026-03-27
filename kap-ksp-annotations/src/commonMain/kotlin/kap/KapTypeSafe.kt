package kap

/**
 * Annotate a data class or function to generate type-safe wrappers for each parameter.
 *
 * For each parameter, KSP generates:
 * - A `@JvmInline value class` wrapper
 * - A `kapSafe()` function that requires those wrapper types
 * - A `.toParamName()` extension function for fluent wrapping
 *
 * ```kotlin
 * @KapTypeSafe
 * data class User(val firstName: String, val lastName: String, val age: Int)
 *
 * // Usage:
 * kapSafe(::User)
 *     .with { fetchFirstName().toFirstName() }
 *     .with { fetchLastName().toLastName() }   // swap? COMPILE ERROR
 *     .with { fetchAge().toAge() }
 * ```
 *
 * Use [prefix] to avoid name collisions when multiple classes share parameter names:
 *
 * ```kotlin
 * @KapTypeSafe(prefix = "Dashboard")
 * fun buildDashboard(userName: String, cartSummary: String): Dashboard
 *
 * // Generates: .toDashboardUserName(), .toDashboardCartSummary()
 * ```
 *
 * @param prefix Optional prefix for generated wrapper names and extension functions.
 *               Default is empty (no prefix). Use when parameter names collide across types.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KapTypeSafe(val prefix: String = "")
