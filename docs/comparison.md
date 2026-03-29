# KAP vs Arrow vs Raw Coroutines

An honest comparison. KAP doesn't replace Arrow or raw coroutines — it solves a specific problem better.

## When to use what

| Scenario | Best tool |
|---|---|
| 2-3 simple parallel calls | Raw coroutines (`coroutineScope { async {} }`) |
| Purely sequential code | Regular `suspend` functions |
| Stream processing | `Flow` |
| Full FP ecosystem (optics, typeclasses) | Arrow |
| Multi-phase orchestration (4+ calls, dependencies) | **KAP** |
| Parallel validation with 10+ fields | **KAP** (`kap-arrow`) |
| Visible dependency graph in code | **KAP** |

## Feature comparison

| Feature | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Multi-phase orchestration** | Nested scopes, shuttle vars | Nested `parZip` blocks | Flat chain with `.then` |
| **Compile-time arg order safety** | No (positional) | No (named lambda) | **Typed function chain** |
| **Partial failure tolerance** | `supervisorScope` (manual) | Not built-in | **`.settled()`** |
| **Timeout + parallel fallback** | Sequential | Not built-in | **`timeoutRace`** (2.6x faster) |
| **Quorum (N-of-M)** | Manual `select` + counting | Not built-in | **`raceQuorum`** |
| **Success-only memoization** | Manual Mutex + cache | Not built-in | **`.memoizeOnSuccess()`** |
| **Parallel validation** | Cancels siblings | `zipOrAccumulate` (max 9) | `zipV` **(max 22)** |
| **Value-dependent phases** | Manual variable threading | Sequential `parZip` | `.andThen` |
| **Retry + backoff** | Manual loop (~20 lines) | `Schedule` | `Schedule` (composable) |
| **Resource safety** | try/finally nesting | `Resource` monad | `bracket` / `Resource` |
| **Racing** | Complex `select` | `raceN` | `raceN` + `raceEither` |
| **Bounded traversal** | Manual Semaphore | `parMap(concurrency)` | `traverse(concurrency)` |
| **Circuit breaker** | Manual state machine | Separate module | Composable in chain |
| **Flat multi-phase code** | No | No | **Yes** |

**Bold** = unique to KAP or significantly better.

## Performance

All numbers from JMH benchmarks on JDK 21, Ubuntu 24.04:

| Dimension | Raw Coroutines | Arrow | KAP |
|---|---|---|---|
| **Framework overhead** (arity 3) | <0.01ms | 0.02ms | <0.01ms |
| **Framework overhead** (arity 9) | <0.01ms | 0.03ms | <0.01ms |
| **Simple parallel** (5 x 50ms) | 50.27ms | 50.33ms | 50.31ms |
| **Multi-phase** (9 calls, 4 phases) | 180.85ms | 181.06ms | 180.98ms |
| **Race** (50ms vs 100ms) | 100.34ms | 50.51ms | 50.40ms |
| **timeoutRace** (primary wins) | 180.55ms | -- | **30.34ms** |
| **Max validation arity** | -- | 9 | **22** |

KAP overhead is indistinguishable from raw coroutines. No reflection, no runtime codegen.

## Code comparison: 11-service checkout

=== "Raw Coroutines"

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

=== "KAP"

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

30 lines vs 12 lines. Invisible phases vs explicit phases. Silent bugs vs compile-time safety.

## KAP + Arrow together

KAP doesn't compete with Arrow — they complement each other. The `kap-arrow` module bridges both:

```kotlin
dependencies {
    implementation("io.github.damian-rafael-lattenero:kap-core:2.4.0")
    implementation("io.github.damian-rafael-lattenero:kap-arrow:2.4.0")
}
```

Use Arrow's `Either`, `NonEmptyList`, and type system. Use KAP's orchestration, phases, and `zipV` for higher-arity validation.
