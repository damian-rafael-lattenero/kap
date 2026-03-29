<p align="center">
  <img src=".github/logo.png" alt="KAP Logo" width="350"/>
</p>

<h1 align="center">KAP — Kotlin Async Parallelism</h1>

<p align="center">
  <strong>Type-safe multi-service orchestration for Kotlin coroutines.</strong><br>
  Flat chains, visible phases, compiler-checked argument order.<br>
  <em>Your code shape <b>is</b> the execution plan.</em>
</p>

<p align="center">
  <a href="https://github.com/damian-rafael-lattenero/kap/actions/workflows/ci.yml"><img src="https://github.com/damian-rafael-lattenero/kap/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://central.sonatype.com/artifact/io.github.damian-rafael-lattenero/kap-core"><img src="https://img.shields.io/maven-central/v/io.github.damian-rafael-lattenero/kap-core?label=Maven%20Central&color=blue" alt="Maven Central"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://github.com/Kotlin/kotlinx.coroutines"><img src="https://img.shields.io/badge/Coroutines-1.10.2-7F52FF.svg" alt="Coroutines"></a>
</p>

<p align="center">
  <a href="#benchmarks"><img src="https://img.shields.io/badge/Tests-906%20across%2061%20suites-brightgreen.svg" alt="Tests"></a>
  <a href="https://damian-rafael-lattenero.github.io/kap/benchmarks/"><img src="https://img.shields.io/badge/Benchmarks-119%20JMH-blueviolet.svg" alt="Benchmarks"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License"></a>
  <a href="#modules"><img src="https://img.shields.io/badge/Multiplatform-JVM%20%7C%20JS%20%7C%20Native-orange.svg" alt="Multiplatform"></a>
</p>

<h2 align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/guide/quickstart/">🚀 Quick Start</a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://damian-rafael-lattenero.github.io/kap/">📖 Documentation</a>
</h2>

<p align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/playground/">Cookbook</a> · <a href="https://damian-rafael-lattenero.github.io/kap/comparison/">Comparison</a> · <a href="https://damian-rafael-lattenero.github.io/kap/blog/">Blog</a> · <a href="https://damian-rafael-lattenero.github.io/kap/guide/migration-coroutines/">Migration Guide</a> · <a href="https://github.com/damian-rafael-lattenero/kap-starter">Starter Template</a>
</p>

---

## 11 services. 5 phases. One flat chain.

**Raw Coroutines** — 30 lines, invisible phases, silent bugs:

```kotlin
val checkout = coroutineScope {
    val dUser = async { fetchUser() }
    val dCart = async { fetchCart() }
    val dPromos = async { fetchPromos() }
    val dInventory = async { fetchInventory() }
    val user = dUser.await()          // ← move above async? Silent serialization.
    val cart = dCart.await()           // ← swap with promos? Same type = no compiler error.
    val promos = dPromos.await()
    val inventory = dInventory.await()

    val stock = validateStock()       // Where does phase 1 end? Read every line to find out.

    val dShipping = async { calcShipping() }
    val dTax = async { calcTax() }
    val dDiscounts = async { calcDiscounts() }
    val shipping = dShipping.await()
    val tax = dTax.await()
    val discounts = dDiscounts.await()

    val payment = reservePayment()    // Another invisible barrier.

    val dConfirmation = async { generateConfirmation() }
    val dEmail = async { sendEmail() }

    CheckoutResult(
        user, cart, promos, inventory, stock,
        shipping, tax, discounts, payment,
        dConfirmation.await(), dEmail.await()
    )
}
```

**Arrow** — Parallel within phases, but you need intermediate data classes to carry values across boundaries:

```kotlin
// You need these just to pass values between phases:
data class Phase1(val user: UserProfile, val cart: ShoppingCart,
                  val promos: PromotionBundle, val inventory: InventorySnapshot)
data class Phase3(val shipping: ShippingQuote, val tax: TaxBreakdown,
                  val discounts: DiscountSummary)

// Phase 1: parallel fetch
val p1 = parZip(
    { fetchUser() },
    { fetchCart() },
    { fetchPromos() },
    { fetchInventory() },
) { user, cart, promos, inventory ->   // parZip maxes at 9 args
    Phase1(user, cart, promos, inventory)
}

// Phase 2: barrier (just a suspend call, no explicit marker)
val stock = validateStock()

// Phase 3: parallel calculations
val p3 = parZip(
    { calcShipping() },
    { calcTax() },
    { calcDiscounts() },
) { shipping, tax, discounts ->
    Phase3(shipping, tax, discounts)
}

// Phase 4: barrier
val payment = reservePayment()

// Phase 5: parallel finalization
val p5 = parZip(
    { generateConfirmation() },
    { sendEmail() },
) { confirmation, email ->
    Pair(confirmation, email)
}

// Assemble the result — manually thread all intermediate values:
val checkout = CheckoutResult(
    p1.user, p1.cart, p1.promos, p1.inventory,
    stock,
    p3.shipping, p3.tax, p3.discounts,
    payment,
    p5.first, p5.second,
)
// 40+ lines. Intermediate data classes. Manual assembly.
// Phases exist but invisible — where does one end and the next begin?
// Swap p1.user and p1.cart? Same type = no compiler error.
```

**KAP** — 12 lines, explicit phases, compile-time safe:

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

**Swap any two `.with` lines → compile error.** 130ms total vs 460ms sequential.

---

## Value-dependent phases with `.andThen`

When phase 2 **needs** phase 1's results:

```kotlin
val dashboard: FinalDashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(userId) }       // ┐
        .with { fetchPreferences(userId) }   // ├─ phase 1: parallel
        .with { fetchLoyaltyTier(userId) }   // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::EnrichedContent)
                .with { fetchRecommendations(ctx.profile) }  // ┐
                .with { fetchPromotions(ctx.tier) }           // ├─ phase 2: parallel
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

14 calls, 3 phases, **115ms vs 460ms sequential**. The dependency graph **is** the code shape.

---

## Nothing runs until `Async { }`

`Kap<A>` is lazy — it's a plan, not a running computation:

```kotlin
val plan: Kap<Dashboard> = kap(::Dashboard)
    .with { fetchUser() }
    .with { fetchCart() }
    .with { fetchPromos() }

println("Plan built. Nothing has executed yet.")

val result: Dashboard = Async { plan }  // NOW it runs
```

---

## Same-type safety with `@KapTypeSafe` (KSP)

Two `String` params? Swap them and nobody notices — until production. KAP's KSP processor fixes this:

```kotlin
@KapTypeSafe
data class User(val firstName: String, val lastName: String, val age: Int)

kapSafe(::User)
    .with { fetchFirstName().toFirstName() }   // generated UserFirstName
    .with { fetchLastName().toLastName() }     // generated UserLastName — swap? COMPILE ERROR
    .with { fetchAge().toAge() }               // generated UserAge
```

Works on functions too. `@KapTypeSafe(prefix = "Dashboard")` avoids collisions. [Full docs](https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/).

---

## Install

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")

    // Optional
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.5.0")  // Schedule, CircuitBreaker, Resource, timeoutRace
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.5.0")       // Parallel validation with error accumulation
    implementation("io.github.damian-rafael-lattenero:kap-ktor:2.5.0")        // Ktor server plugin
    testImplementation("io.github.damian-rafael-lattenero:kap-kotest:2.5.0")  // Test matchers
    implementation("io.github.damian-rafael-lattenero:kap-ksp-annotations:2.5.0")  // @KapTypeSafe
    ksp("io.github.damian-rafael-lattenero:kap-ksp:2.5.0")                         // KSP processor
}
```

## Benchmarks

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | **<0.01ms** |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | **180.98ms** |
| **timeoutRace** (primary wins) | 180.55ms | — | **30.34ms** |
| **Max validation arity** | — | 9 | **22** |

[Live benchmark dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/) · All claims backed by **119 JMH benchmarks** and deterministic virtual-time proofs.

---

<h2 align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/guide/quickstart/">🚀 Quick Start</a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://damian-rafael-lattenero.github.io/kap/">📖 Full Documentation</a>
</h2>

<p align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/playground/">Cookbook</a> · <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/">@KapTypeSafe</a> · <a href="https://github.com/damian-rafael-lattenero/kap/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22">Contribute</a> · <a href="https://github.com/damian-rafael-lattenero/kap/discussions">Discussions</a>
</p>

## License

Apache 2.0
