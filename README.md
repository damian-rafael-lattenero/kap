<p align="center">
  <img src=".github/logo.png" alt="KAP Logo" width="350"/>
</p>

<h1 align="center">KAP</h1>

<p align="center">
  <strong>Stop wiring async calls manually.</strong><br>
  KAP makes your coroutine orchestration compile-time safe and structurally visible.
</p>

<p align="center">
  <a href="https://github.com/damian-rafael-lattenero/kap/actions/workflows/ci.yml"><img src="https://github.com/damian-rafael-lattenero/kap/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://central.sonatype.com/artifact/io.github.damian-rafael-lattenero/kap-core"><img src="https://img.shields.io/maven-central/v/io.github.damian-rafael-lattenero/kap-core?label=Maven%20Central&color=blue" alt="Maven Central"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License"></a>
</p>

<p align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/guide/quickstart/"><strong>Get Started</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/"><strong>Documentation</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/playground/"><strong>Cookbook</strong></a>
</p>

---

You have three async calls. They should run in parallel. Your code doesn't show it.

```kotlin
// KAP — what you mean is what you write
val dashboard: Dashboard = Async {
    kap(::Dashboard)
        .with { fetchUser() }       // ┐ all three start at t=0
        .with { fetchCart() }        // │ total time = max(30, 20, 10) = 30ms
        .with { fetchPromos() }      // ┘ not 60ms sequential
}
```

<details>
<summary><b>Same thing with raw coroutines</b></summary>

```kotlin
val dashboard = coroutineScope {
    val dUser  = async { fetchUser() }
    val dCart   = async { fetchCart() }
    val dPromos = async { fetchPromos() }
    Dashboard(
        dUser.await(),
        dCart.await(),
        dPromos.await()
    )
}
```

</details>

Now add a dependency: phase 2 needs phase 1's results.

```kotlin
// Fetch context first, then personalize — two phases, one chain
val dashboard = Async {
    kap(::UserContext)
        .with { fetchProfile(id) }           // ┐ phase 1: parallel
        .with { fetchPreferences(id) }       // │
        .with { fetchLoyaltyTier(id) }       // ┘
        .andThen { ctx ->                    // ── barrier: ctx available
            kap(::PersonalizedDashboard)
                .with { fetchRecommendations(ctx.profile) }  // ┐ phase 2: parallel
                .with { fetchPromotions(ctx.tier) }           // │ uses ctx
                .with { fetchTrending(ctx.prefs) }            // ┘
        }
}
```

<details>
<summary><b>Same thing with raw coroutines</b></summary>

```kotlin
val dashboard = coroutineScope {
    val dProfile = async { fetchProfile(id) }
    val dPrefs   = async { fetchPreferences(id) }
    val dTier    = async { fetchLoyaltyTier(id) }
    val ctx = UserContext(dProfile.await(), dPrefs.await(), dTier.await())

    val dRecs     = async { fetchRecommendations(ctx.profile) }
    val dPromos   = async { fetchPromotions(ctx.tier) }
    val dTrending = async { fetchTrending(ctx.prefs) }
    PersonalizedDashboard(dRecs.await(), dPromos.await(), dTrending.await())
}
```

</details>

---

## Three concepts. That's it.

| You write | What happens | Think of it as |
|---|---|---|
| `.with { }` | Runs in parallel with everything else in the same phase | "and at the same time..." |
| `.then { }` | Waits for all above, then continues | "once that's done..." |
| `.andThen { ctx -> }` | Waits, passes the result, then continues | "using what we got..." |

---

## And it scales.

11 microservice calls. 5 phases. Dependencies between them. One flat chain:

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)
        .with { fetchUser() }               // ┐
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

<details>
<summary><b>Same thing with raw coroutines (30 lines)</b></summary>

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

</details>

---

## What only KAP can do

- <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-core/#then-phase-barrier" target="_blank"><b>Phase barriers in flat chains</b></a> — <code>.then</code> creates visible ordering without nesting or shuttle variables
- <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-resilience/#schedule-composable-retry-policies" target="_blank"><b>Composable retry schedules</b></a> — <code>Schedule.exponential.jittered.and(Schedule.times(5))</code>
- <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-arrow/#zipv-parallel-validation-2-22-args" target="_blank"><b>Parallel validation up to 22 fields</b></a> — <code>zipV</code> accumulates ALL errors, no short-circuit
- <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-resilience/#racequorum-n-of-m-successes" target="_blank"><b>N-of-M quorum racing</b></a> — <code>raceQuorum(2, c1, c2, c3)</code> — first 2 to succeed win
- <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-resilience/#resource-composable-resource" target="_blank"><b>Composable resources</b></a> — <code>Resource.zip(db, cache, queue) { ... }</code> with guaranteed cleanup

<p>
  <a href="https://damian-rafael-lattenero.github.io/kap/" target="_blank"><b>Full API reference — every combinator, every module →</b></a>
</p>

---

## Install

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.5.0")
}
```

Optional modules: [resilience](https://damian-rafael-lattenero.github.io/kap/modules/kap-resilience/) (retry, circuit breaker, resources) · [arrow](https://damian-rafael-lattenero.github.io/kap/modules/kap-arrow/) (parallel validation) · [ktor](https://damian-rafael-lattenero.github.io/kap/modules/kap-ktor/) · [kotest](https://damian-rafael-lattenero.github.io/kap/modules/kap-kotest/) · [ksp](https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/)

---

## Benchmarks

| Dimension | Raw Coroutines | KAP |
|---|---|---|
| Framework overhead (arity 3) | <0.01ms | <0.01ms |
| Multi-phase (9 calls, 4 phases) | 180.85ms | 180.98ms |
| 5 parallel calls @ 50ms each | 50.27ms | 50.31ms |

Zero overhead. No reflection. No runtime code generation. [119 JMH benchmarks](https://damian-rafael-lattenero.github.io/kap/benchmarks/).

---

## `(String) -> (String) -> (String) -> String` — which is which?

Swap two `String` parameters and the compiler says nothing. Add `@KapTypeSafe` and it does. <a href="https://damian-rafael-lattenero.github.io/kap/modules/kap-ksp/" target="_blank"><b>See how →</b></a>

---

900+ tests · Multiplatform (JVM, JS, WASM, Native) · Published on [Maven Central](https://central.sonatype.com/artifact/io.github.damian-rafael-lattenero/kap-core) · Apache 2.0

<p align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/guide/quickstart/"><strong>Get Started</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/"><strong>Documentation</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/playground/"><strong>Cookbook</strong></a>
</p>
