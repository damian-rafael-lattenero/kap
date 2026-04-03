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
@KapTypeSafe
data class CheckoutResult(
    val user: UserProfile, val cart: ShoppingCart, val promos: PromotionBundle,
    val inventory: InventorySnapshot, val stock: StockValidation,
    val shipping: ShippingQuote, val tax: TaxBreakdown, val discounts: DiscountSummary,
    val payment: PaymentReservation, val confirmation: OrderConfirmation, val email: EmailReceipt,
)

val checkout: CheckoutResult = kap(::CheckoutResult)
    .withUser { fetchUser() }              // ┐
    .withCart { fetchCart() }               // ├─ phase 1: parallel
    .withPromos { fetchPromos() }           // │
    .withInventory { fetchInventory() }    // ┘
    .thenStock { validateStock() }         // ── phase 2: barrier
    .withShipping { calcShipping() }       // ┐
    .withTax { calcTax() }                 // ├─ phase 3: parallel
    .withDiscounts { calcDiscounts() }     // ┘
    .thenPayment { reservePayment() }      // ── phase 4: barrier
    .withConfirmation { generateConfirmation() }  // ┐ phase 5: parallel
    .withEmail { sendEmail() }             // ┘
    .executeGraph()
```

12 lines. Phases are explicit. And here's the key: **swap any two `.with` lines and the compiler rejects it.** Each service returns a distinct type, and the typed function chain locks parameter order at compile time.

## How it works

`kap(::CheckoutResult)` curries the constructor. Each `.with` applies the next argument. The resulting type shrinks: `Kap<(A) -> (B) -> ... -> R>` → `Kap<(B) -> ... -> R>` → ... → `Kap<R>`.

`.then` creates a real phase barrier: a `CompletableDeferred` that gates all subsequent work until everything above completes.

`.andThen { ctx -> }` does the same but passes the accumulated result — so phase 2 can use phase 1's data:

```kotlin
@KapTypeSafe
data class UserContext(val profile: String, val preferences: String, val loyaltyTier: String)
@KapTypeSafe
data class EnrichedContent(val recs: String, val promotions: String, val trending: String, val history: String)
@KapTypeSafe
data class FinalDashboard(val layout: String, val analytics: String)

val dashboard: FinalDashboard = kap(::UserContext)
    .withProfile { fetchProfile(userId) }           // ┐
    .withPreferences { fetchPreferences(userId) }   // ├─ phase 1
    .withLoyaltyTier { fetchLoyaltyTier(userId) }   // ┘
    .andThen { ctx ->                               // ── barrier: ctx available
        kap(::EnrichedContent)
            .withRecs { fetchRecommendations(ctx.profile) }       // ┐
            .withPromotions { fetchPromotions(ctx.loyaltyTier) }  // ├─ phase 2
            .withTrending { fetchTrending(ctx.preferences) }      // │
            .withHistory { fetchHistory(ctx.profile) }             // ┘
            .andThen { enriched ->                                 // ── barrier
                kap(::FinalDashboard)
                    .withLayout { renderLayout(ctx, enriched) }       // ┐ phase 3
                    .withAnalytics { trackAnalytics(ctx, enriched) }  // ┘
            }
    }
    .executeGraph()
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

We went further. KAP ships a KSP processor that generates step builders for you:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

// KSP generates named step builders — no wrapper types needed:
kap(::User)
    .withFirstName { fetchFirstName() }   // step only accepts .withFirstName
    .withLastName { fetchLastName() }     // swap? COMPILE ERROR — .withFirstName not available here
    .withAge { fetchAge() }               // each step shows only the next parameter
    .executeGraph()
```

One annotation. Zero runtime overhead (no wrapper types, no companion objects). Every same-type swap becomes a compile error — each step in the chain only exposes the method for the next parameter. Works on functions too, with optional prefix for collision avoidance. As far as we know, no other framework in the Kotlin ecosystem does this.

## Beyond orchestration

Once you have a composable `Kap<A>` type, you can chain everything:

```kotlin
val result = Kap { fetchUser() }
    .timeout(500.milliseconds)
    .withCircuitBreaker(breaker)
    .retry(Schedule.times<Throwable>(3) and Schedule.exponential(50.milliseconds))
    .recover { "cached-user" }
    .executeGraph()
```

Timeout → circuit breaker → retry with exponential backoff → fallback. One flat chain.

For validation, `zipV` runs all validators in parallel and collects every error:

```kotlin
val result = zipV(
    { validateName("A") },
    { validateEmail("bad") },
    { validateAge(10) },
) { name, email, age -> User(name, email, age) }
    .executeGraph()
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
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
}
```

- [GitHub](https://github.com/damian-rafael-lattenero/kap)
- [Full Documentation](https://damian-rafael-lattenero.github.io/kap/)
- [Quickstart](https://damian-rafael-lattenero.github.io/kap/guide/quickstart/)
- [Benchmark Dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/)

---

*KAP is open source (Apache 2.0). Contributions welcome — check the [good first issues](https://github.com/damian-rafael-lattenero/kap/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).*
