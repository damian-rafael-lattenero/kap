---
date: 2026-03-27
authors:
  - damian
categories:
  - Kotlin
  - Coroutines
  - Open Source
slug: from-30-lines-of-async-await-to-12
---

# From 30 Lines of async/await to 12: Type-Safe Parallel Orchestration in Kotlin

*How I built KAP to solve the multi-phase parallel execution problem that raw coroutines and Arrow don't address.*

<!-- more -->

Every Kotlin backend has this code somewhere: a checkout flow, a dashboard endpoint, a booking pipeline. Multiple microservice calls. Some parallel, some sequential. Dependencies between phases.

And every time, the same problems.

## The code everyone writes

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

30 lines. 5 phases. But where do the phases start and end? You can't tell without reading every line.

**Three problems I kept hitting:**

1. **Invisible phases.** The parallel groups and barriers are hidden in the `async`/`await` ordering. Move one `await()` above its `async` — you just serialized a parallel call. The compiler says nothing.

2. **Silent bugs.** If `fetchUser()` and `fetchCart()` return the same type, you can swap them in the constructor call. No compile error. Wrong data. Production bug.

3. **Boilerplate that scales badly.** Each new phase doubles the ceremony. 5 phases = 30 lines of shuttle variables.

## Arrow helps, but doesn't solve it

```kotlin
// You need intermediate data classes to carry values across phases:
data class Phase1(val user: UserProfile, val cart: ShoppingCart,
                  val promos: PromotionBundle, val inventory: InventorySnapshot)
data class Phase3(val shipping: ShippingQuote, val tax: TaxBreakdown,
                  val discounts: DiscountSummary)

val phase1 = parZip(
    { fetchUser() }, { fetchCart() }, { fetchPromos() }, { fetchInventory() },
) { user, cart, promos, inventory -> Phase1(user, cart, promos, inventory) }

val stock = validateStock()

val phase3 = parZip(
    { calcShipping() }, { calcTax() }, { calcDiscounts() },
) { shipping, tax, discounts -> Phase3(shipping, tax, discounts) }

val payment = reservePayment()

val phase5 = parZip(
    { generateConfirmation() }, { sendEmail() },
) { confirmation, email -> Pair(confirmation, email) }

// Manual assembly — thread all intermediate values:
val checkout = CheckoutResult(
    phase1.user, phase1.cart, phase1.promos, phase1.inventory,
    stock, phase3.shipping, phase3.tax, phase3.discounts,
    payment, phase5.first, phase5.second,
)
```

Better — parallel within phases. But you still need intermediate data classes, manual assembly, and the phases are invisible in the code structure. Plus `parZip` maxes at 9 arguments.

## What I wanted

Code that looks like the execution plan:

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }              // ┐
        .with { fetchCart() }               // ├─ phase 1: parallel
        .with { fetchPromos() }             // │
        .with { fetchInventory() }          // ┘
        .then { validateStock() }           // ── phase 2: barrier
        .with { calcShipping() }            // ┐
        .with { calcTax() }                 // ├─ phase 3: parallel
        .with { calcDiscounts() }           // ┘
        .then { reservePayment() }          // ── phase 4: barrier
        .with { generateConfirmation() }    // ┐ phase 5: parallel
        .with { sendEmail() }              // ┘
}
```

12 lines. Phases are explicit. And here's the key: **swap any two `.with` lines and the compiler rejects it.** Each service returns a distinct type, and the typed function chain locks parameter order at compile time.

## How it works

`kap(::CheckoutResult)` curries the constructor. Each `.with` applies the next argument. The resulting type shrinks: `Kap<(A) -> (B) -> ... -> R>` → `Kap<(B) -> ... -> R>` → ... → `Kap<R>`.

`.then` creates a real phase barrier: a `CompletableDeferred` that gates all subsequent work until everything above completes.

`.andThen { ctx -> }` does the same but passes the accumulated result — so phase 2 can use phase 1's data:

```kotlin
val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐
        .with { fetchPreferences(userId) }   // ├─ phase 1
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::EnrichedContent)
                .with { fetchRecommendations(ctx.profile) }  // ┐
                .with { fetchPromotions(ctx.tier) }           // ├─ phase 2
                .with { fetchTrending(ctx.prefs) }            // │
                .with { fetchHistory(ctx.profile) }           // ┘
                .andThen { enriched ->                         // ── barrier
                    kap(::FinalDashboard)
                        .with { renderLayout(ctx, enriched) }     // ┐ phase 3
                        .with { trackAnalytics(ctx, enriched) }   // ┘
                }
        }
}
```

14 calls, 3 phases, 115ms vs 460ms sequential. No reflection. No runtime code generation. Pure Kotlin type system.

## Performance

We run 119 JMH benchmarks on every push. KAP overhead is indistinguishable from raw coroutines:

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| Framework overhead (arity 3) | <0.01ms | 0.02ms | <0.01ms |
| Multi-phase (9 calls, 4 phases) | 180.85ms | 181.06ms | 180.98ms |
| Race (50ms vs 100ms) | 100.34ms | 50.51ms | 50.40ms |
| timeoutRace (primary wins) | 180.55ms | — | **30.34ms** |

The `timeoutRace` number is real: instead of waiting for the timeout before starting the fallback, both start at t=0. The fallback is already running when the primary times out. 2.6x faster.

## The same-type problem — and how we solved it

There's one thing the typed chain doesn't catch: two parameters with the same type. `firstName: String` and `lastName: String` can be swapped silently. Every framework has this problem — Haskell, Arrow, everyone recommends "use newtypes" and leaves it to the developer.

We went further. KAP ships a KSP processor that generates the newtypes for you:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// KSP generates value classes + extension functions automatically:
kapSafe(::User)
    .with { fetchFirstName().toFirstName() }   // UserFirstName — distinct type
    .with { fetchLastName().toLastName() }     // UserLastName — swap? COMPILE ERROR
    .with { fetchAge().toAge() }               // UserAge
```

One annotation. Zero runtime overhead (value classes are inlined). Every same-type swap becomes a compile error. Works on functions too, with optional prefix for collision avoidance. As far as we know, no other framework in the Kotlin ecosystem does this.

## Beyond orchestration

Once you have a composable `Kap<A>` type, you can chain everything:

```kotlin
val result = Async {
    Kap { fetchUser() }
        .timeout(500.milliseconds)
        .withCircuitBreaker(breaker)
        .retry(Schedule.times<Throwable>(3) and Schedule.exponential(50.milliseconds))
        .recover { "cached-user" }
}
```

Timeout → circuit breaker → retry with exponential backoff → fallback. One flat chain.

For validation, `zipV` runs all validators in parallel and collects every error:

```kotlin
val result = Async {
    zipV(
        { validateName("A") },
        { validateEmail("bad") },
        { validateAge(10) },
    ) { name, email, age -> User(name, email, age) }
}
// Left(NonEmptyList(NameTooShort, InvalidEmail, AgeTooLow))
// ALL 3 errors in one response. Scales to 22 validators (Arrow maxes at 9).
```

## Production ready

- 900+ tests including property-based algebraic law verification
- 119 JMH benchmarks with regression tracking
- Multiplatform: JVM, JS, WASM, iOS, macOS, Linux
- Published on Maven Central
- Binary compatibility validator (no accidental API breaks)
- Apache 2.0

## Try it

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.4.0")
}
```

- [GitHub](https://github.com/damian-rafael-lattenero/kap)
- [Full Documentation](https://damian-rafael-lattenero.github.io/kap/)
- [Quickstart](https://damian-rafael-lattenero.github.io/kap/guide/quickstart/)
- [Benchmark Dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/)

---

*KAP is open source (Apache 2.0). Contributions welcome — check the [good first issues](https://github.com/damian-rafael-lattenero/kap/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).*
