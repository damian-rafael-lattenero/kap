# SPEC 5.0: KAP as the complete declarative concurrency DSL for Kotlin

**Status:** direction accepted by the author (2026-10-28). This document is
the roadmap; each milestone lands with laws, docs, goldens and benchmarks,
and dogfoods in `agent-brain` before release.

## The vision

> KAP arregla TODOS los problemas de concurrencia, no solo el grafo:
> backpressure, streaming, procesos de vida larga, supervisión, recursos.
> Como ZIO, como Cats, casi Akka sin serlo: RxJava pero en DSL de Kotlin.

One library, five algebras, one law-checked semantic core:

| Algebra | Today (4.2.0) | Target |
|---|---|---|
| **Graph** (request-scoped effects) | `Kap`, `with`/`then`/`zip`, Validated, KSP builders | done; grows only at the edges |
| **Policy** (retry, cadence) | `Schedule` (retry-only), breakers, timeouts, `repeat`/`repeatWhile`/`repeatUntil` (4.1.0) | policies compose and are values |
| **Stream** (backpressured pipelines) | per-element Flow interop | `KapStream`: stages are `Kap`s, backpressure strategies are named values, cancellation is structured |
| **Process** (long-lived, supervised) | first cut (4.2.0): `process()`/`RestartPolicy`/heartbeat handle with convergence laws | named supervision (`OneForOne`/`AllForOne`), restart-with-health as the interpretation of a supervisor |
| **Lifecycle** (acquire/await/release) | `bracket`, `Resource`, `guarantee`, `Kap.awaiting` (4.1.0) | `Scope` shared across a graph |

## Design principle: algebra first, Haskell as the compass

Every new algebra is defined the way the graph was: as a lawful structure
first, as an API second (author's direction, 2026-10-28).

- **Typeclass hierarchy per algebra.** `Kap` already satisfies Functor,
  Applicative and Monad (property-tested, `LAWS.md`). `KapStream` gets
  Functor/Applicative/Monad plus `Traverse` (stages over collections),
  `Semigroup`/`Monoid` instances for merging streams and fusing results, and
  an **Arrow/Profunctor** reading of stages (`Stage<A, B>` composes with
  `>>>`, `first`/`second`, `***`), which is what makes backpressure
  strategies composable per stage instead of global flags.
- **Policies as a Semiring.** `Schedule` composes with `and` (both continue,
  max delay) and `or` (either continues, min delay): make the laws explicit
  (associativity, identities `forever`/`never`) and property-test them, so
  `repeat`/`retry` policies are algebraic values, not configuration.
- **Effects stay explicit.** Failure channels keep the three disciplines
  (ACCUMULATE / SHORT-CIRCUIT / CONVERT) and the Isolation law; `Validated`
  remains Applicative-only by design; Arrow's `Either`/`NonEmptyList`/`Raise`
  are the interop currency, never reimplemented.
- **Haskell vocabulary where it clarifies, Kotlin ergonomics where it
  counts.** `traverse`/`sequence`/`zip` over `parMap`; `Resource` as a
  Monad; `Process` and `Supervisor` modelled after the Free/Cofree intuition
  (a process is a program interpreted by a supervisor) without exposing
  free monads in the public API.
- **Laws before features.** A combinator ships with its laws in
  `kap-core/src/jvmTest/.../*LawsTest.kt` (kotest-property) or it does not
  ship; `scripts/verify-claims.sh` keeps the counts honest.

## Strategic decisions

1. **Wrap, never reimplement, the runtime.** ZIO and Cats own their runtimes
   because Scala's was insufficient. Kotlin's coroutines, channels and select
   ARE the runtime: structured cancellation, real backpressure, virtual-time
   testability. KAP's value is the typed, declarative, law-checked layer
   above them. We own semantics; we rent the engine.
2. **Actors yes (`KapProcess`), Akka no.** A typed mailbox process over
   Channel + select is small, testable and useful. Clustering, persistence
   and location transparency are explicitly out of scope: that is the part of
   Akka nobody misses.
3. **STM no.** `Ref`/`Queue`/`Promise` as thin typed wrappers cover the
   realistic state needs; a software transactional memory on the JVM is a
   research project with negative ROI here.
4. **Virtual time is the proof standard.** Every operator that delays,
   repeats, buffers or restarts must be testable with `runTest` and, where
   timing is semantic, provable with the `kap-kotest` matchers
   (`shouldProveParallel`, `shouldBeMillis`). A concurrency library whose
   tests sleep is a demo.

## Milestone 4.2 — policies and lifecycle (complete: repeat/awaiting in 4.1.0, Process in 4.2.0)

- `Kap<A>.repeat(schedule: Schedule<A>): Kap<A>` — run again while the
  schedule says continue; cadence, jitter, max duration and stop condition
  are values. `retry` composes per iteration (`.retry(p).repeat(s)`).
- `repeatWhile(predicate)` / `repeatUntil(predicate)` — conveniences over
  `Schedule.whileInput`/`untilInput`.
- `Kap.awaiting { resume, cancel -> ... }` — lift a callback (the SDK's
  `onClose`, a listener, a shutdown hook) into a cancellable `Kap`.
- `Process` (first cut) — **shipped in 4.2.0**: `Kap<A>.process(name, restart = RestartPolicy.exponential(...), heartbeatEvery = ...)` — a supervised loop with a heartbeat; exposed via a handle with `stop()`. (`maxRestarts` was folded into `RestartPolicy` so the convergence law — positive delays, finite cap, no infinite hot loop — is a property of the policy value, enforced by its private constructor.)
- Laws: repeat respects the schedule (virtual time), cancellation interrupts
  between iterations, awaiting registers exactly once and unregisters on
  cancel, restart policies converge (no infinite hot loop).

## Milestone 5.0 — streams and processes

- `KapStream<A>`: `from(Flow)`, `emitted(kap).every(schedule)` (polling
  source), stages `mapKap`/`filterKap`/`mapKapOrdered(concurrency)`,
  strategies `buffer(n)`, `conflate`, `dropOldest`, `dropLatest`, `latest(1)`,
  sinks `collectAsKap`, `drainTo(channel)`.
- Backpressure is a NAMED value at every stage; the default is suspend
  (exact), every alternative is explicit and tested for what it drops.
- Supervision: `Supervisor(children, strategy)` with OneForOne/AllForOne and
  backoff; a failing child either restarts or fails the group, per policy.
- Benchmarks (JMH, joining the existing suite): KapStream stages vs raw Flow
  vs RxJava 3 for map/filter/flatMap at n=1e6 with each strategy; overhead
  target: within 2x of raw Flow for suspend stages (it is a thin layer).
- Laws: stream strategies (what may be dropped, what never is), ordering
  (mapKapOrdered), cancellation propagation, mailbox process laws
  (at-most-one handler, FIFO per sender).

## Dogfooding contract

Every milestone ships used, not just tested: 4.2's `repeat` replaces the four
hand-rolled loops of agent-brain (bus tailer, memory worker, Kafka consumer,
bridge) and `awaiting` replaces its process-park; 5.0's `KapStream` must
power at least one real surface (the live turn viewer of the bot, or the
event tailer as a stream) before it is called done.
