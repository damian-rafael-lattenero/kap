package kap

import kotlin.reflect.KClass

/**
 * Generate a type-safe scoped builder for a **third-party class** whose source
 * you don't control (and therefore can't annotate with [KapTypeSafe]).
 *
 * KSP reads the target class's primary constructor parameter names and emits
 * the same scoped wrapper as [KapTypeSafe]:
 *
 * ```kotlin
 * // In any .kt file in your project:
 * @file:KapBridge(ThirdPartyUser::class)
 *
 * // Usage — `.with { field from value }` is order-aware, IDE-narrowed:
 * kap(::ThirdPartyUser)
 *     .with { firstName from fetchFirstName() }
 *     .with { lastName from fetchLastName() }
 *     .with { age from fetchAge() }
 *     .evalGraph()
 * ```
 *
 * @param target The third-party class to generate builders for.
 *               Must have a primary constructor.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class KapBridge(val target: KClass<*>)
