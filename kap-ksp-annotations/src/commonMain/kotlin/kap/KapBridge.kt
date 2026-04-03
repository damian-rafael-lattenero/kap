package kap

import kotlin.reflect.KClass

/**
 * Generate type-safe named builders for a **third-party class** whose source
 * you don't control (and therefore can't annotate with [KapTypeSafe]).
 *
 * KSP reads the target class's primary constructor parameter names and
 * generates the same step-builder chain as [KapTypeSafe]:
 *
 * ```kotlin
 * // In any .kt file in your project:
 * @file:KapBridge(ThirdPartyUser::class)
 *
 * // Usage — same named-builder experience:
 * kap(::ThirdPartyUser)
 *     .withFirstName { fetchFirstName() }
 *     .withLastName { fetchLastName() }
 *     .withAge { fetchAge() }
 *     .executeGraph()
 * ```
 *
 * @param target The third-party class to generate builders for.
 *               Must have a primary constructor.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class KapBridge(val target: KClass<*>)
