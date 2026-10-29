# Algebraic Laws — KAP Composition Guarantees

Unlike most Kotlin libraries, every algebraic law is **property-based tested** with random inputs via Kotest. This means refactoring with these combinators is provably safe — the laws guarantee substitutability.

## `Kap` satisfies Functor, Applicative, and Monad laws

| Law | Property | What it guarantees |
|---|---|---|
| Functor Identity | `fa.map { it } == fa` | `map` with identity is a no-op |
| Functor Composition | `fa.map(g).map(f) == fa.map { f(g(it)) }` | Fusing two maps is safe |
| Applicative Identity | `Kap.of(id) with fa == fa` | Lifting identity does nothing |
| Applicative Homomorphism | `Kap.of(f) with Kap.of(x) == Kap.of(f(x))` | Pure values compose purely |
| Applicative Interchange | `u with Kap.of(y) == Kap.of { f -> f(y) } with u` | Order of pure application doesn't matter |
| Applicative Composition | `Kap.of(compose) with u with v with w == u with (v with w)` | Composition is associative |
| Monad Left Identity | `Kap.of(a).andThen(f) == f(a)` | Wrapping then andThen is same as direct call |
| Monad Right Identity | `m.andThen { Kap.of(it) } == m` | andThen with wrap is identity |
| Monad Associativity | `(m.andThen(f)).andThen(g) == m.andThen { f(it).andThen(g) }` | andThen chains are associative |

## `Validated` (withV/zipV) satisfies Applicative laws

`Validated` is intentionally NOT a Monad — error accumulation requires applicative semantics. Making it a Monad would force short-circuit behavior, which defeats the purpose of collecting all errors.

| Law | Property |
|---|---|
| Applicative Identity | `valid(id) withV fa == fa` |
| Applicative Homomorphism | `valid(f) withV valid(x) == valid(f(x))` |
| Error Accumulation | `invalid(e1) withV invalid(e2)` accumulates both errors into `NonEmptyList` |

## Isolation law

CONVERT and RETRY operators (`settled`, `traverseSettled`, `recover`, `recoverWith`, `orElse`, `firstSuccessOf`/`firstSuccess`, `catching`, every `retry`, and `withCircuitBreaker`) run their operand inside its own `coroutineScope` (kap-core `Isolation.kt`; the public escape hatch is `Kap<A>.isolated()`).

> A CONVERT or RETRY operator applied to a graph with parallel children observes the first child failure as an ordinary failure; the operator's own coroutine is not cancelled; siblings outside the operator are unaffected.

Enforced by [`IsolationLawsTest.kt`](kap-core/src/jvmTest/kotlin/kap/IsolationLawsTest.kt) (kap-core) and [`IsolationResilienceTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/IsolationResilienceTest.kt) (kap-resilience).

## Process laws

A `Process` (kap-resilience, 4.2.0) is a supervised loop: the body completing normally finishes it, a failure restarts it under a `RestartPolicy`, and a handle exposes `stop()`, `await()` and the heartbeat.

1. **Convergence.** Every restart waits a strictly positive delay and the restart count is bounded by the policy — an instantly-failing body can never hot-loop. The `RestartPolicy` factories enforce it (positive `constant`/`exponential` delays, finite `maxRestarts`); the private constructor means an unlawful policy cannot be expressed.
2. **Terminal success.** Normal completion finishes the process: no restart, the heartbeat stops, `await` returns the value.
3. **Stop and cancellation are never failures.** `stop()` (idempotent) and the death of the owning scope end the process without triggering a restart or exhaustion; `await` reports `ProcessStoppedException` / the cancellation respectively.
4. **Heartbeat independence.** The heartbeat beats at its period while the process is alive — including mid-body and during restart delays — and never waits for body progress; it stops with the process. (The beat answers "is the process there", never "did the cycle finish".)
5. **Transparent observation.** `onRestart`/`onHeartbeat` cannot change the lifecycle: an exception in an observer is swallowed and the policy proceeds.

Enforced by [`ProcessLawsTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/ProcessLawsTest.kt) (convergence, bounded restarts; property-checked) and [`ProcessTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/ProcessTest.kt) (lifecycle, virtual time).

## Arrow Integration

Arrow's `NonEmptyList` is used natively in `kap-arrow` — no custom reimplementation. All validated operations produce standard `Either<NonEmptyList<E>, A>` values.

## Test Source

All laws are verified in [`ApplicativeLawsTest.kt`](kap-core/src/jvmTest/kotlin/kap/ApplicativeLawsTest.kt) using Kotest property-based testing with random inputs.

**1112 tests across 82 suites in 6 modules + benchmarks. All passing.** *(Numbers verified on every push by `scripts/verify-claims.sh`.)*
