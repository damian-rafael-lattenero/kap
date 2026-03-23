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

*Applicative-first orchestration: the Haskell Applicative pattern, natively expressed in Kotlin coroutines. Not a framework. Not Arrow-lite. A precision tool for parallel dependency graphs where your code shape is your execution plan.*

**Three modules, pick what you need:**

| Module | What you get | Depends on |
|---|---|---|
| **`kap-core`** | `Computation`, `ap`, `lift`, `followedBy`, `race`, `traverse`, `memoize`, `timeout`, `recover` | `kotlinx-coroutines-core` only |
| **`kap-resilience`** | `Schedule`, `CircuitBreaker`, `Resource`, `bracket`, `raceQuorum`, `timeoutRace` | `kap-core` |
| **`kap-arrow`** | `zipV`, `apV`, `validated {}`, `attempt()`, `raceEither`, `Either`/`Nel` bridges | `kap-core` + Arrow Core |

```kotlin
val checkout: CheckoutResult = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐
        .ap { fetchCart(userId) }           // ├─ phase 1: parallel
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // ── phase 2: barrier
        .ap { calcShipping() }            // ┐
        .ap { calcTax() }                 // ├─ phase 3: parallel
        .ap { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }  // ── phase 4: barrier
        .ap { generateConfirmation() }    // ┐ phase 5: parallel
        .ap { sendReceiptEmail() }        // ┘
}
```

11 service calls. 5 phases. One flat chain. **Swap any two `.ap` lines → compiler error.** Each service returns a distinct type — the curried chain locks parameter order at compile time.

**130ms virtual time** (vs 460ms sequential) — verified in [`ConcurrencyProofTest.kt`](kap-core/src/jvmTest/kotlin/applicative/ConcurrencyProofTest.kt).

### Wait — `::CheckoutResult` is just a function

If you've never seen `::CheckoutResult` before, here's the key insight: **a Kotlin data class constructor *is* a function**.

```kotlin
data class Greeting(val text: String, val target: String)

// ::Greeting has type (String, String) -> Greeting
// So lift2(::Greeting) works — it wraps that function for parallel execution.
```

But `lift` works with **any** function, not just constructors:

```kotlin
// A regular function reference:
fun buildSummary(name: String, items: Int): String = "$name has $items items"

val summary: String = Async {
    lift2(::buildSummary)
        .ap { fetchName() }   // parallel
        .ap { fetchItemCount() }
}

// A stored lambda:
val greet: (String, Int) -> String = { name, age -> "Hi $name, you're $age" }

val greeting: String = Async {
    lift2(greet)
        .ap { fetchName() }
        .ap { fetchAge() }
}
```

Constructors are just the most common case because they give you compile-time parameter order safety for free — each slot expects a specific type, so swapping two `.ap` lines is a compiler error.

> All code examples on this page are compilable and verified in [`readme-examples`](examples/readme-examples/).

### `Computation` is a description, not an execution

Nothing runs until you wrap it in `Async {}`:

```kotlin
// This builds a plan — nothing runs yet
val plan: Computation<Dashboard> = lift3(::Dashboard)
    .ap { fetchUser() }     // NOT executed
    .ap { fetchCart() }     // NOT executed
    .ap { fetchPromos() }   // NOT executed

// NOW it runs — all three in parallel
val result: Dashboard = Async { plan }
println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
```

This means you can build computation graphs, store them, pass them around, and compose them — all without triggering any side effects. Execution only happens at the `Async` boundary.

### All `val`, no `null`, no `!!`

With raw coroutines, parallel results force you into mutable variables or nullable types:

```kotlin
// Raw coroutines: vars and nulls
var user: UserProfile? = null
var cart: ShoppingCart? = null
coroutineScope {
    launch { user = fetchUser() }
    launch { cart = fetchCart() }
}
// Now you need: user!! and cart!! everywhere — or worse, lateinit var
val dashboard = Dashboard(user!!, cart!!)
```

With KAP, the constructor receives everything at once. Every field is `val`. Nothing is ever `null`:

```kotlin
// KAP: complete construction, all val, no nulls
val dashboard: Dashboard = Async {
    lift2(::Dashboard)
        .ap { fetchUser() }
        .ap { fetchCart() }
}
```

This isn't just style — it's **correctness**. Your data classes can have all `val` fields with no default values, because KAP guarantees the constructor is called with all arguments at once. No partial construction. No temporal coupling.

But what does this replace?

---

## Here's What This Replaces

You have 11 microservice calls. Some can run in parallel, others depend on earlier results. You need to orchestrate them with maximum parallelism and clear phase boundaries. Here's what happens with each approach:

**Raw Coroutines — 30+ lines, invisible phases, shuttle variables:**

```kotlin
val checkout = coroutineScope {
    val dUser      = async { fetchUser(userId) }
    val dCart       = async { fetchCart(userId) }
    val dPromos    = async { fetchPromos(userId) }
    val dInventory = async { fetchInventory(userId) }
    val user       = dUser.await()         // ─┐
    val cart        = dCart.await()          //  │ 4 shuttle variables
    val promos     = dPromos.await()        //  │ just to bridge
    val inventory  = dInventory.await()     // ─┘ async → await

    val stock = validateStock(inventory)    // barrier — but you can't SEE it

    val dShipping  = async { calcShipping(cart) }
    val dTax       = async { calcTax(cart) }
    val dDiscounts = async { calcDiscounts(promos) }
    val shipping   = dShipping.await()
    val tax        = dTax.await()
    val discounts  = dDiscounts.await()

    val payment = reservePayment(user, cart)  // barrier — also invisible

    val dConfirmation = async { generateConfirmation(payment) }
    val dEmail        = async { sendReceiptEmail(user) }

    CheckoutResult(user, cart, promos, inventory, stock,
                   shipping, tax, discounts, payment,
                   dConfirmation.await(), dEmail.await())
}
// Where are the phase boundaries? Which calls are parallel? Which are sequential?
// Move one await() above its async → silently serialized. Compiler won't warn.
```

**Arrow — nested `parZip` blocks, max 9 args, phases still invisible:**

```kotlin
val (user, cart, promos, inventory) = parZip(
    { fetchUser(userId) }, { fetchCart(userId) },
    { fetchPromos(userId) }, { fetchInventory(userId) }
) { u, c, p, i -> Tuple4(u, c, p, i) }

val stock = validateStock(inventory)

val (shipping, tax, discounts) = parZip(
    { calcShipping(cart) }, { calcTax(cart) }, { calcDiscounts(promos) }
) { s, t, d -> Triple(s, t, d) }

val payment = reservePayment(user, cart)

val (confirmation, email) = parZip(
    { generateConfirmation(payment) }, { sendReceiptEmail(user) }
) { c, e -> c to e }

CheckoutResult(user, cart, promos, inventory, stock,
               shipping, tax, discounts, payment, confirmation, email)
// Better than raw, but: 3 separate parZip blocks, intermediate tuple destructuring,
// max 9 args per parZip, phases are separate statements — not a single readable chain.
```

**KAP — 12 lines, single flat chain, visible phases:** *(kap-core)*

```kotlin
val checkout = Async {
    lift11(::CheckoutResult)
        .ap { fetchUser(userId) }          // ┐ phase 1
        .ap { fetchCart(userId) }           // │
        .ap { fetchPromos(userId) }        // │
        .ap { fetchInventory(userId) }     // ┘
        .followedBy { validateStock() }    // barrier
        .ap { calcShipping() }            // ┐ phase 3
        .ap { calcTax() }                 // │
        .ap { calcDiscounts() }           // ┘
        .followedBy { reservePayment() }  // barrier
        .ap { generateConfirmation() }    // ┐ phase 5
        .ap { sendReceiptEmail() }        // ┘
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

## When Phase 2 Depends on Phase 1: `flatMap`

The checkout above uses `.followedBy` for barriers — phase 2 doesn't need phase 1's *values*, just needs it to finish. But what if phase 2 **uses** phase 1's results?

**Raw Coroutines — three separate `coroutineScope` blocks, manual variable threading:**

```kotlin
// Phase 1: fetch user context (3 calls in parallel)
val ctx = coroutineScope {
    val dProfile = async { fetchProfile(userId) }
    val dPrefs   = async { fetchPreferences(userId) }
    val dTier    = async { fetchLoyaltyTier(userId) }
    UserContext(dProfile.await(), dPrefs.await(), dTier.await())
}
// Phase 2: fetch personalized content USING ctx (4 calls in parallel)
val enriched = coroutineScope {
    val dRecs     = async { fetchRecommendations(ctx.profile) }
    val dPromos   = async { fetchPromotions(ctx.tier) }
    val dTrending = async { fetchTrending(ctx.prefs) }
    val dHistory  = async { fetchHistory(ctx.profile) }
    EnrichedContent(dRecs.await(), dPromos.await(), dTrending.await(), dHistory.await())
}
// Phase 3: finalize USING both ctx and enriched (2 calls in parallel)
val dashboard = coroutineScope {
    val dLayout = async { renderLayout(ctx, enriched) }
    val dTrack  = async { trackAnalytics(ctx, enriched) }
    FinalDashboard(dLayout.await(), dTrack.await())
}
// 3 separate coroutineScope blocks. ctx and enriched threaded manually.
// Where are the phase boundaries? Which calls depend on what? Invisible.
```

**KAP — single expression, dependencies are the structure:**

```kotlin
val dashboard: FinalDashboard = Async {
    lift3(::UserContext)
        .ap { fetchProfile(userId) }         // ┐
        .ap { fetchPreferences(userId) }     // ├─ phase 1 (parallel)
        .ap { fetchLoyaltyTier(userId) }     // ┘
    .flatMap { ctx ->                        // ── barrier: phase 2 NEEDS ctx
        lift4(::EnrichedContent)
            .ap { fetchRecommendations(ctx.profile) }  // ┐
            .ap { fetchPromotions(ctx.tier) }           // ├─ phase 2 (parallel)
            .ap { fetchTrending(ctx.prefs) }            // │
            .ap { fetchHistory(ctx.profile) }           // ┘
        .flatMap { enriched ->                          // ── barrier: phase 3 NEEDS enriched
            lift2(::FinalDashboard)
                .ap { renderLayout(ctx, enriched) }     // ┐ phase 3 (parallel)
                .ap { trackAnalytics(ctx, enriched) }   // ┘
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

## What Only KAP Can Do

These features solve real problems that neither raw coroutines nor Arrow address. Each links to a detailed section below:

- **Partial failure tolerance** — `.settled()` wraps individual branches in `Result` so one failure doesn't cancel siblings. Raw coroutines cancel everything; Arrow has no equivalent. → [Section 7](#7-partial-failure-tolerance-with-settled-kap-core)
- **Timeout with parallel fallback** — `timeoutRace` starts both primary and fallback at t=0. Raw coroutines wait for the timeout *then* start the fallback. **6x faster in benchmarks.** → [Section 8](#8-timeout-with-parallel-fallback-kap-resilience)
- **Quorum consensus** — `raceQuorum(2, a, b, c)` returns the 2 fastest successes, cancels the rest. No primitive for this anywhere. → [Section 9](#9-quorum-consensus--n-of-m-successes-kap-resilience)
- **Compile-time argument order safety** — Swap two `.ap` lines of the same type → compiler error. Raw coroutines and Arrow use positional args — silent bugs. → [Section 11](#11-compile-time-argument-order-safety-kap-core)
- **Success-only memoization** — `.memoizeOnSuccess()` caches successes but retries failures. No manual `Mutex` + double-checked locking. → [Section 12](#12-memoization-with-success-only-caching-kap-core)
- **Parallel validation up to 22 fields** — `zipV` collects every error in parallel. Raw coroutines can't; Arrow's `zipOrAccumulate` stops at 9. → [Section 1](#1-parallel-validation--collect-every-error-kap-arrow)

---

## Three Primitives — That's the Whole Model

The entire library is built on **three primitives**. If you understand these, you understand everything:

| Primitive | What it does | Think of it as |
|---|---|---|
| `.ap { }` | Launch in parallel with everything above | "and at the same time..." |
| `.followedBy { }` | Wait for everything above, then continue | "then, once that's done..." |
| `.flatMap { ctx -> }` | Wait for everything above, pass the result, then continue | "then, using what we got..." |

```kotlin
// .ap — parallel: both launch at t=0
lift2(::Pair)
    .ap { fetchA() }   // ┐ parallel
    .ap { fetchB() }   // ┘

// .followedBy — barrier: C waits for A and B
lift3(::R)
    .ap { fetchA() }             // ┐ parallel
    .ap { fetchB() }             // ┘
    .followedBy { validate() }   // waits for A and B

// .flatMap — barrier that passes data: phase 2 USES phase 1 results
lift4(::UserContext)
    .ap { fetchProfile(userId) }       // ┐ phase 1: parallel
    .ap { fetchPreferences(userId) }   // │
    .ap { fetchLoyaltyTier(userId) }   // │
    .ap { fetchRecentOrders(userId) }  // ┘
.flatMap { ctx ->                      // ── barrier: phase 2 NEEDS ctx
    lift3(::PersonalizedDashboard)
        .ap { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
        .ap { fetchPromotions(ctx.loyalty) }         // │ uses data from phase 1
        .ap { fetchTrending(ctx.preferences) }       // ┘
}
```

```
flatMap execution timeline:

t=0ms   ─── fetchProfile ───────┐
t=0ms   ─── fetchPreferences ──├─ phase 1 (parallel, all 4)
t=0ms   ─── fetchLoyaltyTier ──┤
t=0ms   ─── fetchRecentOrders ─┘
t=50ms  ─── flatMap { ctx -> ... }  ── barrier, ctx available
t=50ms  ─── fetchRecommendations(ctx.profile) ───┐
t=50ms  ─── fetchPromotions(ctx.loyalty) ─────────├─ phase 2 (parallel, all 3)
t=50ms  ─── fetchTrending(ctx.preferences) ───────┘
t=80ms  ─── PersonalizedDashboard ready
```

---

## Module Architecture

KAP is split into three modules so you only pay for what you use. Non-Arrow projects get a lean DSL with zero Arrow types in auto-complete. Arrow users add `kap-arrow` for native `Either`/`NonEmptyList` integration.

```
┌─────────────────────────────────────────────────────────────┐
│                        kap-arrow                            │
│  zipV · apV · validated {} · attempt() · raceEither         │
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
│  Computation · ap · followedBy · flatMap · lift · zip · mapN│
│  race · traverse · memoize · timeout · recover · retry(n)   │
│  settled · catching · Deferred/Flow bridges                 │
│    depends on: kotlinx-coroutines-core (JVM, JS, Native)    │
└─────────────────────────────────────────────────────────────┘
```

## Quick Start

**Pick only the modules you need:**

```kotlin
// build.gradle.kts
dependencies {
    // Core applicative DSL — the only required module (zero deps beyond coroutines)
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
        lift3(::Dashboard)
            .ap { fetchUser() }    // ┐ all three in parallel
            .ap { fetchCart() }    // │ total time = max(individual)
            .ap { fetchPromos() }  // ┘ not sum
    }
    println(result) // Dashboard(user=Alice, cart=3 items, promos=SAVE20)
}
```

Add resilience to any branch — each module composes naturally:

```kotlin
import applicative.*   // kap-core
// + kap-resilience for Schedule, CircuitBreaker, bracket, timeoutRace

val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)
val retryPolicy = Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds)

val result = Async {
    lift3(::Dashboard)
        .ap(Computation { fetchUser() }
            .withCircuitBreaker(breaker)
            .retry(retryPolicy))
        .ap(Computation { fetchFromSlowApi() }
            .timeoutRace(200.milliseconds, Computation { fetchFromCache() }))
        .ap { fetchPromos() }
}
```

Add validation with Arrow types:

```kotlin
import applicative.*   // kap-core + kap-arrow
import arrow.core.Either
import arrow.core.NonEmptyList

val registration = Async {
    liftV4<RegError, ValidName, ValidEmail, ValidAge, ValidUsername, User>(::User)
        .apV { validateName(input.name) }      // ┐ all 4 in parallel
        .apV { validateEmail(input.email) }    // │ errors accumulated
        .apV { validateAge(input.age) }        // │ (not short-circuited)
        .apV { checkUsername(input.username) }  // ┘
}
// 3 fail? → Either.Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow))
// All pass? → Either.Right(User(...))
```

> **Tip (kap-arrow):** `Validated<E, A>` is a built-in typealias for `Computation<Either<NonEmptyList<E>, A>>` and `Nel<A>` for `NonEmptyList<A>` — reduces type verbosity in validated signatures.

### Choose Your Style

Three ways to compose parallel operations — pick what fits:

```kotlin
// Style 1: lift + ap — compile-time parameter order safety via curried types (recommended for 10+ args)
lift3(::Dashboard)
    .ap { fetchUser() }
    .ap { fetchCart() }
    .ap { fetchPromos() }

// Style 2: liftA — Haskell-named, parZip-like ergonomics (great for 2-9 args)
liftA3(
    { fetchUser() },
    { fetchCart() },
    { fetchPromos() },
) { user, cart, promos -> Dashboard(user, cart, promos) }

// Style 3: mapN / zip — takes pre-built Computations (familiar to Arrow users)
mapN(
    Computation { fetchUser() },
    Computation { fetchCart() },
    Computation { fetchPromos() },
) { user, cart, promos -> Dashboard(user, cart, promos) }

// Bonus: product — returns Pair/Triple (Haskell: (,) <$> fa <*> fb)
val (user, cart) = Async { product({ fetchUser() }, { fetchCart() }) }
```

All styles are parallel by default. `lift+ap` gives stronger type safety; `liftA`/`mapN` give simpler syntax.

---

## Feature Showcase

Every feature below follows the same structure: **the real-world problem** you face, how **raw coroutines** handle it (or fail to), how **Arrow** handles it (or where it falls short), and how **KAP** solves it. Each section is tagged with the module that provides it.

---

### 1. Parallel Validation — Collect *Every* Error `kap-arrow`

**The problem:** You have a registration form with 12 fields. You want to validate all of them in parallel and return *every* error at once — not just the first one. The user shouldn't have to fix errors one at a time.

**Raw Coroutines — impossible.** Structured concurrency cancels all siblings when any coroutine fails. You literally cannot collect all errors in parallel:

```kotlin
coroutineScope {
    val a = async { validateName(input) }    // if this throws...
    val b = async { validateEmail(input) }   // ...this gets cancelled
    val c = async { validatePhone(input) }   // ...this gets cancelled too
    // You only see the first error. The user has no idea about the other 11.
}
// You'd need supervisorScope + manual Result<T> wrapping on every call
// + manual error collection. That's 40+ lines for 12 validators.
```

**Arrow — `zipOrAccumulate`, max 9 args, not truly parallel:**

```kotlin
either {
    zipOrAccumulate(
        { validateName(input.name).bind() },
        { validateEmail(input.email).bind() },
        { validatePhone(input.phone).bind() },
        { validatePassword(input.password).bind() },
        { validateBirth(input.birthDate).bind() },
        { validateCountry(input.country).bind() },
        { validateCity(input.city).bind() },
        { validateZip(input.zipCode).bind() },
        { validateAddress(input.address).bind() },
        // STOP — zipOrAccumulate maxes out at 9 arguments.
        // For 12 validators, you need to nest or split into groups.
    ) { name, email, phone, pass, birth, country, city, zip, addr ->
        PartialRegistration(name, email, phone, pass, birth, country, city, zip, addr)
    }
}
// Then a second zipOrAccumulate for the remaining 3, then combine.
```

**KAP — `zipV`, up to 22 validators, all in parallel:** *(kap-arrow)*

```kotlin
val result = Async {
    zipV(
        { validateName(input.name) },
        { validateEmail(input.email) },
        { validatePhone(input.phone) },
        { validatePassword(input.password) },
        { validateBirth(input.birthDate) },
        { validateCountry(input.country) },
        { validateCity(input.city) },
        { validateZip(input.zipCode) },
        { validateAddress(input.address) },
        { validateTaxId(input.taxId) },
        { validateTerms(input.terms) },
        { validateCaptcha(input.captcha) },
    ) { name, email, phone, pass, birth, country, city, zip, addr, tax, terms, captcha ->
        UserRegistration(name, email, phone, pass, birth, country, city, zip, addr, tax, terms, captcha)
    }
}
// 7 of 12 fail? → Left(NonEmptyList(NameTooShort, InvalidEmail, ..., CaptchaExpired))
// All pass?     → Right(UserRegistration(...))
```

**43.9ms** (vs 173.8ms sequential) — JMH verified.

---

### 2. Value-Dependent Phases with `flatMap` `kap-core`

**The problem:** Your dashboard needs user context (profile, preferences, loyalty tier) before it can fetch personalized content. Phase 2 *depends* on the result of Phase 1. You need a barrier that passes data forward.

**Raw Coroutines — manual variable threading across scopes:**

```kotlin
val ctx = coroutineScope {
    val dProfile     = async { fetchProfile(userId) }
    val dPrefs       = async { fetchPreferences(userId) }
    val dLoyalty     = async { fetchLoyaltyTier(userId) }
    val dOrders      = async { fetchRecentOrders(userId) }
    UserContext(dProfile.await(), dPrefs.await(), dLoyalty.await(), dOrders.await())
}
// ctx is now available — but we had to close the first coroutineScope
val dashboard = coroutineScope {
    val dRecs    = async { fetchRecommendations(ctx.profile) }
    val dPromos  = async { fetchPromotions(ctx.loyalty) }
    val dTrend   = async { fetchTrending(ctx.preferences) }
    PersonalizedDashboard(dRecs.await(), dPromos.await(), dTrend.await())
}
// Two separate coroutineScope blocks. Manual ctx threading.
// The dependency between phases is invisible in the code structure.
```

**Arrow — sequential `parZip` blocks, same problem:**

```kotlin
val ctx = parZip(
    { fetchProfile(userId) }, { fetchPreferences(userId) },
    { fetchLoyaltyTier(userId) }, { fetchRecentOrders(userId) }
) { p, pr, l, o -> UserContext(p, pr, l, o) }

val dashboard = parZip(
    { fetchRecommendations(ctx.profile) },
    { fetchPromotions(ctx.loyalty) },
    { fetchTrending(ctx.preferences) },
) { r, p, t -> PersonalizedDashboard(r, p, t) }
// Cleaner than raw, but still two separate blocks. The "ctx" dependency
// is expressed through variable scoping, not through the structure.
```

**KAP — `flatMap` makes the dependency explicit:** *(kap-core)*

```kotlin
val dashboard = Async {
    lift4(::UserContext)
        .ap { fetchProfile(userId) }       // ┐ phase 1: parallel
        .ap { fetchPreferences(userId) }   // │
        .ap { fetchLoyaltyTier(userId) }   // │
        .ap { fetchRecentOrders(userId) }  // ┘
    .flatMap { ctx ->                      // ── barrier: phase 2 NEEDS ctx
        lift3(::PersonalizedDashboard)
            .ap { fetchRecommendations(ctx.profile) }   // ┐ phase 2: parallel
            .ap { fetchPromotions(ctx.loyalty) }         // │ uses data from phase 1
            .ap { fetchTrending(ctx.preferences) }       // ┘
    }
}
// One expression. The dependency is the structure. Phase 2 cannot start
// without ctx — and that's visible in the code shape.
```

---

### 3. Retry with Composable Backoff Policies `kap-resilience`

**The problem:** A downstream service is flaky. You need to retry with exponential backoff, jitter (to prevent thundering herd), a max duration, and you only want to retry on IOExceptions — not on 4xx client errors.

**Raw Coroutines — manual retry loop, ~20 lines:**

```kotlin
var lastError: Throwable? = null
var delay = 100L  // milliseconds
val maxDuration = 30_000L
val startTime = System.currentTimeMillis()
for (attempt in 1..5) {
    try {
        return fetchUser(userId)
    } catch (e: CancellationException) {
        throw e  // never catch cancellation!
    } catch (e: IOException) {
        lastError = e
        if (System.currentTimeMillis() - startTime > maxDuration) break
        val jitter = (delay * 0.5 * Math.random()).toLong()
        kotlinx.coroutines.delay(delay + jitter)
        delay = (delay * 2).coerceAtMost(10_000L)
    } catch (e: Throwable) {
        throw e  // don't retry client errors
    }
}
throw lastError!!
// 20 lines. Easy to get the jitter math wrong. Easy to forget CancellationException.
// And you have to copy-paste this everywhere.
```

**Arrow — `Schedule` composition (similar API):**

```kotlin
val policy = Schedule.recurs<Throwable>(5)
    .and(Schedule.exponential(100.milliseconds))
    .doWhile { it is IOException }

Schedule.retry(policy) { fetchUser(userId) }
```

**KAP — `Schedule` composition, integrated with `Computation`:** *(kap-resilience)*

```kotlin
val policy = Schedule.recurs<Throwable>(5) and
    Schedule.exponential(100.milliseconds, max = 10.seconds) and
    Schedule.doWhile { it is IOException }

val result = Async {
    Computation { fetchUser(userId) }
        .retry(policy)
}

// Or inline — retry is just another method on Computation:
val result = Async {
    lift4(::CheckoutResult)
        .ap { fetchUser() }
        .ap { flakyService().retry(3, delay = 10.milliseconds) }
        .ap { slowService().timeout(40.milliseconds, default = "cached") }
        .ap { race(primaryAPI(), cacheAPI()) }
}
// Each branch handles its own resilience. All branches run in parallel.
// Total wall time = max(branch times). Verified: 40ms virtual time.
```

Schedule building blocks:

```kotlin
Schedule.recurs(n)                          // max N retries
Schedule.spaced(d)                          // constant delay
Schedule.exponential(base, max)             // 100ms, 200ms, 400ms...
Schedule.fibonacci(base)                    // 100ms, 100ms, 200ms, 300ms, 500ms...
Schedule.linear(base)                       // 100ms, 200ms, 300ms, 400ms...
Schedule.forever()                          // infinite (combine with backoff)
schedule.jittered()                         // ±50% random spread
schedule.withMaxDuration(d)                 // hard time limit
schedule.doWhile { it is IOException }      // retry predicate
s1 and s2                                   // both must agree (max delay)
s1 or s2                                    // either can continue (min delay)
```

---

### 4. Resource Safety in Parallel `kap-resilience`

**The problem:** You need to open three connections (database, cache, HTTP client), use them all in parallel to fetch data, and guarantee that *all three* are closed even if one branch fails — even if the failure is a cancellation.

**Raw Coroutines — try/finally spaghetti, no parallel guarantee:**

```kotlin
val db = openDbConnection()
try {
    val cache = openCacheConnection()
    try {
        val http = openHttpClient()
        try {
            coroutineScope {
                val dDb    = async { db.query("SELECT ...") }
                val dCache = async { cache.get("key") }
                val dHttp  = async { http.get("/api") }
                Result(dDb.await(), dCache.await(), dHttp.await())
            }
        } finally { http.close() }
    } finally { cache.close() }
} finally { db.close() }
// 3 levels of nesting. If you forget one finally → resource leak.
// If a branch fails, sibling resources might not release cleanly.
```

**Arrow — `Resource` monad (similar API):**

```kotlin
val infra = Resource.zip(
    Resource({ openDb() }, { it, _ -> it.close() }),
    Resource({ openCache() }, { it, _ -> it.close() }),
    Resource({ openHttp() }, { it, _ -> it.close() }),
)
infra.use { (db, cache, http) ->
    parZip(
        { db.query("SELECT ...") },
        { cache.get("key") },
        { http.get("/api") },
    ) { a, b, c -> Result(a, b, c) }
}
```

**KAP — `bracket` for inline, `Resource` for composed:** *(kap-resilience)*

```kotlin
// Option 1: bracket — acquire, use in parallel, guaranteed release
val result = Async {
    lift3 { db: String, cache: String, api: String -> "$db|$cache|$api" }
        .ap(bracket(
            acquire = { openDbConnection() },
            use = { conn -> Computation { conn.query("SELECT ...") } },
            release = { conn -> conn.close() },
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
// All 3 acquired and used IN PARALLEL.
// Any branch fails → ALL released (NonCancellable context).

// Option 2: Resource monad — compose first, use later
val infra = Resource.zip(
    Resource({ openDb() }, { it.close() }),
    Resource({ openCache() }, { it.close() }),
    Resource({ openHttp() }, { it.close() }),
) { db, cache, http -> Triple(db, cache, http) }

val result = Async {
    infra.useComputation { (db, cache, http) ->
        lift3(::DashboardData)
            .ap { db.query("SELECT ...") }
            .ap { cache.get("user:prefs") }
            .ap { http.get("/recommendations") }
    }
}
// Released in reverse order, even on failure. NonCancellable.
```

Use `bracketCase` when release behavior depends on the outcome:

```kotlin
bracketCase(
    acquire = { openTransaction() },
    use = { tx -> Computation { tx.execute("INSERT ...") } },
    release = { tx, case ->
        when (case) {
            is ExitCase.Completed<*> -> tx.commit()
            else -> tx.rollback()
        }
        tx.close()
    },
)
```

---

### 5. Racing — First to Succeed Wins `kap-core` `kap-arrow`

**The problem:** You have 3 regional replicas. You want the fastest response — and immediately cancel the losers to free resources.

**Raw Coroutines — complex `select` clause:**

```kotlin
coroutineScope {
    val us = async { fetchFromRegionUS() }   // 200ms
    val eu = async { fetchFromRegionEU() }   // 50ms
    val ap = async { fetchFromRegionAP() }   // 100ms

    select<String> {
        us.onAwait { eu.cancel(); ap.cancel(); it }
        eu.onAwait { us.cancel(); ap.cancel(); it }
        ap.onAwait { us.cancel(); eu.cancel(); it }
    }
}
// 9 lines for 3 competitors. Manual cancel() for each loser.
// Add a 4th competitor → 4 more cancel() calls. Scales O(n²).
```

**Arrow — `raceN` (similar):**

```kotlin
raceN(
    { fetchFromRegionUS() },
    { fetchFromRegionEU() },
    { fetchFromRegionAP() },
)
```

**KAP — `raceN` (kap-core) + `raceEither` for heterogeneous types (kap-arrow):**

```kotlin
val fastest = Async {
    raceN(
        Computation { fetchFromRegionUS() },   // slow (200ms)
        Computation { fetchFromRegionEU() },   // fast (50ms)
        Computation { fetchFromRegionAP() },   // medium (100ms)
    )
}
// Returns EU response at 50ms. US and AP cancelled immediately.

// Race with DIFFERENT types using raceEither:
val result: Either<CachedUser, FreshUser> = Async {
    raceEither(
        fa = Computation { fetchFromCache(userId) },     // fast but stale
        fb = Computation { fetchFromDatabase(userId) },  // slow but fresh
    )
}
// Left(cachedUser) if cache wins, Right(freshUser) if DB wins.
```

---

### 6. Bounded Parallel Collection Processing `kap-core`

**The problem:** You have 200 user IDs to fetch. You want to process them in parallel, but your downstream API can only handle 10 concurrent requests before it starts throttling.

**Raw Coroutines — manual Semaphore management:**

```kotlin
val semaphore = Semaphore(10)
val results = coroutineScope {
    userIds.map { id ->
        async {
            semaphore.withPermit {
                fetchUser(id)
            }
        }
    }.awaitAll()
}
// Works, but you have to wire the Semaphore yourself every time.
// Forget the withPermit → you overwhelm the downstream service.
```

**Arrow — `parMap` with concurrency:**

```kotlin
val results = userIds.parMap(concurrency = 10) { id ->
    fetchUser(id)
}
```

**KAP — `traverse` with concurrency:** *(kap-core)*

```kotlin
val results = Async {
    userIds.traverse(concurrency = 10) { id ->
        Computation { fetchUser(id) }
    }
}
// 200 items @ 30ms each, concurrency=10 → ~600ms (not 6,000ms).
```

---

> ### Features That Don't Exist Anywhere Else
>
> The following features are **unique to KAP**. They solve real problems that neither raw coroutines nor Arrow address.

---

### 7. Partial Failure Tolerance with `settled` `kap-core`

> **UNIQUE TO KAP** — neither raw coroutines nor Arrow offer this.

**The problem:** You're building a dashboard. If the user service fails, you still want to show the cart and config — just with an anonymous user fallback. But structured concurrency cancels *all* siblings when *any* coroutine fails. You don't want that.

**Raw Coroutines — `supervisorScope` breaks guarantees:**

```kotlin
// supervisorScope prevents sibling cancellation, but:
// 1. You lose automatic cleanup guarantees
// 2. You need to manually wrap every call in Result
// 3. You need to manually check each Result
supervisorScope {
    val user   = async { runCatching { fetchUser() } }
    val cart   = async { fetchCart() }
    val config = async { fetchConfig() }

    Dashboard(
        user = user.await().getOrDefault(User.anonymous()),
        cart = cart.await(),    // what if THIS fails? need runCatching too?
        config = config.await() // inconsistent wrapping
    )
}
// Easy to miss wrapping one call. No compile-time safety.
```

**Arrow — no equivalent.** Arrow's `parZip` follows structured concurrency (any failure cancels siblings). There's no built-in way to say "this branch is optional, keep going if it fails."

**KAP — `.settled()`, one method:** *(kap-core)*

```kotlin
val dashboard = Async {
    lift3 { user: Result<User>, cart: Cart, config: Config ->
        Dashboard(user.getOrDefault(User.anonymous()), cart, config)
    }
        .ap(Computation { fetchUser() }.settled())  // won't cancel siblings on failure
        .ap { fetchCart() }
        .ap { fetchConfig() }
}
// fetchUser fails? Dashboard still builds with anonymous user.
// fetchCart fails? Everything cancels (it's not settled).

// For collections — traverseSettled processes ALL items:
val results: List<Result<User>> = Async {
    userIds.traverseSettled { id -> Computation { fetchUser(id) } }
}
val successes = results.filter { it.isSuccess }.map { it.getOrThrow() }
val failures = results.filter { it.isFailure }.map { it.exceptionOrNull()!! }
// Unlike traverse, NO siblings are cancelled on failure.
```

---

### 8. Timeout with Parallel Fallback `kap-resilience`

> **UNIQUE TO KAP** — neither raw coroutines nor Arrow offer this.

**The problem:** Your primary API has a 500ms timeout. If it times out, you want to hit a fallback cache. With a sequential approach, you *wait* the full 500ms before even starting the fallback — wasting time. You want the fallback to start immediately alongside the primary, and take whichever finishes first.

**Raw Coroutines — sequential timeout wastes time:**

```kotlin
val result = try {
    withTimeout(500) { fetchFromPrimary() }  // waits up to 500ms
} catch (e: TimeoutCancellationException) {
    fetchFromFallback()  // THEN starts the fallback — total: 500ms + fallback time
}
// If primary takes 500ms and fallback takes 200ms → total 700ms
```

**Arrow — no `timeoutRace` equivalent.** You'd have to manually compose `race` with timeout logic.

**KAP — `timeoutRace` starts both immediately:** *(kap-resilience)*

```kotlin
val result = Async {
    Computation { fetchFromPrimary() }
        .timeoutRace(500.milliseconds, Computation { fetchFromFallback() })
}
// Fallback starts immediately at t=0. If primary wins before 500ms → use it.
// If primary times out → fallback is ALREADY RUNNING, maybe already done.
// Primary 500ms + fallback 200ms → total 200ms (not 700ms!) — 3.5x faster.
```

**JMH verified:** `timeoutRace` 34.0ms vs sequential timeout 87.2ms — **2.6x faster**.

---

### 9. Quorum Consensus — N-of-M Successes `kap-resilience`

> **UNIQUE TO KAP** — neither raw coroutines nor Arrow offer this.

**The problem:** You have 3 database replicas. For consistency, you need at least 2 of 3 to agree (quorum). Or you're doing hedged requests to 5 CDN nodes and want the 3 fastest. There's no primitive for this anywhere.

**Raw Coroutines — no primitive.** You'd need a manual `select` + atomic counter + cancellation logic. It's ~30 lines of tricky concurrent code.

**Arrow — no equivalent.**

**KAP — `raceQuorum`, one line:** *(kap-resilience)*

```kotlin
val quorum = Async {
    raceQuorum(
        required = 2,  // need 2 of 3 to agree
        Computation { fetchFromReplicaA() },
        Computation { fetchFromReplicaB() },
        Computation { fetchFromReplicaC() },
    )
}
// Returns the 2 fastest successes. Third replica cancelled.
// If 2+ fail → throws (quorum impossible).
```

---

### 10. Circuit Breaker — Protect Downstream from Cascading Failures `kap-resilience`

> **UNIQUE TO KAP** — Arrow has `CircuitBreaker` in a separate module (`arrow-resilience`), but KAP's version composes directly with the `Computation` chain.

**The problem:** A downstream service is degraded. Every request to it takes 30 seconds and then times out. You're sending 100 requests/second. That's 3,000 pending connections — your service is now degraded too. You need to stop calling it after N failures and auto-recover later.

**Raw Coroutines — manual state machine, ~50 lines:**

```kotlin
class ManualCircuitBreaker {
    private val failureCount = AtomicInteger(0)
    private val state = AtomicReference(State.CLOSED)
    private val lastFailure = AtomicLong(0)

    suspend fun <T> execute(block: suspend () -> T): T {
        return when (state.get()) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailure.get() > resetTimeout) {
                    state.set(State.HALF_OPEN)
                    tryExecute(block)
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            State.HALF_OPEN -> tryExecute(block)
            State.CLOSED -> tryExecute(block)
        }
    }
    // ... another 30 lines of tryExecute, state transitions, etc.
}
```

**KAP — 3 lines to create, one method to use:** *(kap-resilience)*

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

val result = Async {
    Computation { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.recurs<Throwable>(3) and Schedule.exponential(100.milliseconds))
        .recover { UserProfile.cached() }
}
// timeout → circuit breaker → retry → recover. All composable.
// breaker is shared across all callers — one instance protects the whole service.
```

---

### 11. Compile-Time Argument Order Safety `kap-core`

> **UNIQUE TO KAP** — neither raw coroutines nor Arrow enforce parameter order at compile time.

**The problem:** You have a function that takes 15 parameters — a realistic BFF aggregation. With raw coroutines, you pass them by position. Swap two parameters of the same type → silent bug, compiles fine, fails at runtime (or worse: succeeds with wrong data).

**Raw Coroutines — positional arguments, silent bugs:**

```kotlin
coroutineScope {
    val profile = async { fetchProfile(userId) }       // UserProfile
    val prefs   = async { fetchPreferences(userId) }   // UserPreferences
    // ... 13 more async calls ...

    DashboardPage(
        profile.await(),
        prefs.await(),
        // Oops — swapped these two. Both are String. Compiles fine.
        fetchNotifications.await(),  // should be slot 5
        fetchMessages.await(),       // should be slot 4
        // ...
    )
}
// No compiler warning. Incorrect data silently propagated.
```

**Arrow — named lambda args help, but don't enforce:**

```kotlin
parZip(
    { fetchProfile(userId) },
    { fetchPreferences(userId) },
    // max 9 args...
) { profile, prefs /* ... */ ->
    // You CHOOSE the names. Nothing stops you from writing:
    // { prefs, profile -> ... }  ← compiles fine if same type
    DashboardPage(profile, prefs)
}
```

**KAP — curried types reject wrong order at compile time:** *(kap-core)*

```kotlin
Async {
    lift15(::DashboardPage)
        .ap { fetchProfile(userId) }       // returns UserProfile     — slot 1
        .ap { fetchPreferences(userId) }   // returns UserPreferences — slot 2
        // Swap these two lines?
        // .ap { fetchLoyaltyTier(userId) }  // returns LoyaltyTier — COMPILE ERROR
        // .ap { fetchPreferences(userId) }  // expected LoyaltyTier, got UserPreferences
}
// The curried function chain (A) -> (B) -> ... -> (O) -> Result
// enforces that each .ap provides exactly the right type for its slot.
```

For same-type parameters, use value classes for full swap-safety:

```kotlin
@JvmInline value class ValidName(val value: String)
@JvmInline value class ValidEmail(val value: String)
// Now even "string-like" types are compile-time distinct
```

---

### 12. Memoization with Success-Only Caching `kap-core`

> **UNIQUE TO KAP** — `memoizeOnSuccess()` retries on failure instead of caching the error.

**The problem:** You want to cache an expensive computation so it only runs once. Standard memoization caches the result — but what if it *failed*? A transient network error gets cached forever, and every subsequent call returns the cached error instead of retrying.

**Raw Coroutines — manual Mutex + nullable cache:**

```kotlin
class MemoizedFetcher {
    private val mutex = Mutex()
    private var cached: Result<User>? = null

    suspend fun get(): User {
        cached?.let { if (it.isSuccess) return it.getOrThrow() }
        return mutex.withLock {
            cached?.let { if (it.isSuccess) return it.getOrThrow() }
            val result = runCatching { fetchUser() }
            if (result.isSuccess) cached = result
            result.getOrThrow()
        }
    }
}
// 15 lines. Double-checked locking. Easy to get wrong.
```

**Arrow — no direct equivalent.** Arrow has memoization in some modules, but no built-in "retry on failure" semantic.

**KAP — one method:** *(kap-core)*

```kotlin
val fetchUser = Computation { fetchUser() }.memoizeOnSuccess()

// First call: executes, caches on success
val a = Async { fetchUser }  // runs the actual fetch
val b = Async { fetchUser }  // returns cached result instantly

// If first call FAILS: not cached, next call retries
// If first call SUCCEEDS: cached forever, never runs again
```

---

### 13. Phased Validation — Parallel Within, Sequential Between `kap-arrow`

**The problem:** Your registration has two phases. Phase 1: validate name, email, age in parallel. Phase 2: check username availability and blacklist — but *only if phase 1 passes*. You want to save the network calls if basic validation already failed.

**Raw Coroutines — impossible.** You can't combine parallel error accumulation (needs `supervisorScope`) with short-circuiting (needs structured concurrency). These are contradictory requirements.

**Arrow — manual nesting:**

```kotlin
val phase1 = either {
    zipOrAccumulate(
        { validateName(input).bind() },
        { validateEmail(input).bind() },
        { validateAge(input).bind() },
    ) { n, e, a -> Identity(n, e, a) }
}
val phase2 = phase1.flatMap { id ->
    either {
        zipOrAccumulate(
            { checkBlocked(id).bind() },
            { checkAvailable(id).bind() },
        ) { a, b -> Reg(id, a, b) }
    }
}
// Two separate either blocks. Manual flatMap between them.
```

**KAP — `accumulate` builder with `bindV`:** *(kap-arrow)*

```kotlin
val result = Async {
    accumulate {
        val identity = zipV(
            { validateName(input.name) },
            { validateEmail(input.email) },
            { validateAge(input.age) },
        ) { name, email, age -> Identity(name, email, age) }
            .bindV()  // executes + unwraps Right, short-circuits on Left

        val cleared = zipV(
            { checkNotBlacklisted(identity) },
            { checkUsernameAvailable(identity.email) },
        ) { a, b -> Clearance(a, b) }
            .bindV()

        Registration(identity, cleared)
    }
}
// Phase 1 (parallel validation) → bindV → Phase 2 (parallel checks) → result
// Phase 1 fails? → short-circuit, phase 2 never runs (saves network calls)
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
| **Compile-time arg safety** | No (positional) | No (named lambda) | **Curried types enforce order** | `kap-core` |
| **Success-only memoization** | Manual Mutex + cache | No equivalent | **`.memoizeOnSuccess()`** | `kap-core` |

---

## How It Works Under the Hood

**Step 1: Currying.** `lift11(::CheckoutResult)` takes your 11-parameter constructor and curries it into a chain of single-argument functions: `(A) -> (B) -> ... -> (K) -> CheckoutResult`. This is wrapped in a `Computation` — a lazy description that hasn't executed yet.

**Step 2: Parallel fork.** Each `.ap { fetchX() }` takes the next curried function and applies one argument. The right-hand side (your lambda) launches as `async` in the current `CoroutineScope`, while the left spine stays inline. This means N `.ap` calls produce N concurrent coroutines with O(N) stack depth — not O(N^2).

**Step 3: Barrier join.** `.followedBy { }` awaits all pending parallel work before continuing. It acts as a phase boundary: everything above the barrier runs in parallel, everything below waits. `.flatMap` does the same but passes the result value into the next phase.

The key insight is that **applicative composition is statically analyzable** — the library knows at build time that `.ap` branches are independent and can run concurrently. Monadic composition (`flatMap`) forces sequencing because later steps depend on earlier values. Your code shape directly encodes the dependency graph.

---

## Cancellation Safety

`CancellationException` is **never** caught by any combinator in KAP. This is a deliberate design choice to respect Kotlin's structured concurrency:

- `recover`, `retry`, `catching` (kap-core), `attempt`, `recoverV` (kap-arrow) — all re-throw `CancellationException`
- `bracket` / `bracketCase` / `guarantee` — release/finalizer always runs in `NonCancellable` context
- `race` / `raceN` — losers are cancelled via structured concurrency, winner propagates normally
- When any `.ap` branch fails, all sibling branches are cancelled (standard `coroutineScope` behavior)

This means you can safely nest KAP inside any coroutine hierarchy without breaking cancellation propagation.

---

## Error Handling Decision Tree

Which combinator should you use? Follow the arrows:

```
Exception occurred?
├─ Need a default value? ──────────────→ .recover { default }              (kap-core)
├─ Need another Computation? ──────────→ .recoverWith { comp }             (kap-core)
├─ Want Either<Throwable, A>? ─────────→ .attempt()                        (kap-arrow)
├─ Want kotlin.Result<A>? ─────────────→ catching { block }                (kap-core)
├─ Want sequential fallback chain? ────→ .orElse(other)                    (kap-core)
├─ Want retry then fallback? ──────────→ .retryOrElse(schedule) { fb }     (kap-resilience)
└─ In validated context? ──────────────→ .recoverV { errorValue }          (kap-arrow)

Need a guard (not an exception)?
├─ Boolean predicate? ─────────────────→ .ensure(error) { pred }           (kap-core)
├─ Null extraction? ───────────────────→ .ensureNotNull(error) { extract } (kap-core)
├─ Validated predicate? ───────────────→ .ensureV(error) { pred }          (kap-arrow)
└─ Validated multi-error? ─────────────→ .ensureVAll(errors) { pred }      (kap-arrow)
```

**Rule of thumb:** Use `recover`/`fallback` for simple cases (kap-core), `retry(schedule)` for transient failures (kap-resilience), `attempt()` when you want `Either` branching (kap-arrow), and the `V` variants for error-accumulation workflows (kap-arrow).

---

## Execution Model

`Computation` is a **description**, not an execution. Nothing runs until `Async {}`:

```kotlin
val graph = lift3(::build)
    .ap { fetchUser() }    // NOT executed yet
    .ap { fetchCart() }    // NOT executed yet

val result = Async { graph }  // NOW everything runs
```

### `followedBy` vs `thenValue` vs `flatMap`

| Scenario | Use | Subsequent `.ap` behavior |
|---|---|---|
| Independent phases | `followedBy` | Gated — waits for barrier |
| Next phase needs the value | `flatMap` | Gated — waits, passes value |
| Maximum overlap | `thenValue` | Ungated — launches immediately |

```
// followedBy: true barrier
lift4(::R) .ap{A} .ap{B} .followedBy{C} .ap{D}
// A,B at t=0. C starts when A,B done. D starts when C done. Total: 110ms

// thenValue: no barrier (D overlaps with C)
lift4(::R) .ap{A} .ap{B} .thenValue{C} .ap{D}
// A,B,D all at t=0. C sequential. Total: 80ms
```

---

## Production Integration

### Context Propagation (MDC, OpenTelemetry, Tracing)

The `Async(context)` overload propagates a `CoroutineContext` into all parallel branches — use it for MDC, tracing spans, or dispatcher control:

```kotlin
import kotlinx.coroutines.slf4j.MDCContext

val result = Async(MDCContext()) {
    lift3(::Dashboard)
        .ap { fetchUser() }      // MDC propagated
        .ap { fetchCart() }      // MDC propagated
        .ap { fetchPromos() }    // MDC propagated
}
```

For per-branch dispatcher control:

```kotlin
val result = Async {
    lift3(::Dashboard)
        .ap { fetchUser().on(Dispatchers.IO) }           // IO thread pool
        .ap { computeRecs().on(Dispatchers.Default) }    // CPU thread pool
        .ap { fetchConfig().on(Dispatchers.IO) }         // IO thread pool
}
```

For OpenTelemetry span propagation:

```kotlin
val result = Async(tracer.asContextElement()) {
    lift3(::Dashboard)
        .ap { fetchUser().traced("fetchUser", otelTracer) }
        .ap { fetchCart().traced("fetchCart", otelTracer) }
        .ap { fetchPromos().traced("fetchPromos", otelTracer) }
}
```

---

## Observability

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

## Empirical Data

All claims backed by **119 JMH benchmarks** across 3 suites (2 forks x 5 measurement iterations each) and deterministic virtual-time proofs. No flaky timing assertions — `runTest` + `currentTime` gives provably correct results.

> **Environment:** JDK 21 (Amazon Corretto), Ubuntu 24.04 (GitHub Actions CI). JMH 1.37.
> Results auto-tracked in [`gh-pages`](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/) with regression alerts on every push.

### kap-core: KAP vs Raw Coroutines vs Arrow

#### 1. Simple Parallel — 5 calls @ 50ms each

| Approach | ms/op | vs Sequential | Overhead vs raw |
|---|---|---|---|
| Sequential baseline | 250.90 | 1x | — |
| Raw coroutines (`async/await`) | 50.27 | **5.0x** | — |
| Arrow (`parZip`) | 50.33 | **5.0x** | +0.06ms |
| **KAP** (`lift+ap`) | **50.32** | **5.0x** | **+0.05ms** |
| **KAP** (`liftA5`) | **50.31** | **5.0x** | **+0.04ms** |

#### 2. Framework Overhead — trivial compute, no I/O

| Approach | Arity 3 | Arity 9 | Arity 15 |
|---|---|---|---|
| Raw coroutines | <0.01ms | <0.01ms | 0.01ms |
| **KAP** (`lift+ap`) | **<0.01ms** | **<0.01ms** | **0.01ms** |
| **KAP** (`liftA3/5`) | **<0.01ms** | — | — |
| Arrow (`parZip`) | 0.02ms | 0.03ms | — |

> KAP overhead is **indistinguishable from raw coroutines**. Arrow's `parZip` is 2-3x higher in pure overhead but still negligible for real I/O.

#### 3. Multi-Phase — 9 calls, 4 phases

| Approach | ms/op | Flat code? | vs Sequential |
|---|---|---|---|
| Sequential baseline | 411.54 | Yes | 1x |
| Raw coroutines (nested blocks) | 180.85 | No | **2.3x** |
| Arrow (nested `parZip`) | 181.06 | No | **2.3x** |
| **KAP** (`lift+ap+followedBy`) | **180.98** | **Yes** | **2.3x** |

> Identical wall-clock. KAP keeps a single flat chain; raw and Arrow need nested blocks per phase.

#### 4. Concurrency — traverse, settle, flow

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `traverse` 20 items unbounded | 30.21ms | 30.19ms | All parallel — theoretical 30ms |
| `traverse` 20 items, concurrency 5 | 120.73ms | 120.73ms | 4 batches — theoretical 120ms |
| `traverseSettled` 10 items, all pass | 30.22ms | 30.20ms | Like traverse but never cancels |
| `traverseSettled` 10 items, half fail | 30.25ms | — | Collects failures without cancelling |
| `flow.mapComputation` c=5, 10 items | 60.53ms | 60.59ms | Bounded parallel flow processing |
| `flow.mapComputationOrdered` c=5 | 60.57ms | — | Preserves emission order |
| `flow.mapComputation` sequential | 301.60ms | — | 10 x 30ms sequentially |

#### 5. Memoization, orElse, firstSuccessOf

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `memoize` cold | <0.01ms | <0.01ms | First call overhead |
| `memoize` warm | <0.01ms | — | Subsequent calls — cached |
| `memoizeOnSuccess` cold | <0.01ms | — | Only caches success |
| `memoizeOnSuccess` failure retry | <0.01ms | — | Retries on failure |
| `orElse` chain (3 fallbacks) | 30.30ms | <0.01ms | 2 failures @ 10ms + 1 success @ 10ms |
| `firstSuccessOf` 5 candidates | 30.31ms | <0.01ms | Parallel race to first success |

#### 6. Race

| Benchmark | KAP | Raw Coroutines | Arrow |
|---|---|---|---|
| `race` (50ms vs 100ms) | **50.40ms** | 100.34ms | 50.51ms |
| `raceEither` (heterogeneous) | 30.29ms | 30.26ms | 30.37ms |

> KAP and Arrow cancel the loser automatically. Raw `select` takes the slower path in this benchmark.

### kap-resilience: KAP vs Raw Coroutines

#### 7. Retry & Schedule

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `retry` (recurs schedule) | 30.21ms | 120.70ms | KAP: declarative schedule with backoff. Raw: manual loop |
| `retry` (exponential jittered) | 30.21ms | — | Same performance — schedule is free |
| `schedule.fold` | <0.01ms | — | Schedule composition overhead is zero |

> KAP's `Schedule` abstraction delivers **4x faster** retry than naive manual loops because it controls the backoff precisely.

#### 8. Timeout & TimeoutRace

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `timeout` with default | 100.47ms | 100.39ms | Wait 100ms, cancel, use default |
| `timeoutRace` primary wins | **30.34ms** | 180.55ms | KAP runs primary+fallback in parallel |
| `timeoutRace` fallback wins | **30.34ms** | 80.55ms | Fallback faster, primary cancelled |
| `timeoutRace` vs plain timeout | 80.57ms | — | Combined timeout+race pattern |

> `timeoutRace` runs primary and fallback **in parallel** instead of sequentially. Raw equivalent needs manual `select` + cancellation.

#### 9. Bracket & Resource Safety

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `bracket` overhead | <0.01ms | <0.01ms | Zero-cost acquire/use/release |
| `bracket` latency (parallel use) | 50.34ms | 50.28ms | Parallel inside bracket — no penalty |
| `bracketCase` overhead | <0.01ms | <0.01ms | Outcome-aware variant |
| `bracketCase` latency | 60.46ms | — | With outcome dispatch in release |
| `guarantee` overhead | <0.01ms | <0.01ms | Finalizer-only pattern |
| `guaranteeCase` overhead | <0.01ms | — | Outcome-aware guarantee |

#### 10. CircuitBreaker

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| Closed (happy path) overhead | <0.01ms | <0.01ms | Mutex check is free |
| Closed latency | 50.27ms | — | Normal operation with state tracking |
| Half-open probe | 2.07ms | — | Single probe let through |

#### 11. RaceQuorum & Resource

| Benchmark | KAP | Raw Coroutines | Notes |
|---|---|---|---|
| `raceQuorum` 2-of-3 overhead | <0.01ms | — | Quorum overhead is zero |
| `raceQuorum` 2-of-5 | 40.26ms | 40.24ms | Wait for 2nd result, cancel rest |
| `raceQuorum` 3-of-5 | 50.27ms | — | Wait for 3rd result |
| `resource.zip` latency (3 resources) | 100.48ms | — | Parallel acquire, guaranteed release |
| `resource.zip` overhead | <0.01ms | <0.01ms | Resource abstraction is free |

### kap-arrow: KAP vs Arrow Validated

#### 12. Parallel Validation — 4 validators @ 40ms each

| Approach | ms/op | Collects all errors? | Parallel? | vs Sequential |
|---|---|---|---|---|
| Sequential | 160.65 | Yes | No | 1x |
| Raw coroutines | N/A | **No** | Yes | — |
| Arrow (`zipOrAccumulate`) all pass | 40.32 | Yes | Yes | **4.0x** |
| Arrow (`zipOrAccumulate`) all fail | 40.36 | Yes | Yes | **4.0x** |
| **KAP** (`zipV`) all pass | **40.28** | **Yes** | **Yes** | **4.0x** |
| **KAP** (`zipV`) all fail | **40.28** | **Yes** | **Yes** | **4.0x** |
| **KAP** (`zipV`) mixed | **40.28** | **Yes** | **Yes** | **4.0x** |

> KAP and Arrow are identical in performance. KAP scales to 22 validators; Arrow stops at 9.

#### 13. Phased Validation (flatMapV)

| Approach | ms/op | Notes |
|---|---|---|
| Arrow (phased validation) | 80.37 | Sequential phases of parallel validation |
| **KAP** (`flatMapV`) | **80.52** | Same pattern via `flatMapV` chaining |

#### 14. Validated Traverse

| Benchmark | ms/op | Notes |
|---|---|---|
| `traverseV` 10 items, all pass | 30.22 | Parallel validated traverse |
| `traverseV` 10 items, half fail | 30.21 | Collects errors, no cancellation |
| `traverseV` bounded 20, c=5, pass | 120.73 | With concurrency limit |
| `traverseV` bounded 20, c=5, half fail | 120.79 | Bounded + error accumulation |

#### 15. Error Handling Overhead

| Benchmark | KAP | Arrow | Raw Coroutines |
|---|---|---|---|
| `attempt` success | <0.01ms | <0.01ms | <0.01ms |
| `attempt` failure | <0.01ms | — | <0.01ms |
| `catching` success | <0.01ms | — | — |
| `catching` failure | <0.01ms | — | — |
| `ensureV` pass | <0.01ms | — | — |
| `ensureV` fail | <0.01ms | — | — |
| `validated` builder | <0.01ms | — | — |

### Comparison Summary

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
| **Parallel error accumulation** | No | Yes (max 9) | **Yes (max 22)** |
| **Quorum race (N-of-M)** | Manual | No | **Yes** |
| **Arrow dependency** | — | Always | **Optional** (kap-arrow) |

> **KAP matches raw coroutines in latency and overhead.** Where it pulls ahead: `race` (auto-cancel loser), `retry` (declarative schedules vs manual loops), and `timeoutRace` (parallel fallback vs sequential). The value proposition is safety, readability, and composability at zero cost.

### Virtual-Time Proofs

Every concurrency property verified with `runTest` + `currentTime` — deterministic, not flaky:

| Proof | Virtual time | Sequential | Speedup |
|---|---|---|---|
| 5 parallel calls @ 50ms | **50ms** | 250ms | **5x** |
| 10 parallel calls @ 30ms | **30ms** | 300ms | **10x** |
| 14-call 5-phase BFF | **130ms** | 460ms | **3.5x** |
| `followedBy` true barrier | **110ms** | — | C waits for barrier |
| Bounded traverse (9 items, concurrency=3) | **90ms** | 270ms | **3x** |
| Bounded traverse (500 items, concurrency=50) | **300ms** | 15,000ms | **50x** |
| Mass cancellation (1 fail, 9 siblings) | — | — | All 9 cancelled |
| `timeoutRace` vs `timeout`+fallback | **50ms** vs 150ms | — | **3x faster** |
| 100 parallel traverse | **50ms** | 5,000ms | **100x** |
| lift22 (22 parallel branches) | **30ms** | 660ms | **22x** |

### Algebraic Laws — Mathematically Verified

Unlike most Kotlin libraries, every algebraic law is **property-based tested** with random inputs via Kotest. This means refactoring with these combinators is provably safe — the laws guarantee substitutability.

**`Computation` satisfies Functor, Applicative, and Monad laws:**

| Law | Property | What it guarantees |
|---|---|---|
| Functor Identity | `fa.map { it } == fa` | `map` with identity is a no-op |
| Functor Composition | `fa.map(g).map(f) == fa.map { f(g(it)) }` | Fusing two maps is safe |
| Applicative Identity | `pure(id) ap fa == fa` | Lifting identity does nothing |
| Applicative Homomorphism | `pure(f) ap pure(x) == pure(f(x))` | Pure values compose purely |
| Monad Associativity | `(m >>= f) >>= g == m >>= { f(it) >>= g }` | FlatMap chains are associative |

**`Validated` (apV/zipV) satisfies Applicative laws** (but intentionally NOT Monad — error accumulation requires applicative semantics).

**Arrow's `NonEmptyList`** used natively in `kap-arrow` — no custom reimplementation.

Source: [`ApplicativeLawsTest.kt`](kap-core/src/jvmTest/kotlin/applicative/ApplicativeLawsTest.kt)

**906 tests across 61 suites in 3 modules. All passing.**

---

## Comparison: Raw Coroutines vs Arrow vs KAP

| | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **N parallel calls** | Manual async/await, shuttle vars | `parZip` max 9 args | `lift2`..`lift22` + `.ap` — flat |
| **Multi-phase** | Manual, phases invisible | Nested `parZip` blocks | `.followedBy` — visible |
| **Value dependencies** | Manual sequencing | Sequential blocks | `flatMap` |
| **Error accumulation** | Not possible in parallel | `zipOrAccumulate` max 9 | `zipV` up to 22 (kap-arrow) |
| **Arg order safety** | None — positional | Named args in lambda | Compile-time via currying |
| **Dependencies** | stdlib | Arrow Core + modules | `kotlinx-coroutines-core` only (core) |
| **Arrow dependency** | — | Always required | Optional (kap-arrow only) |
| **JMH overhead** | <0.01ms | 0.02-0.03ms | **<0.01ms** |
| **Retry** (3 attempts) | 120.70ms (manual loop) | — | **30.21ms** (Schedule) |
| **Race** (auto-cancel loser) | 100.34ms (`select`) | 50.51ms | **50.40ms** |
| **timeoutRace** | 180.55ms (sequential) | — | **30.34ms** (parallel) |

> Side-by-side comparison: [`CoreComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/CoreComparisonTest.kt) | [`ResilienceComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/ResilienceComparisonTest.kt) | [`ArrowComparisonTest.kt`](benchmarks/src/test/kotlin/applicative/ArrowComparisonTest.kt)
> JMH benchmarks: [`CoreBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/CoreBenchmark.kt) | [`ResilienceBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/ResilienceBenchmark.kt) | [`ArrowBenchmark.kt`](benchmarks/src/jmh/kotlin/applicative/benchmarks/ArrowBenchmark.kt)

### Coming from Arrow?

`kap-arrow` uses Arrow's **native types** directly — `arrow.core.Either` and `arrow.core.NonEmptyList`. No wrapper types, no adapters.

| Arrow | KAP | Module |
|---|---|---|
| `parZip(f1, f2, ...) { a, b, ... -> }` | `liftA3(f1, f2, f3) { }` or `lift3(::R).ap{f1}.ap{f2}.ap{f3}` | `kap-core` |
| Nested `parZip` for phases | `.followedBy { }` | `kap-core` |
| `zipOrAccumulate(f1, ...) { }` | `zipV(f1, ...) { }` — up to 22 | `kap-arrow` |
| `either { ... }` | `validated { }` / `accumulate { }` | `kap-arrow` |
| `Resource({ }, { })` | `Resource({ }, { })` — identical API | `kap-resilience` |
| `Schedule.recurs(n)` | `Schedule.recurs(n)` — identical API | `kap-resilience` |
| `parMap(concurrency) { }` | `traverse(concurrency) { }` | `kap-core` |

---

## API Reference

All arities are **unified at 22** — the maximum supported by Kotlin's function types.

### `kap-core` — Orchestration Primitives

| Combinator | Semantics | Parallelism |
|---|---|---|
| `lift2`..`lift22` + `.ap` | N-way fan-out (curried, type-safe ordering) | Parallel |
| `liftA2`..`liftA9` | Haskell-style applicative lifting (parZip-like) | Parallel |
| `product(fa, fb)` / `product(fa, fb, fc)` | Parallel into Pair/Triple | Parallel |
| `.followedBy` | True phase barrier | Sequential (gates) |
| `.thenValue` | Sequential value fill, no barrier | Sequential (no gate) |
| `.flatMap` | Monadic bind (value-dependent) | Sequential |
| `map` / `pure` / `unit` | Transform / wrap value | — |
| `Computation.failed(error)` | Immediately failing computation | — |
| `Computation.defer { }` | Lazy computation construction | — |
| `.memoize()` | Cache result, execute once (thread-safe via `Mutex`) | — |
| `.memoizeOnSuccess()` | Like `memoize` but retries on failure — production-safe | — |
| `.ensure(error) { pred }` | Short-circuit guard: throws if predicate fails | — |
| `.ensureNotNull(error) { extract }` | Extract non-null or throw — avoids nested null checks | — |
| `.on(context)` / `.named(name)` | Dispatcher / coroutine name | — |
| `.void()` / `.tap { }` | Discard result / side-effect | — |
| `.await()` | Execute a `Computation` from any suspend context | — |
| `.orElse(other)` | Sequential fallback on failure (CancellationException always propagates) | Sequential |
| `firstSuccessOf(c1, c2, ...)` | Try each sequentially, return first success | Sequential |
| `computation { }` | Imperative builder with `.bind()` — sequential monadic DSL | Sequential |
| `.raceAgainst(other)` | Extension sugar for `race(this, other)` | Competitive |
| `.settled()` | Wrap outcome in `Result` (no sibling cancellation) | — |
| `.zipLeft` / `.zipRight` | Parallel, keep one result | Parallel |

#### Collections

| Combinator | Semantics | Parallelism |
|---|---|---|
| `zip` (2-22 arity) / `mapN` (2-22) | Combine computations | Parallel |
| `traverse(f)` / `traverse(n, f)` | Map + parallel execution | Parallel (bounded) |
| `traverse_(f)` / `traverse_(n, f)` | Fire-and-forget parallel execution (discard results) | Parallel (bounded) |
| `sequence()` / `sequence(n)` | Execute collection | Parallel (bounded) |
| `sequence_()` / `sequence_(n)` | Execute collection for side-effects only | Parallel (bounded) |
| `traverseSettled(f)` / `traverseSettled(n, f)` | Collect ALL results (no cancellation) | Parallel (bounded) |
| `sequenceSettled()` / `sequenceSettled(n)` | Collect ALL results from collection | Parallel (bounded) |

| `race` / `raceN` / `raceAll` | First to succeed | Competitive |
> **Design choice — no `parZip` / `parMap`:** All composition is parallel by default. The `par` prefix (Arrow/Cats naming) implies parallelism is a special mode — here it's the only mode. **Equivalents:** `parZip(f1, f2) { }` -> `mapN(f1, f2) { }` or `lift2(::R).ap{f1}.ap{f2}`. `parMap { }` -> `traverse { }`.

#### Interop & Observability

| Combinator | Semantics |
|---|---|
| `Deferred.toComputation()` / `Computation.toDeferred(scope)` | Deferred bridge |
| `Flow.firstAsComputation()` / `(suspend () -> A).toComputation()` | Flow/lambda bridge |
| `Computation.toFlow()` / `Flow.collectAsComputation()` | Flow <-> Computation |
| `Flow.mapComputation(concurrency) { }` / `Flow.filterComputation { }` | Flow + Computation pipeline (completion order) |
| `Flow.mapComputationOrdered(concurrency) { }` | Flow + Computation pipeline (upstream order preserved) |
| `catching { }` | Exception-safe `Result<A>` construction |
| `traced(name, tracer)` / `traced(name, onStart, onSuccess, onError)` | Observability hooks |
| `delayed(d, value)` / `apOrNull` | Utilities |

### `kap-resilience` — Retry, Resources & Protection

| Combinator | Semantics |
|---|---|
| `timeoutRace(d, fallback)` | Parallel timeout (fallback starts immediately) |
| `retry(schedule)` / `retry(scheduleFactory)` / `retryOrElse(schedule, fallback)` | Schedule-based retry (factory overload for stateful schedules) |
| `retryWithResult(schedule)` | Retry returning `RetryResult(value, attempts, totalDelay)` |
| `Schedule.recurs` / `.spaced` / `.exponential` / `.fibonacci` / `.linear` / `.forever` | Backoff strategies |
| `Schedule.doWhile` / `.doUntil` / `.jittered` / `.withMaxDuration` | Filters and limits |
| `schedule.collect()` / `schedule.zipWith(other, f)` | Accumulate inputs / custom delay merge |
| `s1 and s2` / `s1 or s2` | Schedule composition |
| `schedule.fold(init) { acc, a -> }` | Accumulate values across retries |
| `CircuitBreaker(maxFailures, resetTimeout)` | Protect downstream from cascading failures |
| `.withCircuitBreaker(breaker)` | Wrap computation with circuit breaker |
| `raceQuorum(required, c1, c2, ...)` | N-of-M quorum race (hedged/consensus) |

#### Resource Safety

| Combinator | Semantics |
|---|---|
| `bracket(acquire, use, release)` | Guaranteed cleanup (NonCancellable) |
| `bracketCase(acquire, use, release)` | Cleanup with `ExitCase` (commit/rollback) |
| `guarantee` / `guaranteeCase` | Finalizers with optional `ExitCase` |
| `Resource(acquire, release)` | Composable resource monad |
| `Resource.zip(r1..r22, f)` | Combine up to 22 resources |
| `Resource.defer { }` | Lazy/conditional resource construction |
| `resource.use` / `resource.useWithTimeout` / `resource.useComputation` | Terminal operations |

### `kap-arrow` — Validation & Arrow Integration

| Combinator | Semantics |
|---|---|
| `zipV` (2-22 args) | Parallel validation, all errors accumulated |
| `liftV2`..`liftV22` + `apV` | Curried parallel validation |
| `followedByV` / `thenValueV` / `flatMapV` | Phase barriers / sequential short-circuit |
| `validated { }` / `accumulate { }` | Short-circuit builder with `.bind()` / `.bindV()` — sequential validation DSL |
| `valid` / `invalid` / `catching` / `validate` | Entry points |
| `Validated<E, A>` (typealias) | Shorthand for `Computation<Either<NonEmptyList<E>, A>>` |
| `Nel<A>` (typealias) | Shorthand for `NonEmptyList<A>` — reduces validated type verbosity |
| `ensureV(error) { pred }` / `ensureVAll(errors) { pred }` | Predicate-based validated guards |
| `recoverV` / `mapV` / `mapError` / `orThrow` | Transforms |
| `traverseV` / `sequenceV` | Collection operations with accumulation |
| `.attempt()` | Catch to Arrow's `Either<Throwable, A>` |
| `raceEither(fa, fb)` | First to succeed, different types (returns `Either<A, B>`) |
| `Result.toEither()` / `Either.toResult()` / `Result.toValidated()` | Kotlin Result <-> Arrow Either bridges |
| `fromArrow { }` / `Computation.runCatchingArrow(scope)` | Arrow interop bridges |

---

## When to Use (and When Not To)

**KAP shines when:**
- You have 4+ concurrent operations with sequential phases (BFF, checkout, booking) → `kap-core`
- You need parallel error accumulation across validators → add `kap-arrow`
- The dependency graph should be visible in code shape → `kap-core`
- You want compile-time parameter order safety → `kap-core`
- You need composable retry/timeout/race policies → add `kap-resilience`
- You want all of the above without pulling Arrow into your entire project → `kap-core` + `kap-resilience` (zero Arrow dep)

**Use something else when:**
- 2-3 simple parallel calls — `coroutineScope { async {} }` is enough
- Purely sequential code — regular `suspend` functions
- Stream processing — use `Flow`
- Full FP ecosystem (optics, typeclasses) — use Arrow directly

**Target audience:** BFF layers, checkout/booking flows, dashboard aggregation, multi-service orchestration.

---

## Examples

Seven runnable examples in [`/examples`](examples/) covering **every module combination**:

| Example | Modules | What it demonstrates |
|---|---|---|
| **[ecommerce-checkout](examples/ecommerce-checkout/)** | `kap-core` | 11 services, 5 phases — `lift11`+`ap`+`followedBy` |
| **[dashboard-aggregator](examples/dashboard-aggregator/)** | `kap-core` | 14-service BFF — `lift14`, type-safe at 14 args |
| **[validated-registration](examples/validated-registration/)** | `kap-core` + `kap-arrow` | Parallel validation, error accumulation, phased validation with `flatMapV` |
| **[ktor-integration](examples/ktor-integration/)** | `kap-core` + `kap-resilience` + `kap-arrow` | Ktor HTTP BFF with 10 endpoints: parallel aggregation, traverse, validation, retry/CB/bracket, raceQuorum, timeoutRace, full order pipeline — 28 integration tests |
| **[resilient-fetcher](examples/resilient-fetcher/)** | `kap-core` + `kap-resilience` | `Schedule`, `CircuitBreaker`, `bracket`, `Resource.zip`, `timeoutRace`, `raceQuorum`, `retryOrElse`, `retryWithResult` |
| **[full-stack-order](examples/full-stack-order/)** | `kap-core` + `kap-resilience` + `kap-arrow` | Validated input + retry/CB/bracket + `attempt`/`raceEither` — complete pipeline using all three modules |
| **[readme-examples](examples/readme-examples/)** | `kap-core` + `kap-resilience` + `kap-arrow` | Every code example from this README — compiled and verified on every CI push |

Each example's `build.gradle.kts` includes comments with the equivalent Maven coordinates for standalone use.

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
./gradlew :benchmarks:jmh            # JMH benchmarks (JSON results in benchmarks/build/results/jmh/)
./gradlew :benchmarks:test           # comparison tests (kap vs raw vs arrow)
./gradlew dokkaHtml                   # API docs
./gradlew :kap-core:generateAll       # regenerate all overloads (arities 2-22)
```

> **First time?** Start with `./gradlew :kap-core:jvmTest` — it runs the core
> module tests on JVM without needing Xcode, Arrow, or native toolchains.
>
> **Native targets:** Apple targets (iOS, macOS) are automatically skipped when
> Xcode is not installed. Linux Native compiles with just the Kotlin toolchain.
> To enable Apple targets, install Xcode and its command-line tools (`xcode-select --install`).

## CI/CD

The project uses a comprehensive GitHub Actions pipeline:

```
Push / PR to master
├── validate          Gradle wrapper validation
├── test              4 parallel jobs: kap-core · kap-resilience · kap-arrow · benchmarks
├── compile-platforms JS + LinuxX64 compilation checks
├── codegen-check     Regenerate all codegen, fail if out-of-date
├── examples          run all 7 examples + Ktor integration tests (28 testApplication cases)
├── benchmark         (push only) Full JMH → store results in gh-pages → historical chart
├── benchmark-pr      (PR only) Quick JMH → compare against baseline → block on regression
└── ci-gate           Aggregate status for branch protection

Release (tag vX.Y.Z)
├── validate          Version consistency + not-already-published check
├── test              Full test suite + multiplatform compilation + codegen check
├── publish           Maven Central (macOS runner for Apple targets)
└── verify            Wait for Maven Central sync → run examples against real artifacts
```

### Benchmark Tracking

**Live dashboard:** [**damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/**](https://damian-rafael-lattenero.github.io/coroutines-applicatives/benchmarks/)

Every push to `master` runs the full JMH suite and stores results in the `gh-pages` branch using [`github-action-benchmark`](https://github.com/benchmark-action/github-action-benchmark). This provides:

- **Historical chart** — visual trend of all benchmark results over time
- **Regression alerts** — notifies with `@mention` if any benchmark regresses >50% on master
- **PR comparison** — PRs run a quick JMH pass (1 fork, 2 iterations) and **block merge** if any benchmark regresses >100%

JMH config is tunable via Gradle properties for local experimentation:

```bash
./gradlew :benchmarks:jmh                                    # defaults: 3 warmup, 5 iterations, 2 forks
./gradlew :benchmarks:jmh -Pjmh.warmup=1 -Pjmh.iterations=2 -Pjmh.fork=1  # quick run
```

## License

Apache 2.0
