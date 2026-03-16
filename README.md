# Coroutines Applicatives

**Declarative coroutine orchestration for Kotlin.** The shape of your code *is* the execution plan.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.9.0-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)

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

11 service calls. 5 phases. 12 lines. **Swap any two `.ap` lines and the compiler rejects it.**

`ap` = parallel. `followedBy` = wait. That's the entire model.

---

## Quick Start

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives:1.0.0")
}
```

```kotlin
import applicative.*

// Three parallel calls, one result
data class Dashboard(val user: String, val cart: String, val promos: String)

suspend fun main() {
    val result = Async {
        lift3(::Dashboard)
            .ap { fetchUser() }    // parallel
            .ap { fetchCart() }    // parallel
            .ap { fetchPromos() }  // parallel
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

That's it. Three calls run in parallel. The result type is enforced at compile time.

---

## The Problem

A real BFF endpoint aggregates 10-15 microservices per request. With raw coroutines:

```kotlin
// 11 calls, 5 phases — 30+ lines of plumbing
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart      = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user      = dUser.await()
    val cart      = dCart.await()
    val promos    = dPromos.await()
    val inventory = dInventory.await()

    val stock = validateStock(inventory)       // barrier — invisible without comments

    val dShipping  = async { calcShipping(cart) }
    val dTax       = async { calcTax(cart) }
    val dDiscounts = async { calcDiscounts(promos) }
    val shipping  = dShipping.await()
    val tax       = dTax.await()
    val discounts = dDiscounts.await()

    val payment = reservePayment(user, cart)   // barrier — also invisible

    val dConfirmation = async { generateConfirmation(payment) }
    val dEmail        = async { sendReceiptEmail(user) }

    CheckoutResult(user, cart, promos, inventory, stock,
                   shipping, tax, discounts, payment,
                   dConfirmation.await(), dEmail.await())
}
```

30+ lines. 11 shuttle variables. Phase boundaries invisible without comments. Move one `await()` above its `async` and you silently serialize -- the compiler won't say a word.

---

## Four Primitives

| Combinator | What it does | When to use |
|---|---|---|
| `ap` | Launches right side as `async` in parallel | Independent work |
| `followedBy` | **True phase barrier** -- subsequent `ap` calls wait | Phase boundaries (post-barrier work is independent) |
| `flatMap` | True dependency -- right side constructed from left's value | Phase boundaries (post-barrier work needs the result) |
| `thenValue` | Sequential value fill, no barrier -- subsequent `ap` still launch eagerly | Overlap for performance when post-barrier work is independent |

`lift` curries your constructor. Each `.ap` fills one slot in parallel. `followedBy` inserts a real barrier. The chain reads top-to-bottom as the execution plan -- what you see is what runs.

---

## Empirical Data

All claims verified with JMH benchmarks and deterministic virtual-time tests. No flaky timing assertions -- `runTest` + `currentTime` gives provably correct results.

### Virtual-Time Proofs

These tests use `kotlinx.coroutines.test.runTest` with `currentTime` assertions:

| Test | Virtual time | Sequential would be | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier (A,B + barrier + C) | **110ms** | -- | C waits for barrier |
| 3 post-barrier aps launch in parallel | **90ms** | -- | All launch together |
| `thenValue` no barrier (eager launch) | **80ms** | -- | C overlaps |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | 3x |
| Library vs raw coroutines overhead | **0ms delta** | -- | Zero overhead |
| `flatMap` true boundary | **80ms** | -- | Post-flatMap waits |
| Mass cancellation (1 fail, 9 siblings) | -- | -- | All 9 cancelled |
| Full production pattern (timeout+retry+recover+race) | **40ms** | -- | All compose |
| `bracket` parallel release on sibling failure | -- | -- | All 3 resources released |
| `timeoutRace` vs `timeout` + fallback | **50ms vs 150ms** | -- | 3x faster fallback |
| `retry` with `shouldRetry` filter | **20ms** | -- | Non-retryable exits immediately |
| `zip3`/`zip4`/`zip5` parallel | **30-50ms** | 90-250ms | N-way parallel |
| `recoverV` inside `zipV` — siblings not cancelled | **50ms** | -- | All errors accumulated |

Source: [`ConcurrencyProofTest.kt`](src/jvmTest/kotlin/applicative/ConcurrencyProofTest.kt), [`CompositionProofTest.kt`](src/jvmTest/kotlin/applicative/CompositionProofTest.kt), [`BracketTest.kt`](src/jvmTest/kotlin/applicative/BracketTest.kt), [`EnhancedCombinatorsTest.kt`](src/jvmTest/kotlin/applicative/EnhancedCombinatorsTest.kt), [`RecoverVTest.kt`](src/jvmTest/kotlin/applicative/RecoverVTest.kt), [`CollectionsExtTest.kt`](src/jvmTest/kotlin/applicative/CollectionsExtTest.kt).

**407 tests across 23 suites.** All passing.

### JMH Benchmarks (`./gradlew :benchmarks:jmh`)

**Simple parallel (5 calls, 50ms each):**

| Approach | Avg (ms/op) | Speedup |
|---|---|---|
| Sequential baseline | 267.6 | 1x |
| Raw coroutines | 53.7 | 5x |
| Arrow (`parZip`) | 53.6 | 5x |
| **This library** | 53.8 | **5x** |

All three parallel approaches perform identically. The difference is ergonomics.

**Framework overhead (no delay, trivial compute):**

| Approach | Arity 3 (ms) | Arity 9 (ms) |
|---|---|---|
| Raw coroutines | 0.001 | 0.001 |
| **This library** | 0.001 | 0.002 |
| Arrow (`parZip`) | 0.008 | 0.023 |

This library has **8x less overhead** than Arrow for trivial workloads. Negligible for I/O-bound work.

**Multi-phase checkout (9 calls, 4 phases):**

| Approach | Avg (ms/op) | Flat code? |
|---|---|---|
| Sequential baseline | 441.7 | Yes |
| Raw coroutines | 193.6 | No -- nested blocks per phase |
| Arrow (`parZip`) | 195.5 | No -- nested `parZip` per phase |
| **This library** | 194.6 | **Yes -- single flat chain** |

Same performance. This library keeps the code flat regardless of phase count.

**Validation with error accumulation (4 validators, 40ms each):**

| Approach | Avg (ms/op) | All errors? | Parallel? |
|---|---|---|---|
| Sequential | 173.9 | Yes | No |
| Raw coroutines | N/A | No -- structured concurrency cancels siblings on first failure | Yes |
| Arrow (`zipOrAccumulate`) | 43.6 | Yes | Yes |
| **This library** (`zipV`) | 43.6 | **Yes** | **Yes** |

### Algebraic Laws (property-based via Kotest)

The test suite verifies functor, applicative, and monad laws with random inputs:

- **Identity:** `map id == id`, `pure id <*> v == v`
- **Composition:** `map (g . f) == map g . map f`
- **Homomorphism:** `pure f <*> pure x == pure (f x)`
- **Associativity:** `(m >>= f) >>= g == m >>= (a -> f a >>= g)`

Source: [`ApplicativeLawsTest.kt`](src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt).

---

## Hero Examples

### 1. Multi-Phase Orchestration (11 calls, 5 phases)

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐ phase 1: fetch everything
        .ap { fetchCart(userId) }           // │  (4 calls, parallel)
        .ap { fetchPromos(userId) }         // │
        .ap { fetchInventory(userId) }      // ┘
        .followedBy { validateStock() }     // ── phase 2: validate before pricing
        .ap { calcShipping() }             // ┐ phase 3: calculate costs
        .ap { calcTax() }                  // │  (3 calls, parallel)
        .ap { calcDiscounts() }            // ┘
        .followedBy { reservePayment() }   // ── phase 4: payment last
        .ap { generateConfirmation() }     // ┐ phase 5: wrap up
        .ap { sendReceiptEmail() }         // ┘  (2 calls, parallel)
}
```

Each service returns a distinct type (`UserProfile`, `ShoppingCart`, `ShippingQuote`...). The constructor `::CheckoutResult` fixes parameter order at compile time.

### 2. Value-Dependent Phases with `flatMap`

When a later phase needs the *result* of an earlier phase:

```kotlin
val dashboard = Async {
    lift4(::UserContext)
        .ap { fetchProfile(userId) }       // ┐ phase 1: all parallel
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

`followedBy` = ordering barrier (post-barrier work is independent).
`flatMap` = value dependency (post-barrier work needs the result).

### 3. Parallel Validation with Error Accumulation

Run all validators in parallel. Collect *every* error, not just the first:

```kotlin
val result = Async {
    zipV(
        { validatePassport(input) },   // ┐ parallel -- all errors
        { validateSeat(seat) },        // ├─ accumulated if
        { validatePayment(card) },     // ┘  multiple fail
    ) { passport, seat, payment -> BookingResult(passport, seat, payment) }
}
// passport AND seat fail → Either.Left(Nel(InvalidPassport(...), SeatUnavailable(...)))
// All pass              → Either.Right(BookingResult(...))
```

Scales to 22 fields with zero type annotations:

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
    // 7 of 12 fail → Either.Left(Nel(FirstName(...), Email(...), ..., Terms(...)))
    // All pass      → Either.Right(UserOnboarding(...))
}
```

### 4. Phased Validation with Short-Circuit

Phase 1 validates fields in parallel. Phase 2 only runs if phase 1 passes:

```kotlin
val result = Async {
    zipV(
        { valFirstName(input.firstName) },
        { valLastName(input.lastName) },
        { valEmail(input.email) },
    ) { fn, ln, em -> IdentityInfo(fn, ln, em) }
    .flatMapV { identity ->
        // Only runs if phase 1 passed -- short-circuits on error
        zipV(
            { checkUsernameAvailable(identity.email) },
            { checkNotBlacklisted(identity) },
        ) { username, cleared -> Registration(identity, username, cleared) }
    }
}
// Phase 1 fails → errors returned immediately, phase 2 never runs (saves network calls)
// Both pass     → Either.Right(Registration(...))
```

### 5. Production Resilience: Combinators Inside `ap`

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
// Total wall time = max(branch times). Verified: 40ms (CompositionProofTest).
```

Stack them for defense in depth — with selective retry and attempt logging:

```kotlin
Computation { fetchUser() }
    .timeout(500.milliseconds)
    .retry(
        maxAttempts = 3,
        delay = 100.milliseconds,
        backoff = exponential,
        shouldRetry = { it is IOException },                     // only retry network errors
        onRetry = { attempt, err, _ -> log("retry #$attempt: $err") }, // observability
    )
    .recover { UserProfile.cached() }
    .traced("fetchUser", tracer)
```

### 6. Resource Safety with `bracket`

Acquire a resource, use it in parallel branches, guarantee release even on failure or cancellation:

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
// All 3 resources acquired/released in parallel.
// If any branch fails, ALL resources are released (NonCancellable).
// Verified: BracketTest — barrier-proven parallel release on sibling failure.
```

For simpler cases, `guarantee` and `guaranteeCase`:

```kotlin
Computation { fetchUser() }
    .guarantee { releaseConnection() }          // always runs

Computation { fetchUser() }
    .guaranteeCase { case ->
        when (case) {
            is ExitCase.Completed -> metrics.success()
            is ExitCase.Failed    -> metrics.error(case.error)
            is ExitCase.Cancelled -> metrics.cancelled()
        }
    }
```

### 7. Racing: First to Succeed Wins

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response (first to complete). Others cancelled.
// If EU fails, US and AP continue. Only when ALL fail does it throw.
```

---

## Why Not Raw Coroutines or Arrow?

| | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **15 parallel calls** | 30+ lines, shuttle vars | `parZip` caps at 9 -- nest for more | `lift15` + 15 `.ap` -- flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `followedBy` barriers -- visible |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Not possible in parallel (structured concurrency cancels siblings) | `zipOrAccumulate` (caps at 9) | `zipV` up to 22 |
| **Type-safe arg order** | No -- positional constructor args | Named args in lambda | Compile-time via currying |
| **Code size** | stdlib | Full FP framework (~50k+ lines) | **~2,100 lines** |
| **Dependencies** | stdlib | Arrow Core + modules | `kotlinx-coroutines-core` only |
| **Platforms** | JVM, JS, Native | JVM, JS, Native | JVM, JS, Native |

For simple fan-out (2-3 calls), all three are fine. The difference shows at scale.

> All three approaches are tested side-by-side in [`ThreeWayComparisonTest.kt`](src/jvmTest/kotlin/applicative/ThreeWayComparisonTest.kt)
> and benchmarked with JMH in [`OrchestrationBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/OrchestrationBenchmark.kt).

---

## Execution Model

### Laziness

`Computation` is a **description**, not an execution. Nothing runs until `Async {}`:

```kotlin
val graph = lift3(::build)
    .ap { fetchUser() }    // NOT executed yet
    .ap { fetchCart() }    // NOT executed yet

// ... later ...
val result = Async { graph }  // NOW everything runs
```

The same graph can be executed multiple times, producing fresh results each time.

### `followedBy`: True Phase Barrier

`followedBy` creates a real phase boundary. `ap` calls after a barrier **do not launch** until the barrier completes:

```kotlin
lift4(::Result)
    .ap { delay(30); fetchA() }         // ┐ phase 1: launched at t=0
    .ap { delay(30); fetchB() }         // ┘
    .followedBy { delay(50); barrier }  // ── barrier: starts at t=30, ends at t=80
    .ap { delay(30); fetchC() }         // ── phase 2: launched at t=80, done at t=110
// Total: 110ms. C waits for the barrier. The code means what it says.
```

### `thenValue`: Sequential Fill, No Barrier

When post-barrier work is truly independent and should overlap for performance:

```kotlin
lift4(::Result)
    .ap { delay(30); fetchA() }         // launched at t=0
    .ap { delay(30); fetchB() }         // launched at t=0
    .thenValue { delay(50); enrich() }  // sequential value, but...
    .ap { delay(30); fetchC() }         // launched at t=0 (overlaps!)
// Total: 80ms. C launched eagerly -- no barrier.
```

### `flatMap`: Value Dependency

`flatMap` is like `followedBy` but passes the result -- use it when the next phase *needs* the value:

```kotlin
Computation { delay(30); fetchBase() }
    .flatMap { base ->
        lift2(::Result)
            .ap { delay(50); transform(base) }   // launched at t=30
            .ap { delay(50); enrich(base) }       // launched at t=30
    }
// Total: 30 + 50 = 80ms. flatMap enforced the dependency.
```

### Decision Table

| Scenario | Use | Effect on subsequent `ap` |
|---|---|---|
| Post-barrier work is **independent, no values needed** | `followedBy` | Gated -- waits for barrier |
| Post-barrier work **needs the value** | `flatMap` | Gated -- waits and passes value |
| Post-barrier work should **overlap** for performance | `thenValue` | Ungated -- launches eagerly at t=0 |

---

## Type Safety

With raw coroutines, you pass 15 variables to a constructor by position -- hope you got the order right. With this library, the curried type chain enforces it:

```kotlin
data class DashboardPage(
    val profile: UserProfile,
    val preferences: UserPreferences,
    val loyaltyTier: LoyaltyTier,
    // ... 12 more fields, each a different type
)

Async {
    lift15(::DashboardPage)
        .ap { fetchProfile(userId) }       // returns UserProfile     -- slot 1 ok
        .ap { fetchPreferences(userId) }   // returns UserPreferences -- slot 2 ok

    // Swap any two lines?
    //  .ap { fetchLoyaltyTier(userId) }   // returns LoyaltyTier -- expected UserPreferences
    //  .ap { fetchPreferences(userId) }   // COMPILE ERROR
}
```

If you have multiple parameters of the same type, wrap them in value classes -- a Kotlin best practice independent of this library:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
// Now swap-safe -- the compiler enforces the order
```

---

## Observability

Instrument any computation with lifecycle hooks -- no logging framework coupled:

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

---

## Full API Reference

### Core

| Combinator | Semantics | Parallelism |
|---|---|---|
| `lift2`..`lift22` + `ap` | N-way fan-out | Parallel |
| `followedBy` | True phase barrier -- gates subsequent `ap` | Sequential |
| `thenValue` | Sequential value fill -- no barrier | Sequential (no gate) |
| `flatMap` | Monadic bind (value-dependent) | Sequential |
| `map` | Transform result | -- |
| `zip` | Combine two computations | Parallel |
| `traverse` / `traverse(n)` | Map over collection (optionally bounded) | Parallel |
| `sequence` / `sequence(n)` | Execute computation list (optionally bounded) | Parallel |
| `zip` (3-5 arity) / `mapN` | N-way parallel combination | Parallel |
| `race` / `raceN` | First to succeed wins | Competitive |
| `pure` / `unit` | Wrap a value / Unit | -- |
| `on` | Switch dispatcher | -- |
| `named` | Set `CoroutineName` for debugging | -- |
| `context` | Read `CoroutineContext` | -- |
| `parMap` / `parMap(n)` | Alias for `traverse` (Arrow-familiar name) | Parallel |

### Error Handling

| Combinator | Semantics |
|---|---|
| `recover` | Catch exceptions, return fallback value |
| `recoverWith` | Catch exceptions, switch to fallback computation |
| `fallback` | On failure, run alternative |
| `timeout(duration)` | Fail if too slow |
| `timeout(duration, default)` | Default if too slow |
| `retry(n, delay, backoff, shouldRetry, onRetry)` | Retry with selective filter and attempt callback |
| `timeoutRace(duration, fallback)` | Parallel timeout — fallback starts immediately |

### Validated (Error Accumulation)

| Combinator | Semantics |
|---|---|
| `liftV2`..`liftV22` + `apV` | Parallel validation, errors accumulated |
| `zipV` (2-22 args) | Parallel validation with full type inference |
| `followedByV` | True phase barrier for validation |
| `thenValueV` | Sequential validation fill, no barrier |
| `flatMapV` | Sequential, short-circuits on error |
| `valid(a)` / `invalid(e)` | Wrap success/error |
| `catching { toError }` | Bridge exceptions to validated |
| `validate { predicate }` | Predicate-based validation |
| `mapV` / `mapError` | Transform success/error side |
| `traverseV` | Parallel traverse with accumulation |
| `recoverV` | Bridge exceptions into validation errors (prevents sibling cancellation) |
| `orThrow()` | Unwrap or throw `ValidationException` |

### Observability

| Combinator | Semantics |
|---|---|
| `traced(name, onStart, onSuccess, onError)` | Lifecycle hooks per computation |
| `traced(name, tracer)` | Structured `ComputationTracer` interface |

### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Acquire/use/release with guaranteed cleanup |
| `guarantee(finalizer)` | Unconditional finalizer (success, failure, or cancellation) |
| `guaranteeCase(finalizer)` | Finalizer with `ExitCase` (Completed, Failed, Cancelled) |

### Interop

| Combinator | Semantics |
|---|---|
| `catching { block }` | Exception-safe computation → `Computation<Result<A>>` |
| `Deferred.toComputation()` | Wrap existing Deferred |
| `Computation.toDeferred(scope)` | Start eagerly |
| `Flow.firstAsComputation()` | First emission |
| `(suspend () -> A).toComputation()` | Wrap suspend lambda |
| `apOrNull` | Handle nullable computations |
| `Result.toEither()` / `Either.toResult()` | Kotlin Result bridge |
| `Result.toValidated(onError)` | Result to validated computation |
| `delayed(duration, value)` | Computation that waits then returns |

### Either

| Combinator | Semantics |
|---|---|
| `map` / `flatMap` / `mapLeft` | Transform success or failure |
| `fold` / `getOrElse` / `getOrNull` | Extract values |
| `swap` / `bimap` / `merge` | Structural transforms |
| `onLeft` / `onRight` | Side-effects |
| `isLeft` / `isRight` | Predicates |

---

## Arrow Interop

Optional module for teams using Arrow alongside this library:

```kotlin
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives-arrow:1.0.0")
}
```

```kotlin
import applicative.arrow.*

// Arrow Either ↔ Applicative Either
val appEither = arrowEither.toApplicativeEither()
val backToArrow = appEither.toArrowEither()

// Arrow NonEmptyList ↔ Applicative Nel
val appNel = arrowNel.toApplicativeNel()

// Bridge Arrow's parZip into a Computation chain
val phase = fromArrow { parZip({ fetchUser() }, { fetchCart() }) { u, c -> u to c } }

// Run a Computation and get an Arrow Either back
val result = myComputation.runCatchingArrow(scope)
```

---

## Structured Concurrency

All parallelism is scoped via `coroutineScope`. If any computation fails:

- Sibling coroutines are automatically cancelled (verified: 1 failure cancels 9 siblings)
- The exception propagates to the `Async {}` call site
- `CancellationException` is never caught -- structured concurrency is always respected
- No resource leaks

---

## When to Use (and When Not To)

**Use this library when:**
- 4+ concurrent operations with sequential phases between them
- You need error accumulation across parallel branches
- The dependency graph should be visible in code shape

**Don't use this library when:**
- 2-3 independent parallel calls -- raw `coroutineScope { async {} }` is simpler
- Purely sequential operations -- use regular `suspend` functions
- Stream processing -- use `Flow`
- Full FP ecosystem (optics, typeclasses) -- use Arrow

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, any multi-service orchestration.

---

## Building

```bash
./gradlew jvmTest              # run core tests
./gradlew :arrow-interop:test  # run Arrow interop tests
./gradlew :benchmarks:jmh      # run JMH benchmarks
./gradlew dokkaHtml             # generate API docs
./gradlew generateCurry         # regenerate curry functions (arities 2-22)
```

## Publishing

See [PUBLISHING.md](PUBLISHING.md) for Maven Central publishing instructions.

## License

Apache 2.0
