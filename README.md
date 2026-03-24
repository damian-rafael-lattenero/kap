# KAP — Kotlin Applicative Parallelism

**Your code shape *is* the execution plan.**

[![CI](https://github.com/damian-rafael-lattenero/coroutines-applicatives/actions/workflows/ci.yml/badge.svg)](https://github.com/damian-rafael-lattenero/coroutines-applicatives/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.9.0-blue.svg)](https://github.com/Kotlin/kotlinx.coroutines)
[![Tests](https://img.shields.io/badge/Tests-906%20across%2061%20suites-brightgreen.svg)](#empirical-data)
[![Benchmarks](https://img.shields.io/badge/Benchmarks-119%20JMH-blueviolet.svg)](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/)

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg)](#)
[![Modular](https://img.shields.io/badge/Modules-kap--core%20%7C%20kap--resilience%20%7C%20kap--arrow-informational.svg)](#module-architecture)

**Three modules, pick what you need:**

| Module | What you get | Depends on |
|---|---|---|
| **`kap-core`** | `Computation`, `with`, `kap`, `followedBy`, `race`, `traverse`, `memoize`, `timeout`, `recover` | `kotlinx-coroutines-core` only |
| **`kap-resilience`** | `Schedule`, `CircuitBreaker`, `Resource`, `bracket`, `raceQuorum`, `timeoutRace` | `kap-core` |
| **`kap-arrow`** | `zipV`, `withV`, `validated {}`, `attempt()`, `raceEither`, `Either`/`Nel` bridges | `kap-core` + Arrow Core |

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }              // ┐
        .with { fetchCart() }               // ├─ phase 1: parallel
        .with { fetchPromos() }             // │
        .with { fetchInventory() }          // ┘
        .followedBy { validateStock() }     // ── phase 2: barrier
        .with { calcShipping() }            // ┐
        .with { calcTax() }                 // ├─ phase 3: parallel
        .with { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }    // ── phase 4: barrier
        .with { generateConfirmation() }    // ┐ phase 5: parallel
        .with { sendEmail() }              // ┘
}
```

11 service calls. 5 phases. One flat chain. **Swap any two `.with` lines and the compiler rejects it.** Each service returns a distinct type — the typed function chain locks parameter order at compile time.

**130ms total** (vs 460ms sequential) — verified in [`ConcurrencyProofTest.kt`](kap-core/src/jvmTest/kotlin/applicative/ConcurrencyProofTest.kt).

### `::CheckoutResult` is just a function

A Kotlin data class constructor *is* a function. `::CheckoutResult` has type `(UserProfile, ShoppingCart, ...) -> CheckoutResult`. So `kap(::CheckoutResult)` wraps that function for parallel execution.

But `kap` works with **any** function, not just constructors:

```kotlin
data class Greeting(val text: String, val target: String)

// Constructor reference — (String, String) -> Greeting
val g1: Greeting = Async {
    kap(::Greeting)
        .with { fetchName() }
        .with { "hello" }
}

// Any lambda works too:
val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }
val g2: String = Async {
    kap(greet)
        .with { fetchName() }
        .with { fetchAge() }
}

// A regular function reference:
fun buildSummary(name: String, items: Int): String = "$name has $items items"

val g3: String = Async {
    kap(::buildSummary)
        .with { fetchName() }
        .with { 5 }
}
```

Constructors are just the most common case because they give you compile-time parameter order safety for free — each slot expects a specific type, so swapping two `.with` lines is a compiler error.

### `Computation` is a description, not an execution

Nothing runs until you wrap it in `Async {}`:

```kotlin
val plan: Computation<Dashboard> = kap(::Dashboard)
    .with { fetchDashUser() }
    .with { fetchDashCart() }
    .with { fetchDashPromos() }

println("Plan built. Nothing has executed yet.")

val result: Dashboard = Async { plan }
println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
```

Build computation graphs, store them, pass them around, compose them — all without triggering any side effects. Execution only happens at the `Async` boundary.

### All `val`, no `null`, no `!!`

With raw coroutines, parallel results force you into mutable variables or nullable types:

```kotlin
// Raw coroutines: vars and nulls
var user: String? = null
var cart: String? = null
coroutineScope {
    launch { user = fetchDashUser() }
    launch { cart = fetchDashCart() }
}
val rawResult = DashboardView(user!!, cart!!)
```

With KAP, the constructor receives everything at once. Every field is `val`. Nothing is ever `null`:

```kotlin
val kapResult: DashboardView = Async {
    kap(::DashboardView)
        .with { fetchDashUser() }
        .with { fetchDashCart() }
}
```

This isn't just style — it's **correctness**. Your data classes keep all `val` fields with no default values, because KAP guarantees the constructor is called with all arguments at once. No partial construction. No temporal coupling.

> All code examples on this page are compilable and verified in [`readme-examples`](examples/readme-examples/).

---

## Here's What This Replaces

You have 11 microservice calls. Some can run in parallel, others depend on earlier results. You need maximum parallelism with clear phase boundaries.

**Raw Coroutines — 30+ lines, invisible phases, shuttle variables:**

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
// Where are the phase boundaries? Which calls are parallel? Which are sequential?
// Move one await() above its async — silently serialized. Compiler won't warn.
```

**KAP — 12 lines, single flat chain, visible phases:**

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }              // ┐
        .with { fetchCart() }               // ├─ phase 1: parallel
        .with { fetchPromos() }             // │
        .with { fetchInventory() }          // ┘
        .followedBy { validateStock() }     // ── phase 2: barrier
        .with { calcShipping() }            // ┐
        .with { calcTax() }                 // ├─ phase 3: parallel
        .with { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }    // ── phase 4: barrier
        .with { generateConfirmation() }    // ┐ phase 5: parallel
        .with { sendEmail() }              // ┘
}
// Same wall-clock time. Phases visible. Swap = compiler error.
// Scales to 22 args. No intermediate variables. No nesting.
```

```
Execution timeline:

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

---

## Three Primitives — That's the Whole Model

| Primitive | What it does | Think of it as |
|---|---|---|
| `.with { }` | Launch in parallel with everything above | "and at the same time..." |
| `.followedBy { }` | Wait for everything above, then continue | "then, once that's done..." |
| `.flatMap { ctx -> }` | Wait for everything above, pass the result, then continue | "then, using what we got..." |

```kotlin
// .with — parallel: both launch at t=0
val result = Async {
    kap(::AB)
        .with { fetchA() }   // ┐ parallel
        .with { fetchB() }   // ┘
}
```

```kotlin
// .followedBy — barrier: validate waits for A and B
val result = Async {
    kap(::R3)
        .with { fetchA() }             // ┐ parallel
        .with { fetchB() }             // ┘
        .followedBy { validate() }     // waits for A and B
}
```

```kotlin
// .flatMap — barrier that passes data: phase 2 USES phase 1 results
val userId = "user-1"
val dashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐ phase 1: parallel
        .with { fetchPreferences(userId) }   // │
        .with { fetchLoyaltyTier(userId) }   // ┘
        .flatMap { ctx ->                     // ── barrier: phase 2 NEEDS ctx
            kap(::PersonalizedDashboard)
                .with { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
                .with { fetchPromotions(ctx.tier) }           // │
                .with { fetchTrending(ctx.prefs) }            // ┘
        }
}
```

```
flatMap execution timeline:

t=0ms   ─── fetchProfile ───────┐
t=0ms   ─── fetchPreferences ──├─ phase 1 (parallel, all 3)
t=0ms   ─── fetchLoyaltyTier ──┘
t=50ms  ─── flatMap { ctx -> ... }  ── barrier, ctx available
t=50ms  ─── fetchRecommendations(ctx.profile) ───┐
t=50ms  ─── fetchPromotions(ctx.tier) ───────────├─ phase 2 (parallel, all 3)
t=50ms  ─── fetchTrending(ctx.prefs) ────────────┘
t=80ms  ─── PersonalizedDashboard ready
```

---

## When Phase 2 Depends on Phase 1: `flatMap`

The checkout above uses `.followedBy` for barriers — phase 2 doesn't need phase 1's *values*, just needs it to finish. But what if phase 2 **uses** phase 1's results?

**Raw Coroutines — three separate `coroutineScope` blocks, manual variable threading:**

```kotlin
val userId = "user-42"

val ctx = coroutineScope {
    val dProfile = async { fetchProfile(userId) }
    val dPrefs = async { fetchPreferences(userId) }
    val dTier = async { fetchLoyaltyTier(userId) }
    UserContext(dProfile.await(), dPrefs.await(), dTier.await())
}

val enriched = coroutineScope {
    val dRecs = async { fetchRecommendations(ctx.profile) }
    val dPromos = async { fetchPromotions(ctx.tier) }
    val dTrending = async { fetchTrending(ctx.prefs) }
    val dHistory = async { fetchHistory(ctx.profile) }
    EnrichedContent(dRecs.await(), dPromos.await(), dTrending.await(), dHistory.await())
}

val dashboard = coroutineScope {
    val dLayout = async { renderLayout(ctx, enriched) }
    val dTrack = async { trackAnalytics(ctx, enriched) }
    FinalDashboard(dLayout.await(), dTrack.await())
}
// 3 separate coroutineScope blocks. ctx and enriched threaded manually.
```

**KAP — single expression, dependencies are the structure:**

```kotlin
val userId = "user-42"

val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }
        .with { fetchPreferences(userId) }
        .with { fetchLoyaltyTier(userId) }
        .flatMap { ctx ->
            kap(::EnrichedContent)
                .with { fetchRecommendations(ctx.profile) }
                .with { fetchPromotions(ctx.tier) }
                .with { fetchTrending(ctx.prefs) }
                .with { fetchHistory(ctx.profile) }
                .flatMap { enriched ->
                    kap(::FinalDashboard)
                        .with { renderLayout(ctx, enriched) }
                        .with { trackAnalytics(ctx, enriched) }
                }
        }
}
// One expression. Phase 2 can't start without ctx. Phase 3 can't start without enriched.
// The dependency graph IS the code shape.
```

```
Execution timeline:

t=0ms   ─── fetchProfile ──────┐
t=0ms   ─── fetchPreferences ──├─ phase 1 (parallel, all 3)
t=0ms   ─── fetchLoyaltyTier ──┘
t=50ms  ─── flatMap { ctx -> }  ── barrier, ctx available
t=50ms  ─── fetchRecommendations ──┐
t=50ms  ─── fetchPromotions ───────├─ phase 2 (parallel, all 4)
t=50ms  ─── fetchTrending ─────────┤
t=50ms  ─── fetchHistory ──────────┘
t=90ms  ─── flatMap { enriched -> } ── barrier, enriched available
t=90ms  ─── renderLayout ──┐
t=90ms  ─── trackAnalytics ┘─ phase 3 (parallel, both)
t=115ms ─── FinalDashboard ready
```

---

## Quick Start

**Pick only the modules you need:**

```kotlin
// build.gradle.kts
dependencies {
    // Core — the only required module (zero deps beyond coroutines)
    implementation("io.github.damian-rafael-lattenero:kap-core:2.1.0")

    // Optional: resilience patterns (Schedule, Resource, CircuitBreaker, bracket)
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.1.0")

    // Optional: Arrow integration (validated DSL, Either/Nel, raceEither, attempt)
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.1.0")
}
```

```kotlin
import applicative.*

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

Add resilience to any branch — each module composes naturally:

```kotlin
import applicative.*

val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)
val retryPolicy = Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds)

val result = Async {
    kap(::Dashboard)
        .with(Computation { fetchDashUser() }
            .withCircuitBreaker(breaker)
            .retry(retryPolicy))
        .with(Computation { fetchFromSlowApi() }
            .timeoutRace(100.milliseconds, Computation { fetchFromCache() }))
        .with { fetchDashPromos() }
}
```

Add validation with Arrow types:

```kotlin
import applicative.*
import arrow.core.Either
import arrow.core.NonEmptyList

val valid: Either<NonEmptyList<RegError>, User> = Async {
    kapV<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
        .withV { validateName("Alice") }
        .withV { validateEmail("alice@example.com") }
        .withV { validateAge(25) }
        .withV { checkUsername("alice") }
}
// 3 fail? -> Either.Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow))
// All pass? -> Either.Right(User(...))
```

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

## Choose Your Style

Three ways to compose parallel operations — pick what fits:

```kotlin
// Style 1: kap + with — compile-time parameter order safety (recommended for 4+ args)
val s1 = Async {
    kap(::Dashboard)
        .with { fetchDashUser() }
        .with { fetchDashCart() }
        .with { fetchDashPromos() }
}

// Style 2: combine with suspend lambdas (great for 2-9 args)
val s2 = Async {
    combine(
        { fetchDashUser() },
        { fetchDashCart() },
        { fetchDashPromos() },
    ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
}

// Style 3: combine with pre-built Computations
val s3 = Async {
    combine(
        Computation { fetchDashUser() },
        Computation { fetchDashCart() },
        Computation { fetchDashPromos() },
    ) { user: String, cart: String, promos: String -> Dashboard(user, cart, promos) }
}

// Bonus: pair/triple — returns Pair/Triple
val (user, cart) = Async { pair({ fetchDashUser() }, { fetchDashCart() }) }
```

All styles are parallel by default. `kap+with` gives stronger type safety; `combine` gives simpler syntax.

---

## Module Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        kap-arrow                            │
│  zipV · withV · validated {} · attempt() · raceEither       │
│  Either/Nel bridges · accumulate {} · Validated<E,A>        │
│          depends on: kap-core + Arrow Core (JVM only)       │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────┐
│                      kap-resilience                         │
│  Schedule · CircuitBreaker · Resource · bracket             │
│  raceQuorum · timeoutRace · retry(schedule) · retryOrElse   │
│               depends on: kap-core                          │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────┐
│                        kap-core                             │
│  Computation · with · followedBy · flatMap · kap · combine  │
│  race · traverse · memoize · timeout · recover · retry(n)   │
│  settled · catching · Deferred/Flow bridges                 │
│    depends on: kotlinx-coroutines-core (JVM, JS, Native)    │
└─────────────────────────────────────────────────────────────┘
```

Non-Arrow projects get a lean DSL with zero Arrow types in auto-complete. Arrow users add `kap-arrow` for native `Either`/`NonEmptyList` integration.

---

## Feature Showcase

Every feature below follows the same structure: the **real-world problem**, how **raw coroutines** handle it (or fail to), and how **KAP** solves it. Each section is tagged with the module that provides it.

---

### 1. Partial Failure Tolerance with `.settled()` `kap-core`

**The problem:** Your dashboard has three data sources. If the user service fails, you still want to show the cart and config — with a fallback user. But structured concurrency cancels *all* siblings when *any* coroutine fails.

**KAP — `.settled()`, one method:**

```kotlin
val dashboard = Async {
    kap { user: Result<String>, cart: String, config: String ->
        PartialDashboard(user.getOrDefault("anonymous"), cart, config)
    }
        .with(Computation { fetchUserMayFail() }.settled())
        .with { fetchCartAlways() }
        .with { fetchConfigAlways() }
}
// fetchUser fails? Dashboard still builds with "anonymous".
// fetchCart fails? Everything cancels (it's not settled).
```

For collections — `traverseSettled` processes ALL items, no cancellation:

```kotlin
val ids = listOf(1, 2, 3, 4, 5)
val results: List<Result<String>> = Async {
    ids.traverseSettled { id ->
        Computation {
            if (id % 2 == 0) throw RuntimeException("fail-$id")
            "user-$id"
        }
    }
}
val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!!.message }
// successes=[user-1, user-3, user-5], failures=[fail-2, fail-4]
```

Neither raw coroutines nor Arrow offer this. Raw `supervisorScope` breaks cleanup guarantees. Arrow's `parZip` cancels siblings on any failure.

---

### 2. Timeout with Parallel Fallback `kap-resilience`

**The problem:** Your primary API has a timeout. With a sequential approach, you *wait* the full timeout before starting the fallback — wasting time. You want both to start immediately.

```kotlin
val result = Async {
    Computation { fetchFromPrimary() }
        .timeoutRace(100.milliseconds, Computation { fetchFromFallback() })
}
// Fallback starts immediately at t=0. If primary wins before 100ms, use it.
// If primary times out, fallback is ALREADY RUNNING — maybe already done.
```

**JMH verified:** `timeoutRace` 34.0ms vs sequential timeout 87.2ms — **2.6x faster**.

---

### 3. Compile-Time Argument Order Safety `kap-core`

**The problem:** You have a function with 15 parameters. With raw coroutines, you pass them by position. Swap two parameters of the same type and it silently compiles with wrong data.

With KAP, the typed function chain `(A) -> (B) -> ... -> (O) -> Result` enforces that each `.with` provides exactly the right type for its slot:

```kotlin
Async {
    kap(::DashboardPage)
        .with { fetchProfile(userId) }       // returns UserProfile     — slot 1
        .with { fetchPreferences(userId) }   // returns UserPreferences — slot 2
        // Swap these two lines?
        // .with { fetchLoyaltyTier(userId) }  // returns LoyaltyTier — COMPILE ERROR
        // .with { fetchPreferences(userId) }  // expected LoyaltyTier, got UserPreferences
}
```

For same-type parameters, use value classes for full swap-safety:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
```

Neither raw coroutines nor Arrow enforce parameter order at compile time.

---

### 4. Quorum Consensus — N-of-M Successes `kap-resilience`

**The problem:** You have 3 database replicas. For consistency, you need at least 2 of 3 to agree. Or you're doing hedged requests and want the N fastest.

```kotlin
val quorum: List<String> = Async {
    raceQuorum(
        required = 2,
        Computation { fetchReplicaA() },
        Computation { fetchReplicaB() },
        Computation { fetchReplicaC() },
    )
}
// Returns the 2 fastest successes. Third replica cancelled.
// If 2+ fail, throws (quorum impossible).
```

No primitive for this exists in raw coroutines or Arrow.

---

### 5. Success-Only Memoization `kap-core`

**The problem:** You want to cache an expensive computation so it runs once. But standard memoization caches failures too — a transient network error gets cached forever.

```kotlin
var callCount = 0
val fetchOnce = Computation { callCount++; delay(30); "expensive-result" }.memoizeOnSuccess()

val a = Async { fetchOnce }  // runs the actual fetch
val b = Async { fetchOnce }  // returns cached result instantly
// callCount == 1 — only executed once
// If first call FAILS: not cached, next call retries
```

No manual `Mutex` + double-checked locking. Arrow has no equivalent.

---

### 6. Retry with Composable Backoff Policies `kap-resilience`

```kotlin
val policy = Schedule.times<Throwable>(5) and
    Schedule.exponential(10.milliseconds) and
    Schedule.doWhile<Throwable> { it is RuntimeException }

val result = Async {
    Computation { flakyService() }.retry(policy)
}

// Or inline — retry is just another method on Computation:
val result2 = Async {
    kap { user: String, service: String -> RetryResult(user, service) }
        .with { fetchDashUser() }
        .with(Computation { flakyService() }
            .retry(3, delay = 10.milliseconds))
}
// Each branch handles its own resilience. All branches run in parallel.
```

Schedule building blocks:

```kotlin
Schedule.times(n)                          // max N retries
Schedule.spaced(d)                         // constant delay
Schedule.exponential(base, max)            // 100ms, 200ms, 400ms...
Schedule.fibonacci(base)                   // 100ms, 100ms, 200ms, 300ms, 500ms...
Schedule.linear(base)                      // 100ms, 200ms, 300ms, 400ms...
Schedule.forever()                         // infinite (combine with backoff)
schedule.jittered()                        // random spread (prevents thundering herd)
schedule.withMaxDuration(d)                // hard time limit
schedule.doWhile { it is IOException }     // retry predicate
s1 and s2                                  // both must agree (max delay)
s1 or s2                                   // either can continue (min delay)
```

---

### 7. Resource Safety in Parallel `kap-resilience`

**The problem:** Open three connections, use them all in parallel, and guarantee *all three* are closed even if one branch fails — even on cancellation.

```kotlin
// bracket: acquire, use in parallel, guaranteed release
val result = Async {
    kap { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .with(bracket(
            acquire = { openDbConnection() },
            use = { conn -> Computation { conn.query("SELECT 1") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openCacheConnection() },
            use = { conn -> Computation { conn.get("key") } },
            release = { conn -> conn.close() },
        ))
        .with(bracket(
            acquire = { openHttpClient() },
            use = { client -> Computation { client.get("/api") } },
            release = { client -> client.close() },
        ))
}
// All 3 acquired and used IN PARALLEL.
// Any branch fails -> ALL released (NonCancellable context).
```

Or compose resources first, use later:

```kotlin
val infra = Resource.zip(
    Resource({ openDbConnection() }, { it.close() }),
    Resource({ openCacheConnection() }, { it.close() }),
    Resource({ openHttpClient() }, { it.close() }),
) { db, cache, http -> Triple(db, cache, http) }

val result2 = Async {
    infra.useComputation { (db, cache, http) ->
        kap(::DashboardData)
            .with { db.query("SELECT 1") }
            .with { cache.get("user:prefs") }
            .with { http.get("/recommendations") }
    }
}
// Released in reverse order, even on failure. NonCancellable.
```

Use `bracketCase` when release behavior depends on the outcome:

```kotlin
bracketCase(
    acquire = { openDbConnection() },
    use = { tx -> Computation { tx.query("INSERT 1") } },
    release = { tx, case ->
        when (case) {
            is ExitCase.Completed<*> -> println("commit")
            else -> println("rollback")
        }
        tx.close()
    },
)
```

---

### 8. Racing — First to Succeed Wins `kap-core` `kap-arrow`

**The problem:** 3 regional replicas. You want the fastest response and immediately cancel the losers.

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (100ms)
        Computation { fetchFromRegionEU() },   // fast (30ms)
        Computation { fetchFromRegionAP() },   // medium (60ms)
    )
}
// Returns EU response at 30ms. US and AP cancelled immediately.

// Race with DIFFERENT types using raceEither:
val raceResult: Either<String, Int> = Async {
    raceEither(
        fa = Computation { delay(30); "fast-string" },
        fb = Computation { delay(100); 42 },
    )
}
// Either.Left("fast-string") — string won the race
```

---

### 9. Bounded Parallel Collection Processing `kap-core`

**The problem:** 200 user IDs to fetch. Your downstream API handles 10 concurrent requests max.

```kotlin
val userIds = (1..10).toList()

val results = Async {
    userIds.traverse(concurrency = 3) { id ->
        Computation { delay(20); "user-$id" }
    }
}
// 10 items @ 20ms each, concurrency=3 -> ~80ms (not 200ms)
```

---

### 10. Circuit Breaker `kap-resilience`

**The problem:** A downstream service is degraded. Every request takes 30 seconds then times out. You need to stop calling it after N failures and auto-recover later.

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

val result = Async {
    Computation { fetchDashUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(10.milliseconds))
        .recover { "cached-user" }
}
// timeout -> circuit breaker -> retry -> recover. All composable.
// breaker is shared across all callers — one instance protects the whole service.
```

---

### 11. Parallel Validation — Collect Every Error `kap-arrow`

**The problem:** A registration form with multiple fields. Validate all in parallel and return *every* error at once — not just the first one.

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

`zipV` scales to 22 validators and runs them all in parallel. Raw coroutines can't collect errors in parallel (structured concurrency cancels siblings). Arrow's `zipOrAccumulate` stops at 9.

---

### 12. Phased Validation `kap-arrow`

**The problem:** Phase 1 validates name, email, age in parallel. Phase 2 checks username availability and blacklist — but only if phase 1 passes.

```kotlin
val result: Either<NonEmptyList<RegError>, Registration> = Async {
    accumulate {
        val identity = zipV(
            { validateName("Alice") },
            { validateEmail("alice@example.com") },
            { validateAge(25) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()

        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email.value) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
// Phase 1 fails? Short-circuit, phase 2 never runs (saves network calls)
```

---

### 13. `attempt()` — Catch to Either `kap-arrow`

```kotlin
val success: Either<Throwable, String> = Async {
    Computation { "hello" }.attempt()
}
// Either.Right("hello")

val failure: Either<Throwable, String> = Async {
    Computation<String> { throw RuntimeException("boom") }.attempt()
}
// Either.Left(RuntimeException("boom"))
```

---

### 14. Fallback Chains `kap-core`

```kotlin
// Sequential fallback on failure:
val result = Async {
    Computation<String> { throw RuntimeException("fail-1") }
        .orElse(Computation { "fallback-ok" })
}
// "fallback-ok"

// Try each sequentially, return first success:
val result2 = Async {
    firstSuccessOf(
        Computation { throw RuntimeException("fail-1") },
        Computation { throw RuntimeException("fail-2") },
        Computation { "third-wins" },
    )
}
// "third-wins"
```

---

### 15. `computation {}` Builder `kap-core`

For sequential step-by-step composition when you need imperative style:

```kotlin
val result = Async {
    computation {
        val user = Computation { fetchDashUser() }.bind()
        val cart = Computation { fetchDashCart() }.bind()
        "$user has $cart"
    }
}
// "Alice has 3 items"
```

---

### Summary Table

| Feature | Raw Coroutines | Arrow | KAP | Module |
|---|---|---|---|---|
| **Multi-phase orchestration** | Nested scopes, shuttle vars | Nested `parZip` blocks | Flat chain with `.followedBy` | `kap-core` |
| **Parallel validation** | Impossible (cancels siblings) | `zipOrAccumulate` max 9 | `zipV` up to 22 | `kap-arrow` |
| **Value-dependent phases** | Manual variable threading | Sequential `parZip` blocks | `.flatMap` — dependency is the structure | `kap-core` |
| **Retry + backoff** | Manual loop (~20 lines) | `Schedule` (similar) | `Schedule` + composable with chain | `kap-resilience` |
| **Resource safety** | try/finally nesting | `Resource` monad | `bracket` / `Resource` — parallel use | `kap-resilience` |
| **Racing** | Complex `select` | `raceN` (similar) | `raceN` + `raceEither` | `kap-core` + `kap-arrow` |
| **Bounded traversal** | Manual Semaphore | `parMap(concurrency)` | `traverse(concurrency)` | `kap-core` |
| **Partial failure** | `supervisorScope` breaks guarantees | No equivalent | **`.settled()`** | `kap-core` |
| **Timeout + parallel fallback** | Sequential (wastes time) | No equivalent | **`timeoutRace`** — 2.6x faster | `kap-resilience` |
| **Quorum (N-of-M)** | No primitive | No equivalent | **`raceQuorum`** | `kap-resilience` |
| **Circuit breaker** | Manual state machine | Separate module | **Composable in chain** | `kap-resilience` |
| **Compile-time arg safety** | No (positional) | No (named lambda) | **Typed function chain enforces order** | `kap-core` |
| **Success-only memoization** | Manual Mutex + cache | No equivalent | **`.memoizeOnSuccess()`** | `kap-core` |

---

## Production Integration

### Context Propagation (MDC, OpenTelemetry, Tracing)

`Async(context)` propagates a `CoroutineContext` into all parallel branches:

```kotlin
import kotlinx.coroutines.slf4j.MDCContext

val result = Async(MDCContext()) {
    kap(::Dashboard)
        .with { fetchUser() }      // MDC propagated
        .with { fetchCart() }      // MDC propagated
        .with { fetchPromos() }    // MDC propagated
}
```

Per-branch dispatcher control:

```kotlin
val result = Async {
    kap(::Dashboard)
        .with { fetchUser().on(Dispatchers.IO) }           // IO thread pool
        .with { computeRecs().on(Dispatchers.Default) }    // CPU thread pool
        .with { fetchConfig().on(Dispatchers.IO) }         // IO thread pool
}
```

### Observability

```kotlin
val tracer = ComputationTracer { event ->
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
        .with { fetchCart().traced("cart", tracer) }
}
```

No logging framework coupled. Bring your own.

### Cancellation Safety

`CancellationException` is **never** caught by any combinator in KAP:

- `recover`, `retry`, `catching`, `attempt`, `recoverV` — all re-throw `CancellationException`
- `bracket` / `bracketCase` / `guarantee` — release always runs in `NonCancellable` context
- `race` / `raceN` — losers cancelled via structured concurrency
- When any `.with` branch fails, all sibling branches are cancelled (standard `coroutineScope` behavior)

### Execution Model

```kotlin
val graph = kap(::combineThree)
    .with { fetchA() }    // NOT executed yet
    .with { fetchB() }    // NOT executed yet

println("graph built, not executed")
val result = Async { graph.with { "C" } }  // NOW everything runs
```

| Scenario | Use | Subsequent `.with` behavior |
|---|---|---|
| Independent phases | `followedBy` | Gated — waits for barrier |
| Next phase needs the value | `flatMap` | Gated — waits, passes value |
| Maximum overlap | `thenValue` | Ungated — launches immediately |

### Error Handling Decision Tree

```
Exception occurred?
├─ Need a default value?           -> .recover { default }              (kap-core)
├─ Need another Computation?       -> .recoverWith { comp }             (kap-core)
├─ Want Either<Throwable, A>?      -> .attempt()                        (kap-arrow)
├─ Want kotlin.Result<A>?          -> catching { block }                (kap-core)
├─ Want sequential fallback chain? -> .orElse(other)                    (kap-core)
├─ Want retry then fallback?       -> .retryOrElse(schedule) { fb }     (kap-resilience)
└─ In validated context?           -> .recoverV { errorValue }          (kap-arrow)

Need a guard (not an exception)?
├─ Boolean predicate?              -> .ensure(error) { pred }           (kap-core)
├─ Null extraction?                -> .ensureNotNull(error) { extract } (kap-core)
├─ Validated predicate?            -> .ensureV(error) { pred }          (kap-arrow)
└─ Validated multi-error?          -> .ensureVAll(errors) { pred }      (kap-arrow)
```

---

## Empirical Data

All claims backed by **119 JMH benchmarks** (2 forks x 5 measurement iterations each) and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

> **Environment:** JDK 21, Ubuntu 24.04. JMH 1.37. [Live dashboard](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/).

### Performance Summary

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | **<0.01ms** |
| **Framework overhead** (arity 9) | <0.01ms | 0.03ms | **<0.01ms** |
| **Simple parallel** (5 x 50ms) | 50.27ms | 50.33ms | **50.31ms** |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | **180.98ms** |
| **Validation** (4 x 40ms) | N/A | 40.32ms | **40.28ms** |
| **Race** (50ms vs 100ms) | 100.34ms | 50.51ms | **50.40ms** |
| **Retry** (3 attempts w/ backoff) | 120.70ms | — | **30.21ms** |
| **timeoutRace** (primary wins) | 180.55ms | — | **30.34ms** |
| **Max validation arity** | — | 9 | **22** |
| **Flat multi-phase code** | No | No | **Yes** |
| **Compile-time arg order safety** | No | No | **Yes** |
| **Quorum race (N-of-M)** | Manual | No | **Yes** |

> KAP overhead is indistinguishable from raw coroutines. Where it pulls ahead: `race` (auto-cancel loser), `retry` (declarative schedules vs manual loops), and `timeoutRace` (parallel fallback vs sequential).

### Virtual-Time Proofs

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier | **110ms** | — | C waits for barrier |
| Bounded traverse (500 items, c=50) | **300ms** | 15,000ms | **50x** |
| kap (22 parallel branches) | **30ms** | 660ms | **22x** |

<details>
<summary>Full JMH benchmark tables</summary>

See [`CoreBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/CoreBenchmark.kt), [`ResilienceBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/ResilienceBenchmark.kt), [`ArrowBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/ArrowBenchmark.kt).

Comparison tests: [`CoreComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/CoreComparisonTest.kt) | [`ResilienceComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/ResilienceComparisonTest.kt) | [`ArrowComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/ArrowComparisonTest.kt).

</details>

---

## How It Works

**Step 1: Wrapping.** `kap(::CheckoutResult)` takes your 11-parameter constructor and wraps it into a chain of single-argument functions: `(A) -> (B) -> ... -> (K) -> CheckoutResult`. This is wrapped in a `Computation` — a lazy description that hasn't executed yet.

**Step 2: Parallel fork.** Each `.with { fetchX() }` takes the next function in the chain and applies one argument. The right-hand side (your lambda) launches as `async` in the current `CoroutineScope`, while the left spine stays inline. N `.with` calls produce N concurrent coroutines with O(N) stack depth.

**Step 3: Barrier join.** `.followedBy { }` awaits all pending parallel work before continuing. `.flatMap` does the same but passes the result value into the next phase.

The library knows at build time that `.with` branches are independent and can run concurrently. `flatMap` forces sequencing because later steps depend on earlier values. Your code shape directly encodes the dependency graph.

---

## Coming from Arrow?

`kap-arrow` uses Arrow's **native types** directly — `arrow.core.Either` and `arrow.core.NonEmptyList`. No wrapper types, no adapters.

| Arrow | KAP | Module |
|---|---|---|
| `parZip(f1, f2, ...) { }` | `combine(f1, f2, f3) { }` or `kap(::R).with{f1}.with{f2}` | `kap-core` |
| Nested `parZip` for phases | `.followedBy { }` | `kap-core` |
| `zipOrAccumulate(f1, ...) { }` | `zipV(f1, ...) { }` — up to 22 | `kap-arrow` |
| `either { ... }` | `validated { }` / `accumulate { }` | `kap-arrow` |
| `Resource({ }, { })` | `Resource({ }, { })` — identical API | `kap-resilience` |
| `Schedule.times(n)` | `Schedule.times(n)` — identical API | `kap-resilience` |
| `parMap(concurrency) { }` | `traverse(concurrency) { }` | `kap-core` |

---

## API Reference

All arities are **unified at 22** — the maximum supported by Kotlin's function types.

### `kap-core` — Orchestration Primitives

| Combinator | Semantics | Parallelism |
|---|---|---|
| `kap` + `.with` | N-way fan-out (typed, safe ordering) | Parallel |
| `combine` | Lifting with suspend lambdas or Computations | Parallel |
| `pair(fa, fb)` / `triple(fa, fb, fc)` | Parallel into Pair/Triple | Parallel |
| `.followedBy` | True phase barrier | Sequential (gates) |
| `.thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `.flatMap` | Value-dependent sequencing | Sequential |
| `map` / `Computation.of` / `Computation.empty` | Transform / wrap value | — |
| `Computation.failed(error)` / `Computation.defer { }` | Error / lazy construction | — |
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
| `Deferred.toComputation()` / `Computation.toDeferred(scope)` | Deferred bridge |
| `Flow.firstAsComputation()` / `(suspend () -> A).toComputation()` | Flow/lambda bridge |
| `Flow.mapComputation(concurrency) { }` / `Flow.mapComputationOrdered` | Flow + Computation pipeline |
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
| `resource.use` / `resource.useComputation` | Terminal operations |

### `kap-arrow` — Validation & Arrow Integration

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `kapV` + `withV` | Typed parallel validation |
| `followedByV` / `flatMapV` | Phase barriers / sequential short-circuit |
| `validated { }` / `accumulate { }` | Short-circuit builder with `.bindV()` |
| `valid` / `invalid` / `catching` | Entry points |
| `Validated<E, A>` (typealias) | Shorthand for `Computation<Either<NonEmptyList<E>, A>>` |
| `ensureV(error) { pred }` / `ensureVAll(errors) { pred }` | Validated guards |
| `recoverV` / `mapV` / `mapError` / `orThrow` | Transforms |
| `traverseV` / `sequenceV` | Collection operations with error accumulation |
| `.attempt()` | Catch to `Either<Throwable, A>` |
| `raceEither(fa, fb)` | First to succeed, different types |

---

## Examples

Seven runnable examples in [`/examples`](examples/):

| Example | Modules | What it demonstrates |
|---|---|---|
| **[ecommerce-checkout](examples/ecommerce-checkout/)** | `kap-core` | 11 services, 5 phases — `kap`+`with`+`followedBy` |
| **[dashboard-aggregator](examples/dashboard-aggregator/)** | `kap-core` | 14-service BFF — type-safe at 14 args |
| **[validated-registration](examples/validated-registration/)** | `kap-core` + `kap-arrow` | Parallel validation, error accumulation, phased validation |
| **[ktor-integration](examples/ktor-integration/)** | All three modules | Ktor HTTP BFF: aggregation, traverse, validation, retry/CB/bracket — 28 integration tests |
| **[resilient-fetcher](examples/resilient-fetcher/)** | `kap-core` + `kap-resilience` | `Schedule`, `CircuitBreaker`, `bracket`, `Resource.zip`, `timeoutRace`, `raceQuorum` |
| **[full-stack-order](examples/full-stack-order/)** | All three modules | Validated input + retry/CB/bracket + `attempt`/`raceEither` — complete pipeline |
| **[readme-examples](examples/readme-examples/)** | All three modules | Every code example from this README — compiled and verified on every CI push |

---

## Building

```bash
# Tests per module
./gradlew :kap-core:jvmTest          # core tests (438 tests)
./gradlew :kap-resilience:jvmTest    # resilience tests (164 tests)
./gradlew :kap-arrow:test            # arrow integration tests (223 tests)
./gradlew :benchmarks:test           # benchmark comparison tests (81 tests)

# All tests at once
./gradlew jvmTest                    # recommended first command

# Full build
./gradlew build                      # auto-skips Apple targets without Xcode

# Examples
./gradlew :examples:ecommerce-checkout:run
./gradlew :examples:resilient-fetcher:run
./gradlew :examples:full-stack-order:run

# Benchmarks & docs
./gradlew :benchmarks:jmh            # JMH benchmarks
./gradlew dokkaHtml                  # API docs
./gradlew :kap-core:generateAll      # regenerate all overloads (arities 2-22)
```

> **First time?** Start with `./gradlew :kap-core:jvmTest` — runs core module tests on JVM without needing Xcode, Arrow, or native toolchains.
>
> **Native targets:** Apple targets (iOS, macOS) are automatically skipped when Xcode is not installed. Linux Native compiles with just the Kotlin toolchain.

## CI/CD

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

**Benchmark tracking:** [Live dashboard](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/) — regression alerts on every push, PR comparison blocks merge on >100% regression.

---

## Composition Guarantees

`Computation` satisfies standard composition laws (identity, associativity, homomorphism) — property-based tested with random inputs via Kotest. This means refactoring with these combinators is provably safe. See [LAWS.md](LAWS.md) for the full law tables and test references.

**906 tests across 61 suites in 3 modules. All passing.**

---

## License

Apache 2.0
