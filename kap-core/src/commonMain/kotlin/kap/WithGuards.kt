package kap

// ── .with diagnostics guards (issue 19) ──────────────────────────────────

/**
 * Catch-all guard for `.with` arguments that are neither a [Kap] nor a
 * suspend lambda: without it, the compiler reports only
 * `None of the following candidates is applicable … Kap<uninferred A>`
 * (see [with]'s docs). Every argument that compiles against the real
 * overloads resolves to them — this one is only picked when nothing else
 * applies — so it exists purely to turn that wall of text into the fix.
 *
 * Deprecated at [DeprecationLevel.ERROR], so the call still does not compile.
 *
 * This overload lives in its own file because, on the JVM, it erases to the
 * same signature `with(Kap, Object)` as the plain-value guard
 * (`Kap<(A) -> B>.with(value: A)` in Kap.kt); two top-level functions with the
 * same JVM signature in one facade class are a platform declaration clash, and
 * `@JvmName` cannot disambiguate it in common code.
 */
@Deprecated(
    message = "with() takes a Kap<A> or a suspend lambda producing the slot's " +
        "argument type A — the type the next curried slot expects. The value you " +
        "passed matches neither. Check the curried function's next parameter and " +
        "provide a producer of that type: `.with { producer() }`.",
    level = DeprecationLevel.ERROR,
)
infix fun <A, B> Kap<(A) -> B>.with(wrong: Any?): Kap<B> =
    error("unreachable: the with(Any?) guard is deprecated at ERROR level and cannot be called (arg was $wrong)")
