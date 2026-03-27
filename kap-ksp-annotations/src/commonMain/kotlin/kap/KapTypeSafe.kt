package kap

/**
 * Annotate a data class to generate type-safe wrappers for each constructor parameter.
 *
 * For each parameter, KSP generates a `@JvmInline value class` wrapper and a
 * `kapSafe()` function that requires those wrapper types — making it impossible
 * to swap same-typed parameters by accident.
 *
 * ```kotlin
 * @KapTypeSafe
 * data class User(val firstName: String, val lastName: String, val age: Int)
 *
 * // Generated:
 * // @JvmInline value class UserFirstName(val value: String)
 * // @JvmInline value class UserLastName(val value: String)
 * // @JvmInline value class UserAge(val value: Int)
 * // fun kapSafe(f: (String, String, Int) -> User): Kap<(UserFirstName) -> (UserLastName) -> (UserAge) -> User>
 *
 * // Usage:
 * kapSafe(::User)
 *     .with { UserFirstName(fetchFirstName()) }
 *     .with { UserLastName(fetchLastName()) }   // swap? COMPILE ERROR
 *     .with { UserAge(25) }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class KapTypeSafe
