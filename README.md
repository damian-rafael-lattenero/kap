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
        .ap { fetchCart(userId) }          // ├─ parallel
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // ── barrier
        .ap { calcShipping() }             // ┐
        .ap { calcTax() }                  // ├─ parallel
        .ap { calcDiscounts() }            // ┘
        .followedBy { reservePayment() }   // ── barrier
        .ap { generateConfirmation() }     // ┐ parallel
        .ap { sendReceiptEmail() }         // ┘
}
```

11 service calls. 5 phases. 12 lines. **Swap any two `.ap` lines and the compiler rejects it.**

`ap` = parallel. `followedBy` = wait. That's the entire model.

---

## Install

```kotlin
dependencies {
    implementation("org.applicative.coroutines:coroutines-applicatives:1.0.0")
}
```

```kotlin
import applicative.*
```

---

## The Problem

A real BFF endpoint aggregates 10-15 microservices per request. With raw coroutines:

```kotlin
// 11 calls, 5 phases, 30+ lines of plumbing
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart      = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user      = dUser.await()
    val cart      = dCart.await()
    val promos    = dPromos.await()
    val inventory = dInventory.await()

    val stock = validateStock(inventory)

    val dShipping  = async { calcShipping(cart) }
    val dTax       = async { calcTax(cart) }
    val dDiscounts = async { calcDiscounts(promos) }
    val shipping  = dShipping.await()
    val tax       = dTax.await()
    val discounts = dDiscounts.await()

    val payment = reservePayment(user, cart)

    val dConfirmation = async { generateConfirmation(payment) }
    val dEmail        = async { sendReceiptEmail(user) }

    CheckoutResult(user, cart, promos, inventory, stock,
                   shipping, tax, discounts, payment,
                   dConfirmation.await(), dEmail.await())
}
```

30+ lines. 11 shuttle variables. Phases invisible without comments. Move one `await()` above its `async` and you silently serialize -- the compiler won't say a word.

---

## Four Primitives

| Combinator | What it does | When to use |
|---|---|---|
| `ap` | Launches right side as `async` in parallel | Independent work |
| `followedBy` | **True phase barrier** -- subsequent `ap` calls wait | Phase boundaries (post-barrier work is independent) |
| `flatMap` | True dependency -- right side constructed from left's value | Phase boundaries (post-barrier work needs the result) |
| `thenValue` | Sequential value fill, no barrier -- subsequent `ap` still launch eagerly | When post-barrier work should overlap for performance |

`lift` curries your constructor. Each `.ap` fills one slot in parallel. `followedBy` inserts a real barrier. The chain reads top-to-bottom as the execution plan -- what you see is what runs.

---

## Hero Examples

### 1. Multi-Phase Orchestration

The checkout example above in full -- 11 calls across 5 phases:

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐ phase 1: fetch everything
        .ap { fetchCart(userId) }          // │  (4 calls, parallel)
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // ── phase 2: validate before pricing
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

When a later phase needs the *result* of an earlier phase, use `flatMap`:

```kotlin
val dashboard = Async {
    lift4(::UserContext)
        .ap { fetchProfile(userId) }       // ┐ phase 1: all parallel
        .ap { fetchPreferences(userId) }   // │
        .ap { fetchLoyaltyTier(userId) }   // │
        .ap { fetchRecentOrders(userId) }  // ┘
    .flatMap { ctx ->                      // phase 2: NEEDS ctx values
        lift3(::PersonalizedDashboard)
            .ap { fetchRecommendations(ctx.profile) }
            .ap { fetchPromotions(ctx.loyalty) }
            .ap { fetchTrending(ctx.preferences) }
    }
}
```

`followedBy` = ordering barrier (post-barrier work is independent).
`flatMap` = value dependency (post-barrier work needs the result).

### 3. Parallel Validation with Error Accumulation

Run all validators in parallel. Collect *every* error, not just the first:

```kotlin
val booking = Async {
    zipV(
        { validatePassport(input) },   // ┐ parallel -- all errors
        { validateSeat(seat) },        // ├─ accumulated if
        { validatePayment(card) },     // ┘ multiple fail
    ) { passport, seat, payment -> BookingResult(passport, seat, payment) }
}
// passport AND seat fail → Either.Left(Nel(InvalidPassport(...), SeatUnavailable(...)))
// All pass              → Either.Right(BookingResult(...))
```

Scale to 12+ fields with zero type annotations:

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
// Phase 1 fails → errors returned, phase 2 never runs (saves network calls)
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
// Total = max(branch times). Verified: 40ms (CompositionProofTest).
```

### 6. Racing: First to Succeed Wins

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response (first to complete). Others cancelled.
// If EU fails, US and AP get a chance. Only when ALL fail does it throw.
```

---

## Empirical Data

All claims verified with JMH benchmarks and deterministic virtual-time tests.

### JMH Benchmarks (`./gradlew :benchmarks:jmh`)

**Simple parallel (5 calls, 50ms each):**

| Approach | Wall time | Speedup |
|---|---|---|
| Sequential baseline | ~250ms | 1x |
| Raw coroutines | ~50ms | 5x |
| Arrow (`parZip`) | ~50ms | 5x |
| **This library** | ~50ms | **5x** |

All three parallel approaches perform identically. The difference is ergonomics.

**Multi-phase checkout (9 calls, 4 phases):**

| Approach | Wall time | Speedup | Flat code? |
|---|---|---|---|
| Sequential baseline | ~430ms | 1x | Yes |
| Raw coroutines | ~180ms | 2.4x | No |
| Arrow (`parZip`) | ~180ms | 2.4x | No |
| **This library** | ~180ms | **2.4x** | **Yes** |

Same performance. This library keeps the code flat regardless of phase count.

**Validation with error accumulation (4 validators, 40ms each):**

| Approach | Wall time | All errors? | Parallel? |
|---|---|---|---|
| Sequential | ~160ms | Yes | No |
| Raw coroutines | ~40ms | **No** -- cancels siblings | Yes |
| Arrow (`zipOrAccumulate`) | ~40ms | Yes | Yes |
| **This library** (`zipV`) | ~40ms | **Yes** | **Yes** |

Raw coroutines *cannot* accumulate errors from parallel branches.

### Virtual-Time Proofs (deterministic, not flaky)

These tests use `kotlinx.coroutines.test.runTest` with `currentTime` assertions -- provably correct:

| Test | Virtual time | Sequential would be | Proof |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | 5x speedup |
| 10 parallel calls @ 30ms | **30ms** | 300ms | 10x speedup |
| 14-call 5-phase BFF (3 phases + 2 barriers) | **130ms** | 460ms | **3.5x speedup** |
| `followedBy` true barrier (A,B + barrier + C) | **110ms** | -- | C waits for barrier |
| Post-barrier aps run in parallel | **90ms** | -- | 3 aps launch together |
| `thenValue` no barrier (eager launch) | **80ms** | -- | C overlaps |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | Semaphore works |
| Library vs raw coroutines overhead | **0ms** | -- | Zero overhead |
| Mass cancellation (1 fail, 9 siblings) | -- | -- | All 9 cancelled |
| `flatMap` true boundary | **80ms** | -- | Post-flatMap waits |
| Full production pattern (timeout+retry+recover+race) | **40ms** | -- | All compose |

Source: [`ConcurrencyProofTest.kt`](src/jvmTest/kotlin/applicative/ConcurrencyProofTest.kt), [`CompositionProofTest.kt`](src/jvmTest/kotlin/applicative/CompositionProofTest.kt).

### Algebraic Laws (property-based)

The test suite verifies functor, applicative, and monad laws with random inputs via Kotest:

- **Identity:** `map id == id`, `pure id <*> v == v`
- **Composition:** `map (g . f) == map g . map f`
- **Homomorphism:** `pure f <*> pure x == pure (f x)`
- **Associativity:** `(m >>= f) >>= g == m >>= (a -> f a >>= g)`

Source: [`ApplicativeLawsTest.kt`](src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt).

---

## Why Not Raw Coroutines or Arrow?

| | Raw Coroutines | Arrow | This Library |
|---|---|---|---|
| **15 parallel calls** | 30+ lines, shuttle vars | `parZip` (max 9, nest for more) | `lift15` + 15 `.ap` -- flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `followedBy` barriers |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Impossible in parallel | `zipOrAccumulate` (max 9) | `zipV` (up to 22) |
| **Type-safe arg order** | No | Named args in lambda | Compile-time via currying |
| **Code size** | -- | ~50,000+ lines | ~1,600 lines |
| **Dependencies** | stdlib | Arrow core + modules | `kotlinx-coroutines-core` only |
| **Platforms** | JVM, JS, Native | JVM, JS | JVM, JS, Native |

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

The same graph can be executed multiple times.

### `followedBy`: True Phase Barrier

`followedBy` creates a real phase boundary. `ap` calls after a barrier do not launch until the barrier completes:

```kotlin
lift4(::Result)
    .ap { delay(30); fetchA() }         // ┐ phase 1: launched at t=0
    .ap { delay(30); fetchB() }         // ┘
    .followedBy { delay(50); barrier }  // ── barrier: starts at t=30, ends at t=80
    .ap { delay(30); fetchC() }         // ── phase 2: launched at t=80, done at t=110
// Total: 110ms. C waits for the barrier. The code means what it says.
```

### `thenValue`: Sequential Fill, No Barrier

When post-barrier work is truly independent and should overlap for performance, use `thenValue`:

```kotlin
lift4(::Result)
    .ap { delay(30); fetchA() }         // launched at t=0
    .ap { delay(30); fetchB() }         // launched at t=0
    .thenValue { delay(50); enrich() }  // sequential value, but...
    .ap { delay(30); fetchC() }         // launched at t=0 (overlaps!)
// Total: 80ms. C launched eagerly at t=0.
```

### `flatMap`: Value Dependency

`flatMap` is like `followedBy` but passes the result -- use it when the next phase needs the value:

```kotlin
Computation { delay(30); fetchBase() }
    .flatMap { base ->
        lift2(::Result)
            .ap { delay(50); transform(base) }   // launched at t=30
            .ap { delay(50); enrich(base) }       // launched at t=30
    }
// Total: 30 + 50 = 80ms. flatMap enforced the dependency.
```

### When to Use Which

| Scenario | Use | Behavior |
|---|---|---|
| Post-barrier work is **independent, no values needed** | `followedBy` | True barrier, clean phases |
| Post-barrier work **needs the value** | `flatMap` | True barrier + passes value |
| Post-barrier work should **overlap** for performance | `thenValue` | No barrier, eager launch |

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

Composes with everything else:

```kotlin
Computation { fetchUser() }
    .timeout(500.milliseconds)
    .retry(maxAttempts = 3, delay = 100.milliseconds, backoff = exponential)
    .recover { UserProfile.cached() }
    .traced("fetchUser", tracer)
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
| `race` / `raceN` | First to succeed wins | Competitive |
| `pure` | Wrap a value | -- |
| `on` | Switch dispatcher | -- |
| `context` | Read `CoroutineContext` | -- |

### Error Handling

| Combinator | Semantics |
|---|---|
| `recover` | Catch exceptions, return fallback value |
| `recoverWith` | Catch exceptions, switch to fallback computation |
| `fallback` | On failure, run alternative |
| `timeout(duration)` | Fail if too slow |
| `timeout(duration, default)` | Default if too slow |
| `retry(n, delay, backoff)` | Retry with exponential backoff |

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
| `orThrow()` | Unwrap or throw `ValidationException` |

### Observability

| Combinator | Semantics |
|---|---|
| `traced(name, onStart, onSuccess, onError)` | Lifecycle hooks per computation |
| `traced(name, tracer)` | Structured `ComputationTracer` interface |

### Interop

| Combinator | Semantics |
|---|---|
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

- Sibling coroutines are automatically cancelled
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

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation.

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
