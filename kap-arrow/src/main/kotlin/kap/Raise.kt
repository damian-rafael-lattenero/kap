@file:OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)

package kap

import arrow.core.Either
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.AT_MOST_ONCE
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference

// ── Raise: direct-style typed errors ─────────────────────────────────────

/**
 * Builder-inference marker (the [buildList] recipe): a user-declared
 * annotation meta-annotated with [ExperimentalTypeInference] upgrades lambda
 * inference so the builders below infer E/A from the BLOCK BODY
 * (`either { user.bind() }` infers E from bind's receiver without explicit
 * type arguments).
 */
@OptIn(ExperimentalTypeInference::class)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@ExperimentalTypeInference
public annotation class BuilderInference

/**
 * # The raise world — direct-style typed errors over Either
 *
 * Companion to [validated] (the Kap world, NonEmptyList channel): `Raise` is
 * the PLAIN world with a SINGLE error channel — the Arrow Raise shape,
 * expressed with kap's own control-flow machinery. No coroutine is required;
 * [either] is a pure value function, and [eitherSuspend] is its suspend
 * boundary for workflows that call suspend steps.
 *
 * | Need | Reach for |
 * |---|---|
 * | linear multi-step workflow, first error cuts | [either] + [bind] |
 * | guard a condition | [ensure] |
 * | turn null into a typed error | [ensureNotNull] |
 * | suspend workflow boundary | [eitherSuspend] |
 * | reusable step helper | an extension on `Raise<E>` |
 * | parallel, accumulate ALL errors | [withV] / [traverseV] (validated world) |
 *
 * Reusable steps are extensions on [Raise] — they resolve inside the blocks
 * exactly like the built-in operators:
 *
 * ```
 * fun Raise<ParseError>.trimNonBlank(s: String): String {
 *     ensure(s.isNotBlank()) { ParseError.BlankName }
 *     return s.trim()
 * }
 * ```
 *
 * NOTE: Kotlin context-parameter functions do NOT resolve inside the blocks
 * (2.4 limitation measured while building this DSL) — use extensions on
 * [Raise] for the same ergonomics. Give the result an EXPECTED TYPE (a typed
 * val or the function return type) so E flows into the block — the Arrow-docs
 * idiom; a bare expression-position either { ... } infers E from its own
 * body first and may need explicit type arguments.
 *
 * Error semantics: SHORT-CIRCUIT. The first [raise] abandons the rest of the
 * block. Bugs and [kotlinx.coroutines.CancellationException] are never
 * captured — the builders only intercept their own control-flow exception, so
 * the CE law (cancellation is sacred) is structural here, not a catch arm to
 * forget.
 */
public interface Raise<in E> {
    /**
     * Abandons the computation with [e] — everything after is not executed.
     *
     * DO NOT store or escape a `Raise` beyond its [either] / [eitherSuspend]
     * block: the control-flow exception it throws must be caught by the
     * builder that created it. Raising from another coroutine or after the
     * block returned crashes honestly with it.
     */
    public fun raise(e: E): Nothing

    /**
     * Unwraps an [Either] in direct style: the [Either.Right] value, or raises
     * the [Either.Left] error.
     *
     * Error semantics: SHORT-CIRCUIT — raising abandons the enclosing builder
     * with [Either.Left].
     */
    public fun <A> Either<E, A>.bind(): A = when (this) {
        is Either.Right -> value
        is Either.Left -> raise(value)
    }

    /**
     * Guard: continues when [condition] holds, raises [error] when it does not.
     */
    public fun ensure(condition: Boolean, error: () -> E): Unit {
        if (!condition) raise(error())
    }

    /**
     * Like [ensure] for absence: the non-null value, or raises [error] on null.
     */
    public fun <A : Any> A?.ensureNotNull(error: () -> E): A =
        if (this == null) raise(error()) else this
}

/** Internal control flow: the raised error rides the exception (no mutable scope state). */
@PublishedApi
internal class Raised(val error: Any?) : ControlFlowException()

@PublishedApi
internal class RaiseImpl<E> : Raise<E> {
    override fun raise(e: E): Nothing = throw Raised(e)
}

// ── builders ─────────────────────────────────────────────────────────────

/**
 * Runs [block] in the raise world: [Either.Right] with the block's value, or
 * [Either.Left] with the first raised error.
 *
 * ```
 * fun parse(s: String): Either<ParseError, User> = either {
 *     val name = parsedName(s).bind()
 *     ensure(name.isNotBlank()) { ParseError.BlankName }
 *     User(name)
 * }
 * ```
 *
 * Error semantics: SHORT-CIRCUIT. Only [Raise]'s own control flow is
 * intercepted — a bug in the block and a [kotlinx.coroutines.CancellationException]
 * propagate untouched.
 */
@BuilderInference
public inline fun <E, A> either(@BuilderInference block: Raise<E>.() -> A): Either<E, A> {
    contract { callsInPlace(block, AT_MOST_ONCE) }
    return try {
        Either.Right(RaiseImpl<E>().block())
    } catch (e: Raised) {
        @Suppress("UNCHECKED_CAST")
        Either.Left(e.error as E)
    }
}

/**
 * Suspend variant of [either] — same short-circuit semantics, for workflows
 * that call suspend steps. Suspend calls in the block behave exactly as in any
 * suspend lambda, and cancellation still propagates untouched.
 */
@BuilderInference
public suspend inline fun <E, A> eitherSuspend(@BuilderInference block: suspend Raise<E>.() -> A): Either<E, A> {
    contract { callsInPlace(block, AT_MOST_ONCE) }
    return try {
        Either.Right(RaiseImpl<E>().block())
    } catch (e: Raised) {
        @Suppress("UNCHECKED_CAST")
        Either.Left(e.error as E)
    }
}
