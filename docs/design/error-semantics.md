# KAP Error Semantics — Norm

> Status: **normative** for the current API. The "future directions" section
> sketches a type-level encoding that is NOT implemented yet — treat it as
> design intent, not behavior.

KAP has exactly **three** failure disciplines. Every operator in the public
API belongs to one of them; adding an operator that doesn't is an API design
error. This document is the reference the tests (`FailurePathLawsTest`,
`ValidatedLawsTest`, `CancellationPropertyTest`) enforce.

## The three disciplines

| Discipline | Meaning | Exception-world operators | Validated-world operators |
|---|---|---|---|
| **Accumulate** | Run everything; collect all failures into one value | — | `withV`, `zipV`, `traverseV`, `sequenceV` |
| **Short-circuit** | First failure wins; later work is skipped | `andThen` (bind), `then`* | `thenV`, `thenValueV`, `andThenV`, `validated { bind() }` |
| **Convert** | Capture the failure as a value; siblings unaffected | `settled`, `recover`, `recoverWith`, `orElse`†, `settled { }` | `catching`, `recoverV`, `orThrow` |

\* `then` is *sequential* (a phase barrier) but does **not** inspect values —
it always runs its right side. Its "short-circuit" is structural (ordering),
not error-driven. In the validated world, `thenV` short-circuits on `Left`.

† `orElse` converts the *primary's* failure into "use the fallback" — the
fallback itself may still fail, so it is conversion with a retry flavor.

## Decision table

| You want… | Use |
|---|---|
| Every failure reported together (form validation) | `withV` / `zipV` / `traverseV` |
| Stop at the first failure, no further effects | `andThenV` / `validated { bind() }` / `andThen` |
| A branch that may fail without cancelling siblings | `settled` (`Result<A>` slot) |
| A default value/graph when something fails | `recover` / `orElse` / `timeout(d, default)` |
| Failures from an exception-throwing bridge folded into validation | `catching` |

## Laws (already enforced by property tests)

1. **Cancellation is sacred.** `CancellationException` is never caught,
   converted, accumulated or short-circuited by ANY operator
   (`CancellationPropertyTest`).
2. **Accumulation is associative and order-preserving.** NEL append keeps
   left-to-right order across arbitrary groupings (`ValidatedLawsTest`).
3. **Short-circuit never executes the skipped side** — asserted by
   side-effect flags, not just result equality.
4. **Conversion preserves the failure token.** `settled`/`recover` capture the
   original exception (type+message; JVM identity may differ due to
   kotlinx-coroutines stack-trace recovery across suspension points).
5. **Failure path of `with` cancels siblings** — structured concurrency: one
   failing branch fails the phase.

## Naming conventions (normative)

- `V` suffix = the validated world (`F<A> = Kap<Either<NonEmptyList<E>, A>>>`).
  Within it, the discipline is expressed by the verb (`withV` accumulate /
  `thenV` short-circuit / `andThenV` bind), per the table above.
- No `V` suffix = the exception world. There, `with`/`then` never skip, and
  conversion is explicit via `settled`/`recover*`.
- New operators MUST choose a discipline from the table and document it in
  their KDoc with a line of the form: `Error semantics: ACCUMULATE`.

## Future directions (not implemented)

The disciplines are currently tracked by naming convention. A type-level
encoding would make the compiler reject mixing them unintentionally —
sketches, in increasing ambition:

1. **Phantom tag on `Kap`**: `Kap<A, phase: ErrorMode>` with `Accumulate` /
   `ShortCircuit` / `Convert` as sealed sub-kinds; operators only accept the
   kind they belong to. Cheap, but infects every signature.
2. **Two type constructors**: keep `Kap<A>` for the exception world, promote
   the validated world to its own `ValidatedKap<E, A>` (it already has its
   own builder class). Discipline mixing becomes a type error at the boundary
   (`catching`/`orThrow` become the only bridges).
3. **Effect rows / capability-style** encoding — researched, deliberately
   deferred; Kotlin's type system makes the ergonomics poor today.

Option 2 is the leading candidate for 5.x; it would not change the call-site
DSL (`.withV` stays), only the internal carrier types.
