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

You have 11 microservice calls. Some run in parallel, some depend on earlier results. With raw coroutines you get invisible phases, silent swap bugs, and 30 lines of `async`/`await` boilerplate. With KAP:

```kotlin
val checkout: CheckoutResult = Async {
    kap(::CheckoutResult)

        .with { fetchUser() }           // starts a coroutine
        .with { fetchCart() }            // starts another, in parallel
        .with { fetchPromos() }          // and another, all at the same time
        .with { fetchInventory() }       // four independent tasks, running together

        .then { validateStock() }        // waits for ALL four above to finish, then runs alone

        .with { calcShipping() }         // starts a new parallel group
        .with { calcTax() }              // runs alongside calcShipping
        .with { calcDiscounts() }        // three independent tasks again

        .andThen { partial ->            // waits, receives everything built so far
            kap(::FinalCheckout)
                .with { reservePayment(partial) }    // uses the partial result
                .with { applyLoyaltyPoints(partial) } // both run in parallel, both need partial
        }

        .with { generateConfirmation() } // one more parallel group after payment
        .with { sendEmail() }            // both fire at the same time
}
// 13 calls. 6 phases. The dependency graph IS the code shape.
// Swap any .with that returns a different type → compile error.
```

---

## Three concepts. That's it.

| You write | What happens | Think of it as |
|---|---|---|
| `.with { }` | Runs in parallel with everything else in the same phase | "and at the same time..." |
| `.then { }` | Waits for all above, then continues | "once that's done..." |
| `.andThen { ctx -> }` | Waits, passes the result, then continues | "using what we got..." |

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

900+ tests · Multiplatform (JVM, JS, WASM, Native) · Published on [Maven Central](https://central.sonatype.com/artifact/io.github.damian-rafael-lattenero/kap-core) · Apache 2.0

<p align="center">
  <a href="https://damian-rafael-lattenero.github.io/kap/guide/quickstart/"><strong>Get Started</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/"><strong>Documentation</strong></a> · <a href="https://damian-rafael-lattenero.github.io/kap/playground/"><strong>Cookbook</strong></a>
</p>
