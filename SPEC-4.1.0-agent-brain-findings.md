# SPEC 4.1.0: findings from dogfooding KAP in agent-brain (2026-10-28)

Source: the first integration of KAP 4.0.0 (Maven Central) by a system that is
not its own test suite: `agent-brain` (Kotlin 2.4.21, coroutines 1.11.0, ktor
3.6.0, JDK 25), an indexing pipeline + memory extraction service with
Postgres/pgvector and local LLMs. Every finding below has a reproduction that
runs today in `agent-brain/core/src/test/kotlin/dev/lattenero/brain/kapx/KapFindingsTest.kt`
or in this document; the ones marked **BUG** make the library do something
its own docs say it does not do.

Legend: **BUG** (behaviour contradicts documented semantics), **TRAP** (works
as implemented, but the obvious usage silently does the wrong thing),
**DOCS** (documentation drift), **ABI** (public surface vs CHANGELOG),
**DX** (developer experience), **TOOLCHAIN**.

Target version: **4.1.0** (minor: behaviour fixes under the documented laws,
additive API, no removals). Release path: fixes + tests + CHANGELOG →
`./gradlew check` (laws, goldens, apiCheck after `apiDump`) → `publishToMavenLocal`
for immediate consumption → tag `v4.1.0` → the existing CI publishes to Central.

---

## F-01 · BUG · CONVERT operators do not isolate nested parallel graphs

**Symptom.** `traverseSettled`, `settled`, `recover`, `recoverWith`, `orElse`,
`firstSuccessOf`, `catching` and every `retry*` variant fail to contain the
failure of a graph that has parallel children (`traverse`, `with`, `zip`).
The exception escapes as if no CONVERT operator were present.

**Reproduction** (10 lines, passes today = documents the defect):
```kotlin
val nested = listOf(1, 2).traverse { n -> Kap { if (n == 2) error("child 2 failed") else n } }
assertThrows<IllegalStateException> { listOf("doc").traverseSettled { nested }.evalGraph() }
assertThrows<IllegalStateException> { nested.settled().evalGraph() }
// expected by LAWS/error-semantics.md: Result.failure, siblings unaffected
```

**Root cause.** `traverse` (`kap-core/src/commonMain/kotlin/kap/Collections.kt:44-56`)
and `with` (`Kap.kt:236+`) launch children with `async { }` on the **receiver**
`CoroutineScope` of `execute()`, i.e. the caller's coroutine. Inside a CONVERT
operator that coroutine is the operator's own (`settled`: `Kap.kt:733`;
`recover`: `Combinators.kt:59`; `traverseSettled`: `Collections.kt:132,143`;
`catching`: `Interop.kt:87`; core `retry`: `Combinators.kt:103`; resilience
`retry`/`retryOrElse`/`retryWithResult`: `ResilienceCombinators.kt:42,102,154`;
`withCircuitBreaker`: `CircuitBreaker.kt:125`). When a grandchild fails,
structured concurrency cancels that coroutine's Job **before** `awaitAll`
throws into the `try`; the catch block runs, returns a `Result`, but the
coroutine is already failed, so `awaitAll`/`await` upstream rethrows the
original exception. `runCatching` inside `traverseSettled`'s `async` has the
same fate.

**Why KAP's suite never saw it.** The law tests exercise CONVERT with leaf
computations (`Kap { throw }`), never with a nested parallel graph.

**Fix.** Give every CONVERT and RETRY operator a scope boundary: run the
inner `execute()` inside its own `coroutineScope { }` so child failures are
delivered as a thrown exception to the operator, not as a cancellation of the
operator's coroutine.
```kotlin
// kap-core, internal helper
@PublishedApi internal suspend fun <A> CoroutineScope.executeIsolated(k: Kap<A>): A =
    coroutineScope { with(k) { execute() } }
```
Apply in: `settled`, `recover`, `recoverWith`, `orElse`, `firstSuccessOf`
(per candidate), `catching` (wrap `block`), core `retry` (per attempt),
`traverseSettled` ×2 (inside the `async`), resilience `retry` ×2,
`retryOrElse`, `retryWithResult` (per attempt), `withCircuitBreaker` (so the
failure is recorded and rethrown from a live coroutine). `timeout(d, default)`
and `timeout(d, fallback)` already run under `withTimeoutOrNull`, which is a
scope; leave them.

**Law to add** (`docs/design/error-semantics.md`, enforced in
`kap-core/src/jvmTest/kotlin/kap/ErrorSemanticsLawsTest.kt`):
> **Isolation.** A CONVERT or RETRY operator applied to a graph with
> parallel children observes the first child failure as an ordinary
> failure; the operator's own coroutine is not cancelled; siblings outside
> the operator are unaffected.

Property test: for each operator in the CONVERT/RETRY set, `nested` (above)
is converted; and `kap(::Pair).with { a from nested.settled() }.with { b from Kap { 42 } }`
yields `Pair(Result.failure, 42)`.

**Cost.** One extra `coroutineScope` frame per CONVERT node; JMH `settled`
benchmark should move by nanoseconds. Record it in the 4.1.0 notes.

**Workaround shipped in agent-brain** (to be deleted when 4.1.0 lands):
`fun <A> Kap<A>.isolated(): Kap<A> = Kap { coroutineScope { with(this@isolated) { execute() } } }`.

---

## F-02 · TRAP · `timeout(d)` before `retry` is never retried

**Symptom.** `Kap { call() }.timeout(5.seconds).retry(policy)` never retries a
slow call: the timeout propagates as a cancellation and the graph fails after
one attempt.

**Root cause.** `timeout(duration)` (`Combinators.kt:15`) uses `withTimeout`,
which throws `TimeoutCancellationException : CancellationException`; every
`retry` rethrows `CancellationException` by law (`ResilienceCombinators.kt:52`,
`Combinators.kt` core retry). Both behaviours are individually correct; the
composition is a trap and nothing warns.

**Fix (additive).**
1. KDoc on `timeout(duration)`: "Not retryable: the failure is a
   CancellationException. To retry a timeout use `timeoutAsFailure` or the
   fallback overload."
2. New in kap-core:
   ```kotlin
   class KapTimeoutException(val duration: Duration) : RuntimeException("timed out after $duration")
   fun <A> Kap<A>.timeoutAsFailure(duration: Duration): Kap<A> =
       timeout(duration, Kap.failed(KapTimeoutException(duration)))
   ```
3. `docs/guide/recipes.md`: "Retrying timeouts" recipe; `kap-resilience`
   README mention next to `retry`.
4. Test: virtual time, provider hangs once, `timeoutAsFailure(1.seconds).retry(times(1))`
   succeeds on the second attempt (`kap-core/src/jvmTest/.../TimeoutTest.kt`).

---

## F-03 · ABI · `CircuitBreaker` exposes less than the CHANGELOG says

**Symptom.** CHANGELOG 4.0.0 lists `shouldAttempt`, `recordSuccess`,
`recordFailure` as public; in `CircuitBreaker.kt:60-95` and
`kap-resilience/api/kap-resilience.api` they are `internal`. A breaker cannot
guard non-Kap code (e.g. a plain `suspend fun`) and cannot be driven in tests.

**Fix.** Public, mutex-taking `suspend` API, keeping the internal unlocked
variants for `withCircuitBreaker`:
```kotlin
suspend fun tryAcquire(): Boolean     // mutex { shouldAttempt() }
suspend fun onSuccess()               // mutex { recordSuccess() }
suspend fun onFailure()               // mutex { recordFailure() }
suspend fun <A> protect(block: suspend () -> A): A  // acquire-or-throw, record, rethrow
```
Tests in `CircuitBreakerTest`; `apiDump`; CHANGELOG corrected to name the
real surface.

---

## F-04 · DX · `Kap.of` / `Kap.empty` need an import nobody expects

**Symptom.** `Kap.of(x)` does not compile without `import kap.of`
(`Kap.kt:180`: `fun <A> Kap.Companion.of(a: A)` is a top-level extension).
IDE auto-import works, `-Werror` builds and code review do not; the README
never shows the import.

**Fix.** Move `of` and `empty` into `Kap.Companion` as members; keep the
extensions for binary compatibility (members win at resolution), annotate
them `@Deprecated(level = HIDDEN)` after one minor. Same for `Kap.failed`'s
neighbours if any are extensions. Note in the README quickstart which
imports a user needs (`kap.*`, `kap.KapTypeSafe`).

---

## F-05 · DOCS · drift between docs and code

| Where | Says | Code |
|---|---|---|
| `docs/modules/kap-core.md` ~829-893 | `Flow.mapEffect`, `mapEffectOrdered` | `mapKap`, `mapKapOrdered`, `filterKap` (`FlowIntegration.kt`) |
| `docs/modules/kap-core.md:1100` | `evalGraph(MDCContext())` | `evalGraph()` has no parameters (`Kap.kt:653`); use `withContext(MDCContext()) { g.evalGraph() }` or the `context` Kap |
| `docs/modules/kap-ktor.md`, `kap-kotest.md`, `kap-ksp.md` | "Unreleased / not published", coordinates `3.0.0` | all published at 4.0.0 (verified on repo1) |
| README KSP section | `kap(::Dashboard)` in every example | see F-09: never generated for packaged classes in 4.0.0 |
| README / quickstart | no imports shown | `import kap.KapTypeSafe` (package `kap`, not `kap.ksp`), `import kap.of` (until F-04) |
| `docs/modules/kap-ktor.md` | `kapExceptionHandlers()` | needs `ktor-server-status-pages` on the user's classpath (it is `compileOnly`); say so or ship it (F-08) |
| README `raceN` | "fastest wins" | first **success** wins; early failures do not win; errors accumulate as suppressed (`Race.kt` KDoc is right) |

---

## F-06 · DX · `KapLike` wrappers cannot take resilience mid-chain

**Symptom.** A `@KapTypeSafe` chain (`kapT(::T).with { ... }`) returns
`TKap<F> : KapLike<F>`; `KapLike` only delegates core operators
(`KapLike.kt:21-39`), so `.retry(...)`, `.withCircuitBreaker(...)`,
`.timeoutRace(...)`, `.traced(...)`, `.timeout(d, fallback)` are unavailable
until the chain is finished.

**Fix.** Add delegates:
- kap-core: `fun <F> KapLike<F>.traced(name, tracer)`, `timeout(duration, fallback: Kap<F>)`, `timeoutAsFailure`, `orElse`, `settled` (exists), `peek`, `named`, `on`.
- kap-resilience: `retry(schedule)`, `retry(factory)`, `retryOrElse`, `retryWithResult`, `withCircuitBreaker`, `timeoutRace`.
Tests: one chain per module applying the operator mid-way.

---

## F-07 · TOOLCHAIN · align with current consumers

Current: Kotlin 2.3.20, coroutines 1.10.2, ktor 3.4.2, Arrow 2.1.2, KSP
2.3.6, JVM toolchain 21 (`gradle/libs.versions.toml`). Consumers today:
Kotlin 2.4.21, coroutines 1.11.0, ktor 3.6.0, KSP 2.3.12, JDK 25.

**Fix.** Bump Kotlin → 2.4.21, coroutines → 1.11.0, KSP → 2.3.12, ktor →
3.6.0, Arrow → 2.3.0 (revert to 2.1.2 if `kap-arrow` laws break), keep JVM
toolchain 21 (consumers on 21+). Run the full suite incl. goldens and
benchmarks' compile. Document the supported ranges in README ("built with X,
tested against Y").

---

## F-08 · DX · kap-ktor packaging and naming

1. `ktor-server-status-pages` is `compileOnly` → `kapExceptionHandlers()`
   fails with a confusing NoClassDefFoundError unless the user adds it. Make
   it `implementation` (small, stable) or document loudly.
2. The plugin is `val Kap` (`kap-ktor/.../KapPlugin.kt:60`), colliding with
   `kap.Kap` when both packages are star-imported. Add `val KapPlugin = Kap`
   as the recommended name; keep `Kap` for compatibility; docs use `KapPlugin`.
3. `respondAsync` / `respondKap` are `RoutingCall` extensions only; add
   `ApplicationCall` variants or document why not.

---

## F-09 · BUG · the plain `kap(::T)` entry is never generated for packaged classes

**Symptom.** `@KapTypeSafe data class Checks(val db: DbCheck, val embed: ProviderHealth)`
in package `dev.lattenero.brain.http` generates `kapChecks(...)` only; the
README promises `kap(::Checks)` whenever the constructor shape is unique (it
is: one annotated class in the module).

**Root cause.** `KapTypeSafeProcessor.recordSignature` (`:110-120`) keys the
class signature with `symbol.simpleName` as return type (`"Checks"`), while
`generateForClass` (`:269-276`) looks it up with the fully qualified name
(`"dev.lattenero.brain.http.Checks"`). The keys only coincide when the
package is empty, which is the case of **every** golden fixture
(`kap-ksp/src/test/resources/golden/*.kt` have no `package` line) and of
`examples/ksp-demo/src/main/kotlin/Main.kt`. For every packaged class
`signatureCounts[key] == null`, so `signatureIsUnique` is `false` and the
fallback name wins. Functions (`generateForFunction`, `:330`) should be
checked for the same asymmetry (`recordSignature` renders the function's
return via `renderType`, the lookup via `renderType` as well: likely
consistent, verify with a test).

**Fix.** Render the return type identically in both places (use the FQN in
`recordSignature`: `symbol.qualifiedName?.asString()`). Add a golden fixture
**with a package** (`golden/pkg/FixtPackagedKapBuilder.kt`) and a test that
asserts the plain `kap(` entry exists for it. Add the same for a packaged
`@KapTypeSafe` function.

---

## F-11 · BUG · nested `@KapTypeSafe` classes generate code that does not compile

**Symptom.** `class BusPipeline { @KapTypeSafe data class Probe(val fileBytes: Long, val dbEvents: Long) }`
generates `ProbeKapBuilder.kt` referencing `dev.lattenero.brain.bus.Probe`: the
outer class is dropped, every reference is unresolved, the module fails to compile.

**Root cause.** `generateForClass` (`KapTypeSafeProcessor.kt:242,274`) builds
the class reference as `"$packageName.$simpleName"`; for a nested class the real
name is `pkg.Outer.Inner` (`KSClassDeclaration.qualifiedName`). No golden
fixture is nested.

**Fix.** Reference the class by `qualifiedName`; name generated declarations by
the enclosing chain (`BusPipelineProbeKap`, `kap(::Probe)` still resolves by
callable reference) so two nested classes with the same simple name in one
package cannot collide. Golden fixture `Outer.Nested` added.

---

## F-12 · GAP · loops were code, not policy

**Symptom.** Every long-lived loop in agent-brain (bus tailer, memory worker,
Kafka consumer, bridge) was `while (true) { try poll catch log; delay }`:
`Schedule` existed but only `retry` could use it.

**Fix (shipped in 4.1.0).** `Kap<A>.repeat(schedule)`, `repeatWhile`,
`repeatUntil` in kap-resilience; `Kap.awaitUntil(every, upTo) { }` for the
"wait until it happens, with a ceiling" idiom. See SPEC-5.0 for the full
direction (streams, processes, supervision).

---

## F-13 · GAP · no way to lift a callback into a Kap

**Symptom.** Parking a process on an SDK's `onClose` callback needed a
`CompletableFuture`; nothing in KAP expressed "await this callback,
cancellably".

**Fix (shipped in 4.1.0).** `Kap.awaiting { resume, cancel -> register }`
in kap-core over `suspendCancellableCoroutine`.

---

## F-10 · DX · small things

- `kap-kotest/src/main/kotlin/kap/kotest/TimingMatchers.kt`: unused `TestScope` import.
- `traverse(concurrency)` creates one coroutine per element eagerly and gates
  with a `Semaphore`; fine for thousands, document the memory shape for
  millions (or launch lazily in windows of `concurrency`).
- `Schedule.withMaxDuration` statefulness is documented; consider making
  `retry(schedule)` detect a stateful schedule (a marker interface) and warn
  once, or make the factory overload the primary in docs.
- CHANGELOG `[Unreleased]` section is empty; this spec becomes its content.

---

## Acceptance for 4.1.0

1. All findings F-01, F-02, F-03, F-04, F-06, F-08, F-09, F-11 implemented with
   tests; F-05 and F-10 docs fixed; F-07 toolchain bumped.
2. `./gradlew check` green: unit, laws (property), goldens (regenerated on
   purpose where the emitter changed), `apiCheck` after `apiDump`, detekt.
3. CHANGELOG 4.1.0 entry written from this document.
4. `./gradlew publishToMavenLocal -PskipSigning` → agent-brain switches to
   4.1.0 from `mavenLocal()`, deletes `kapx/Isolated.kt`, inverts
   `KapFindingsTest` (the pinned defects must now PASS as fixed), and uses
   `kap(::T)` builders throughout.
5. Tag `v4.1.0` and push when the author says so; CI publishes to Central.
