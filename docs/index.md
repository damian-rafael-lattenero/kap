---
hide:
  - navigation
  - toc
---

<style>
.md-content__inner { max-width: 900px; margin: 0 auto; }
.hero { text-align: center; padding: 2rem 0 1rem; }
.hero img { width: 160px; }
.hero h1 { font-size: 2.4rem; margin-top: 1rem; }
.hero p { font-size: 1.2rem; color: var(--md-default-fg-color--light); }
.features { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 1.5rem; margin: 2rem 0; }
.feature { padding: 1.2rem; border-radius: 8px; border: 1px solid var(--md-default-fg-color--lightest); }
.feature h3 { margin-top: 0; }
</style>

<div class="hero">
  <img src="assets/logo.svg" alt="KAP">
  <h1>KAP</h1>
  <p><strong>Type-safe multi-service orchestration for Kotlin coroutines.</strong><br>
  Flat chains, visible phases, compiler-checked argument order.</p>
  <p>
    <a href="guide/quickstart/" class="md-button md-button--primary">Get Started</a>
    <a href="https://github.com/damian-rafael-lattenero/kap" class="md-button">GitHub</a>
  </p>
</div>

---

## The Problem

You have 11 microservice calls. Some run in parallel, others depend on earlier results. With raw coroutines you get invisible phases, silent bugs, and 30+ lines of boilerplate:

```kotlin
val checkout = coroutineScope {
    val dUser = async { fetchUser() }
    val dCart = async { fetchCart() }
    val dPromos = async { fetchPromos() }
    val dInventory = async { fetchInventory() }
    val user = dUser.await()      // ← move this above async? Silent serialization.
    val cart = dCart.await()       // ← swap with promos? Same type, no compiler error.
    val promos = dPromos.await()
    val inventory = dInventory.await()
    // ... 20 more lines of shuttle variables
}
```

## The Solution

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

11 service calls. 5 phases. One flat chain. **Swap any two `.with` lines and the compiler rejects it.** 130ms total vs 460ms sequential.

---

<div class="features">
  <div class="feature">
    <h3>Visible Phases</h3>
    <p><code>.with</code> = parallel, <code>.then</code> = barrier. The code shape <em>is</em> the execution plan. No guessing where phases begin and end.</p>
  </div>
  <div class="feature">
    <h3>Compile-Time Safety</h3>
    <p>Each <code>.with</code> slot is typed. Swap two same-type services? The compiler catches it. No positional bugs.</p>
  </div>
  <div class="feature">
    <h3>Zero Overhead</h3>
    <p>JMH benchmarks show KAP overhead is indistinguishable from raw coroutines. No reflection, no code generation at runtime.</p>
  </div>
  <div class="feature">
    <h3>Multiplatform</h3>
    <p>JVM, JS, iOS, macOS, Linux. One dependency: <code>kotlinx-coroutines-core</code>.</p>
  </div>
  <div class="feature">
    <h3>Resilience Built-In</h3>
    <p>Schedule, CircuitBreaker, Resource, bracket, timeoutRace, raceQuorum. All composable in the chain.</p>
  </div>
  <div class="feature">
    <h3>Arrow Integration</h3>
    <p>Parallel validation with error accumulation. <code>zipV</code> scales to 22 validators (Arrow maxes at 9).</p>
  </div>
</div>

---

## Modules

Pick what you need:

| Module | What you get | Depends on |
|---|---|---|
| [`kap-core`](modules/kap-core.md) | `Kap`, `with`, `then`, `race`, `traverse`, `memoize`, `timeout`, `recover` | `kotlinx-coroutines-core` |
| [`kap-resilience`](modules/kap-resilience.md) | `Schedule`, `CircuitBreaker`, `Resource`, `bracket`, `timeoutRace`, `raceQuorum` | `kap-core` |
| [`kap-arrow`](modules/kap-arrow.md) | `zipV`, `withV`, `validated {}`, `attempt()`, `raceEither` | `kap-core` + Arrow |
| [`kap-ktor`](modules/kap-ktor.md) | Ktor plugin, circuit breaker registry, tracers, `respondAsync` | `kap-core` + Ktor |
| [`kap-kotest`](modules/kap-kotest.md) | `shouldSucceedWith`, `shouldFailWith`, timing & lifecycle matchers | `kap-core` |

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.3.0")

    // Optional
    implementation("io.github.damian-rafael-lattenero:kap-resilience:2.3.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.3.0")
    implementation("io.github.damian-rafael-lattenero:kap-ktor:2.3.0")
    testImplementation("io.github.damian-rafael-lattenero:kap-kotest:2.3.0")
}
```

---

## Benchmarks

All claims backed by **119 JMH benchmarks** and deterministic virtual-time proofs.

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | **<0.01ms** |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | **180.98ms** |
| **timeoutRace** (primary wins) | 180.55ms | -- | **30.34ms** |
| **Max validation arity** | -- | 9 | **22** |

[Live benchmark dashboard](https://damian-rafael-lattenero.github.io/kap/benchmarks/){ .md-button }
