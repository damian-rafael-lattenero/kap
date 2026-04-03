package kap

/**
 * Generate type-safe named builders for this class or function.
 *
 * KSP reads the parameter names and generates step-builder classes where
 * each step exposes only `.withParamName {}` and `.thenParamName {}`.
 * The IDE autocomplete guides the user through the correct parameter order.
 *
 * ```kotlin
 * @KapTypeSafe
 * data class User(val firstName: String, val lastName: String, val age: Int)
 *
 * // Usage:
 * kap(::User)
 *     .withFirstName { fetchFirstName() }
 *     .withLastName { fetchLastName() }   // swap? COMPILE ERROR
 *     .withAge { fetchAge() }
 *     .executeGraph()
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
 * Use [prefix] to avoid name collisions when multiple classes share parameter names:
 *
 * ```kotlin
 * @KapTypeSafe(prefix = "Dashboard")
 * fun buildDashboard(userName: String, cartSummary: String): Dashboard
 *
 * // Generates: .withDashboardUserName(), .thenDashboardCartSummary()
 * ```
 *
 * @param prefix Optional prefix for generated method names and step class names.
 *               Default is empty (no prefix). Use when parameter names collide across types.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class KapTypeSafe(val prefix: String = "")
