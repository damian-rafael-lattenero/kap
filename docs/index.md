---
hide:
  - navigation
  - toc
---

<style>
.md-content__inner { max-width: 960px; margin: 0 auto; }
.hero { text-align: center; padding: 2rem 0 1rem; }
.hero img { width: 160px; }
.hero h1 { font-size: 2.4rem; margin-top: 1rem; }
.hero p { font-size: 1.15rem; color: var(--md-default-fg-color--light); }
</style>

<div class="hero">
  <img src="assets/logo.png" alt="KAP">
  <h1>KAP</h1>
  <p><strong>Type-safe coroutine orchestration for Kotlin Multiplatform.</strong><br>
  The code reads like a diagram. The compiler won't let you wire it wrong.</p>
  <p>
    <a href="guide/quickstart/" class="md-button md-button--primary">Get Started</a>
    <a href="https://github.com/damian-rafael-lattenero/kap" class="md-button">GitHub</a>
  </p>
</div>

---

## You know this code

You've written it. Maybe last week. A backend endpoint that calls a few services, combines the results, and returns a response. It starts simple:

```kotlin
coroutineScope {
    val dUser = async { fetchUser() }
    val dCart = async { fetchCart() }
    val dPromos = async { fetchPromos() }
    CheckoutResult(dUser.await(), dCart.await(), dPromos.await())
}
```

Three calls, three awaits. Not bad. But then the requirements come in. Stock validation needs retry because the inventory service is flaky. Payment needs a circuit breaker. Promos have a timeout. And suddenly your clean coroutine code looks like this:

```kotlin
coroutineScope {
    val dUser  = async { fetchUser() }
    val dCart  = async { fetchCart() }
    val dPromos = async { withTimeout(3.seconds) { fetchPromos() } }
    val user   = dUser.await()
    val cart    = dCart.await()
    val promos  = dPromos.await()

    // retry loop — breaks the async/await rhythm
    var stock = false
    var attempt = 0
    var delay = 100.milliseconds
    while (true) {
        try { stock = validateStock(); break }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (++attempt >= 3) throw e
            delay(delay); delay *= 2
        }
    }

    val dShipping = async { calcShipping() }
    val dTax      = async { calcTax() }

    // circuit breaker — interleaved with business logic
    val payment = if (!breaker.shouldAttempt()) {
        throw CircuitBreakerOpenException()
    } else {
        try {
            val p = withTimeout(5.seconds) { reservePayment() }
            breaker.recordSuccess(); p
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { breaker.recordFailure(); throw e }
    }

    CheckoutResult(user, cart, promos, stock, dShipping.await(), dTax.await(), payment)
}
```

Where are the phases? Which calls run in parallel? Where does the retry end and the business logic begin? You have to read every line to answer these questions. And this is a *simple* example — just 7 services.

---

## Now look at this

```kotlin
@KapTypeSafe
data class CheckoutResult(
    val user: String, val cart: String, val promos: String,
    val stock: Boolean,
    val shipping: Double, val tax: Double,
    val payment: String,
)

val retryPolicy = Schedule.exponential<Throwable>(100.milliseconds) and Schedule.times(3)
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

kap(::CheckoutResult)
    .withUser { fetchUser() }                                         // ┐
    .withCart { fetchCart() }                                          // ├─ phase 1: parallel
    .withPromos(Kap { fetchPromos() }.timeout(3.seconds))             // ┘
    .thenStock(Kap { validateStock() }.retry(retryPolicy))            // ── phase 2: barrier + retry
    .withShipping { calcShipping() }                                  // ┐ phase 3: parallel
    .withTax { calcTax() }                                            // ┘
    .thenPayment(Kap { reservePayment() }                             // ── phase 4: barrier
        .withCircuitBreaker(breaker)                                  //    + circuit breaker
        .timeout(5.seconds))                                          //    + timeout
    .executeGraph()
```

Same 7 calls. Same retry, circuit breaker, timeout. But now the phases are *visible*. `.with` means parallel. `.then` means wait. The retry is on the call, not around it. The circuit breaker is on the call, not interleaved with it. You can read the execution plan top to bottom.

`@KapTypeSafe` generates a step class per field — after `.withUser`, the IDE only offers `.withCart`. You can't swap, skip, or forget a field.

That's KAP. Let's start from the beginning.

---

## Three things to learn

That's it. Three.

| You write | What happens | Think of it as |
|---|---|---|
| `.withX { }` | Runs in parallel with everything else in the same phase | *"and at the same time..."* |
| `.thenX { }` | Waits for all above, then continues | *"once that's done..."* |
| `.andThen { result -> }` | Waits, passes the result, builds the next graph | *"using what we got..."* |

Everything else in KAP — retry, circuit breaker, racing, validation — is built *on top* of these three. Learn them once, and the rest follows.

---

## Your first KAP graph

A dashboard that loads user, feed, and notification count in parallel:

```kotlin
@KapTypeSafe
data class Dashboard(val user: String, val feed: String, val notifications: Int)

kap(::Dashboard)
    .withUser { fetchUser() }                // ┐
    .withFeed { fetchFeed() }                // ├─ all three run in parallel
    .withNotifications { countUnread() }     // ┘
    .executeGraph()
```

```
t=0ms   ─── fetchUser ──────────┐
t=0ms   ─── fetchFeed ──────────├─ parallel
t=0ms   ─── countUnread ────────┘
t=50ms  ─── Dashboard ready
```

Three services, one result, **50ms instead of 120ms sequential**. Your suspend functions go in, your data class comes out. No framework, no wrapper types, no runtime magic.

---

## Adding phases

Real APIs have dependencies. You can't calculate shipping until you know the cart. You can't generate a confirmation until payment is reserved. With raw coroutines, you'd nest `coroutineScope` blocks. With KAP, you change one character — `.with` becomes `.then`:

```kotlin
kap(::CheckoutResult)
    .withUser { fetchUser() }            // ┐ phase 1: parallel
    .withCart { fetchCart() }             // ┘
    .thenStock { validateStock() }       // ── phase 2: waits for phase 1
    .withShipping { calcShipping() }     // ┐ phase 3: parallel
    .withTax { calcTax() }              // ┘
    .executeGraph()
```

```
t=0ms   ─── fetchUser ──────┐
t=0ms   ─── fetchCart ───────┘─ phase 1
t=50ms  ─── validateStock ───── phase 2 (barrier)
t=70ms  ─── calcShipping ───┐
t=70ms  ─── calcTax ────────┘─ phase 3
t=100ms ─── done
```

The phases are explicit. You see them in the code. No nesting, no shuttle variables, no mental reconstruction required.

---

## Value-dependent phases

Sometimes phase 2 needs the *result* of phase 1 — not just to wait for it, but to use the data. That's `.andThen`:

```kotlin
@KapTypeSafe
data class UserContext(val profile: String, val prefs: String, val tier: String)
@KapTypeSafe
data class PersonalizedDashboard(val recs: String, val promos: String, val trending: String)

kap(::UserContext)
    .withProfile { fetchProfile(userId) }       // ┐
    .withPrefs { fetchPreferences(userId) }     // ├─ phase 1: parallel
    .withTier { fetchLoyaltyTier(userId) }      // ┘
    .andThen { ctx ->                           // ── barrier: ctx available
        kap(::PersonalizedDashboard)
            .withRecs { fetchRecommendations(ctx.profile) }    // ┐
            .withPromos { fetchPromotions(ctx.tier) }          // ├─ phase 2: parallel
            .withTrending { fetchTrending(ctx.prefs) }         // ┘
    }
    .executeGraph()
```

Phase 1 fetches the user context in parallel. Phase 2 uses that context to personalize — also in parallel. The dependency is explicit, type-safe, and readable.

---

## It scales

Here's a real checkout: 11 services, 5 phases, 8 Strings, 2 Booleans, 3 Doubles. The compiler catches every swap:

```kotlin
@KapTypeSafe
data class CheckoutResult(
    val user: String, val cart: String,
    val promos: String, val inventory: Boolean,
    val stock: Boolean,
    val shipping: Double, val tax: Double, val discounts: Double,
    val payment: String,
    val confirmation: String, val email: String,
)

kap(::CheckoutResult)
    .withUser { fetchUser() }                      // ┐
    .withCart { fetchCart() }                       // ├─ phase 1: parallel
    .withPromos { fetchPromos() }                  // │
    .withInventory { fetchInventory() }            // ┘
    .thenStock { validateStock() }                 // ── phase 2: barrier
    .withShipping { calcShipping() }               // ┐
    .withTax { calcTax() }                         // ├─ phase 3: parallel
    .withDiscounts { calcDiscounts() }             // ┘
    .thenPayment { reservePayment() }              // ── phase 4: barrier
    .withConfirmation { generateConfirmation() }   // ┐ phase 5
    .withEmail { sendEmail() }                     // ┘
    .executeGraph()
```

```
t=0ms   ─── fetchUser ────────┐
t=0ms   ─── fetchCart ────────┤
t=0ms   ─── fetchPromos ─────├─ phase 1
t=0ms   ─── fetchInventory ──┘
t=50ms  ─── validateStock ───── phase 2
t=70ms  ─── calcShipping ────┐
t=70ms  ─── calcTax ─────────├─ phase 3
t=70ms  ─── calcDiscounts ───┘
t=100ms ─── reservePayment ──── phase 4
t=140ms ─── generateConfirm ─┐
t=140ms ─── sendEmail ───────┘─ phase 5
t=170ms ─── done
```

**170ms total** (vs 460ms sequential). Verified with [deterministic virtual-time tests](https://github.com/damian-rafael-lattenero/kap/blob/master/kap-core/src/jvmTest/kotlin/kap/ConcurrencyProofTest.kt).

---

## "What if one call fails?"

Good question. By default, if any `.with` branch fails, the whole graph is cancelled — that's structured concurrency, and it's usually what you want. But sometimes a call is optional. The feed can fail, but you still want the profile.

`settled { }` wraps the result in `Result<A>` so a failure doesn't kill the rest:

```kotlin
@KapTypeSafe
data class HomePage(val profile: String, val feed: Result<String>, val ads: Result<String>)

kap(::HomePage)
    .withProfile { fetchProfile() }              // critical — failure cancels everything
    .withFeed(settled { fetchFeed() })           // optional — failure returns Result.failure
    .withAds(settled { fetchAds() })             // optional — failure returns Result.failure
    .executeGraph()
// Feed throws? Profile and ads still complete. You get Result.failure for feed.
```

Need ALL results even if some fail? `traverseSettled` runs every item and collects outcomes:

```kotlin
val results = listOf("svc-a", "svc-b", "svc-c").traverseSettled { svc ->
    Kap { callService(svc) }
}.executeGraph()
// → [Success("ok"), Failure(TimeoutException), Success("ok")]
```

No `supervisorScope`. No `runCatching` per item. One method call.

---

## Adding resilience

This is where it gets interesting. Every team needs retry, circuit breaker, timeout. Every team reimplements them. And they never compose well with each other.

In KAP, resilience is per-call, inline, and composable:

```kotlin
val breaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)
val retryPolicy = Schedule.exponential<Throwable>(100.milliseconds)
    .jittered()                          // ±50% random spread (no thundering herd)
    .and(Schedule.times(3))              // max 3 attempts
    .withMaxDuration(10.seconds)         // total budget

kap(::Dashboard)
    .withUser(Kap { fetchUser() }
        .withCircuitBreaker(breaker)
        .retry(retryPolicy))
    .withSlowData(Kap { fetchFromSlowApi() }
        .timeoutRace(100.milliseconds, Kap { fetchFromCache() }))
    .withPromos { fetchPromos() }
    .executeGraph()
```

`Schedule` policies are reusable objects — define once, apply anywhere. `timeoutRace` starts *both* the primary and fallback at t=0, so the fallback is already warm when the timeout fires. `bracket` guarantees cleanup even on cancellation:

```kotlin
bracket(
    acquire = { openConnection() },
    use = { conn ->
        kap(::QueryResult)
            .withData { conn.query("SELECT ...") }
            .withMeta { conn.metadata() }
    },
    release = { conn -> conn.close() }  // runs in NonCancellable context
).executeGraph()
```

---

## Collecting every error at once

You know the frustration: a user submits a form, gets "invalid email", fixes it, resubmits, gets "age too young". Why not show *all* errors the first time?

With `kap-arrow`, validations run **in parallel** and accumulate **every** error:

```kotlin
val result: Either<NonEmptyList<RegError>, User> = zipV(
    { validateName("A") },           // ← too short
    { validateEmail("bad") },         // ← invalid
    { validateAge(10) },              // ← too young
    { checkUsername("al") },          // ← too short
) { name, email, age, username -> User(name, email, age, username) }
    .executeGraph()
// → Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow, UsernameTaken))
// ALL 4 errors in ONE response. No round trips.
```

Scales to **22 validators** (Arrow's `zipOrAccumulate` maxes at 9). And since they run in parallel, if each validator hits the database, all queries run concurrently.

!!! tip "KAP and Arrow"
    KAP doesn't replace Arrow — it builds on it. Arrow gives you the types (`Either`, `NonEmptyList`). KAP gives you the orchestration (parallel execution, phase barriers, resilience). Use both.

---

## Everything together

Here's a real order placement that uses everything you've seen: parallel validation with error accumulation, racing pricing providers, retry with backoff, circuit breaker on payment, partial failure on notifications, and transactional safety with guaranteed cleanup.

```kotlin
@KapTypeSafe
data class OrderResult(
    val finalPrice: Double,
    val reservationId: String,
    val paymentId: String,
    val notifications: List<Result<Unit>>,
)

val retryPolicy = Schedule.exponential<Throwable>(100.milliseconds).jittered() and Schedule.times(3)
val paymentBreaker = CircuitBreaker(maxFailures = 5, resetTimeout = 30.seconds)

suspend fun placeOrder(input: OrderInput): Either<Nel<OrderError>, OrderResult> {

    // ── Phase 1: validate (parallel, accumulate ALL errors) ──────────
    val validated = kapV<OrderError, ValidAddress, ValidCard, ValidItems, ValidOrder>(::ValidOrder)
        .withV { validateAddress(input.address) }       // ┐ all three run in parallel
        .withV { validatePaymentInfo(input.card) }      // ├─ errors accumulate
        .withV { validateItems(input.items) }           // ┘
        .executeGraph()

    val order = validated.getOrElse { return Either.Left(it) }

    // ── Phases 2–5: process inside DB transaction ────────────────────
    return bracketCase(
        acquire = { db.beginTransaction() },
        use = { tx ->
            kap(::OrderResult)
                .withFinalPrice(raceN(                              // phase 2: race 3 providers
                    Kap { pricingServiceA(order) },                 //   fastest wins
                    Kap { pricingServiceB(order) },
                    Kap { pricingServiceC(order) },
                ))
                .thenReservationId(                                 // phase 3: barrier + retry
                    Kap { reserveInventory(tx, order) }
                        .retry(retryPolicy)
                )
                .thenPaymentId(                                     // phase 4: circuit breaker
                    Kap { chargePayment(tx, order) }
                        .withCircuitBreaker(paymentBreaker)
                        .timeout(5.seconds)
                )
                .withNotifications(listOf(                          // phase 5: partial failure OK
                    Kap { sendEmail(order) },
                    Kap { sendPush(order) },
                    Kap { updateAnalytics(order) },
                ).sequenceSettled())
                .map { Either.Right(it) }
        },
        release = { tx, exit -> when (exit) {
            is ExitCase.Completed -> tx.commit()
            else                  -> tx.rollback()
        }}
    ).executeGraph()
}
```

One function. Five phases. Validation, racing, retry, circuit breaker, partial failure, transactional safety. Each concern is one composable call. The business logic reads top to bottom.

---

## More tools in the box

Every one of these is a method call — no boilerplate, no manual state:

| Pattern | KAP | What it does |
|---|---|---|
| **Race** | `raceN(a, b, c)` | Fastest wins, losers cancelled |
| **Bounded concurrency** | `ids.traverse(concurrency = 5) { Kap { fetch(it) } }` | Process N items, max M at a time |
| **Timeout with fallback** | `kap.timeoutRace(2.seconds, fallback)` | Both start at t=0, fastest wins |
| **Composable retry** | `kap.retry(Schedule.exponential().jittered().and(times(3)))` | Define once, reuse everywhere |
| **Timed** | `timed { fetchSlowService() }` | Returns `TimedResult(value, duration)` |
| **Memoize** | `Kap { loadConfig() }.memoizeOnSuccess()` | Compute once, cache thread-safely |
| **Quorum** | `raceQuorum(required = 2, a, b, c)` | N-of-M consensus |
| **Resource safety** | `bracket(acquire, use, release)` | Guaranteed cleanup, even on cancellation |

---

## Extra type safety with `kapTyped`

`kap(::User)` with `@KapTypeSafe` enforces parameter **order** via step classes. But if `firstName` and `lastName` are both `String`, nothing stops you from returning the wrong one inside the lambda.

`kapTyped` adds **opaque wrapper types** — each field gets a distinct type, so the compiler rejects mismatches:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// Named builders — enforces order, raw types
kap(::User)
    .withFirstName { fetchFirstName() }     // String
    .withLastName { fetchLastName() }       // String — could accidentally swap
    .withAge { fetchAge() }
    .executeGraph()

// Opaque types — enforces order AND type identity
kapTyped(::User)
    .with { fetchFirstName().firstNameUser }   // String → UserFirstName
    .with { fetchLastName().lastNameUser }     // String → UserLastName
    .with { fetchAge().ageUser }               // Int → UserAge
    .executeGraph()
```

The IDE shows the expected opaque type in autocomplete — you always know which field comes next. Use `kap()` for most cases, `kapTyped()` when same-typed fields need extra safety.

---

## Zero overhead

All claims backed by **119 JMH benchmarks** and deterministic virtual-time proofs.

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | **<0.01ms** |
| **Framework overhead** (arity 9) | <0.01ms | 0.03ms | **<0.01ms** |
| **Simple parallel** (5 x 50ms) | 50.27ms | 50.33ms | **50.31ms** |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | **180.98ms** |
| **Race** (50ms vs 100ms) | 100.34ms | 50.51ms | **50.40ms** |
| **timeoutRace** (primary wins) | 180.55ms | -- | **30.34ms** |
| **Max validation arity** | -- | 9 | **22** |

KAP adds **zero measurable overhead**. The abstraction compiles away. What you're left with is pure coroutines running in a structured scope.

[Live benchmark dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/){ .md-button }

---

## Pick what you need

KAP is modular. Start with core, add as you grow:

| Module | What you get | Depends on |
|---|---|---|
| [`kap-core`](modules/kap-core.md) | `with`, `then`, `andThen`, `race`, `traverse`, `memoize`, `settled`, `timed` | `kotlinx-coroutines-core` |
| [`kap-resilience`](modules/kap-resilience.md) | `Schedule`, `CircuitBreaker`, `Resource`, `bracket`, `timeoutRace`, `raceQuorum` | `kap-core` |
| [`kap-arrow`](modules/kap-arrow.md) | `zipV`, `withV`, `kapV`, `accumulate {}`, `attempt()`, `raceEither` | `kap-core` + Arrow |
| [`kap-ksp`](modules/kap-ksp.md) | `@KapTypeSafe`, `@KapBridge` — compile-time named builders | KSP |
| [`kap-ktor`](modules/kap-ktor.md) | Ktor plugin, circuit breaker registry, tracers, `respondAsync` | `kap-core` + Ktor |
| [`kap-kotest`](modules/kap-kotest.md) | `shouldSucceedWith`, `shouldFailWith`, timing & lifecycle matchers | `kap-core` (test) |

[API Reference (KDocs)](api/index.html){ .md-button }

---

## Get started

```kotlin
plugins {
    id("com.google.devtools.ksp")  // Required for @KapTypeSafe
}

dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.6.0")

    // KSP — named builder generation (@KapTypeSafe)
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.6.0")
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.6.0")

    // Optional
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.6.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.6.0")
    implementation("io.github.damian-rafael-lattenero:kap-ktor:2.6.0")
    testImplementation("io.github.damian-rafael-lattenero:kap-kotest:2.6.0")
}
```

Or clone the [starter project](https://github.com/damian-rafael-lattenero/kap-starter) and run `./gradlew run` in 30 seconds.

<p style="text-align: center; margin-top: 2rem;">
  <a href="guide/quickstart/" class="md-button md-button--primary">Quickstart Guide</a>
  <a href="playground/" class="md-button">Cookbook (12 runnable examples)</a>
</p>
