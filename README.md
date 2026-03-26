# KAP — Kotlin Async Parallelism

Multi-service orchestration for Kotlin coroutines. Flat chains, visible phases, compiler-checked argument order.

**Your code shape *is* the execution plan.**

[![CI](https://github.com/damian-rafael-lattenero/coroutines-applicatives/actions/workflows/ci.yml/badge.svg)](https://github.com/damian-rafael-lattenero/coroutines-applicatives/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.10.2-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![Tests](https://img.shields.io/badge/Tests-906%20across%2061%20suites-brightgreen.svg)](#benchmarks)
[![Benchmarks](https://img.shields.io/badge/Benchmarks-119%20JMH-blueviolet.svg)](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/)

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)
[![Modular](https://img.shields.io/badge/Modules-kap--core%20%7C%20kap--resilience%20%7C%20kap--arrow-informational.svg)](#modules)

---

## The Problem

You have 11 microservice calls. Some can run in parallel, others depend on earlier results. With raw coroutines:

```kotlin
val checkout = coroutineScope {
    val dUser = async { fetchUser() }
    val dCart = async { fetchCart() }
    val dPromos = async { fetchPromos() }
    val dInventory = async { fetchInventory() }
    val user = dUser.await()
    val cart = dCart.await()
    val promos = dPromos.await()
    val inventory = dInventory.await()

    val stock = validateStock()

    val dShipping = async { calcShipping() }
    val dTax = async { calcTax() }
    val dDiscounts = async { calcDiscounts() }
    val shipping = dShipping.await()
    val tax = dTax.await()
    val discounts = dDiscounts.await()

    val payment = reservePayment()

    val dConfirmation = async { generateConfirmation() }
    val dEmail = async { sendEmail() }

    CheckoutResult(
        user, cart, promos, inventory, stock,
        shipping, tax, discounts, payment,
        dConfirmation.await(), dEmail.await()
    )
}
```

- **Invisible phases.** Where are the parallel groups? Where are the barriers? You can't tell without reading every line.
- **Silent bugs.** Move one `await()` above its `async` — silently serialized. The compiler says nothing.
- **Boilerplate.** 30+ lines of shuttle variables, no structure. Each new phase doubles the `async`/`await` ceremony.

---

## The Solution

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }              // ┐
        .with { fetchCart() }               // ├─ phase 1: parallel
        .with { fetchPromos() }             // │
        .with { fetchInventory() }          // ┘
        .then { validateStock() }     // ── phase 2: barrier
        .with { calcShipping() }            // ┐
        .with { calcTax() }                 // ├─ phase 3: parallel
        .with { calcDiscounts() }           // ┘
        .then { reservePayment() }    // ── phase 4: barrier
        .with { generateConfirmation() }    // ┐ phase 5: parallel
        .with { sendEmail() }              // ┘
}
```

11 service calls. 5 phases. One flat chain. **Swap any two `.with` lines and the compiler rejects it.** Each service returns a distinct type — the typed function chain locks parameter order at compile time.

**130ms total** (vs 460ms sequential) — verified in [`ConcurrencyProofTest.kt`](kap-core/src/jvmTest/kotlin/kap/ConcurrencyProofTest.kt).

```
t=0ms   ─── fetchUser ────────┐
t=0ms   ─── fetchCart ────────┤
t=0ms   ─── fetchPromos ─────├─ phase 1 (parallel)
t=0ms   ─── fetchInventory ──┘
t=50ms  ─── validateStock ───── phase 2 (barrier)
t=60ms  ─── calcShipping ────┐
t=60ms  ─── calcTax ─────────├─ phase 3 (parallel)
t=60ms  ─── calcDiscounts ───┘
t=80ms  ─── reservePayment ──── phase 4 (barrier)
t=90ms  ─── generateConfirm ─┐
t=90ms  ─── sendEmail ───────┘─ phase 5 (parallel)
t=130ms ─── done
```

No intermediate variables. The constructor receives all arguments at once — no shuttle vals, no manual threading.

> All code examples on this page are compilable and verified in [`readme-examples`](examples/readme-examples/).

---

## Quick Start

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")
}
```

<details>
<summary>Maven</summary>

```xml
<dependency>
    <groupId>io.github.damian-rafael-lattenero</groupId>
    <artifactId>kap-core-jvm</artifactId>
    <version>2.3.0</version>
</dependency>
```

</details>

```kotlin
import kap.*

data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun main() {
    val result = Async {
        kap(::Dashboard)
            .with { fetchDashUser() }    // ┐ all three in parallel
            .with { fetchDashCart() }     // │ total time = max(individual)
            .with { fetchDashPromos() }   // ┘ not sum
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

Try it: `./gradlew :examples:ecommerce-checkout:run`

---

## Core Model: Three Primitives

| Primitive | What it does | Think of it as |
|---|---|---|
| `.with { }` | Launch in parallel with everything above | "and at the same time..." |
| `.then { }` | Wait for everything above, then continue | "then, once that's done..." |
| `.andThen { ctx -> }` | Wait for everything above, pass the result, then continue | "then, using what we got..." |

That's the whole model. Here's all three in a BFF endpoint — the classic pattern where phase 2 **depends** on phase 1's results:

```kotlin
val userId = "user-42"

val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐
        .with { fetchPreferences(userId) }   // ├─ phase 1: parallel
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                     // ── barrier: phase 2 NEEDS ctx
            kap(::EnrichedContent)
                .with { fetchRecommendations(ctx.profile) }  // ┐
                .with { fetchPromotions(ctx.tier) }          // ├─ phase 2: parallel
                .with { fetchTrending(ctx.prefs) }           // │
                .with { fetchHistory(ctx.profile) }          // ┘
                .andThen { enriched ->                        // ── barrier
                    kap(::FinalDashboard)
                        .with { renderLayout(ctx, enriched) }     // ┐ phase 3
                        .with { trackAnalytics(ctx, enriched) }   // ┘
                }
        }
}
```

```
t=0ms   ─── fetchProfile ──────┐
t=0ms   ─── fetchPreferences ──├─ phase 1 (parallel, all 3)
t=0ms   ─── fetchLoyaltyTier ──┘
t=50ms  ─── andThen { ctx -> }  ── barrier, ctx available
t=50ms  ─── fetchRecommendations ──┐
t=50ms  ─── fetchPromotions ───────├─ phase 2 (parallel, all 4)
t=50ms  ─── fetchTrending ─────────┤
t=50ms  ─── fetchHistory ──────────┘
t=90ms  ─── andThen { enriched -> } ── barrier
t=90ms  ─── renderLayout ──┐
t=90ms  ─── trackAnalytics ┘─ phase 3 (parallel)
t=115ms ─── FinalDashboard ready
```

The dependency graph IS the code shape.

---

## KAP vs Raw Coroutines vs Arrow

| Feature | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Multi-phase orchestration** | Nested scopes, shuttle vars | Nested `parZip` blocks | Flat chain with `.then` |
| **Compile-time arg order safety** | No (positional) | No (named lambda) | **Typed function chain enforces order** |
| **Partial failure tolerance** | `supervisorScope` (manual) | Not built-in | **`.settled()`** |
| **Timeout + parallel fallback** | Sequential (wastes time) | Not built-in | **`timeoutRace`** — 2.6x faster |
| **Quorum (N-of-M)** | Manual `select` + counting | Not built-in | **`raceQuorum`** |
| **Success-only memoization** | Manual Mutex + cache | Not built-in | **`.memoizeOnSuccess()`** |
| **Parallel validation** | Cancels siblings on failure | `zipOrAccumulate` max 9 | `zipV` up to 22 |
| **Value-dependent phases** | Manual variable threading | Sequential `parZip` blocks | `.andThen` — dependency is the structure |
| **Retry + backoff** | Manual loop (~20 lines) | `Schedule` (similar) | `Schedule` + composable with chain |
| **Resource safety** | try/finally nesting | `Resource` monad | `bracket` / `Resource` — parallel use |
| **Racing** | Complex `select` | `raceN` (similar) | `raceN` + `raceEither` |
| **Bounded traversal** | Manual Semaphore | `parMap(concurrency)` | `traverse(concurrency)` |
| **Circuit breaker** | Manual state machine | Separate module | **Composable in chain** |
| **Flat multi-phase code** | No | No | **Yes** |

Bold = unique to KAP or significantly better.

---

## Feature Overview

| Feature | One-liner | Module |
|---|---|---|
| **[`.settled()`](#settled)** | One branch fails, the rest still complete | `kap-core` |
| **[`timeoutRace`](#timeoutrace)** | Fallback starts at t=0, not after timeout — 2.6x faster | `kap-resilience` |
| **[`raceQuorum`](#racequorum)** | N-of-M fastest successes, rest cancelled | `kap-resilience` |
| **[Compile-time safety](#compile-time-safety)** | Swap two `.with` lines → compiler error | `kap-core` |
| **[`memoizeOnSuccess`](#memoizeonsuccess)** | Cache results, NOT failures | `kap-core` |
| **[`raceN`](#racen)** | Fastest replica wins, losers cancelled | `kap-core` |
| **[`Schedule` + retry](#schedule--retry)** | Composable backoff policies | `kap-resilience` |
| **[`CircuitBreaker`](#circuitbreaker)** | Stop calling degraded services, auto-recover | `kap-resilience` |
| **[`bracket` / `Resource`](#resource-safety)** | Parallel resource use, guaranteed cleanup | `kap-resilience` |
| **[`traverse`](#traverse)** | Bounded parallel collection processing | `kap-core` |
| **[Parallel validation](#parallel-validation)** | Collect ALL errors, not just the first | `kap-arrow` |

### Feature Deep Dives

<details id="settled">
<summary><strong><code>.settled()</code> — Partial Failure Tolerance</strong></summary>

Your dashboard has three data sources. If the user service fails, you still want the cart and config — with a fallback. But structured concurrency cancels *all* siblings when *any* fails.

```kotlin
val dashboard = Async {
    kap { user: Result<String>, cart: String, config: String ->
        PartialDashboard(user.getOrDefault("anonymous"), cart, config)
    }
        .with(Kap { fetchUserMayFail() }.settled())
        .with { fetchCartAlways() }
        .with { fetchConfigAlways() }
}
// fetchUser fails? Dashboard still builds with "anonymous".
// fetchCart fails? Everything cancels (it's not settled).
```

For collections — `traverseSettled` processes ALL items, no cancellation:

```kotlin
val results: List<Result<String>> = Async {
    ids.traverseSettled { id -> Kap { fetchUser(id) } }
}
```

</details>

<details id="timeoutrace">
<summary><strong><code>timeoutRace</code> — Timeout with Parallel Fallback</strong></summary>

Your primary API has a timeout. Sequential approach *waits* the full timeout before starting the fallback. `timeoutRace` starts both immediately:

```kotlin
val result = Async {
    Kap { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Kap { fetchFromFallback() })
}
// Fallback starts at t=0. If primary wins before 100ms, use it.
// If primary times out, fallback is ALREADY RUNNING.
```

**JMH verified:** 34.0ms vs sequential 87.2ms — **2.6x faster**.

</details>

<details id="racequorum">
<summary><strong><code>raceQuorum</code> — N-of-M Successes</strong></summary>

3 database replicas. You need 2-of-3 to agree for consistency. Or hedged requests where you want the N fastest:

```kotlin
val quorum: List<String> = Async {
    raceQuorum(
        required = 2,
        Kap { fetchReplicaA() },
        Kap { fetchReplicaB() },
        Kap { fetchReplicaC() },
    )
}
// Returns the 2 fastest. Third cancelled. If 2+ fail, throws.
```

</details>

<details id="compile-time-safety">
<summary><strong>Compile-Time Argument Order Safety</strong></summary>

With raw coroutines, you pass 15 parameters by position. Swap two same-type values — silent bug. With KAP, the typed chain `(A) -> (B) -> ... -> Result` enforces each `.with` provides the right type:

```kotlin
Async {
    kap(::DashboardPage)
        .with { fetchProfile(userId) }       // returns UserProfile     — slot 1
        .with { fetchPreferences(userId) }   // returns UserPreferences — slot 2
        // Swap these two? COMPILE ERROR — expected LoyaltyTier, got UserPreferences
}
```

For same-type parameters, use value classes: `@JvmInline value class ValidName(val value: String)`

</details>

<details id="memoizeonsuccess">
<summary><strong><code>memoizeOnSuccess</code> — Cache Results, Not Failures</strong></summary>

Standard memoization caches failures too — a transient network error gets cached forever.

```kotlin
val fetchOnce = Kap { expensiveCall() }.memoizeOnSuccess()

val a = Async { fetchOnce }  // runs the actual fetch
val b = Async { fetchOnce }  // cached, instant
// First call FAILS? Not cached. Next call retries.
```

No manual `Mutex` + double-checked locking.

</details>

<details id="racen">
<summary><strong><code>raceN</code> — First to Succeed Wins</strong></summary>

3 regional replicas. Fastest response wins, losers cancelled immediately:

```kotlin
val fastest = Async {
    raceN(
        Kap { fetchFromRegionUS() },   // 100ms
        Kap { fetchFromRegionEU() },   // 30ms
        Kap { fetchFromRegionAP() },   // 60ms
    )
}
// Returns EU at 30ms. US and AP cancelled.
```

</details>

<details id="schedule--retry">
<summary><strong><code>Schedule</code> + Retry — Composable Backoff Policies</strong></summary>

```kotlin
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(10.milliseconds) and
    Schedule.doWhile<Throwable> { it is RuntimeException }

val result = Async {
    Kap { flakyService() }.retry(policy)
}
```

Building blocks: `times(n)`, `spaced(d)`, `exponential(base, max)`, `fibonacci(base)`, `linear(base)`, `forever()`, `.jittered()`, `.withMaxDuration(d)`, `.doWhile { }`. Combine with `and` (both agree) or `or` (either continues).

</details>

<details id="circuitbreaker">
<summary><strong><code>CircuitBreaker</code> — Protect Degraded Services</strong></summary>

Downstream service is degraded. Every request times out. Stop calling it after N failures, auto-recover later:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

val result = Async {
    Kap { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
        .recover { "cached-user" }
}
// timeout -> circuit breaker -> retry -> recover. All composable.
```

</details>

<details id="resource-safety">
<summary><strong><code>bracket</code> / <code>Resource</code> — Parallel Resource Safety</strong></summary>

Open three connections, use them in parallel, guarantee all three close — even on failure, even on cancellation:

```kotlin
val result = Async {
    kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .with(bracket(
            acquire = { openDb() },
            use = { conn -> Kap { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openCache() },
            use = { conn -> Kap { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openHttp() },
            use = { client -> Kap { client.get("/api") } },
            release = { client -> client.close() },
        ))
}
// All 3 in parallel. Any fails -> ALL released (NonCancellable context).
```

Or compose resources first: `Resource.zip(r1, r2, r3) { db, cache, http -> Triple(db, cache, http) }`

</details>

<details id="traverse">
<summary><strong><code>traverse</code> — Bounded Parallel Collection Processing</strong></summary>

200 user IDs. Downstream handles 10 concurrent requests max:

```kotlin
val results = Async {
    userIds.traverse(concurrency = 10) { id ->
        Kap { fetchUser(id) }
    }
}
```

</details>

<details id="parallel-validation">
<summary><strong>Parallel Validation — Collect Every Error</strong> (<code>kap-arrow</code>)</summary>

Registration form with multiple fields. Validate all in parallel, return *every* error at once:

```kotlin
val result: Either<NonEmptyList<RegError>, User> = Async {
    zipV(
        { validateName("Alice") },
        { validateEmail("alice@example.com") },
        { validateAge(25) },
        { checkUsername("alice") },
    ) { name, email, age, username -> User(name, email, age, username) }
}
// All pass? -> Either.Right(User(...))
// 3 fail?  -> Either.Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow))
```

`zipV` scales to 22 validators. Requires `kap-arrow` module.

</details>

---

## Modules

**Three modules, pick what you need:**

| Module | What you get | Depends on |
|---|---|---|
| **`kap-core`** | `Kap`, `with`, `kap`, `then`, `race`, `traverse`, `memoize`, `timeout`, `recover` | `kotlinx-coroutines-core` only |
| **`kap-resilience`** | `Schedule`, `CircuitBreaker`, `Resource`, `bracket`, `raceQuorum`, `timeoutRace` | `kap-core` |
| **`kap-arrow`** | `zipV`, `withV`, `validated {}`, `attempt()`, `raceEither`, `Either`/`Nel` bridges | `kap-core` + Arrow Core |

```
┌──────────────────────────────┐   ┌──────────────────────────────┐
│          kap-arrow           │   │       kap-resilience         │
│  zipV · withV · validated {} │   │  Schedule · CircuitBreaker   │
│  attempt() · raceEither      │   │  Resource · bracket          │
│  Either/Nel · accumulate {}  │   │  raceQuorum · timeoutRace    │
│  + Arrow Core (JVM only)     │   │  retry(schedule) · retryOrElse│
└──────────────┬───────────────┘   └──────────────┬───────────────┘
               │                                  │
               └──────────────┬───────────────────┘
                              │
┌─────────────────────────────┴───────────────────────────────┐
│                        kap-core                             │
│  Kap · with · then · andThen · kap · combine  │
│  race · traverse · memoize · timeout · recover · retry(n)   │
│  settled · catching · Deferred/Flow bridges                 │
│    depends on: kotlinx-coroutines-core (JVM, JS, Native)    │
└─────────────────────────────────────────────────────────────┘
```

**Adoption path:** Start with `kap-core`. It covers orchestration, race, traverse, memoize, settled, and basic retry — with zero dependencies beyond coroutines. Add `kap-resilience` when you need composable Schedule policies, CircuitBreaker, or Resource management. Add `kap-arrow` only if you're already using Arrow and want validated error accumulation.

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")

    // Optional
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.3.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.3.0")
}
```

---

## Production Ready

**Cancellation safety.** `CancellationException` is never caught by any KAP combinator. `recover`, `retry`, `catching`, `attempt` — all re-throw it. `bracket`/`guarantee` release in `NonCancellable` context. When any `.with` branch fails, all siblings cancel via standard `coroutineScope` behavior.

**Context propagation.** `Async(context)` propagates `CoroutineContext` into all parallel branches — MDC, OpenTelemetry, tracing:

```kotlin
val result = Async(MDCContext()) {
    kap(::Dashboard)
        .with { fetchUser() }      // MDC propagated
        .with { fetchCart() }      // MDC propagated
        .with { fetchPromos() }    // MDC propagated
}
```

**Observability.** Bring your own logger — no framework coupled:

```kotlin
val tracer = KapTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

val result = Async {
    kap(::Dashboard)
        .with { fetchUser().traced("user", tracer) }
        .with { fetchConfig().traced("config", tracer) }
}
```

**Composition guarantees.** `Kap` satisfies functor, applicative, and monad laws — property-based tested via Kotest. Refactoring with these combinators is provably safe. See [LAWS.md](LAWS.md).

---

## When to Use (and When Not To)

**KAP shines when:**
- You have 4+ concurrent operations with sequential phases (BFF, checkout, booking)
- You need parallel error accumulation across validators (add `kap-arrow`)
- The dependency graph should be visible in code shape
- You want compile-time parameter order safety
- You need composable retry/timeout/race policies (add `kap-resilience`)
- You want all of the above without pulling Arrow into your entire project

**Use something else when:**
- 2-3 simple parallel calls — `coroutineScope { async {} }` is enough
- Purely sequential code — regular `suspend` functions
- Stream processing — use `Flow`
- Full FP ecosystem (optics, typeclasses) — use Arrow directly

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, multi-service orchestration.

---

## Examples

Seven runnable examples in [`/examples`](examples/):

| Example | Modules | What it demonstrates |
|---|---|---|
| **[ecommerce-checkout](examples/ecommerce-checkout/)** | `kap-core` | 11 services, 5 phases — `kap`+`with`+`then` |
| **[dashboard-aggregator](examples/dashboard-aggregator/)** | `kap-core` | 14-service BFF — type-safe at 14 args |
| **[validated-registration](examples/validated-registration/)** | `kap-core` + `kap-arrow` | Parallel validation, error accumulation, phased validation |
| **[ktor-integration](examples/ktor-integration/)** | All three modules | Ktor HTTP BFF: aggregation, traverse, validation, retry/CB/bracket — 28 integration tests |
| **[resilient-fetcher](examples/resilient-fetcher/)** | `kap-core` + `kap-resilience` | `Schedule`, `CircuitBreaker`, `bracket`, `Resource.zip`, `timeoutRace`, `raceQuorum` |
| **[full-stack-order](examples/full-stack-order/)** | All three modules | Validated input + retry/CB/bracket + `attempt`/`raceEither` — complete pipeline |
| **[readme-examples](examples/readme-examples/)** | All three modules | Every code example from this README — compiled and verified on every CI push |

---

## Benchmarks

All claims backed by **119 JMH benchmarks** and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

> **Environment:** JDK 21, Ubuntu 24.04. JMH 1.37. [Live dashboard](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/).

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | **<0.01ms** |
| **Framework overhead** (arity 9) | <0.01ms | 0.03ms | **<0.01ms** |
| **Simple parallel** (5 x 50ms) | 50.27ms | 50.33ms | **50.31ms** |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | **180.98ms** |
| **Race** (50ms vs 100ms) | 100.34ms | 50.51ms | **50.40ms** |
| **timeoutRace** (primary wins) | 180.55ms | — | **30.34ms** |
| **Max validation arity** | — | 9 | **22** |

KAP overhead is indistinguishable from raw coroutines. It pulls ahead on `race` (auto-cancel loser), `timeoutRace` (parallel fallback), and validation arity.

<details>
<summary>Virtual-time proofs</summary>

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `then` true barrier | **110ms** | — | C waits for barrier |
| Bounded traverse (500 items, c=50) | **300ms** | 15,000ms | **50x** |
| kap (22 parallel branches) | **30ms** | 660ms | **22x** |

Sources: [`CoreBenchmark.kt`](benchmarks/src/jmh/kotlin/kap/benchmarks/CoreBenchmark.kt) | [`ResilienceBenchmark.kt`](benchmarks/src/jmh/kotlin/kap/benchmarks/ResilienceBenchmark.kt) | [`ArrowBenchmark.kt`](benchmarks/src/jmh/kotlin/kap/benchmarks/ArrowBenchmark.kt) | [`CoreComparisonTest.kt`](benchmarks/src/test/kotlin/kap/CoreComparisonTest.kt)

</details>

---

<details>
<summary><strong>API Reference</strong></summary>

### `kap-core` — Orchestration Primitives

| Combinator | Semantics | Parallelism |
|---|---|---|
| `kap` + `.with` | N-way fan-out (typed, safe ordering) | Parallel |
| `combine` | Lifting with suspend lambdas or Kaps | Parallel |
| `pair(fa, fb)` / `triple(fa, fb, fc)` | Parallel into Pair/Triple | Parallel |
| `.then` | True phase barrier | Sequential (gates) |
| `.thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `.andThen` | Value-dependent sequencing | Sequential |
| `map` / `Kap.of` / `Kap.empty` | Transform / wrap value | — |
| `Kap.failed(error)` / `Kap.defer { }` | Error / lazy construction | — |
| `.memoize()` / `.memoizeOnSuccess()` | Cache result (thread-safe) | — |
| `.ensure(error) { pred }` / `.ensureNotNull(error) { extract }` | Guards | — |
| `.on(context)` / `.named(name)` | Dispatcher / coroutine name | — |
| `.discard()` / `.peek { }` | Discard result / side-effect | — |
| `.await()` | Execute from any suspend context | — |
| `.settled()` | Wrap in `Result` (no sibling cancellation) | — |
| `.orElse(other)` / `firstSuccessOf(...)` | Fallback chains | Sequential |
| `computation { }` | Imperative builder with `.bind()` | Sequential |
| `.keepFirst` / `.keepSecond` | Parallel, keep one result | Parallel |

#### Collections

| Combinator | Semantics | Parallelism |
|---|---|---|
| `zip` (2-22) / `combine` (2-22) | Combine computations | Parallel |
| `traverse(f)` / `traverse(n, f)` | Map + parallel execution | Parallel (bounded) |
| `traverseDiscard(f)` | Fire-and-forget parallel | Parallel (bounded) |
| `sequence()` / `sequence(n)` | Execute collection | Parallel (bounded) |
| `traverseSettled(f)` / `traverseSettled(n, f)` | Collect ALL results (no cancellation) | Parallel (bounded) |
| `race` / `raceN` / `raceAll` | First to succeed | Competitive |

#### Interop & Observability

| Combinator | Semantics |
|---|---|
| `Deferred.toKap()` / `Kap.toDeferred(scope)` | Deferred bridge |
| `Flow.firstAsKap()` / `(suspend () -> A).toKap()` | Flow/lambda bridge |
| `Flow.mapEffect(concurrency) { }` / `Flow.mapEffectOrdered` | Flow + Kap pipeline |
| `catching { }` | Exception-safe `Result<A>` construction |
| `traced(name, tracer)` | Observability hooks |
| `delayed(d, value)` / `withOrNull` | Utilities |

### `kap-resilience` — Retry, Resources & Protection

| Combinator | Semantics |
|---|---|
| `timeoutRace(d, fallback)` | Parallel timeout (fallback starts immediately) |
| `retry(schedule)` / `retryOrElse(schedule, fallback)` | Schedule-based retry |
| `retryWithResult(schedule)` | Retry returning `RetryResult(value, attempts, totalDelay)` |
| `Schedule.times` / `.spaced` / `.exponential` / `.fibonacci` / `.linear` / `.forever` | Backoff strategies |
| `Schedule.doWhile` / `.doUntil` / `.jittered` / `.withMaxDuration` | Filters and limits |
| `s1 and s2` / `s1 or s2` | Schedule composition |
| `CircuitBreaker(maxFailures, resetTimeout)` | Protect downstream |
| `.withCircuitBreaker(breaker)` | Wrap computation with circuit breaker |
| `raceQuorum(required, c1, c2, ...)` | N-of-M quorum race |

#### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Guaranteed cleanup (NonCancellable) |
| `bracketCase(acquire, use, release)` | Cleanup with `ExitCase` (commit/rollback) |
| `guarantee` / `guaranteeCase` | Finalizers with optional `ExitCase` |
| `Resource(acquire, release)` | Composable resource |
| `Resource.zip(r1..r22, f)` | Combine up to 22 resources |
| `resource.use` / `resource.useKap` | Terminal operations |

### `kap-arrow` — Validation & Arrow Integration

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `kapV` + `withV` | Typed parallel validation |
| `thenV` / `andThenV` | Phase barriers / sequential short-circuit |
| `validated { }` / `accumulate { }` | Short-circuit builder with `.bindV()` |
| `valid` / `invalid` / `catching` | Entry points |
| `Validated<E, A>` (typealias) | Shorthand for `Kap<Either<NonEmptyList<E>, A>>` |
| `ensureV(error) { pred }` / `ensureVAll(errors) { pred }` | Validated guards |
| `recoverV` / `mapV` / `mapError` / `orThrow` | Transforms |
| `traverseV` / `sequenceV` | Collection operations with error accumulation |
| `.attempt()` | Catch to `Either<Throwable, A>` |
| `raceEither(fa, fb)` | First to succeed, different types |

</details>

---

## Building

```bash
./gradlew :kap-core:jvmTest          # core tests (438 tests) — start here
./gradlew :kap-resilience:jvmTest    # resilience tests (164 tests)
./gradlew :kap-arrow:test            # arrow integration tests (223 tests)
./gradlew jvmTest                    # all tests at once

./gradlew :examples:ecommerce-checkout:run   # run examples
./gradlew :benchmarks:jmh                    # JMH benchmarks
```

<details>
<summary>CI pipeline</summary>

```
Push / PR to master
├── validate          Gradle wrapper validation
├── test              4 parallel jobs: kap-core · kap-resilience · kap-arrow · benchmarks
├── compile-platforms JS + LinuxX64 compilation checks
├── codegen-check     Regenerate all codegen, fail if out-of-date
├── examples          Run all 7 examples + Ktor integration tests
├── benchmark         (push only) Full JMH -> store results in gh-pages
├── benchmark-pr      (PR only) Quick JMH -> compare against baseline
└── ci-gate           Aggregate status for branch protection

Release (tag vX.Y.Z)
├── test              Full test suite + multiplatform compilation + codegen check
├── publish           Maven Central (macOS runner for Apple targets)
└── verify            Wait for Maven Central sync -> run examples against real artifacts
```

[Benchmark dashboard](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/) — regression alerts on every push.

</details>

---

## License

Apache 2.0
