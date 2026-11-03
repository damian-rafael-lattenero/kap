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

## `Schedule` is a semiring (`and` = multiplication, `or` = addition)

`Schedule` composition is an algebra over the extended non-negative reals:
`Decision.Continue(d)` is the delay `d`, `Decision.Done` is `+∞` (an infinite
wait is a stop). `and` merges delays with `max` (both must continue), `or`
merges them with `min` (either may continue). `max`/`min` on `[0, +∞]` form a
distributive lattice, so the laws below hold exactly — distributivity included:

| Law | Property |
|---|---|
| `or` associative | `(a or b) or c == a or (b or c)` |
| `or` commutative | `a or b == b or a` |
| `and` associative | `(a and b) and c == a and (b and c)` |
| `and` commutative | `a and b == b and a` |
| Additive identity | `a or Schedule.never() == a` |
| Multiplicative identity | `a and Schedule.immediate() == a` |
| Multiplicative annihilator | `a and Schedule.never() == Schedule.never()` |
| Distributivity | `a and (b or c) == (a and b) or (a and c)` |
| Idempotence | `a or a == a`, `a and a == a` |

`Schedule.never()` is always `Done` (delay `+∞`); `Schedule.immediate()` is
always `Continue(ZERO)` (delay `0`, the same value as `Schedule.forever()`).
Because both operations are idempotent, the structure is an idempotent
commutative semiring — a bounded distributive lattice — with `0` = `never`
and `1` = `immediate`, ordered inversely to the delay (`never` is the
"smallest" policy: it never retries; `immediate` is the "largest": retry
now, forever).

**Domain of the law statements.** Decisions are compared value-for-value;
`Continue(INFINITE)` is observationally `Done` (a retry that waits forever
stops), so the equalities are stated over schedules whose `Continue` carries
a finite delay — which is every factory unless an explicit `Duration.INFINITE`
cap is passed.

Enforced by [`ScheduleLawsTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/ScheduleLawsTest.kt)
(kotest-property over a generated family of composed schedules; identities and
distributivity included, no sleeping — decisions are pure).

## Process laws

A `Process` (kap-resilience, 4.2.0) is a supervised loop: the body completing normally finishes it, a failure restarts it under a `RestartPolicy`, and a handle exposes `stop()`, `await()` and the heartbeat.

1. **Convergence.** Every restart waits a strictly positive delay and the restart count is bounded by the policy — an instantly-failing body can never hot-loop. The `RestartPolicy` factories enforce it (positive `constant`/`exponential` delays, finite `maxRestarts`); the private constructor means an unlawful policy cannot be expressed.
2. **Terminal success.** Normal completion finishes the process: no restart, the heartbeat stops, `await` returns the value.
3. **Stop and cancellation are never failures.** `stop()` (idempotent) and the death of the owning scope end the process without triggering a restart or exhaustion; `await` reports `ProcessStoppedException` / the cancellation respectively.
4. **Heartbeat independence.** The heartbeat beats at its period while the process is alive — including mid-body and during restart delays — and never waits for body progress; it stops with the process. (The beat answers "is the process there", never "did the cycle finish".)
5. **Transparent observation.** `onRestart`/`onHeartbeat` cannot change the lifecycle: an exception in an observer is swallowed and the policy proceeds.

Enforced by [`ProcessLawsTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/ProcessLawsTest.kt) (convergence, bounded restarts; property-checked) and [`ProcessTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/ProcessTest.kt) (lifecycle, virtual time).

## Supervisor laws

A `Supervisor` (kap-resilience, 5.0.0) is a group of named [Process]-style children with a `SupervisionStrategy` and one `RestartPolicy`.

1. **Isolation (`OneForOne`).** A failure restarts only the failed child. Every sibling's restart counter stays at zero and its body never re-runs: siblings keep running untouched.
2. **Totality (`AllForOne`).** A failure restarts every child of the group that has not completed normally — running siblings are cancelled and relaunched together with the failed child. Simultaneous failures coalesce into a single group restart cycle; they never multiply the counters.
3. **Terminal success.** A child completing normally finishes for good (it is never resurrected, even by an `AllForOne` restart); the group completes normally when every child has completed normally.
4. **Stop and cancellation are never failures.** `stop()` (idempotent) and the death of the owning scope end the group without a restart or exhaustion; `await` reports `SupervisorStoppedException` / the cancellation respectively. Neither depends on the strategy or the restart budget.
5. **Honest exhaustion.** A child gets exactly `maxRestarts` restarts; the next failure fails the group with `SupervisorExhaustedException` whose `cause` is that child's last failure, and whose `child`/`restarts` name the truth.
6. **Honest liveness.** `isAlive` is true exactly while the group exists — including while it waits out a backoff — and false after any terminal outcome. Restart counters advance when a restart actually runs, never when it is merely scheduled.
7. **Convergence.** Inherited from `RestartPolicy`: each restart waits a strictly positive delay and the per-child restart count is bounded, so an instantly-failing child cannot hot-loop. Restart delays are serialized by the supervision loop (one restart cycle at a time).

Enforced by [`SupervisorLawsTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/SupervisorLawsTest.kt) (all seven laws; property-checked under virtual time) and [`SupervisorTest.kt`](kap-resilience/src/jvmTest/kotlin/kap/SupervisorTest.kt) (lifecycle and coalescing, virtual time).

## KapStream laws

A `KapStream` (kap-stream, 5.0.0) is a backpressured pipeline whose stages are `Kap`s and whose backpressure policy is a named value ([`Backpressure`](kap-stream/src/commonMain/kotlin/kap/Backpressure.kt)) at every stage. The default is `Suspend` — exact.

1. **Exact strategies never drop.** `Backpressure.Suspend` (the default at every stage) and `Backpressure.Buffer(n)` deliver every element, in upstream order: the producer suspends instead of discarding.
2. **Lossy strategies drop only what they say.**
   - `Conflate` = `Latest(1)`: superseded elements are dropped; the element buffered at completion is never dropped.
   - `DropOldest(n)` / `Latest(n)`: the oldest elements are the victims; the newest `n` survive.
   - `DropLatest(n)`: the incoming element is the victim; the already-buffered (oldest) elements survive.
   Every strategy preserves upstream order among the elements it keeps and never duplicates one.
3. **Ordering.** `mapKap(concurrency = 1)`, `filterKap` and `flatMapKap(concurrency = 1)` preserve upstream order; `mapKapOrdered(concurrency)` preserves upstream order for every concurrency; concurrent `mapKap`/`flatMapKap` preserve the multiset (completion order).
4. **Cancellation propagates.** Streams are cold and structured: cancelling the collection cancels the source through every channel boundary (strategy buffers included) — no stream escapes its scope.
5. **Named, not global.** The strategy is a value at the stage where it applies; a drop upstream never silently changes a stage configured to be exact, and vice versa.

Enforced by [`KapStreamLawsTest.kt`](kap-stream/src/jvmTest/kotlin/kap/KapStreamLawsTest.kt) (strategy drops, ordering, multiset; property-checked under virtual time) and [`KapStreamTest.kt`](kap-stream/src/jvmTest/kotlin/kap/KapStreamTest.kt) (sources, stages, sinks, cancellation; virtual time).

## Arrow Integration

Arrow's `NonEmptyList` is used natively in `kap-arrow` — no custom reimplementation. All validated operations produce standard `Either<NonEmptyList<E>, A>` values.

## Test Source

All laws are verified in [`ApplicativeLawsTest.kt`](kap-core/src/jvmTest/kotlin/kap/ApplicativeLawsTest.kt) using Kotest property-based testing with random inputs.

**1188 tests across 87 suites in 7 modules + benchmarks. All passing.** *(Numbers verified on every push by `scripts/verify-claims.nu`.)*
