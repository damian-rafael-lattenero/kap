# Coroutines Applicatives

**Your code shape *is* the execution plan.** Declarative coroutine orchestration for Kotlin — parallel by default, sequential by intent.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.9.0-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![Tests](https://img.shields.io/badge/Tests-446%20passing-brightgreen.svg)](#empirical-data)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)

> **Type-safe, zero-overhead parallel composition for Kotlin coroutines.**
> - Compile-time parameter ordering via curried types — swap two `.ap` lines and the compiler rejects it
> - True phase barriers (`followedBy`) make execution phases visible in code shape
> - Parallel error accumulation (`zipV`) collects *every* failure, not just the first
> - One dependency (`kotlinx-coroutines-core`), ~2,100 lines, all platforms

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐
        .ap { fetchCart(userId) }           // ├─ phase 1: parallel
        .ap { fetchPromos(userId) }         // │
        .ap { fetchInventory(userId) }      // ┘
        .followedBy { validateStock() }     // ── phase 2: barrier
        .ap { calcShipping() }             // ┐
        .ap { calcTax() }                  // ├─ phase 3: parallel
        .ap { calcDiscounts() }            // ┘
        .followedBy { reservePayment() }   // ── phase 4: barrier
        .ap { generateConfirmation() }     // ┐ phase 5: parallel
        .ap { sendReceiptEmail() }         // ┘
}
```

**11 service calls. 5 phases. 12 lines. Swap any two `.ap` lines and the compiler rejects it.**

---

## What You Get

| Problem | Solution | Code |
|---|---|---|
| 15 parallel service calls | `lift15` + `.ap` chain | 17 lines, flat |
| Multi-phase orchestration | `.followedBy` barriers | 1 line per barrier |
| 12-field parallel validation | `zipV(12 validators)` | All errors accumulated |
| Retry + exponential + jitter | `Schedule` composition | 3 lines |
| Resource safety in parallel | `bracket` / `Resource` | Guaranteed cleanup |
| First-to-succeed racing | `raceN(...)` | Losers auto-cancelled |

---

## When to Use (and When Not To)

**Use this library when:**
- 4+ concurrent operations with sequential phases
- Error accumulation across parallel validators
- The dependency graph should be visible in code shape
- You want compile-time parameter order safety

**Don't use this library when:**
- 2-3 simple parallel calls — `coroutineScope { async {} }` is enough
- Purely sequential — regular `suspend` functions
- Stream processing — use `Flow`
- Full FP ecosystem (optics, typeclasses) — use Arrow

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, multi-service orchestration.

---

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives:1.0.0")
}
```

Only dependency: `kotlinx-coroutines-core`. ~2,100 lines total.

---

## Why This Exists

A typical BFF endpoint aggregates 10-15 microservices. Here's the same checkout flow with raw coroutines:

```kotlin
// Raw coroutines: 30+ lines, 11 shuttle variables, invisible phase boundaries
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart      = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user      = dUser.await()
    val cart       = dCart.await()
    val promos     = dPromos.await()
    val inventory  = dInventory.await()

    val stock = validateStock(inventory)       // barrier -- invisible without comments

    val dShipping  = async { calcShipping(cart) }
    val dTax       = async { calcTax(cart) }
    val dDiscounts = async { calcDiscounts(promos) }
    val shipping  = dShipping.await()
    val tax        = dTax.await()
    val discounts  = dDiscounts.await()

    val payment = reservePayment(user, cart)   // barrier -- also invisible

    val dConfirmation = async { generateConfirmation(payment) }
    val dEmail        = async { sendReceiptEmail(user) }

    CheckoutResult(user, cart, promos, inventory, stock,
                   shipping, tax, discounts, payment,
                   dConfirmation.await(), dEmail.await())
}
```

Move one `await()` above its `async` and you silently serialize. The compiler won't say a word.

**This library reduces that to a flat, type-safe chain where the structure *is* the execution plan.**

---

## The Model: Two Primitives

| Combinator | Semantics | Mental model |
|---|---|---|
| `.ap { }` | Launch in parallel | "and at the same time..." |
| `.followedBy { }` | Wait, then continue | "then, once that's done..." |

That's it. `ap` = parallel. `followedBy` = barrier. The code reads top-to-bottom as the execution timeline.

Two more for advanced use:

| Combinator | Semantics | When |
|---|---|---|
| `.flatMap { }` | Barrier + pass the value | Next phase *needs* the result |
| `.thenValue { }` | Sequential fill, no barrier | Overlap for max performance |

---

## Empirical Data

All claims backed by JMH benchmarks and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

### JMH Results (`./gradlew :benchmarks:jmh`)

**Simple parallel (5 calls @ 50ms):**

| Approach | ms/op | vs Sequential |
|---|---|---|
| Sequential baseline | 267.6 | 1x |
| Raw coroutines | 53.7 | 5x |
| Arrow (`parZip`) | 53.6 | 5x |
| **This library** | **53.8** | **5x** |

**Framework overhead (trivial compute, no I/O):**

| Approach | Arity 3 (ms) | Arity 9 (ms) |
|---|---|---|
| Raw coroutines | 0.001 | 0.001 |
| **This library** | **0.001** | **0.002** |
| Arrow (`parZip`) | 0.008 | 0.023 |

> **8x less overhead** than Arrow on trivial workloads. Negligible for real I/O.

**Multi-phase checkout (9 calls, 4 phases):**

| Approach | ms/op | Flat code? |
|---|---|---|
| Sequential baseline | 441.7 | Yes |
| Raw coroutines | 193.6 | No — nested blocks per phase |
| Arrow (`parZip`) | 195.5 | No — nested `parZip` per phase |
| **This library** | **194.6** | **Yes — single flat chain** |

Same wall-clock time. Only this library keeps the code flat regardless of phase count.

**Validation with error accumulation (4 validators @ 40ms):**

| Approach | ms/op | Collects all errors? | Parallel? |
|---|---|---|---|
| Sequential | 173.9 | Yes | No |
| Raw coroutines | N/A | No (structured concurrency cancels siblings) | Yes |
| Arrow (`zipOrAccumulate`) | 43.6 | Yes | Yes |
| **This library** (`zipV`) | **43.6** | **Yes** | **Yes** |

### Virtual-Time Proofs

Every concurrency property is verified with `runTest` + `currentTime` — deterministic, not flaky:

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier | **110ms** | — | C waits for barrier |
| Post-barrier aps launch in parallel | **90ms** | — | All launch together |
| `thenValue` no barrier | **80ms** | — | Overlaps |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | **3x** |
| Zero overhead vs raw coroutines | **0ms delta** | — | Identical |
| Mass cancellation (1 fail, 9 siblings) | — | — | All 9 cancelled |
| `timeoutRace` vs `timeout`+fallback | **50ms** vs 150ms | — | **3x** faster |
| `recoverV` inside `zipV` | **50ms** | — | No sibling cancellation |

### Algebraic Laws (property-based via Kotest)

Functor, applicative, and monad laws verified with random inputs:

- **Identity:** `map id == id`, `pure id <*> v == v`
- **Composition:** `map (g . f) == map g . map f`
- **Homomorphism:** `pure f <*> pure x == pure (f x)`
- **Associativity:** `(m >>= f) >>= g == m >>= (a -> f a >>= g)`

Source: [`ApplicativeLawsTest.kt`](src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt)

**446 tests across 26 suites. All passing.**

---

## Hero Examples

### 1. Multi-Phase Orchestration (11 Calls, 5 Phases)

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐ phase 1: fetch everything
        .ap { fetchCart(userId) }           // │  (4 calls, parallel)
        .ap { fetchPromos(userId) }         // │
        .ap { fetchInventory(userId) }      // ┘
        .followedBy { validateStock() }     // ── phase 2: validate
        .ap { calcShipping() }             // ┐ phase 3: pricing
        .ap { calcTax() }                  // │  (3 calls, parallel)
        .ap { calcDiscounts() }            // ┘
        .followedBy { reservePayment() }   // ── phase 4: payment
        .ap { generateConfirmation() }     // ┐ phase 5: wrap up
        .ap { sendReceiptEmail() }         // ┘  (2 calls, parallel)
}
```

The constructor `::CheckoutResult` fixes parameter order at compile time. Each service returns a distinct type (`UserProfile`, `ShoppingCart`, `ShippingQuote`...). Swap any two `.ap` lines? Compiler error.

### 2. 12-Field Parallel Validation with Error Accumulation

Run all validators in parallel. Collect *every* error, not just the first:

```kotlin
val onboarding = Async {
    zipV(
        { valFirstName(input.firstName) },
        { valLastName(input.lastName) },
        { valEmail(input.email) },
        { valPhone(input.phone) },
        { valPassword(input.password) },
        { valBirthDate(input.birthDate) },
        { valCountry(input.country) },
        { valCity(input.city) },
        { valZipCode(input.zipCode) },
        { valAddress(input.address) },
        { valTaxId(input.taxId) },
        { valTerms(input.acceptedTerms) },
    ) { fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm ->
        UserOnboarding(fn, ln, em, ph, pw, bd, co, ci, zc, ad, tx, tm)
    }
    // 7 of 12 fail → Left(Nel(FirstName(...), Email(...), ..., Terms(...)))
}
```

All 12 validators run in parallel. All errors accumulated. Scales to 22 fields with full type inference.

### 3. Phased Validation with Short-Circuit

Phase 1 validates in parallel. Phase 2 only runs if phase 1 passes:

```kotlin
val result = Async {
    zipV(
        { valFirstName(input.firstName) },
        { valLastName(input.lastName) },
        { valEmail(input.email) },
    ) { fn, ln, em -> IdentityInfo(fn, ln, em) }
    .flatMapV { identity ->
        zipV(
            { checkUsernameAvailable(identity.email) },
            { checkNotBlacklisted(identity) },
        ) { username, cleared -> Registration(identity, username, cleared) }
    }
}
// Phase 1 fails → errors returned, phase 2 never runs (saves network calls)
```

### 4. Value-Dependent Phases with `flatMap`

When a later phase *needs* the result of an earlier phase:

```kotlin
val dashboard = Async {
    lift4(::UserContext)
        .ap { fetchProfile(userId) }       // ┐ phase 1
        .ap { fetchPreferences(userId) }   // │
        .ap { fetchLoyaltyTier(userId) }   // │
        .ap { fetchRecentOrders(userId) }  // ┘
    .flatMap { ctx ->                      // ── phase 2: NEEDS ctx
        lift3(::PersonalizedDashboard)
            .ap { fetchRecommendations(ctx.profile) }   // ┐ parallel
            .ap { fetchPromotions(ctx.loyalty) }         // │
            .ap { fetchTrending(ctx.preferences) }       // ┘
    }
}
```

`followedBy` = ordering barrier. `flatMap` = value dependency.

### 5. Production Resilience: Compose Everything

Every combinator (`timeout`, `retry`, `recover`, `race`) composes inside `ap` branches:

```kotlin
val result = Async {
    lift4(::CheckoutResult)
        .ap { fetchUser() }
        .ap { flakyService().retry(3, delay = 10.milliseconds) }
        .ap { slowService().timeout(40.milliseconds, "cached") }
        .ap { race(primaryAPI(), cacheAPI()) }
}
// All 4 branches run in parallel. Each handles its own resilience.
// Total wall time = max(branch times). Verified: 40ms virtual time.
```

Stack them for defense in depth:

```kotlin
Computation { fetchUser() }
    .timeout(500.milliseconds)
    .retry(
        maxAttempts = 3,
        delay = 100.milliseconds,
        backoff = exponential,
        shouldRetry = { it is IOException },
        onRetry = { attempt, err, _ -> log("retry #$attempt: $err") },
    )
    .recover { UserProfile.cached() }
    .traced("fetchUser", tracer)
```

### 6. Composable Retry Policies with `Schedule`

Build retry policies from composable pieces — recurrence limits, backoff strategies, error filters, and jitter:

```kotlin
val policy = Schedule.recurs<Throwable>(5) and
    Schedule.exponential(100.milliseconds, max = 10.seconds) and
    Schedule.doWhile { it is IOException }

Computation { fetchUser() }.retry(policy)
```

`and` = both must agree to continue (uses max delay). `or` = either can continue (uses min delay).

Add jitter to prevent thundering herd:

```kotlin
val withJitter = Schedule.recurs<Throwable>(5) and
    Schedule.exponential(100.milliseconds).jittered()

// Delays: ~100ms, ~200ms, ~400ms... each ±50% random spread
```

Or use fibonacci backoff for gentler ramp-up:

```kotlin
val gentle = Schedule.recurs<Throwable>(8) and
    Schedule.fibonacci(50.milliseconds, max = 5.seconds)

// Delays: 50ms, 50ms, 100ms, 150ms, 250ms, 400ms, 650ms, 1050ms...
```

### 7. Resource Safety with `bracket`

Acquire resources, use them in parallel, guarantee release even on failure:

```kotlin
val result = Async {
    lift3 { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .ap(bracket(
            acquire = { openDbConnection() },
            use = { conn -> Computation { conn.query("SELECT ...") } },
            release = { conn -> conn.close() },  // runs even if siblings fail
        ))
        .ap(bracket(
            acquire = { openCacheConnection() },
            use = { conn -> Computation { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .ap(bracket(
            acquire = { openHttpClient() },
            use = { client -> Computation { client.get("/api") } },
            release = { client -> client.close() },
        ))
}
// All 3 resources acquired and used in parallel.
// If any branch fails, ALL resources released (NonCancellable).
// Verified: BracketTest proves parallel release on sibling failure.
```

### 8. Racing: First to Succeed Wins

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response. Others cancelled. All fail → throws with suppressed.
```

### 9. Composable Resources with `Resource` Monad

```kotlin
val infra = Resource.zip(
    Resource({ openDb() }, { it.close() }),
    Resource({ openCache() }, { it.close() }),
    Resource({ openHttpClient() }, { it.close() }),
) { db, cache, http -> Triple(db, cache, http) }

val result = Async {
    infra.useComputation { (db, cache, http) ->
        lift3(::DashboardData)
            .ap { Computation { db.query("SELECT ...") } }
            .ap { Computation { cache.get("user:prefs") } }
            .ap { Computation { http.get("/recommendations") } }
    }
}
// All 3 released in reverse order, even on failure. NonCancellable.
```

---

## Type Safety via Currying

With raw coroutines, you pass 15 variables to a constructor by position. Hope you got the order right.

With this library, the curried type chain enforces it:

```kotlin
data class DashboardPage(
    val profile: UserProfile,
    val preferences: UserPreferences,
    val loyaltyTier: LoyaltyTier,
    // ... 12 more fields, each a different type
)

Async {
    lift15(::DashboardPage)
        .ap { fetchProfile(userId) }       // returns UserProfile     -- slot 1
        .ap { fetchPreferences(userId) }   // returns UserPreferences -- slot 2

    // Swap any two lines?
    //  .ap { fetchLoyaltyTier(userId) }   // returns LoyaltyTier -- expected UserPreferences
    //  .ap { fetchPreferences(userId) }   // COMPILE ERROR ✗
}
```

For parameters of the same type, use value classes:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
// Now swap-safe — compiler enforces the order
```

---

## Execution Model

### Laziness

`Computation` is a **description**, not an execution. Nothing runs until `Async {}`:

```kotlin
val graph = lift3(::build)
    .ap { fetchUser() }    // NOT executed
    .ap { fetchCart() }    // NOT executed

val result = Async { graph }  // NOW everything runs
```

The same graph can be executed multiple times.

### `followedBy`: True Phase Barrier

```
lift4(::Result)
    .ap { delay(30); A }             // ┐ launched at t=0
    .ap { delay(30); B }             // ┘
    .followedBy { delay(50); C }     // ── barrier: t=30 → t=80
    .ap { delay(30); D }             // ── launched at t=80, done at t=110
// Total: 110ms. D waits for the barrier.
```

### `thenValue`: No Barrier (Eager Launch)

```
lift4(::Result)
    .ap { delay(30); A }             // launched at t=0
    .ap { delay(30); B }             // launched at t=0
    .thenValue { delay(50); C }      // sequential value, but...
    .ap { delay(30); D }             // launched at t=0 (overlaps!)
// Total: 80ms. D launched eagerly.
```

### Decision Table

| Scenario | Use | Subsequent `ap` behavior |
|---|---|---|
| Independent, no values needed | `followedBy` | Gated — waits |
| Needs the value | `flatMap` | Gated — waits, passes value |
| Should overlap for performance | `thenValue` | Ungated — launches at t=0 |

---

## Observability

Instrument any computation with lifecycle hooks:

```kotlin
val tracer = ComputationTracer { event ->
    when (event) {
        is TraceEvent.Started -> logger.info("${event.name} started")
        is TraceEvent.Succeeded -> metrics.timer(event.name).record(event.duration)
        is TraceEvent.Failed -> logger.error("${event.name} failed", event.error)
    }
}

val result = Async {
    lift3(::Dashboard)
        .ap { fetchUser().traced("user", tracer) }
        .ap { fetchConfig().traced("config", tracer) }
        .ap { fetchCart().traced("cart", tracer) }
}
```

No logging framework coupled. Bring your own.

---

## Comparison: Raw Coroutines vs Arrow vs This Library

| | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **15 parallel calls** | 30+ lines, shuttle vars | `parZip` caps at 5 | `lift15` + 15 `.ap` — flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `followedBy` — visible |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Not possible in parallel | `zipOrAccumulate` (caps at 9) | `zipV` up to 22 |
| **Arg order safety** | None — positional | Named args in lambda | Compile-time via currying |
| **Code size** | stdlib | ~50k+ lines | **~2,100 lines** |
| **Dependencies** | stdlib | Arrow Core + modules | `kotlinx-coroutines-core` only |
| **JMH overhead** | 0.001ms | 0.008–0.023ms | **0.001–0.002ms** |
| **Platforms** | JVM, JS, Native | JVM, JS, Native | JVM, JS, Native |

For 2-3 simple parallel calls, raw coroutines are fine. The difference shows at scale.

> All three approaches tested side-by-side: [`ThreeWayComparisonTest.kt`](src/jvmTest/kotlin/applicative/ThreeWayComparisonTest.kt)
> JMH benchmarks: [`OrchestrationBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/OrchestrationBenchmark.kt)

---

## Structured Concurrency

All parallelism is scoped via `coroutineScope`. If any computation fails:

- Sibling coroutines are automatically cancelled (verified: 1 failure cancels 9 siblings)
- The exception propagates to the `Async {}` call site
- `CancellationException` is never caught — structured concurrency always respected
- No resource leaks

---

## Full API Reference

### Core

| Combinator | Semantics | Parallelism |
|---|---|---|
| `lift2`..`lift22` + `ap` | N-way fan-out | Parallel |
| `followedBy` | True phase barrier | Sequential (gates) |
| `thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `flatMap` | Monadic bind (value-dependent) | Sequential |
| `map` | Transform result | — |
| `zip` / `zip` (3-5 arity) / `mapN` | Combine computations | Parallel |
| `traverse` / `traverse(n)` | Map over collection | Parallel (optionally bounded) |
| `sequence` / `sequence(n)` | Execute computation list | Parallel (optionally bounded) |
| `parMap` / `parMap(n)` | Alias for `traverse` | Parallel |
| `race` / `raceN` / `raceAll` | First to succeed wins | Competitive |
| `pure` / `unit` | Wrap value / Unit | — |
| `on` | Switch dispatcher | — |
| `named` | Set `CoroutineName` | — |
| `context` | Read `CoroutineContext` | — |

### Error Handling & Resilience

| Combinator | Semantics |
|---|---|
| `recover { }` | Catch exceptions, return fallback value |
| `recoverWith { }` | Catch exceptions, switch to fallback computation |
| `fallback(other)` | On failure, run alternative |
| `timeout(duration)` | Fail if too slow |
| `timeout(duration, default)` | Default value if too slow |
| `timeout(duration, fallback)` | Fallback computation if too slow |
| `timeoutRace(duration, fallback)` | Parallel timeout — fallback starts immediately |
| `retry(n, delay, backoff, shouldRetry, onRetry)` | Configurable retry with selective filter |
| `retry(schedule)` | Retry with composable `Schedule` policy |
| `exponential` / `exponential(max)` | Backoff strategies |

### Schedule (Composable Retry Policies)

| Factory / Combinator | Semantics |
|---|---|
| `Schedule.recurs(n)` | Retry up to n times |
| `Schedule.spaced(duration)` | Fixed delay between attempts |
| `Schedule.exponential(base, factor, max)` | Exponential backoff |
| `Schedule.fibonacci(base, max)` | Fibonacci backoff |
| `Schedule.doWhile { predicate }` | Continue while predicate holds |
| `schedule.jittered(factor)` | Add random jitter (default ±50%) |
| `s1 and s2` | Both must agree (max delay) |
| `s1 or s2` | Either can continue (min delay) |

### Validation (Error Accumulation)

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `liftV2`..`liftV22` + `apV` | Curried parallel validation |
| `followedByV` | Phase barrier for validation |
| `flatMapV` | Sequential, short-circuits on error |
| `valid(a)` / `invalid(e)` | Wrap success/error |
| `catching { toError }` | Bridge exceptions to validation errors |
| `validate { predicate }` | Predicate-based validation |
| `recoverV` | Prevent sibling cancellation on exception |
| `mapV` / `mapError` | Transform success/error |
| `traverseV` / `sequenceV` | Parallel traverse with accumulation |
| `orThrow()` | Unwrap or throw `ValidationException` |

### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Guaranteed cleanup (NonCancellable release) |
| `guarantee(finalizer)` | Unconditional finalizer |
| `guaranteeCase(finalizer)` | Finalizer with `ExitCase` (Completed/Failed/Cancelled) |
| `Resource(acquire, release)` | Composable resource with `map`/`flatMap`/`zip` |
| `Resource.zip(r1, r2, ..., f)` | Combine up to 4 resources |
| `resource.use { }` | Terminal: acquire, use, release |
| `resource.useComputation { }` | Terminal: integrate with `ap` chains |

### Observability

| Combinator | Semantics |
|---|---|
| `traced(name, onStart, onSuccess, onError)` | Lifecycle hooks |
| `traced(name, tracer)` | Structured `ComputationTracer` |

### Interop

| Combinator | Semantics |
|---|---|
| `Deferred.toComputation()` | Wrap existing Deferred |
| `Computation.toDeferred(scope)` | Start eagerly |
| `Flow.firstAsComputation()` | First emission |
| `(suspend () -> A).toComputation()` | Wrap suspend lambda |
| `catching { }` | Exception-safe -> `Result<A>` |
| `apOrNull` | Handle nullable computations |
| `Result.toEither()` / `Either.toResult()` | Kotlin Result bridge |
| `Result.toValidated(onError)` | Result to validated |
| `delayed(duration, value)` | Wait then return |

### Either

| Combinator | Semantics |
|---|---|
| `map` / `flatMap` / `mapLeft` | Transform |
| `fold` / `getOrElse` / `getOrNull` | Extract |
| `swap` / `bimap` / `merge` | Structural |
| `onLeft` / `onRight` | Side-effects |
| `isLeft` / `isRight` | Predicates |

---

## Examples

Full working examples in the [`/examples`](examples/) directory:

| Example | Description |
|---|---|
| [`ecommerce-checkout`](examples/ecommerce-checkout/) | 11-service, 5-phase checkout orchestration |
| [`dashboard-aggregator`](examples/dashboard-aggregator/) | Multi-service dashboard with parallel data sources |
| [`validated-registration`](examples/validated-registration/) | 12-field parallel validation with error accumulation |

---

## Arrow Interop

Optional module for teams already using Arrow:

```kotlin
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives-arrow:1.0.0")
}
```

```kotlin
import applicative.arrow.*

// Arrow Either <-> Applicative Either
val appEither = arrowEither.toApplicativeEither()
val backToArrow = appEither.toArrowEither()

// Arrow NonEmptyList <-> Applicative Nel
val appNel = arrowNel.toApplicativeNel()

// Bridge Arrow's parZip into a Computation chain
val phase = fromArrow { parZip({ fetchUser() }, { fetchCart() }) { u, c -> u to c } }
```

---

## Building

```bash
./gradlew jvmTest              # run 407 tests
./gradlew :arrow-interop:test  # Arrow interop tests
./gradlew :benchmarks:jmh      # JMH benchmarks
./gradlew dokkaHtml             # API docs
./gradlew generateAll           # regenerate curry/lift/validated overloads (arities 2-22)
```

## Publishing

See [PUBLISHING.md](PUBLISHING.md) for Maven Central publishing instructions.

## License

Apache 2.0
